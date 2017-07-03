package com.tmtravlr.soundfilters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import com.tmtravlr.soundfilters.SoundFiltersMod.BlockMeta;

import paulscode.sound.Source;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * This class handles the tick events and updates the filter parameters for each
 * source playing.
 * 
 * @author Rebeca Rey
 * @Date 2014
 */
public class SoundTickHandler {
	private Minecraft mc = Minecraft.getMinecraft();
	private static int profileTickCountdown = 13;
	private static float prevDecayFactor = 0.0F;
	private static float prevRoomFactor = 0.0F;
	private static float prevSkyFactor = 0.0F;
	public static float baseLowPassGain = 1.0F;
	public static float baseLowPassGainHF = 1.0F;
	public static boolean waterSound = false;
	public static boolean lavaSound = false;

	// Comparator for the ComparablePositions
	public static Comparator<ComparablePosition> CPcomparator = new Comparator<ComparablePosition>() {
		public int compare(SoundTickHandler.ComparablePosition first, SoundTickHandler.ComparablePosition second) {
			return first.compareTo(second);
		}
	};

	public static ConcurrentSkipListMap<ComparablePosition, DoubleWithTimeout> sourceOcclusionMap = new ConcurrentSkipListMap(CPcomparator);

	// static
	// {
	// //Synchronize the sourceOcclusionMap
	// sourceOcclusionMap = Collections.synchronizedMap(sourceOcclusionMap);
	// }

	/**
	 * Represents a x, y, z position which has a comparator and a few methods
	 * that make comparing it with another ComparablePositions very fast. Used
	 * for fast-access sets/maps.
	 * 
	 * @author Rebeca Rey
	 * @Date 2014
	 */
	public static class ComparablePosition implements Comparable {
		float x;
		float y;
		float z;

		ComparablePosition(float xToSet, float yToSet, float zToSet) {
			this.x = xToSet;
			this.y = yToSet;
			this.z = zToSet;
		}

		// Return the int floor of x
		public int x() {
			return MathHelper.floor_float(x);
		}

		// Return the int floor of y
		public int y() {
			return MathHelper.floor_float(y);
		}

		// Return the int floor of z
		public int z() {
			return MathHelper.floor_float(z);
		}

		public boolean equals(Object object) {
			if (!(object instanceof ComparablePosition)) {
				return false;
			}
			ComparablePosition toCompare = (ComparablePosition) object;

			return toCompare.compareTo(this) == 0;
		}

		public int compareTo(Object object) {
			if (!(object instanceof ComparablePosition)) {
				return 0;
			}
			ComparablePosition toCompare = (ComparablePosition) object;

			if (toCompare.x - this.x > 0.1) {
				return 1;
			} else if (toCompare.x - this.x < -0.1) {
				return -1;
			} else if (toCompare.y - this.y > 0.1) {
				return 1;
			} else if (toCompare.y - this.y < -0.1) {
				return -1;
			} else if (toCompare.z - this.z > 0.1) {
				return 1;
			} else if (toCompare.z - this.z < -0.1) {
				return -1;
			} else {
				return 0;
			}
		}

		@Override
		public String toString() {
			return "[" + x + ", " + y + ", " + z + "]";
		}
	}

	/**
	 * Represents two values for use in the occlusion: the sound source and
	 * occlusion amount. Also has a timeout variables, which the source should
	 * be constantly setting to 10. When the source stops setting it to 10, the
	 * sound is assumed to have finished playing.
	 * 
	 * @author Rebeca Rey
	 * @Date 2014
	 */
	public static class DoubleWithTimeout {
		public Source source;
		public double amount;
		public int timeout;

		DoubleWithTimeout() {
			this(null, 0.0D, 10);
		}

		DoubleWithTimeout(Source sToSet, double dToSet, int iToSet) {
			this.source = sToSet;
			this.amount = dToSet;
			this.timeout = iToSet;
		}
	}

	@SubscribeEvent
	public void tick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			// Handle the low-pass inside of liquids
			if (this.mc != null && this.mc.theWorld != null && this.mc.thePlayer != null) {
				if (this.mc.thePlayer.isInsideOfMaterial(Material.WATER)) {
					if (!waterSound) {
						if (SoundFiltersMod.DEBUG) {
							System.out.println("[SoundFilters] Applying water sound low pass.");
						}

						baseLowPassGain = 1.0F;
						baseLowPassGainHF = 0.4F;
						lavaSound = false;
						waterSound = true;
					}
				} else if (waterSound) {
					if (SoundFiltersMod.DEBUG) {
						System.out.println("[SoundFilters] Stopping water sound low pass.");
					}

					baseLowPassGain = 1.0F;
					baseLowPassGainHF = 1.0F;
					waterSound = false;
				}

				if (this.mc.thePlayer.isInsideOfMaterial(Material.LAVA)) {
					if (!lavaSound) {
						if (SoundFiltersMod.DEBUG) {
							System.out.println("[SoundFilters] Applying lava sound low pass.");
						}

						baseLowPassGain = 0.6F;
						baseLowPassGainHF = 0.2F;
						lavaSound = true;
						waterSound = false;
					}
				} else if (lavaSound) {
					if (SoundFiltersMod.DEBUG) {
						System.out.println("[SoundFilters] Stopping lava sound low pass.");
					}

					baseLowPassGain = 1.0F;
					baseLowPassGainHF = 1.0F;
					lavaSound = false;
				}
			} else {
				// We must be in the menu; turn everything off:

				baseLowPassGain = 1.0F;
				baseLowPassGainHF = 1.0F;
				SoundFiltersMod.reverbFilter.decayTime = 0.1F;
				SoundFiltersMod.reverbFilter.reflectionsDelay = 0.0F;
				SoundFiltersMod.reverbFilter.lateReverbDelay = 0.0F;
				lavaSound = false;
				waterSound = false;
			}
		} else {
			ArrayList<ComparablePosition> toRemove = new ArrayList<ComparablePosition>();

			// Calculate sound occlusion for all the sources playing.
			if (SoundFiltersMod.doOcclusion) {
				// synchronized (sourceOcclusionMap)
				// {
				for (ComparablePosition sourcePosition : sourceOcclusionMap.keySet()) {
					DoubleWithTimeout sourceAndAmount = (DoubleWithTimeout) sourceOcclusionMap.get(sourcePosition);
					if (sourceAndAmount != null) {
						if (sourceAndAmount.source != null && sourceAndAmount.source.position != null && sourceAndAmount.source.playing() && !sourceAndAmount.source.stopped()
								&& sourceAndAmount.timeout > 0) {
							sourceAndAmount.timeout--;

							if (this.mc != null && this.mc.theWorld != null && this.mc.thePlayer != null) {
								Vec3d roomSize = new Vec3d(this.mc.thePlayer.posX, this.mc.thePlayer.posY + (double) this.mc.thePlayer.getEyeHeight(), this.mc.thePlayer.posZ);
								sourceAndAmount.amount = (sourceAndAmount.amount * 3 + getSoundOcclusion(this.mc.theWorld,
										new Vec3d((double) sourceAndAmount.source.position.x, (double) sourceAndAmount.source.position.y, (double) sourceAndAmount.source.position.z), roomSize)) / 4.0;
							} else {
								sourceAndAmount.amount = 0.0D;
							}
						} else {
							// Remove any sources that have "timed out" (have
							// stopped setting the timeout
							// to 10 because they finished playing)

							if (sourceAndAmount.timeout <= 0) {
								toRemove.add(sourcePosition);
							}

							--sourceAndAmount.timeout;
						}
					}
				}

				for (ComparablePosition positionToRemove : toRemove) {
					if (SoundFiltersMod.SUPER_DUPER_DEBUG)
						System.out.println("[Sound Filters] Removing " + positionToRemove + ", " + positionToRemove.hashCode() + ", " + profileTickCountdown);
					sourceOcclusionMap.remove(positionToRemove);
				}
			}

			// Create a profile of the reverb in the area.
			if (this.mc != null && this.mc.theWorld != null && this.mc.thePlayer != null && SoundFiltersMod.doReverb) {
				--profileTickCountdown;

				// Only run every 13 ticks.
				if (profileTickCountdown <= 0) {
					profileTickCountdown = 13;

					Random rand = new Random();
					TreeSet<ComparablePosition> visited = new TreeSet<ComparablePosition>(CPcomparator);
					ArrayList<IBlockState> blocksFound = new ArrayList<IBlockState>();

					LinkedList<ComparablePosition> toVisit = new LinkedList<ComparablePosition>();
					toVisit.add(new ComparablePosition(MathHelper.floor_double(this.mc.thePlayer.posX), MathHelper.floor_double(this.mc.thePlayer.posY), MathHelper.floor_double(this.mc.thePlayer.posZ)));
					Block block;
					IBlockState state;

					// Flood fill through the area the player is in, with
					// maximum size of
					// SoundFiltersMod.profileSize (the size in the config file)
					int i;
					for (i = 0; i < SoundFiltersMod.profileSize && !toVisit.isEmpty(); i++) {
						ComparablePosition current = (ComparablePosition) toVisit.remove(rand.nextInt(toVisit.size()));
						visited.add(current);

						// Check for valid neighbours in 6 directions (valid
						// means a non-solid block that hasn't been visited)

						// South
						ComparablePosition pos = new ComparablePosition(current.x, current.y, current.z + 1);
						state = this.mc.theWorld.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()));
						block = state.getBlock();
						Material material = state.getMaterial();

						if (!material.blocksMovement()) {
							if (!visited.contains(pos) && !toVisit.contains(pos)) {
								toVisit.add(pos);
							}

							if (material != Material.AIR) {
								blocksFound.add(state);
							}
						} else {
							blocksFound.add(state);
						}

						// North
						pos = new ComparablePosition(current.x, current.y, current.z - 1);
						state = this.mc.theWorld.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()));
						block = state.getBlock();
						material = state.getMaterial();

						if (!material.blocksMovement()) {
							if (!visited.contains(pos) && !toVisit.contains(pos)) {
								toVisit.add(pos);
							}

							if (material != Material.AIR) {
								blocksFound.add(state);
							}
						} else {
							blocksFound.add(state);
						}

						// Up
						pos = new ComparablePosition(current.x, current.y + 1, current.z);
						state = this.mc.theWorld.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()));
						block = state.getBlock();
						material = state.getMaterial();

						if (!material.blocksMovement()) {
							if (!visited.contains(pos) && !toVisit.contains(pos)) {
								toVisit.add(pos);
							}

							if (material != Material.AIR) {
								blocksFound.add(state);
							}
						} else {
							blocksFound.add(state);
						}

						// Down
						pos = new ComparablePosition(current.x, current.y - 1, current.z);
						state = this.mc.theWorld.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()));
						block = state.getBlock();
						material = state.getMaterial();

						if (!material.blocksMovement()) {
							if (!visited.contains(pos) && !toVisit.contains(pos)) {
								toVisit.add(pos);
							}

							if (material != Material.AIR) {
								blocksFound.add(state);
							}
						} else {
							blocksFound.add(state);
						}

						// East
						pos = new ComparablePosition(current.x + 1, current.y, current.z);
						state = this.mc.theWorld.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()));
						block = state.getBlock();
						material = state.getMaterial();

						if (!material.blocksMovement()) {
							if (!visited.contains(pos) && !toVisit.contains(pos)) {
								toVisit.add(pos);
							}

							if (material != Material.AIR) {
								blocksFound.add(state);
							}
						} else {
							blocksFound.add(state);
						}

						// West
						pos = new ComparablePosition(current.x - 1, current.y, current.z);
						state = this.mc.theWorld.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()));
						block = state.getBlock();
						material = state.getMaterial();

						if (!material.blocksMovement()) {
							if (!visited.contains(pos) && !toVisit.contains(pos)) {
								toVisit.add(pos);
							}

							if (material != Material.AIR) {
								blocksFound.add(state);
							}
						} else {
							blocksFound.add(state);
						}
					}

					// Now we've gone through the whole room, or we hit the size
					// limit.
					// Figure out the reverb from the blocks found.

					int roomSize = visited.size();
					double highReverb = 0;
					double midReverb = 0;
					double lowReverb = 0;

					for (IBlockState s : blocksFound) {
						Block b = s.getBlock();

						// Check for custom reverb blocks
						BlockMeta blockInfo = new BlockMeta(b, 16);

						if (!SoundFiltersMod.customReverb.containsKey(blockInfo)) {
							blockInfo = new BlockMeta(b, b.getMetaFromState(s));
						}

						if (SoundFiltersMod.customReverb.containsKey(blockInfo)) {
							double factor = SoundFiltersMod.customReverb.get(blockInfo);

							lowReverb += factor >= 1.0 || factor < 0.0 ? 0.0 : 1.0 - factor;
							midReverb += factor >= 2.0 || factor <= 0.0 ? 0.0 : 1.0 - Math.abs(factor - 1.0);
							highReverb += factor <= 1.0 ? 0.0 : factor > 2.0 ? 1.0 : factor - 1.0;
						} else if (s.getMaterial() != Material.ROCK && s.getMaterial() != Material.GLASS && s.getMaterial() != Material.ICE && s.getMaterial() != Material.IRON) {
							if (s.getMaterial() != Material.CACTUS && s.getMaterial() != Material.CAKE && s.getMaterial() != Material.CLOTH && s.getMaterial() != Material.CORAL
									&& s.getMaterial() != Material.GRASS && s.getMaterial() != Material.LEAVES && s.getMaterial() != Material.CARPET && s.getMaterial() != Material.PLANTS
									&& s.getMaterial() != Material.GOURD && s.getMaterial() != Material.SNOW && s.getMaterial() != Material.SPONGE && s.getMaterial() != Material.VINE
									&& s.getMaterial() != Material.WEB) {
								// Generic materials that don't fall into either
								// catagory (wood, dirt, etc.)
								++midReverb;
							} else {
								// Reverb-absorbing materials (like wool and
								// plants)
								++lowReverb;
							}
						} else {
							// Materials that relfect sound (smooth and solid
							// like stone, glass, and ice)
							++highReverb;
						}

					}

					float skyFactor = 0.0F;

					if (SoundFiltersMod.doSkyChecks && roomSize == SoundFiltersMod.profileSize) {
						/*
						 * Check if you and blocks around you can see the sky in
						 * a pattern like so:
						 * 
						 * B B B
						 * 
						 * B P B
						 * 
						 * B B B
						 * 
						 * With distances of random from 5 to 10, and also above
						 * 5 for the B's
						 */

						int x = MathHelper.floor_double(mc.thePlayer.posX);
						int y = MathHelper.floor_double(mc.thePlayer.posY);
						int z = MathHelper.floor_double(mc.thePlayer.posZ);

						if (onlySkyAboveBlock(mc.theWorld, x, y, z))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x + rand.nextInt(5) + 5, y, z))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x - rand.nextInt(5) + 5, y, z))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x, y, z + rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x, y, z - rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x + rand.nextInt(5) + 5, y, z + rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x - rand.nextInt(5) + 5, y, z + rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x + rand.nextInt(5) + 5, y, z - rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x - rand.nextInt(5) + 5, y, z - rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x + rand.nextInt(5) + 5, y + 5, z))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x - rand.nextInt(5) + 5, y + 5, z))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x, y + 5, z + rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x, y + 5, z - rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x + rand.nextInt(5) + 5, y + 5, z + rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x - rand.nextInt(5) + 5, y + 5, z + rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x + rand.nextInt(5) + 5, y + 5, z - rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x - rand.nextInt(5) + 5, y + 5, z - rand.nextInt(5) + 5))
							skyFactor++;

						// System.out.println(skyFactor);
					}

					skyFactor = 1.0F - skyFactor / 17.0F;

					float decayFactor = 0.0F;
					float roomFactor = (float) roomSize / (float) SoundFiltersMod.profileSize;

					if (highReverb + midReverb + lowReverb > 0) {
						decayFactor += (float) (highReverb - lowReverb) / (float) (highReverb + midReverb + lowReverb);
					}

					if (decayFactor < 0.0F) {
						decayFactor = 0.0F;
					}

					if (decayFactor > 1.0F) {
						decayFactor = 1.0F;
					}

					if (SoundFiltersMod.SUPER_DUPER_DEBUG) {
						System.out.println("[Sound Filters] Reverb Profile - Room Size: " + roomSize + ", Looked at: " + i + ", Sky Factor: " + skyFactor + ", High, Mid, and Low Reverb: ("
								+ highReverb + ", " + midReverb + ", " + lowReverb + ")");
					}

					decayFactor = (decayFactor + prevDecayFactor) / 2.0F;
					roomFactor = (roomFactor + prevRoomFactor) / 2.0F;
					skyFactor = (skyFactor + prevSkyFactor) / 2.0F;

					prevDecayFactor = decayFactor;
					prevRoomFactor = roomFactor;
					prevSkyFactor = skyFactor;

					SoundFiltersMod.reverbFilter.decayTime = SoundFiltersMod.reverbPercent * 8.0F * decayFactor * roomFactor * skyFactor;

					if (SoundFiltersMod.reverbFilter.decayTime < 0.1F) {
						SoundFiltersMod.reverbFilter.decayTime = 0.1F;
					}

					SoundFiltersMod.reverbFilter.reflectionsGain = SoundFiltersMod.reverbPercent * (0.05F + (0.05F * roomFactor));
					SoundFiltersMod.reverbFilter.reflectionsDelay = 0.025F * roomFactor;
					SoundFiltersMod.reverbFilter.lateReverbGain = SoundFiltersMod.reverbPercent * (1.26F + (0.1F * roomFactor));
					SoundFiltersMod.reverbFilter.lateReverbDelay = 0.01F * roomFactor;
				}
			}
		}
	}

	private static boolean onlySkyAboveBlock(World world, int x, int y, int z) {
		for (int i = y; i < 256; i++) {
			IBlockState state = world.getBlockState(new BlockPos(x, i, z));
			Block block = state.getBlock();

			if (state.getMaterial().blocksMovement()) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Ray traces through the world from sound to listener, and returns the
	 * amount of occlusion the sound should have. Note that it ignores the block
	 * which the sound plays from inside (so sounds playing from inside blocks
	 * don't sound occluded).
	 * 
	 * @param world
	 *            The world
	 * @param sound
	 *            The position the sound plays from
	 * @param listener
	 *            The listener's position (the player)
	 * @return A double representing the amount of occlusion to apply to the
	 *         sound
	 */
	public static double getSoundOcclusion(World world, Vec3d sound, Vec3d listener) {
		double occludedPercent = 0.0D;

		// Fixes some funky things
		sound = sound.addVector(0.1, 0.1, 0.1);

		if (!Double.isNaN(sound.xCoord) && !Double.isNaN(sound.yCoord) && !Double.isNaN(sound.zCoord)) {
			if (!Double.isNaN(listener.xCoord) && !Double.isNaN(listener.yCoord) && !Double.isNaN(listener.zCoord)) {
				int listenerX = MathHelper.floor_double(listener.xCoord);
				int listenerY = MathHelper.floor_double(listener.yCoord);
				int listenerZ = MathHelper.floor_double(listener.zCoord);
				int soundX = MathHelper.floor_double(sound.xCoord);
				int soundY = MathHelper.floor_double(sound.yCoord);
				int soundZ = MathHelper.floor_double(sound.zCoord);
				int countDown = 200;

				while (countDown-- >= 0) {
					if (Double.isNaN(sound.xCoord) || Double.isNaN(sound.yCoord) || Double.isNaN(sound.zCoord)) {
						return occludedPercent;
					}

					if (soundX == listenerX && soundY == listenerY && soundZ == listenerZ) {
						return occludedPercent;
					}

					boolean shouldChangeX = true;
					boolean shouldChangeY = true;
					boolean shouldChangeZ = true;
					double newX = 999.0D;
					double newY = 999.0D;
					double newZ = 999.0D;

					if (listenerX == soundX) {
						shouldChangeX = false;
					}

					if (shouldChangeX) {
						newX = (double) soundX + (listenerX > soundX ? 1.0D : 0.0D);
					}

					if (listenerY == soundY) {
						shouldChangeY = false;
					}

					if (shouldChangeY) {
						newY = (double) soundY + (listenerY > soundY ? 1.0D : 0.0D);
					}

					if (listenerZ == soundZ) {
						shouldChangeZ = false;
					}

					if (shouldChangeZ) {
						newZ = (double) soundZ + (listenerZ > soundZ ? 1.0D : 0.0D);
					}

					double xPercentChange = 999.0D;
					double yPercentChange = 999.0D;
					double zPercentChange = 999.0D;
					double xDifference = listener.xCoord - sound.xCoord;
					double yDifference = listener.yCoord - sound.yCoord;
					double zDifference = listener.zCoord - sound.zCoord;

					if (shouldChangeX) {
						xPercentChange = (newX - sound.xCoord) / xDifference;
					}

					if (shouldChangeY) {
						yPercentChange = (newY - sound.yCoord) / yDifference;
					}

					if (shouldChangeZ) {
						zPercentChange = (newZ - sound.zCoord) / zDifference;
					}

					byte whichToChange;

					if (xPercentChange < yPercentChange && xPercentChange < zPercentChange) {
						if (listenerX > soundX) {
							whichToChange = 4;
						} else {
							whichToChange = 5;
						}

						sound = new Vec3d(newX, sound.yCoord + yDifference * xPercentChange, sound.zCoord + zDifference * xPercentChange);
					} else if (yPercentChange < zPercentChange) {
						if (listenerY > soundY) {
							whichToChange = 0;
						} else {
							whichToChange = 1;
						}

						sound = new Vec3d(sound.xCoord + xDifference * yPercentChange, newY, sound.zCoord + zDifference * yPercentChange);
					} else {
						if (listenerZ > soundZ) {
							whichToChange = 2;
						} else {
							whichToChange = 3;
						}

						sound = new Vec3d(sound.xCoord + xDifference * zPercentChange, sound.yCoord + yDifference * zPercentChange, newZ);
					}

					Vec3d vec32 = new Vec3d(MathHelper.floor_double(sound.xCoord), MathHelper.floor_double(sound.yCoord), MathHelper.floor_double(sound.zCoord));
					soundX = (int) vec32.xCoord;

					if (whichToChange == 5) {
						--soundX;
						vec32 = vec32.addVector(1.0, 0.0, 0.0);
					}

					soundY = (int) vec32.yCoord;

					if (whichToChange == 1) {
						--soundY;
						vec32 = vec32.addVector(0.0, 1.0, 0.0);
					}

					soundZ = (int) vec32.zCoord;

					if (whichToChange == 3) {
						--soundZ;
						vec32 = vec32.addVector(0.0, 0.0, 1.0);
					}

					BlockPos pos = new BlockPos(soundX, soundY, soundZ);
					IBlockState state = world.getBlockState(pos);
					Block block = world.getBlockState(pos).getBlock();
					int meta = block.getMetaFromState(state);
					Material material = state.getMaterial();

					if (block != null && block != Blocks.AIR && state.getBoundingBox(world, new BlockPos(soundX, soundY, soundZ)) != Block.NULL_AABB && block.canCollideCheck(state, false)) {
						RayTraceResult rayTrace = state.collisionRayTrace(world, pos, sound, listener);

						if (rayTrace != null) {
							// Check for custom occlusion blocks
							BlockMeta blockInfo = new BlockMeta(block, 16);

							if (!SoundFiltersMod.customOcclusion.containsKey(blockInfo)) {
								blockInfo = new BlockMeta(block, meta);
							}

							if (SoundFiltersMod.customOcclusion.containsKey(blockInfo)) {
								occludedPercent += SoundFiltersMod.customOcclusion.get(blockInfo) * 0.1D;
							} else if (occludedPercent < 0.7D) {
								occludedPercent += material.isOpaque() ? 0.1D : 0.05D;
							}

							if (occludedPercent > 0.98D) {
								return 0.98D;
							}
						}
					}
				}

				return occludedPercent;
			} else {
				return occludedPercent;
			}
		} else {
			return occludedPercent;
		}
	}
}
