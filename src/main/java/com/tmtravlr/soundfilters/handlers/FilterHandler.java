package com.tmtravlr.soundfilters.handlers;

import com.tmtravlr.soundfilters.SoundFiltersConfig;
import com.tmtravlr.soundfilters.SoundFiltersMod;
import com.tmtravlr.soundfilters.filters.BaseFilter;
import com.tmtravlr.soundfilters.filters.FilterException;
import com.tmtravlr.soundfilters.filters.FilterLowPass;
import com.tmtravlr.soundfilters.filters.FilterReverb;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.ReportedException;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Initializes and updates the filters for the sound sources.
 *
 * @author Tmtravlr (Rebeca Rey)
 * @since January 2020
 */
public class FilterHandler {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final FilterLowPass FILTER_LOW_PASS = new FilterLowPass();
    private static final FilterReverb FILTER_REVERB = new FilterReverb();

    public static final ConcurrentHashMap<ISound, Integer> SOURCE_IDS = new ConcurrentHashMap<>();

    public static void initializeReverbFilter() {
        FILTER_REVERB.density = SoundFiltersConfig.REVERB_ADVANCED_DENSITY.get().floatValue();
        FILTER_REVERB.diffusion = SoundFiltersConfig.REVERB_ADVANCED_DIFFUSION.get().floatValue();
        FILTER_REVERB.gain = SoundFiltersConfig.REVERB_ADVANCED_GAIN.get().floatValue();
        FILTER_REVERB.gainHF = SoundFiltersConfig.REVERB_ADVANCED_GAIN_HF.get().floatValue();
        FILTER_REVERB.decayTime = SoundFiltersConfig.REVERB_ADVANCED_DECAY_TIME_MIN.get().floatValue();
        FILTER_REVERB.decayHFRatio = SoundFiltersConfig.REVERB_ADVANCED_DECAY_HF_RATIO.get().floatValue();
        FILTER_REVERB.reflectionsGain = 0;
        FILTER_REVERB.reflectionsDelay = 0;
        FILTER_REVERB.lateReverbGain = 0;
        FILTER_REVERB.lateReverbDelay = 0;
        FILTER_REVERB.airAbsorptionGainHF = SoundFiltersConfig.REVERB_ADVANCED_AIR_ABSORPTION_GAIN_HF.get().floatValue();
        FILTER_REVERB.roomRolloffFactor = 0;
    }

    public static void updateReverbFilter(float decayFactor, float roomFactor, float skyFactor) {
        float reverbPercent = SoundFiltersConfig.REVERB_PERCENT.get().floatValue();
        float minDecayTime = SoundFiltersConfig.REVERB_ADVANCED_DECAY_TIME_MIN.get().floatValue();
        float reflectionGainBase = SoundFiltersConfig.REVERB_ADVANCED_REFLECTIONS_GAIN_BASE.get().floatValue();
        float reflectionGainMultiplier = SoundFiltersConfig.REVERB_ADVANCED_REFLECTIONS_GAIN_MULTIPLIER.get().floatValue();
        float reflectionDelayMultiplier = SoundFiltersConfig.REVERB_ADVANCED_REFLECTIONS_DELAY_MULTIPLIER.get().floatValue();
        float lateReverbGainBase = SoundFiltersConfig.REVERB_ADVANCED_LATE_REVERB_GAIN_BASE.get().floatValue();
        float lateReverbGainMultiplier = SoundFiltersConfig.REVERB_ADVANCED_LATE_REVERB_GAIN_MULTIPLIER.get().floatValue();
        float lateReverbDelayMultiplier = SoundFiltersConfig.REVERB_ADVANCED_LATE_REVERB_DELAY_MULTIPLIER.get().floatValue();

        float decayTime = reverbPercent * 6.0F * decayFactor * roomFactor * skyFactor;

        if (decayTime < minDecayTime) {
            decayTime = minDecayTime;
        }

        FILTER_REVERB.decayTime = decayTime;
        FILTER_REVERB.reflectionsGain = reverbPercent * (reflectionGainBase + (reflectionGainMultiplier * roomFactor));
        FILTER_REVERB.reflectionsDelay = reflectionDelayMultiplier * roomFactor;
        FILTER_REVERB.lateReverbGain = reverbPercent * (lateReverbGainBase + (lateReverbGainMultiplier * roomFactor));
        FILTER_REVERB.lateReverbDelay = lateReverbDelayMultiplier * roomFactor;
    }

    public static void updateSourceFilters(ISound sound) {
        if (sound != null) {
            Integer sourceId = SOURCE_IDS.get(sound);

            if (sourceId != null) {
                if (MC.world != null) {

                    double occlusionAmount = OcclusionHandler.getOcclusion(sound);
                    float lowPassGain = LiquidLowPassHandler.getLowPassGain();
                    float lowPassGainHF = LiquidLowPassHandler.getLowPassGainHF();

                    if (occlusionAmount > 0.05 && !(sound.getAttenuationType() == ISound.AttenuationType.NONE || sound.isGlobal())) {
                        lowPassGain = (float) ((double) lowPassGain * (1.0D - 1.0D * occlusionAmount));
                        lowPassGainHF = (float) ((double) lowPassGainHF * (1.0D - 1.0D * (double) MathHelper.sqrt(occlusionAmount)));
                    }

                    if (lowPassGain >= 1.0F && lowPassGainHF >= 1.0F) {
                        FILTER_LOW_PASS.disable();
                    } else {
                        FILTER_LOW_PASS.gain = lowPassGain;
                        FILTER_LOW_PASS.gainHF = lowPassGainHF;

                        FILTER_LOW_PASS.enable();
                        FILTER_LOW_PASS.loadParameters();
                    }

                    if (FILTER_REVERB.reflectionsDelay <= 0.0F && FILTER_REVERB.lateReverbDelay <= 0.0F) {
                        FILTER_REVERB.disable();
                    } else {
                        FILTER_REVERB.enable();

                        if (!sound.isGlobal() && sound.getAttenuationType() != ISound.AttenuationType.NONE) {
                            FILTER_REVERB.roomRolloffFactor = 2 / (Math.max(sound.getVolume(), 1) + 2);
                        }

                        FILTER_REVERB.loadParameters();
                    }

                    try {
                        loadSourceFilter(sourceId, EXTEfx.AL_DIRECT_FILTER, FILTER_LOW_PASS);
                        load3SourceFilters(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, FILTER_REVERB, null, FILTER_LOW_PASS);
                    } catch (FilterException e) {
                        e.printStackTrace();
                        SoundFiltersMod.LOGGER.error("Error while updating sound filters", e);
                        CrashReport crashreport = CrashReport.makeCrashReport(e, "Updating Sound Filters");
                        throw new ReportedException(crashreport);
                    }
                } else {
                    try {
                        loadSourceFilter(sourceId, EXTEfx.AL_DIRECT_FILTER, null);
                        load3SourceFilters(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, null, null, null);
                    } catch (FilterException e) {
                        SoundFiltersMod.LOGGER.error("Error while removing sound filters", e);
                        CrashReport crashreport = CrashReport.makeCrashReport(e, "Removing Sound Filters");
                        throw new ReportedException(crashreport);
                    }

                    SOURCE_IDS.remove(sound);
                }
            }
        }
    }

    private static void load3SourceFilters(int sourceChannel, int type, BaseFilter filter1, BaseFilter filter2, BaseFilter filter3) throws FilterException {
        AL11.alSource3i(sourceChannel, type, safeSlot(filter1), safeSlot(filter2), safeSlot(filter3));

        //Reload and retry if there was an error, and check again.
        if (checkError("load3SourceFilters attempt 1") != 0) {
            if (filter1 != null) {
                filter1.isLoaded = false;
                filter1.loadFilter();
            }

            if (filter2 != null) {
                filter2.isLoaded = false;
                filter2.loadFilter();
            }

            if (filter3 != null) {
                filter3.isLoaded = false;
                filter3.loadFilter();
            }

            int error = checkError("load3SourceFilters attempt 2");
            if (error != 0) {
                throw new FilterException("Sound Filters - Error while trying to load 3 source filters. Error code is " + getErrorMessage(error));
            }
        }
    }

    private static void loadSourceFilter(int sourceChannel, int type, BaseFilter filter) throws FilterException {
        AL10.alSourcei(sourceChannel, type, safeSlot(filter));

        //Reload and retry if there was an error, and check again.
        if (checkError("loadSourceFilter attempt 1") != 0) {
            if (filter != null) {
                filter.isLoaded = false;
                filter.loadFilter();
            }

            AL10.alSourcei(sourceChannel, type, safeSlot(filter));

            int error = checkError("loadSourceFilter attempt 2");
            if (error != AL10.AL_NO_ERROR) {
                throw new FilterException("Sound Filters - Error while trying to load source filter. Error code is " + getErrorMessage(error));
            }
        }
    }

    private static int safeSlot(BaseFilter filter) {
        return filter != null && filter.isEnabled && filter.slot != -1 ? filter.slot : 0;
    }

    private static int checkError(String location) {
        int error = AL10.alGetError();

        if (error != AL10.AL_NO_ERROR) {
            SoundFiltersMod.LOGGER.error("Caught AL error in '" + location + "'! Error is " + getErrorMessage(error));
            return error;
        }

        return 0;
    }

    private static String getErrorMessage(int error) {
        switch(error) {
            case AL10.AL_INVALID_NAME:
                return "AL_INVALID_NAME (" + error + ")";
            case AL10.AL_INVALID_ENUM:
                return "AL_INVALID_ENUM (" + error + ")";
            case AL10.AL_INVALID_VALUE:
                return "AL_INVALID_VALUE (" + error + ")";
            case AL10.AL_INVALID_OPERATION:
                return "AL_INVALID_OPERATION (" + error + ")";
            case AL10.AL_OUT_OF_MEMORY:
                return "AL_OUT_OF_MEMORY (" + error + ")";
            default:
                return "Unrecognized Error (" + error + ")";
        }
    }

}
