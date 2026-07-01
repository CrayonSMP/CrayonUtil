package space.qouve.core.models;


import space.qouve.core.CrayonUtil;

public interface SubCommandProvider {
    SubCommand createInstance(CrayonUtil plugin);
}