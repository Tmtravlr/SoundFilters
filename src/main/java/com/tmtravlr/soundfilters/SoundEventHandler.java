package com.tmtravlr.soundfilters;

import com.tmtravlr.soundfilters.handlers.FilterHandler;
import com.tmtravlr.soundfilters.handlers.LiquidLowPassHandler;
import com.tmtravlr.soundfilters.handlers.OcclusionHandler;
import com.tmtravlr.soundfilters.handlers.ReverbHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.*;
import net.minecraft.client.audio.ISound.AttenuationType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.SoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.util.Map;

/**
 * This class handles the sound events like when sounds start playing.
 *
 * @author Rebeca Rey
 * @Date 2014
 */
@Mod.EventBusSubscriber(Dist.CLIENT)
public class SoundEventHandler {
	private static final Minecraft MC = Minecraft.getInstance();

	private static boolean hasLoadedFirstTime = false;

	public static Map<ISound, ChannelManager.Entry> playingSoundsChannel = null;

	@SubscribeEvent
	public static void onSoundPlaying(SoundEvent.SoundSourceEvent event) {
		if (MC.world != null) {
			try {
				int sourceId = ObfuscationReflectionHelper.getPrivateValue(SoundSource.class, event.getSource(), "field_216441_b");
				FilterHandler.SOURCE_IDS.put(event.getSound(), sourceId);
			} catch (Exception e) {
				SoundFiltersMod.LOGGER.warn("Caught an error while trying to handle the sound '" + event.getName() + "'. Filters will not be applied to it.", e);
			}

			if (MC.player != null && event.getSound().getAttenuationType() != AttenuationType.NONE && !event.getSound().isGlobal()) {
				OcclusionHandler.addOcclusionToMap(event.getSound());
			}

			if (!event.getSource().func_216435_g()) {
				FilterHandler.updateSourceFilters(event.getSound());
			}
		}
	}

	@SubscribeEvent
	public static void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			if (!hasLoadedFirstTime) {
				hasLoadedFirstTime = true;
				loadSoundChannels();
			}

			if (playingSoundsChannel != null) {
				if (MC.world != null && MC.player != null && !MC.isGamePaused()) {
					LiquidLowPassHandler.updateLiquidLowPass();
					ReverbHandler.updateReverb();
					OcclusionHandler.updateOcclusion();
				} else {
					// We must be in the menu; turn everything off:
					LiquidLowPassHandler.stopLiquidLowPass();
					ReverbHandler.stopReverb();
				}

				updateFilters();
			}
		}
	}

	private static void loadSoundChannels() {
		try {
			SoundEngine soundEngine = ObfuscationReflectionHelper.getPrivateValue(SoundHandler.class, Minecraft.getInstance().getSoundHandler(), "field_147694_f");
			playingSoundsChannel = ObfuscationReflectionHelper.getPrivateValue(SoundEngine.class, soundEngine, "field_217942_m");

			SoundFiltersMod.LOGGER.info("Sound Filters successfully loaded the sound channels");
		} catch (Exception e) {
			SoundFiltersMod.LOGGER.error("Sound Filters wasn't able to load the sound channels. The sound filters will no longer work.", e);
		}
	}

	//Update filters for the sources currently playing, and remove sounds that are no longer playing
	private static void updateFilters() {
		playingSoundsChannel.forEach((sound, entry) -> entry.runOnSoundExecutor(source -> {
			if (!source.func_216435_g()) {
				FilterHandler.updateSourceFilters(sound);
			}
		}));

		FilterHandler.SOURCE_IDS.entrySet().removeIf((entry) -> !playingSoundsChannel.containsKey(entry.getKey()));
	}
}
