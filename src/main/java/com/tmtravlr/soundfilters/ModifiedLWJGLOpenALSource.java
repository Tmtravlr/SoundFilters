package com.tmtravlr.soundfilters;

import com.tmtravlr.soundfilters.SoundTickHandler.ComparablePosition;
import com.tmtravlr.soundfilters.SoundTickHandler.DoubleWithTimeout;
import com.tmtravlr.soundfilters.filters.BaseFilter;
import com.tmtravlr.soundfilters.filters.FilterException;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;

import javax.sound.sampled.AudioFormat;

import org.lwjgl.openal.AL10;

import net.minecraft.client.Minecraft;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.MathHelper;
import paulscode.sound.FilenameURL;
import paulscode.sound.SoundBuffer;
import paulscode.sound.Source;
import paulscode.sound.libraries.ChannelLWJGLOpenAL;
import paulscode.sound.libraries.SourceLWJGLOpenAL;

/**
 * @author Rebeca Rey
 * @Date 2014 <br>
 *       <br>
 * 
 *       Derived from the SourceLWJGLOpenAL class, which has the following
 *       copyright:
 * 
 *       <b><br>
 *       <br>
 *       This software is based on or using the LWJGL Lightweight Java Gaming
 *       Library available from http://www.lwjgl.org/. </b><br>
 *       <br>
 *       LWJGL License: <br>
 *       <i> Copyright (c) 2002-2008 Lightweight Java Game Library Project All
 *       rights reserved. <br>
 *       Redistribution and use in source and binary forms, with or without
 *       modification, are permitted provided that the following conditions are
 *       met: <br>
 *       * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer. <br>
 *       * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *       <br>
 *       * Neither the name of 'Light Weight Java Game Library' nor the names of
 *       its contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission. <br>
 *       THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 *       IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 *       TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 *       PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 *       OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *       SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *       LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *       DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *       THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *       (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *       OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *       <br>
 *       <br>
 *       <br>
 *       </i> <b><i> SoundSystem LibraryLWJGLOpenAL License:</b></i><br>
 *       <b><br>
 *       <b> You are free to use this library for any purpose, commercial or
 *       otherwise. You may modify this library or source code, and distribute
 *       it any way you like, provided the following conditions are met: <br>
 *       1) You must abide by the conditions of the aforementioned LWJGL
 *       License. <br>
 *       2) You may not falsely claim to be the author of this library or any
 *       unmodified portion of it. <br>
 *       3) You may not copyright this library or a modified version of it and
 *       then sue me for copyright infringement. <br>
 *       4) If you modify the source code, you must clearly document the changes
 *       made before redistributing the modified source code, so other users
 *       know it is not the original code. <br>
 *       5) You are not required to give me credit for this library in any
 *       derived work, but if you do, you must also mention my website:
 *       http://www.paulscode.com <br>
 *       6) I the author will not be responsible for any damages (physical,
 *       financial, or otherwise) caused by the use if this library or any part
 *       of it. <br>
 *       7) I the author do not guarantee, warrant, or make any representations,
 *       either expressed or implied, regarding the use of this library or any
 *       part of it. <br>
 *       <br>
 *       Author: Paul Lamb <br>
 *       http://www.paulscode.com </b>
 */

public class ModifiedLWJGLOpenALSource extends SourceLWJGLOpenAL {
	public ModifiedLWJGLOpenALSource(FloatBuffer listenerPosition, IntBuffer myBuffer, Source old, SoundBuffer soundBuffer) {
		super(listenerPosition, myBuffer, old, soundBuffer);
	}

	public ModifiedLWJGLOpenALSource(FloatBuffer listenerPosition, IntBuffer myBuffer, boolean priority, boolean toStream, boolean toLoop, String sourcename, FilenameURL filenameURL,
			SoundBuffer soundBuffer, float x, float y, float z, int attModel, float distOrRoll, boolean temporary) {
		super(listenerPosition, myBuffer, priority, toStream, toLoop, sourcename, filenameURL, soundBuffer, x, y, z, attModel, distOrRoll, temporary);
	}

	public ModifiedLWJGLOpenALSource(FloatBuffer listenerPosition, AudioFormat audioFormat, boolean priority, String sourcename, float x, float y, float z, int attModel, float distOrRoll) {
		super(listenerPosition, audioFormat, priority, sourcename, x, y, z, attModel, distOrRoll);
	}

	public boolean stopped() {
		boolean stopped = super.stopped();

		if (this.channel != null && this.channel.attachedSource == this && !stopped && !this.paused()) {
			this.updateFilters();
		}

		return stopped;
	}

	private void updateFilters() {
		ChannelLWJGLOpenAL alChannel = (ChannelLWJGLOpenAL) this.channel;

		if (alChannel != null && alChannel.ALSource != null && this.position != null) {
			Minecraft mc = Minecraft.getMinecraft();
			boolean isMusic = this.toStream && this.position.x == 0.0F && this.position.y == 0.0F && this.position.z == 0.0F && this.attModel == 0;

			if (!isMusic && mc != null && mc.theWorld != null) {
				boolean isOccluded = false;
				DoubleWithTimeout sourceInfo = new DoubleWithTimeout((Source) null, 0.0D, 10);

				if (SoundFiltersMod.doOcclusion && this.position != null) {
					ComparablePosition sourcePosition = new ComparablePosition(this.position.x, this.position.y, this.position.z);

					if (SoundTickHandler.sourceOcclusionMap.containsKey(sourcePosition)) {
						DoubleWithTimeout tempSourceInfo = (DoubleWithTimeout) SoundTickHandler.sourceOcclusionMap.get(sourcePosition);

						if(tempSourceInfo != null) {
							sourceInfo = tempSourceInfo;
							
							sourceInfo.timeout = 10;
							sourceInfo.source = this;
						} else {
							SoundTickHandler.sourceOcclusionMap.put(sourcePosition, sourceInfo);
						}
					}

					isOccluded = sourceInfo.amount > 0.05D;
				}

				SoundFiltersMod.lowPassFilter.gain = SoundTickHandler.baseLowPassGain;
				SoundFiltersMod.lowPassFilter.gainHF = SoundTickHandler.baseLowPassGainHF;

				if (isOccluded && this.attModel != 0) {
					SoundFiltersMod.lowPassFilter.gain = (float) ((double) SoundFiltersMod.lowPassFilter.gain * (1.0D - 1.0D * sourceInfo.amount));
					SoundFiltersMod.lowPassFilter.gainHF = (float) ((double) SoundFiltersMod.lowPassFilter.gainHF * (1.0D - 1.0D * (double) MathHelper.sqrt_double(sourceInfo.amount)));
				}

				if (SoundFiltersMod.lowPassFilter.gain >= 1.0F && SoundFiltersMod.lowPassFilter.gainHF >= 1.0F) {
					SoundFiltersMod.lowPassFilter.disable();
				} else {
					SoundFiltersMod.lowPassFilter.enable();
					SoundFiltersMod.lowPassFilter.loadParameters();
				}

				if (SoundFiltersMod.reverbFilter.reflectionsDelay <= 0.0F && SoundFiltersMod.reverbFilter.lateReverbDelay <= 0.0F) {
					SoundFiltersMod.reverbFilter.disable();
				} else {
					SoundFiltersMod.reverbFilter.enable();
					SoundFiltersMod.reverbFilter.loadParameters();
				}

				try {
					BaseFilter.loadSourceFilter(alChannel.ALSource.get(0), 131077, SoundFiltersMod.lowPassFilter);
					BaseFilter.load3SourceFilters(alChannel.ALSource.get(0), 131078, SoundFiltersMod.reverbFilter, (BaseFilter) null, SoundFiltersMod.lowPassFilter);
				} catch (FilterException e) {
					CrashReport crashreport = CrashReport.makeCrashReport(e, "Updating Sound Filters");
					throw new ReportedException(crashreport);
				}
			} else {
				SoundFiltersMod.lowPassFilter.disable();
				SoundFiltersMod.reverbFilter.disable();

				try {
					BaseFilter.loadSourceFilter(alChannel.ALSource.get(0), 131077, (BaseFilter) null);
					BaseFilter.load3SourceFilters(alChannel.ALSource.get(0), 131078, (BaseFilter) null, (BaseFilter) null, (BaseFilter) null);
				} catch (FilterException e) {
					CrashReport crashreport = CrashReport.makeCrashReport(e, "Updating Sound Filters");
					throw new ReportedException(crashreport);
				}
			}

		}
	}
}
