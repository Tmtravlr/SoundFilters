package com.tmtravlr.soundfilters.filters;

public abstract class BaseFilter {
    public boolean isLoaded = false;
    public boolean isEnabled = false;
    public int id = -1;
    public int slot = -1;

    public abstract void loadFilter();

    public abstract void checkParameters();

    public abstract void loadParameters();

    public void enable() {
        this.isEnabled = true;
    }

    public void disable() {
        this.isEnabled = false;
    }

}
