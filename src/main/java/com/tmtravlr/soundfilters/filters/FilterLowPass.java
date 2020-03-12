package com.tmtravlr.soundfilters.filters;

import org.lwjgl.openal.EXTEfx;

public class FilterLowPass extends BaseFilter
{
    public float gain = 1.0F;
    public float gainHF = 1.0F;

    public void loadFilter()
    {
        if (!this.isLoaded)
        {
            this.isLoaded = true;
            this.id = EXTEfx.alGenFilters();
            this.slot = this.id;
            EXTEfx.alFilteri(this.id, 32769, 1);
        }
    }

    public void checkParameters()
    {
        if (this.gain < 0.0F)
        {
            this.gain = 0.0F;
        }

        if (this.gain > 1.0F)
        {
            this.gain = 1.0F;
        }

        if (this.gainHF < 0.0F)
        {
            this.gainHF = 0.0F;
        }

        if (this.gainHF > 1.0F)
        {
            this.gainHF = 1.0F;
        }
    }

    public void loadParameters()
    {
        this.checkParameters();

        if (!this.isLoaded)
        {
            this.loadFilter();
        }

        EXTEfx.alFilterf(this.id, 1, this.gain);
        EXTEfx.alFilterf(this.id, 2, this.gainHF);
    }
}
