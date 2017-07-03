package com.tmtravlr.soundfilters;

import java.util.Map;

import com.tmtravlr.soundfilters.SoundTickHandler.ComparablePosition;
import com.tmtravlr.soundfilters.SoundTickHandler.DoubleWithTimeout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.ISound.AttenuationType;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import paulscode.sound.Source;

/**
 * This class handles the sound events, specifically when sounds start playing.
 * It adds the sounds to the source map. Created to solve the "random movements"
 * problem that happened occasionally.
 * 
 * @author Rebeca Rey
 * @Date 2014
 */
public class SoundEventHandler {
	@SubscribeEvent
	public void playSoundEventHandler(PlaySoundEvent event) {
		if (event.getSound().getAttenuationType() != AttenuationType.NONE) {
			this.addSourceToMap(event.getSound().getXPosF(), event.getSound().getYPosF(), event.getSound().getZPosF());
		}
	}

	/**
	 * Finds the occlusion amount for the given location, and adds a new source
	 * to the sourceOccusionMap, with the source as null (will get set to the
	 * actual source the first time it runs through the SoundTickHandler).
	 * 
	 * @param x
	 *            x position of the source
	 * @param y
	 *            y position of the source
	 * @param z
	 *            z position of the source
	 */
	private void addSourceToMap(float x, float y, float z) {
		Minecraft mc = Minecraft.getMinecraft();
		double amount = 0.0D;

		if (mc.theWorld != null && mc.thePlayer != null) {
			amount = SoundTickHandler.getSoundOcclusion(mc.theWorld, new Vec3d((double) x, (double) y, (double) z), new Vec3d(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
		}

		SoundTickHandler.sourceOcclusionMap.put(new ComparablePosition(x, y, z), new DoubleWithTimeout((Source) null, amount, 10));

	}
}
