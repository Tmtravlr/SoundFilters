package com.tmtravlr.soundfilters;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tmtravlr.soundfilters.handlers.ReverbHandler;
import net.minecraft.command.arguments.BlockPredicateArgument;
import net.minecraft.util.CachedBlockInfo;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/**
 * Loads and holds all the config values.
 *
 * @author Tmtravlr (Rebeca Rey)
 * @since December 2019
 */
public class SoundFiltersConfig {

    private static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();

    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("soundfilters-client.toml");
    public static ForgeConfigSpec CLIENT_CONFIG;
    public static Predicate<Object> LIST_VALIDATOR = entry -> entry instanceof String && !((String)entry).isEmpty() && !((String)entry).startsWith("#") && ((String)entry).split(" ").length >= 2;

    private static final String CATEGORY_LOW_PASS = "lowPass";
    public static ForgeConfigSpec.BooleanValue LOW_PASS_ENABLED;

    private static final String CATEGORY_LOW_PASS_WATER = "lowPassWater";
    public static ForgeConfigSpec.DoubleValue LOW_PASS_WATER_GAIN;
    public static ForgeConfigSpec.DoubleValue LOW_PASS_WATER_GAIN_HF;

    private static final String CATEGORY_LOW_PASS_LAVA = "lowPassLava";
    public static ForgeConfigSpec.DoubleValue LOW_PASS_LAVA_GAIN;
    public static ForgeConfigSpec.DoubleValue LOW_PASS_LAVA_GAIN_HF;

    private static final String CATEGORY_OCCLUSION = "occlusion";
    public static ForgeConfigSpec.BooleanValue OCCLUSION_ENABLED;
    public static ForgeConfigSpec.DoubleValue OCCLUSION_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue OCCLUSION_MAX;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> OCCLUSION_BLOCK_ENTRIES;
    public static Map<BlockPredicateArgument.IResult, Double> OCCLUSION_BLOCKS = new HashMap<>();

    private static final String CATEGORY_REVERB = "reverb";
    public static ForgeConfigSpec.BooleanValue REVERB_ENABLED;
    public static ForgeConfigSpec.DoubleValue REVERB_PERCENT;
    public static ForgeConfigSpec.BooleanValue REVERB_SKY_CHECKS;
    public static ForgeConfigSpec.IntValue REVERB_MAX_BLOCKS;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> REVERB_BLOCK_ENTRIES;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> REVERB_DIMENSION_ENTRIES;
    public static Map<BlockPredicateArgument.IResult, Double> REVERB_BLOCKS = new HashMap<>();
    public static Map<ResourceLocation, Double> REVERB_DIMENSIONS = new HashMap<>();

    private static final String CATEGORY_REVERB_ADVANCED = "reverbAdvanced";
    public static ForgeConfigSpec.DoubleValue REVERB_ADVANCED_DENSITY;
    public static ForgeConfigSpec.DoubleValue REVERB_ADVANCED_DIFFUSION;
    public static ForgeConfigSpec.DoubleValue REVERB_ADVANCED_GAIN;
    public static ForgeConfigSpec.DoubleValue REVERB_ADVANCED_GAIN_HF;
    public static ForgeConfigSpec.DoubleValue REVERB_ADVANCED_DECAY_TIME_MIN;
    public static ForgeConfigSpec.DoubleValue REVERB_ADVANCED_DECAY_HF_RATIO;
    public static ForgeConfigSpec.DoubleValue REVERB_ADVANCED_AIR_ABSORPTION_GAIN_HF;
    public static ForgeConfigSpec.DoubleValue REVERB_ADVANCED_REFLECTIONS_GAIN_BASE;
    public static ForgeConfigSpec.DoubleValue REVERB_ADVANCED_REFLECTIONS_GAIN_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue REVERB_ADVANCED_REFLECTIONS_DELAY_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue REVERB_ADVANCED_LATE_REVERB_GAIN_BASE;
    public static ForgeConfigSpec.DoubleValue REVERB_ADVANCED_LATE_REVERB_GAIN_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue REVERB_ADVANCED_LATE_REVERB_DELAY_MULTIPLIER;

    public static Double getCustomBlockOcclusion(World world, CachedBlockInfo blockInfo) {
        return OCCLUSION_BLOCKS.entrySet().stream().filter(entry -> {
            try {
                return entry.getKey().create(world.getTags()).test(blockInfo);
            } catch (CommandSyntaxException e) {
                SoundFiltersMod.LOGGER.warn("Failed to get custom block occlusion", e);
                return false;
            }
        }).map(Map.Entry::getValue).findAny().orElse(null);
    }

    public static Double getCustomBlockReverb(World world, CachedBlockInfo blockInfo) {
        return REVERB_BLOCKS.entrySet().stream().filter(entry -> {
            try {
                return entry.getKey().create(world.getTags()).test(blockInfo);
            } catch (CommandSyntaxException e) {
                SoundFiltersMod.LOGGER.warn("Failed to get custom block reverb", e);
                return false;
            }
        }).map(Map.Entry::getValue).findAny().orElse(null);
    }

    public static Double getCustomDimensionReverb(ResourceLocation dimensionName) {
        return REVERB_DIMENSIONS.get(dimensionName);
    }

    public static void loadConfig() {
        initConfig();

        CommentedFileConfig configData = CommentedFileConfig.builder(CONFIG_PATH).sync().autosave().writingMode(WritingMode.REPLACE).build();
        configData.load();
        CLIENT_CONFIG.setConfig(configData);

        REVERB_BLOCK_ENTRIES.get().forEach(blockEntry -> {
            if (!blockEntry.isEmpty()) {
                String[] values = blockEntry.split(" ", 2);

                if (values.length == 2) {
                    try {
                        double amount = Double.parseDouble(values[0]);

                        if (amount < 0) {
                            SoundFiltersMod.LOGGER.warn("Cannot have a negative reverb amount in config: '" + blockEntry + "'");
                        } else {
                            parseBlockState(values[1]).ifPresent(blockStateInput -> REVERB_BLOCKS.put(blockStateInput, amount));
                        }
                    } catch (NumberFormatException e) {
                        SoundFiltersMod.LOGGER.warn("Unable to parse reverb amount from config: '" + blockEntry + "'");
                    }
                } else {
                    SoundFiltersMod.LOGGER.warn("Unable to parse reverb block entry from config: '" + blockEntry + "'");
                }
            }
        });

        REVERB_DIMENSION_ENTRIES.get().forEach(dimensionEntry -> {
            if (!dimensionEntry.isEmpty()) {
                String[] values = dimensionEntry.split(" ", 2);

                if (values.length == 2) {
                    try {
                        double amount = Double.parseDouble(values[0]);

                        if (amount < 0) {
                            SoundFiltersMod.LOGGER.warn("Cannot have a negative reverb amount in config: '" + dimensionEntry + "'");
                        } else {
                            REVERB_DIMENSIONS.put(new ResourceLocation(values[1]), amount);
                        }
                    } catch (NumberFormatException e) {
                        SoundFiltersMod.LOGGER.warn("Unable to parse reverb amount from config: '" + dimensionEntry + "'");
                    }
                } else {
                    SoundFiltersMod.LOGGER.warn("Unable to parse reverb dimension entry from config: '" + dimensionEntry + "'");
                }
            }
        });

        OCCLUSION_BLOCK_ENTRIES.get().forEach(blockEntry -> {
            if (!blockEntry.isEmpty()) {
                String[] values = blockEntry.split(" ", 2);

                if (values.length == 2) {
                    try {
                        double amount = Double.parseDouble(values[0]);

                        if (amount < 0) {
                            SoundFiltersMod.LOGGER.warn("Cannot have a negative occlusion amount in config: '" + blockEntry + "'");
                        } else {
                            parseBlockState(values[1]).ifPresent(predicate -> OCCLUSION_BLOCKS.put(predicate, amount));
                        }
                    } catch(NumberFormatException e) {
                        SoundFiltersMod.LOGGER.warn("Unable to parse occlusion amount from config: '" + blockEntry + "'");
                    }
                } else {
                    SoundFiltersMod.LOGGER.warn("Unable to parse occlusion block entry from config: '" + blockEntry + "'");
                }
            }
        });

        //Re-initialize reverb filter
        ReverbHandler.initializeReverb();
    }

    private static void initConfig() {
        CLIENT_BUILDER.comment("Low Pass Filter Settings").push(CATEGORY_LOW_PASS);
        LOW_PASS_ENABLED = CLIENT_BUILDER.comment("###############################################################################\n" +
                "Set to false to disable low pass filter in water and lava.")
                .define("useLowPass", true);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.comment("Low Pass Filter Settings - Water").push(CATEGORY_LOW_PASS_WATER);
        LOW_PASS_WATER_GAIN = CLIENT_BUILDER.comment("###############################################################################\n" +
                "The multiplier for volume when you are in water. Lower is quieter.")
                .defineInRange("waterLowPassVolume", 1.0, 0, 1);
        LOW_PASS_WATER_GAIN_HF = CLIENT_BUILDER.comment("###############################################################################\n" +
                "The multiplier for volume when you are in water for high frequencies.\n" +
                "Lower is less high frequency sound.")
                .defineInRange("waterLowPassHighFrequencyVolume", 0.4, 0, 1);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.comment("Low Pass Filter Settings - Lava").push(CATEGORY_LOW_PASS_LAVA);
        LOW_PASS_LAVA_GAIN = CLIENT_BUILDER.comment("###############################################################################\n" +
                "The multiplier for volume when you are in lava. Lower is quieter.")
                .defineInRange("lavaLowPassVolume", 0.6, 0, 1);
        LOW_PASS_LAVA_GAIN_HF = CLIENT_BUILDER.comment("###############################################################################\n" +
                "The multiplier for volume when you are in lava for high frequencies.\n" +
                "Lower is less high frequency sound.")
                .defineInRange("lavaLowPassHighFrequencyVolume", 0.2, 0, 1);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.comment("Occlusion Filter Settings").push(CATEGORY_OCCLUSION);
        OCCLUSION_ENABLED = CLIENT_BUILDER.comment("###############################################################################\n" +
                "Set to false to disable low pass filter for sounds behind solid walls.\n" +
                "If you are getting lag, disabling this might help.")
                .define("useOcclusion", true);
        OCCLUSION_MULTIPLIER = CLIENT_BUILDER.comment("###############################################################################\n" +
                "The multiplier per block for occlusion. You can lower this if you\n" +
                "find the occlusion to be too much or raise it for a more noticeable\n" +
                "effect.")
                .defineInRange("occlusionMultiplier", 0.1, 0, 1);
        OCCLUSION_MAX = CLIENT_BUILDER.comment("###############################################################################\n" +
                "The maximum percent sound can be occluded behind a wall.")
                .defineInRange("occlusionMaximum", 0.98, 0, 1);
        OCCLUSION_BLOCK_ENTRIES = CLIENT_BUILDER.comment("###############################################################################\n" +
                "Add new entries separated by commas in the format\n" +
                "\"<reverb double> <block>\"\n" +
                "where <block> can be either a block tag or id the format\n" +
                "<block id>[<state (optional)>]{<nbt tag (optional>}\n" +
                "to customize how much sound the block should absorb when it is between you and the\n" +
                "sound source. It should be in the same format as in the setblock command. The\n" +
                "amount is a double, with 0.0 absorbing no sound (like air), and 1.0 being the\n" +
                "normal amount, and 2.0 being twice the normal amount. By default, wool and sponge\n" +
                "have 2.0 which is twice the normal sound absorption.").defineList("blockSpecificOcclusion", Arrays.asList("2.0 #minecraft:wool", "2.0 minecraft:sponge", "2.0 minecraft:wet_sponge"), LIST_VALIDATOR);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.comment("Reverb Filter Settings").push(CATEGORY_REVERB);
        REVERB_ENABLED = CLIENT_BUILDER.comment("###############################################################################\n" +
                "Set to false to disable reverb.")
                .define("useReverb", true);
        REVERB_PERCENT = CLIENT_BUILDER.comment("###############################################################################\n" +
                "The percentage of reverb you can get. You can lower this if you find\n" +
                "the reverb to be too much (or raise it if you really want an echo).")
                .defineInRange("reverbPercent", 1.0, 0, 2);
        REVERB_SKY_CHECKS = CLIENT_BUILDER.comment("###############################################################################\n" +
                "If this is true, when you're in an area that can see the sky, then\n" +
                "there will be less reverb. This is for aboveground areas with\n" +
                "lots of stone like extreme hills biomes. There still might\n" +
                "be some reverb, but less then when the sky isn't visible.")
                .define("doSkyChecks", true);
        REVERB_MAX_BLOCKS = CLIENT_BUILDER.comment("###############################################################################\n" +
                "If you are getting lag, set this number lower. The higher it is,\n" +
                "the more realistic the reverb will be.")
                .defineInRange("numberOfBlocksReverbWillCheckThrough", 1024, 0, Integer.MAX_VALUE);
        REVERB_BLOCK_ENTRIES = CLIENT_BUILDER.comment("###############################################################################\n" +
                "Add new entries separated by commas in the format\n" +
                "\"<reverb amount> <block>\"\n" +
                "where <block> can be either a block tag or id the format\n" +
                "<block id>[<state (optional)>]{<nbt tag (optional>}" +
                "to customize how specific blocks absorb or create reverb.\n" +
                "By default things like wool, snow, carpets, and plants absorb reverb\n" +
                "(value 0.0), things like wood and dirt are neutral (value 1.0),\n" +
                "and things like stone, metal, ice, and glass create reverb (value 2.0).\n" +
                "The state and tag are optional. It should be in the same format as in\n" +
                "the setblock command. For instance, making snowy grass increase reverb\n" +
                "would be '2.0 grass_block[snowy=true]'.").defineList("blockSpecificReverb", Collections.singletonList(""), LIST_VALIDATOR);
        REVERB_DIMENSION_ENTRIES = CLIENT_BUILDER.comment("###############################################################################\n" +
                "Add new entries separated by commas in the format\n" +
                "\"<reverb percent> <dimension id>\"\n" +
                "to customize how much ambient reverb a specific dimension has.\n" +
                "The reverb percent should be between 0.0 and 1.0. By default the\n" +
                "nether has 1.0, meaning full reverb without any special blocks.").defineList("dimensionSpecificReverb", Collections.singletonList("1.0 minecraft:the_nether"), LIST_VALIDATOR);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.comment(
                "Advanced Reverb Filter Settings - You can edit these if you know what you are\n" +
                "doing. You can even edit them if you don't know what you are doing, and just\n" +
                "want to experiment. =)\n" +
                "For more info about what all these things are, look starting at the bottom of page\n" +
                "101 on https://kcat.strangesoft.net/misc-downloads/Effects%20Extension%20Guide.pdf")
                .push(CATEGORY_REVERB_ADVANCED);
        REVERB_ADVANCED_DENSITY = CLIENT_BUILDER.defineInRange("density", 0.2, 0, 1);
        REVERB_ADVANCED_DIFFUSION = CLIENT_BUILDER.defineInRange("diffusion", 0.6, 0, 1);
        REVERB_ADVANCED_GAIN = CLIENT_BUILDER.defineInRange("gain", 0.15, 0, 1);
        REVERB_ADVANCED_GAIN_HF = CLIENT_BUILDER.defineInRange("gainHF", 0.8, 0, 1);
        REVERB_ADVANCED_DECAY_TIME_MIN = CLIENT_BUILDER.defineInRange("decayTimeMinimum", 0.1, 0.1, 20);
        REVERB_ADVANCED_DECAY_HF_RATIO = CLIENT_BUILDER.defineInRange("decayHFRatio", 0.7, 0.1, 20);
        REVERB_ADVANCED_AIR_ABSORPTION_GAIN_HF = CLIENT_BUILDER.defineInRange("airAbsorptionGainHF", 0.99, 0.892, 1);
        REVERB_ADVANCED_REFLECTIONS_GAIN_BASE = CLIENT_BUILDER.defineInRange("reflectionsGainBase", 0.05, 0, 1.58);
        REVERB_ADVANCED_REFLECTIONS_GAIN_MULTIPLIER = CLIENT_BUILDER.defineInRange("reflectionsGainMultiplier", 0.05, 0, 1.58);
        REVERB_ADVANCED_REFLECTIONS_DELAY_MULTIPLIER = CLIENT_BUILDER.defineInRange("reflectionsDelayMultiplier", 0.025, 0, 0.3);
        REVERB_ADVANCED_LATE_REVERB_GAIN_BASE = CLIENT_BUILDER.defineInRange("lateReverbGainBase", 1.26, 0, 5);
        REVERB_ADVANCED_LATE_REVERB_GAIN_MULTIPLIER = CLIENT_BUILDER.defineInRange("lateReverbGainMultiplier", 0.1, 0, 5);
        REVERB_ADVANCED_LATE_REVERB_DELAY_MULTIPLIER = CLIENT_BUILDER.defineInRange("lateReverbDelayMultiplier", 0.01, 0, 0.1);
        CLIENT_BUILDER.pop();

        CLIENT_CONFIG = CLIENT_BUILDER.build();
    }

    private static Optional<BlockPredicateArgument.IResult> parseBlockState(String blockInput) {
        try {
            return Optional.of(BlockPredicateArgument.blockPredicate().parse(new StringReader(blockInput)));
        } catch (CommandSyntaxException e) {
            SoundFiltersMod.LOGGER.warn("Problem parsing block state from config: '" + blockInput + "'", e);
        }

        return Optional.empty();
    }
}
