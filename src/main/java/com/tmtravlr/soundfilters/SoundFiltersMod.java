package com.tmtravlr.soundfilters;

import com.tmtravlr.soundfilters.SoundTickHandler.ComparablePosition;
import com.tmtravlr.soundfilters.filters.FilterLowPass;
import com.tmtravlr.soundfilters.filters.FilterReverb;

import java.util.Comparator;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.SoundSystemException;
/**
 * Main mod class.
 * 
 * @author Rebeca
 * @Date 2014
 */
@Mod(
		modid = "soundfilters",
		name = "Sound Filters",
		version = "0.9_for_1.9"
		)
public class SoundFiltersMod
{
	@Instance("soundfilters")
	public static SoundFiltersMod soundFilters;

	@SidedProxy(
			clientSide = "com.tmtravlr.soundfilters.ClientProxy",
			serverSide = "com.tmtravlr.soundfilters.CommonProxy"
			)
	public static CommonProxy proxy;

	//This line will crash if it runs on the server!
	@SideOnly(Side.CLIENT)
	private static Minecraft mc = Minecraft.getMinecraft();

	private static Random rand = new Random();

	public static int profileSize = 1024;
	public static boolean doSkyChecks = true;
	public static boolean doReverb = true;
	public static boolean doLowPass = true;
	public static boolean doOcclusion = true;

	public static FilterLowPass lowPassFilter = new FilterLowPass();
	public static FilterReverb reverbFilter = new FilterReverb();
	
	//Comparator for the BlockMeta
	public static Comparator<BlockMeta> BlockComparator = new Comparator<BlockMeta>()
	{
		public int compare(BlockMeta first, BlockMeta second)
		{
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

	static
	{
		if (SoundSystemConfig.getLibraries() != null)
		{
			SoundSystemConfig.getLibraries().clear();
		}

		try
		{
			SoundSystemConfig.addLibrary(ModifiedLWJGLOpenALLibrary.class);
		}
		catch (SoundSystemException e)
		{
			e.printStackTrace();
			System.out.println("[Sound Filters] Problem while loading modified library!");
		}

		System.out.println("[Sound Filters] Loaded modified library.");
	}

	/**
	 * Holds info about both a block's id and metadata.
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
	public void preInit(FMLPreInitializationEvent event)
	{
		Configuration config = new Configuration(event.getSuggestedConfigurationFile());


		config.load();

		//config.addCustomCategoryComment("debug", "Set Debug to true to write simple debug information to the console.\nYou probably don't want to set High Output Debug to true\nunless you actually want to debug the mod, since\nit writes a lot of output.");
		Property prop = config.get("debug", "Debug", false);
			prop.setComment("Set to true to write simple debug info to the console. [default: false]");
			DEBUG = prop.getBoolean(true);
			
		prop = config.get("debug", "High Output Debug", false);
			prop.setComment("You probably don't want to set this to true\nunless you actually want to debug the mod.\nIt writes quite a lot in the console. [default: false]");
			SUPER_DUPER_DEBUG = prop.getBoolean(true);

		prop = config.get("filters", "Use Reverb?", true);
			prop.setComment("Set to false to disable reverb. [default: true]");
			doReverb = prop.getBoolean(true); 

		prop = config.get("filters", "Use Low Pass?", true);
			prop.setComment("Set to false to disable low pass filter in water and lava. [default: true]");
			doLowPass = prop.getBoolean(true);

		prop = config.get("filters", "Use Occluded Sounds (muting sounds behind solid walls)?", true);
			prop.setComment("Set to false to disable low pass filter for sounds behind solid walls.\nIf you are getting lag, disabling this might help. [default: true]");
			doOcclusion = prop.getBoolean(true);
		
		prop = config.get("reverb", "Number of blocks reverb will check through:", 1024);
			prop.setComment("If you are getting lag, set this number lower. The higher it is,\nthe more realistic the reverb will be. [range: 0 ~ 2147483647, default: 1024]");
			profileSize = prop.getInt();
			
		prop = config.get("reverb", "Do sky checks:", true);
			prop.setComment("If this is true, when you're in an area that can see the sky, the\nthere will be less reverb. This is for aboveground areas with\nlots of stone and such like extreme hills biomes. [default: true]");
			doSkyChecks = prop.getBoolean(true);
		
		String[] reverbBlocksList = new String[] {"soul_sand-16-2.0"};
		prop = config.get("reverb", "Specific block reverb:", reverbBlocksList);
			prop.setComment("Add values to this list (each on a new line) in the format \n<block id>-<metadata>-<reverb double>, to change how the block\nwith that metadata absorbs or creates reverb. If the\nmetadata is 16, that means it will apply to any metadata value.\nBy default things like wool, snow, carpets, and plants absorb reverb\n(value 0.0), things like wood and dirt are neutral (value 1.0),\nand things like stone, metal, ice, and glass create reverb (value 2.0).\nSo if, say, you wanted to add pumpkins of any metadata to the blocks\nthat create reverb, you would put pumpkin-16-2.0 on a new line. [default: [soul_sand-16-2.0]]");
			reverbBlocksList = prop.getStringList();
		
		String[] occlusionBlocksList = new String[] {"wool-16-2.0"};
		prop = config.get("occlusion", "Specific block occlusion:", occlusionBlocksList);
			prop.setComment("Add new entries (each on a new line) in the format\n <block id>-<metadata>-<occlusion double> to customize how much sound\nthey should absorb when they are between you and the sound source.\nFor the metadata, 16 means any metadata value. The amount is a\ndouble, with 0.0 absorbing no sound (like air), and 1.0 being the normal\namount, and 2.0 being twice the normal amount. By default,\nwool has entry wool-16-2.0 which is twice the normal sound absorbtion. [default: [wool-16-2.0]]");
			occlusionBlocksList = prop.getStringList();
		
		config.save();


		for (String occlusionInfo : occlusionBlocksList)
		{
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
			}
			catch(Exception e) {
				e.printStackTrace();
				System.out.println("[Sound Filters] Error while loading in custom occlusion entry!" + (blockName == "" ? "" : " Block ID was " + blockName));
			}

			if(block != null && meta >= 0 && strength >= 0) 
			{
				if(DEBUG) System.out.println("[Sound Filters] Loaded custom occlusion: block " + blockName + ", with " + (meta == 16? "any meta" : "meta " + meta) + ", and amount " + strength);
				customOcclusion.put(new BlockMeta(block, meta), strength);
			}

		}

		for (String reverbInfo : reverbBlocksList)
		{
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
			}
			catch(Exception e) {
				e.printStackTrace();
				System.out.println("[Sound Filters] Error while loading in custom reverb entry!" + (blockName == "" ? "" : " Block ID was " + blockName));
			}

			if(block != null && meta >= 0 && strength >= 0)
			{
				if(DEBUG) System.out.println("[Sound Filters] Loaded custom reverb: block " + blockName + ", with " + (meta == 16? "any meta" : "meta " + meta) + ", and amount " + strength);
				customReverb.put(new BlockMeta(block, meta), strength);
			}
		}

		proxy.registerTickHandlers();
		proxy.registerEventHandlers();
	}

	@EventHandler
	public void postInit(FMLPostInitializationEvent event)
	{
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

	//Returns true if the game is paused (which is a private field in Minecraft; don't ask me why..)
	public static boolean isGamePaused()
	{
		return mc.isSingleplayer() && mc.currentScreen != null && mc.currentScreen.doesGuiPauseGame() && !mc.getIntegratedServer().getPublic();
	}

}
