package com.tmtravlr.soundfilters.handlers;

import com.tmtravlr.soundfilters.SoundFiltersConfig;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

/**
 * Holds the methods for calculating and managing the low pass
 * @since December 2019
 */
public class LiquidLowPassHandler {
    private static final Minecraft MC = Minecraft.getInstance();

    private static boolean waterSound = false;
    private static boolean lavaSound = false;
    private static float targetLowPassGain = 1.0F;
    private static float targetLowPassGainHF = 1.0F;
    private static float baseLowPassGain = 1.0F;
    private static float baseLowPassGainHF = 1.0F;

    public static float getLowPassGain() {
        return baseLowPassGain;
    }

    public static float getLowPassGainHF() {
        return baseLowPassGainHF;
    }

    /**
     * Stops the low pass, for instance in menus
     */
    public static void stopLiquidLowPass() {
        baseLowPassGain = 1.0F;
        baseLowPassGainHF = 1.0F;
        targetLowPassGain = 1.0F;
        targetLowPassGainHF = 1.0F;
        lavaSound = false;
        waterSound = false;
    }

    /**
     * Checks if the player is in water or lava, and updates the low pass
     */
    public static void updateLiquidLowPass() {
        if (SoundFiltersConfig.LOW_PASS_ENABLED.get()) {
            // Handle the low-pass inside of liquids
            if (MC.world.getBlockState(new BlockPos(MC.player.getEyePosition(1.0F))).getMaterial() == Material.WATER) {
                if (!waterSound) {
                    targetLowPassGain = SoundFiltersConfig.LOW_PASS_WATER_GAIN.get().floatValue();
                    targetLowPassGainHF = SoundFiltersConfig.LOW_PASS_WATER_GAIN_HF.get().floatValue();
                    lavaSound = false;
                    waterSound = true;
                }
            } else if (waterSound) {
                targetLowPassGain = 1.0F;
                targetLowPassGainHF = 1.0F;
                waterSound = false;
            }

            if (MC.world.getBlockState(new BlockPos(MC.player.getEyePosition(1.0F))).getMaterial() == Material.LAVA) {
                if (!lavaSound) {
                    targetLowPassGain = SoundFiltersConfig.LOW_PASS_LAVA_GAIN.get().floatValue();
                    targetLowPassGainHF = SoundFiltersConfig.LOW_PASS_LAVA_GAIN_HF.get().floatValue();
                    lavaSound = true;
                    waterSound = false;
                }
            } else if (lavaSound) {
                targetLowPassGain = 1.0F;
                targetLowPassGainHF = 1.0F;
                lavaSound = false;
            }

            if (Math.abs(targetLowPassGain - baseLowPassGain) > 0.001F) {
                baseLowPassGain = (targetLowPassGain + baseLowPassGain) / 2;
            }

            if (Math.abs(targetLowPassGainHF - baseLowPassGainHF) > 0.001F) {
                baseLowPassGainHF = (targetLowPassGainHF + baseLowPassGainHF) / 2;
            }
        }
    }
}
