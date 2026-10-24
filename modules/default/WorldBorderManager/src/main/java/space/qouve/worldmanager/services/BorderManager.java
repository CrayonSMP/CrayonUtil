package space.qouve.worldmanager.services;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import space.qouve.worldmanager.WorldBorderManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class BorderManager {

    // Countdown-Zeitpunkte in Sekunden vor dem Start (absteigend sortiert):
    // 5 min, 3 min, 1 min, 30 s, dann 10 -> 0 sekündlich
    private static final long[] COUNTDOWN_STEPS = {300, 180, 60, 30, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0};

    private LocalDateTime startDate;
    private double initialSize;

    private long freezeDuration;
    private ChronoUnit freezeUnit;

    private LocalDateTime growthStartDate;

    private double growthAmount;
    private long growthInterval;
    private ChronoUnit growthUnit;
    private long animationSpeed;

    private List<String> startItemKeys;

    private String countdownMessage;
    private String countdownStartMessage;
    private String unitMinutes;
    private String unitMinute;
    private String unitSeconds;
    private String unitSecond;

    private WorldBorderManager feature;

    public BorderManager(WorldBorderManager feature) {
        this.feature = feature;
        YamlConfiguration config = WorldBorderManager.getFeatureConfig();
        Logger logger = feature.getPlugin().getLogger();

        if (config == null) {
            logger.severe("[WorldBorder] Configuration could not be loaded because it is null!");
            return;
        }

        this.initialSize = config.getDouble("initial-size", 1000.0);

        String startDateStr = config.getString("start-date", "NOW");
        this.startDate = parseStartDate(startDateStr, logger);

        this.startItemKeys = config.getStringList("items-on-start-date");

        this.countdownMessage = config.getString("start-countdown.message", "CrayonSMP starts in {time} {unit}!");
        this.countdownStartMessage = config.getString("start-countdown.start-message", "CrayonSMP has started!");
        this.unitMinutes = config.getString("start-countdown.units.minutes", "minutes");
        this.unitMinute = config.getString("start-countdown.units.minute", "minute");
        this.unitSeconds = config.getString("start-countdown.units.seconds", "seconds");
        this.unitSecond = config.getString("start-countdown.units.second", "second");

        this.freezeDuration = config.getLong("freeze.duration", 7);
        this.freezeUnit = parseChronoUnit(config.getString("freeze.unit", "DAYS"), ChronoUnit.DAYS, logger);

        this.growthStartDate = this.startDate.plus(this.freezeDuration, this.freezeUnit);

        this.growthAmount = config.getDouble("growth.amount", 50.0);
        this.growthInterval = config.getLong("growth.interval", 1);
        this.growthUnit = parseChronoUnit(config.getString("growth.unit", "DAYS"), ChronoUnit.DAYS, logger);
        this.animationSpeed = config.getLong("growth.animation-speed", 300);

        feature.debug("[WorldBorder-Debug] Configuration successfully loaded:");
        feature.debug(" > Start-Date: " + this.startDate + (startDateStr.equalsIgnoreCase("NOW") ? " (NOW)" : ""));
        feature.debug(" > Initial-Size: " + this.initialSize);
        feature.debug(" > Freeze: " + this.freezeDuration + " " + this.freezeUnit);
        feature.debug(" > Growth Start: " + this.growthStartDate);
        feature.debug(" > Growth: +" + this.growthAmount + " every " + this.growthInterval + " " + this.growthUnit + " (Speed: " + this.animationSpeed + "s)");

        startCountdown();
        startScheduler();
    }

    private LocalDateTime parseStartDate(String dateStr, Logger logger) {
        if (dateStr.equalsIgnoreCase("NOW")) {
            return LocalDateTime.now();
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy-HH:mm");
            return LocalDateTime.parse(dateStr, formatter);
        } catch (Exception e) {
            logger.warning("[WorldBorder] Invalid format for 'start-date' (" + dateStr + "). Using 'NOW' instead.");
            return LocalDateTime.now();
        }
    }

    private ChronoUnit parseChronoUnit(String unitStr, ChronoUnit fallback, Logger logger) {
        try {
            return ChronoUnit.valueOf(unitStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("[WorldBorder] Invalid time unit '" + unitStr + "'. Using fallback: " + fallback);
            return fallback;
        }
    }

    /**
     * Sendet Countdown-Nachrichten vor dem Start:
     * 5 min, 3 min, 1 min, 30 s und anschließend 10, 9, ... 1, 0.
     * Wird nur gestartet, wenn der Startzeitpunkt noch in der Zukunft liegt.
     */
    private void startCountdown() {
        if (!LocalDateTime.now().isBefore(startDate)) {
            return;
        }

        final Set<Long> handled = new HashSet<>();

        // Alle 5 Ticks prüfen (4x pro Sekunde), damit kein Schritt durch Ticks-Drift verpasst wird
        new BukkitRunnable() {
            @Override
            public void run() {
                long millisLeft = ChronoUnit.MILLIS.between(LocalDateTime.now(), startDate);
                long secondsLeft = millisLeft <= 0 ? 0 : (long) Math.ceil(millisLeft / 1000.0);

                for (long step : COUNTDOWN_STEPS) {
                    if (secondsLeft <= step && handled.add(step)) {
                        if (secondsLeft >= step - 1) {
                            broadcastCountdown(step);
                        }
                    }
                }

                if (secondsLeft <= 0) {
                    cancel();
                }
            }
        }.runTaskTimer(feature.getPlugin(), 0L, 5L);
    }

    private void broadcastCountdown(long secondsLeft) {
        if (secondsLeft <= 0) {
            Bukkit.broadcastMessage(countdownStartMessage);
            return;
        }

        long time;
        String unit;

        if (secondsLeft >= 60) {
            time = secondsLeft / 60;
            unit = time == 1 ? unitMinute : unitMinutes;
        } else {
            time = secondsLeft;
            unit = time == 1 ? unitSecond : unitSeconds;
        }

        Bukkit.broadcastMessage(countdownMessage
                .replace("{time}", String.valueOf(time))
                .replace("{unit}", unit));
    }

    private void startScheduler() {
        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(growthStartDate)) {
            long secondsUntilStart = ChronoUnit.SECONDS.between(now, startDate);

            if (secondsUntilStart > 0) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        setupBorderToInitial();
                    }
                }.runTaskLater(feature.getPlugin(), secondsUntilStart * 20L);
            } else {
                setupBorderToInitial();
            }
        } else {
            feature.debug("[WorldBorder-Debug] Growth phase already active or passed. Skipping initial-size enforcement.");
        }

        LocalDateTime nextExecution = growthStartDate;

        if (now.isAfter(growthStartDate)) {
            long unitsPassed = growthStartDate.until(now, growthUnit);
            long intervalsPassed = unitsPassed / growthInterval;

            nextExecution = growthStartDate.plus((intervalsPassed + 1) * growthInterval, growthUnit);
        }

        long secondsUntilNextGrowth = ChronoUnit.SECONDS.between(now, nextExecution);
        long delayTicks = Math.max(0, secondsUntilNextGrowth) * 20L;
        long intervalTicks = growthUnit.getDuration().getSeconds() * growthInterval * 20L;

        feature.debug("[WorldBorder-Debug] Next calculated growth scheduled at: " + nextExecution + " (in " + secondsUntilNextGrowth + "s)");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (LocalDateTime.now().isBefore(growthStartDate)) {
                    return;
                }

                Bukkit.getWorlds().forEach(world -> {
                    org.bukkit.WorldBorder border = world.getWorldBorder();
                    double currentSize = border.getSize();
                    double newSize = currentSize + growthAmount;

                    border.changeSize(newSize, animationSpeed);

                    feature.debug("[WorldBorder-Debug] Border of world: " + world.getName() + " increased from " + currentSize + " to " + newSize + " over " + animationSpeed + "s");
                });
            }
        }.runTaskTimer(feature.getPlugin(), delayTicks, intervalTicks);
    }

    private void setupBorderToInitial() {
        Bukkit.getWorlds().forEach(world -> {
            world.getWorldBorder().setSize(initialSize);
        });
        feature.debug("[WorldBorder-Debug] World border size set to initial size: " + initialSize);
    }

    public LocalDateTime getStartDate() { return startDate; }
    public double getInitialSize() { return initialSize; }
    public long getFreezeDuration() { return freezeDuration; }
    public ChronoUnit getFreezeUnit() { return freezeUnit; }
    public double getGrowthAmount() { return growthAmount; }
    public long getGrowthInterval() { return growthInterval; }
    public ChronoUnit getGrowthUnit() { return growthUnit; }
    public long getAnimationSpeed() { return animationSpeed; }
    public List<String> getStartItemKeys() { return startItemKeys; }
}