package space.qouve.core.models;


import space.qouve.core.CrayonUtil;

public interface FeatureProvider {
    Feature createInstance(CrayonUtil plugin);
}