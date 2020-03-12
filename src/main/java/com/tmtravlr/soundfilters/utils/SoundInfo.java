package com.tmtravlr.soundfilters.utils;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundSource;

/**
 * Holds info about a sound and its source together.
 * @since December 2019
 */
public class SoundInfo implements Comparable<SoundInfo> {
    public ISound sound;
    public SoundSource source;
    public int sourceId;

    public SoundInfo(ISound sound, SoundSource source, int sourceId) {
        this.sound = sound;
        this.source = source;
        this.sourceId = sourceId;
    }

    @Override
    public int compareTo(SoundInfo other) {
        if (other == this) {
            return 0;
        }

        if (other == null) {
            return 1;
        }

        if (this.sourceId != other.sourceId) {
            return this.sourceId - other.sourceId;
        }

        if (this.sound != other.sound) {
            if (this.sound == null) {
                return -1;
            }

            if (other.sound == null) {
                return 1;
            }

            return this.sound.getSoundLocation().compareTo(other.sound.getSoundLocation());
        }

        return this.source.hashCode() - other.source.hashCode();
    }
}
