package space.qouve.core.models;

import org.bukkit.command.CommandSender;
import space.qouve.core.CrayonUtil;

import java.util.List;

public abstract class SubCommand {
    private final String id;
    protected final CrayonUtil plugin;

    protected SubCommand(String id, CrayonUtil plugin) {
        this.id = id.toLowerCase();
        this.plugin = plugin;
    }

    public String getId() {
        return id;
    }

    public abstract void run(CommandSender sender, String[] args);

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}