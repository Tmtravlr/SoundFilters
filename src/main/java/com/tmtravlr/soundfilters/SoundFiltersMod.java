package com.tmtravlr.soundfilters;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;

/**
 * Main mod class.
 *
 * @author Tmtravlr (Rebeca Rey)
 * @Date 2014
 */
@Mod(SoundFiltersMod.MOD_ID)
public class SoundFiltersMod {
	public static final String MOD_ID = "soundfilters";
	public static final Logger LOGGER = LogManager.getLogger();

	@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
	public static class RegistryEvents {

		@SubscribeEvent
		public static void onClientSetup(FMLClientSetupEvent event) {
			SoundFiltersConfig.loadConfig();
		}
	}
}
