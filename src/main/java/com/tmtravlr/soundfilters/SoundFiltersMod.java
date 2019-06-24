package com.tmtravlr.soundfilters;

import java.util.Comparator;
import java.util.Random;
import java.util.TreeMap;

import org.apache.logging.log4j.Logger;

import com.tmtravlr.soundfilters.filters.FilterLowPass;
import com.tmtravlr.soundfilters.filters.FilterReverb;

import net.minecraft.block.Block;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.SoundSystemException;

/**
 * Main mod class.
 * 
 * @author Tmtravlr (Rebeca Rey)
 * @Date 2014
 */
@Mod(modid = SoundFiltersMod.MOD_ID, name = SoundFiltersMod.MOD_NAME, version = SoundFiltersMod.MOD_VERSION, clientSideOnly = true)
public class SoundFiltersMod {
	public static final String MOD_ID = "soundfilters";
	public static final String MOD_NAME = "Sound Filters";
	public static final String MOD_VERSION = "@VERSION@";

	@Instance("soundfilters")
	public static SoundFiltersMod soundFilters;

	@SidedProxy(clientSide = "com.tmtravlr.soundfilters.ClientProxy", serverSide = "com.tmtravlr.soundfilters.CommonProxy")
	public static CommonProxy proxy;
	
	public static Logger logger;

	private static Random rand = new Random();

	public static int profileSize = 1024;
	public static boolean doSkyChecks = true;
	public static boolean doReverb = true;
	public static float reverbPercent = 1.0f;
	public static boolean doLowPass = true;
	public static float waterLowPassAmount = 0.4f;
	public static float waterVolume = 1.0f;
	public static float lavaLowPassAmount = 0.1f;
	public static float lavaVolume = 0.6f;
	public static boolean doOcclusion = true;
	public static float occlusionPercent = 1.0f;

	public static FilterLowPass lowPassFilter = new FilterLowPass();
	public static FilterReverb reverbFilter = new FilterReverb();

	// Comparator for the BlockMeta
	public static Comparator<BlockMeta> BlockComparator = new Comparator<BlockMeta>() {
		public int compare(BlockMeta first, BlockMeta second) {
			if (Block.getIdFromBlock(second.block) - Block.getIdFromBlock(first.block) != 0) {
				return Block.getIdFromBlock(second.block) - Block.getIdFromBlock(first.block);
			}
			if (second.meta - first.meta != 0) {
				return second.meta - first.meta;
			}

			return 0;
		}
	};

	public static TreeMap<BlockMeta, Double> customOcclusion = new TreeMap<BlockMeta, Double>(BlockComparator);
	public static TreeMap<BlockMeta, Double> customReverb = new TreeMap<BlockMeta, Double>(BlockComparator);

	public static boolean DEBUG = false;
	public static boolean SUPER_DUPER_DEBUG = false;

	static {
		if (SoundSystemConfig.getLibraries() != null) {
			SoundSystemConfig.getLibraries().clear();
		}

		try {
			SoundSystemConfig.addLibrary(ModifiedLWJGLOpenALLibrary.class);
		} catch (SoundSystemException e) {
			System.out.println("[Sound Filters] Problem while loading modified library!");
			e.printStackTrace();
		}

		System.out.println("[Sound Filters] Loaded modified library.");
	}

	/**
	 * Holds info about both a block's id and metadata.
	 * 
	 * @author Rebeca
	 * @Date 2014
	 */
	public static class BlockMeta {
		public Block block;
		public int meta;

		public BlockMeta(Block b, int m) {
			block = b;
			meta = m;
		}
	}

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		logger = event.getModLog();
		Configuration config = new Configuration(event.getSuggestedConfigurationFile());

		config.load();

		DEBUG = config.getBoolean("Debug", "debug", false, "Set to true to write simple debug info to the console.");
		SUPER_DUPER_DEBUG = config.getBoolean("High Output Debug", "debug", false,
				"You probably don't want to set this to true\n"
				+ "unless you actually want to debug the mod.\n"
				+ "It writes quite a lot in the console.");
		
		doLowPass = config.getBoolean("Use Low Pass?", "low_pass", true, "Set to false to disable low pass filter in water and lava.");
		waterLowPassAmount = config.getFloat("Water Low Pass Amount", "low_pass", 0.4f, 0.0f, 1.0f, 
				"The amount of low pass that will be applied in water. Lower is stronger.");
		lavaLowPassAmount = config.getFloat("Water Low Pass Volume", "low_pass", 1.0f, 0.0f, 1.0f, 
				"The multiplier for volume when you are in water. Lower is quieter.");
		lavaLowPassAmount = config.getFloat("Lava Low Pass Amount", "low_pass", 0.2f, 0.0f, 1.0f, 
				"The amount of low pass that will be applied in lava. Lower is stronger.");
		lavaLowPassAmount = config.getFloat("Lava Low Pass Volume", "low_pass", 0.6f, 0.0f, 1.0f, 
				"The multiplier for volume when you are in lava. Lower is quieter.");
		
		doOcclusion = config.getBoolean("Use Occluded Sounds (muting sounds behind solid walls)?", "occlusion", true,
				"Set to false to disable low pass filter for sounds behind solid walls.\n"
				+ "If you are getting lag, disabling this might help.");
		occlusionPercent = config.getFloat("Occlusion Percent", "occlusion", 1.0f, 0.0f, Float.POSITIVE_INFINITY, 
				"The percentage of occlusion you can get. You can lower this if you find\n"
				+ "the occlusion to be too much or raise it for a more noticeable\n"
				+ "effect.");
		String[] occlusionBlocksList = config.getStringList("Specific block occlusion:", "occlusion", new String[] { "wool-16-2.0" },
				"Add new entries (each on a new line) in the format\n"
				+ "<block id>-<metadata>-<occlusion double> to customize how much sound\n"
				+ "they should absorb when they are between you and the sound source.\n"
				+ "For the metadata, 16 means any metadata value. The amount is a\n"
				+ "double, with 0.0 absorbing no sound (like air), and 1.0 being the normal\n"
				+ "amount, and 2.0 being twice the normal amount. By default, wool has\n"
				+ "entry wool-16-2.0 which is twice the normal sound absorbtion.");

		doReverb = config.getBoolean("Use Reverb?", "reverb", true, "Set to false to disable reverb.");
		reverbPercent = config.getFloat("Reverb Percent", "reverb", 1.0f, 0.0f, 2.0f, 
				"The percentage of reverb you can get. You can lower this if you find\n"
				+ "the reverb to be too much (or raise it if you really want an\n"
				+ "echo).");
		profileSize = config.getInt("Number of blocks reverb will check through:", "reverb", 1024, 0, Integer.MAX_VALUE,
				"If you are getting lag, set this number lower. The higher it is,\n"
				+ "the more realistic the reverb will be.");
		doSkyChecks = config.getBoolean("Do sky checks:", "reverb", true,
				"If this is true, when you're in an area that can see the sky, then\n"
				+ "there will be less reverb. This is for aboveground areas with\n"
				+ "lots of stone and such like extreme hills biomes. There still might\n"
				+ "be some, but less then when the sky isn't visible.");
		String[] reverbBlocksList = config.getStringList("Specific block reverb:", "reverb", new String[] { "soul_sand-16-2.0" },
				"Add values to this list (each on a new line) in the format \n"
				+ "<block id>-<metadata>-<reverb double>, to change how the block\n"
				+ "with that metadata absorbs or creates reverb. If the\n"
				+ "metadata is 16, that means it will apply to any metadata value.\n"
				+ "By default things like wool, snow, carpets, and plants absorb reverb\n"
				+ "(value 0.0), things like wood and dirt are neutral (value 1.0),\n"
				+ "and things like stone, metal, ice, and glass create reverb (value 2.0).\n"
				+ "So if, say, you wanted to add pumpkins of any metadata to the blocks\n"
				+ "that create reverb, you would put pumpkin-16-2.0 on a new line.");

		config.save();

		for (String occlusionInfo : occlusionBlocksList) {
			Block block = null;
			String blockName = "";
			int meta = -1;
			double strength = -1;

			try {
				int lastDashIndex = occlusionInfo.lastIndexOf('-');
				int firstDashIndex = occlusionInfo.substring(0, lastDashIndex).lastIndexOf('-');
				blockName = occlusionInfo.substring(0, firstDashIndex);
				meta = Integer.valueOf(occlusionInfo.substring(firstDashIndex + 1, lastDashIndex)).intValue();
				strength = Double.valueOf(occlusionInfo.substring(lastDashIndex + 1)).doubleValue();
				block = Block.getBlockFromName(blockName);
			} catch (Exception e) {
				logger.error("Error while loading in custom occlusion entry!" + (blockName == "" ? "" : " Block ID was " + blockName), e);
			}

			if (block != null && meta >= 0 && strength >= 0) {
				if (DEBUG)
					logger.debug("Loaded custom occlusion: block " + blockName + ", with " + (meta == 16 ? "any meta" : "meta " + meta) + ", and amount " + strength);
				customOcclusion.put(new BlockMeta(block, meta), strength);
			}

		}

		for (String reverbInfo : reverbBlocksList) {
			Block block = null;
			String blockName = "";
			int meta = -1;
			double strength = -1;

			try {
				int lastDashIndex = reverbInfo.lastIndexOf('-');
				int firstDashIndex = reverbInfo.substring(0, lastDashIndex).lastIndexOf('-');
				blockName = reverbInfo.substring(0, firstDashIndex);
				meta = Integer.valueOf(reverbInfo.substring(firstDashIndex + 1, lastDashIndex)).intValue();
				strength = Double.valueOf(reverbInfo.substring(lastDashIndex + 1)).doubleValue();
				block = Block.getBlockFromName(blockName);
			} catch (Exception e) {
				logger.error("Error while loading in custom reverb entry!" + (blockName == "" ? "" : " Block ID was " + blockName), e);
			}

			if (block != null && meta >= 0 && strength >= 0) {
				if (DEBUG)
					logger.debug("Loaded custom reverb: block " + blockName + ", with " + (meta == 16 ? "any meta" : "meta " + meta) + ", and amount " + strength);
				customReverb.put(new BlockMeta(block, meta), strength);
			}
		}

		proxy.registerTickHandlers();
		proxy.registerEventHandlers();
	}

	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		reverbFilter.density = 0.0F;
		reverbFilter.diffusion = 0.6F;
		reverbFilter.gain = 0.15F;
		reverbFilter.gainHF = 0.8F;
		reverbFilter.decayTime = 0.1F;
		reverbFilter.decayHFRatio = 0.7F;
		reverbFilter.reflectionsGain = 0.6F;
		reverbFilter.reflectionsDelay = 0.0F;
		reverbFilter.lateReverbGain = 0.9F;
		reverbFilter.lateReverbDelay = 0.0F;
		reverbFilter.airAbsorptionGainHF = 0.99F;
		reverbFilter.roomRolloffFactor = 0.0F;
	}

}
