package com.tmtravlr.soundfilters.handlers;

import com.tmtravlr.soundfilters.SoundEventHandler;
import com.tmtravlr.soundfilters.SoundFiltersConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.Entity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.CachedBlockInfo;
import net.minecraft.util.math.*;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Holds the methods for calculating and managing the occlusion
 * @since December 2019
 */
public class OcclusionHandler {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final ConcurrentSkipListMap<ISound, Double> SOURCE_OCCLUSION_MAP = new ConcurrentSkipListMap<>((first, second) -> {
        if (first == second) {
            return 0;
        }

        if (second == null) {
            return 1;
        }

        if (first == null) {
            return -1;
        }

        return first.hashCode() - second.hashCode();
    });

    /**
     * Finds the occlusion amount for the given location, and adds a new source
     * to the sourceOccusionMap, with the source as null (will get set to the
     * actual source the first time it runs through the SoundTickHandler).
     */
    public static void addOcclusionToMap(ISound sound) {
        if (SoundFiltersConfig.OCCLUSION_ENABLED.get()) {
            double amount = 0.0D;

            if (MC.world != null && MC.player != null) {
                amount = getOccludedPercent(MC.world, sound, MC.player);
            }

            SOURCE_OCCLUSION_MAP.put(sound, amount);
        }
    }

    public static double getOcclusion(ISound sound) {
        return Optional.ofNullable(SOURCE_OCCLUSION_MAP.get(sound)).orElse(0.0);
    }

    /**
     * Updates the occlusion amounts based on the sound's and player's current
     * positions and removes any that are no longer playing.
     */
    public static void updateOcclusion() {
        if (SoundFiltersConfig.OCCLUSION_ENABLED.get()) {
            // Check for sounds that have stopped playing
            SOURCE_OCCLUSION_MAP.entrySet().removeIf((entry) -> !SoundEventHandler.playingSoundsChannel.containsKey(entry.getKey()));

            // Calculate sound occlusion for all the sounds playing.
            for (Map.Entry<ISound, Double> sourceEntry : SOURCE_OCCLUSION_MAP.entrySet()) {
                ISound sound = sourceEntry.getKey();
                Double amount = sourceEntry.getValue();

                if (sound != null && amount != null) {
                    if (MC.world != null && MC.player != null) {
                        SOURCE_OCCLUSION_MAP.put(sound, (amount * 3 + getOccludedPercent(MC.world, sound, MC.player)) / 4.0);
                    } else {
                        SOURCE_OCCLUSION_MAP.put(sound, 0.0);
                    }
                }
            }
        }
    }

    /**
     * Gets the occluded percent for the sound and player scaled to the config value
     */
    private static double getOccludedPercent(World world, ISound sound, Entity player) {
        return getBaseOccludedPercent(world, new Vec3d(sound.getX(), sound.getY(), sound.getZ()), MC.player.getPositionVec().add(0, player.getEyeHeight(),0));
    }

    /**
     * Ray traces through the world from sound to listener, and returns the
     * amount of occlusion the sound should have. Note that it ignores the block
     * which the sound plays from inside (so sounds playing from inside blocks
     * don't sound occluded). Goes through 200 blocks max.
     *
     * @return A double representing the amount of occlusion to apply to the
     *         sound
     */
    private static double getBaseOccludedPercent(World world, Vec3d sound, Vec3d listener) {
        double occludedPercent = 0.0D;

        // Fixes some funky things
        sound = sound.add(0.01, 0.01, 0.01);

        if (!Double.isNaN(sound.x) && !Double.isNaN(sound.y) && !Double.isNaN(sound.z)) {
            if (!Double.isNaN(listener.x) && !Double.isNaN(listener.y) && !Double.isNaN(listener.z)) {
                BlockPos listenerPos = new BlockPos(listener);
                BlockPos soundPos = new BlockPos(sound);
                Vec3d prevSound;
                BlockPos prevSoundPos;
                int i = 0;

                while (i++ < 200) {
                    prevSound = sound;
                    prevSoundPos = soundPos;

                    if (Double.isNaN(sound.x) || Double.isNaN(sound.y) || Double.isNaN(sound.z)) {
                        return occludedPercent;
                    }

                    if (soundPos.equals(listenerPos)) {
                        return occludedPercent;
                    }

                    boolean shouldChangeX = listenerPos.getX() != soundPos.getX();
                    boolean shouldChangeY = listenerPos.getY() != soundPos.getY();
                    boolean shouldChangeZ = listenerPos.getZ() != soundPos.getZ();
                    int nextX = soundPos.getX() + (listenerPos.getX() > soundPos.getX() ? 1 : 0);
                    int nextY = soundPos.getY() + (listenerPos.getY() > soundPos.getY() ? 1 : 0);
                    int nextZ = soundPos.getZ() + (listenerPos.getZ() > soundPos.getZ() ? 1 : 0);

                    double xDifference = listener.x - sound.x;
                    double yDifference = listener.y - sound.y;
                    double zDifference = listener.z - sound.z;
                    double xPercentChange = shouldChangeX ? (((double) nextX - sound.x) / xDifference) : Double.POSITIVE_INFINITY;
                    double yPercentChange = shouldChangeY ? (((double) nextY - sound.y) / yDifference) : Double.POSITIVE_INFINITY;
                    double zPercentChange = shouldChangeZ ? (((double) nextZ - sound.z) / zDifference) : Double.POSITIVE_INFINITY;
                    BlockPos soundPosOffset = null;

                    if (xPercentChange < yPercentChange && xPercentChange < zPercentChange) {
                        sound = new Vec3d(nextX, sound.y + yDifference * xPercentChange, sound.z + zDifference * xPercentChange);

                        if (listenerPos.getX() < soundPos.getX()) {
                            soundPosOffset = new BlockPos(-1, 0, 0);
                        }
                    } else if (yPercentChange < zPercentChange) {
                        sound = new Vec3d(sound.x + xDifference * yPercentChange, nextY, sound.z + zDifference * yPercentChange);

                        if (listenerPos.getY() < soundPos.getY()) {
                            soundPosOffset = new BlockPos(0, -1, 0);
                        }
                    } else {
                        sound = new Vec3d(sound.x + xDifference * zPercentChange, sound.y + yDifference * zPercentChange, nextZ);

                        if (listenerPos.getZ() < soundPos.getZ()) {
                            soundPosOffset = new BlockPos(0, 0, -1);
                        }
                    }

                    soundPos = new BlockPos(sound);

                    if (soundPosOffset != null) {
                        soundPos = soundPos.add(soundPosOffset);
                    }

                    // Skip the block the sound is playing from
                    if (i > 1) {
                        CachedBlockInfo worldState = new CachedBlockInfo(world, prevSoundPos, true);
                        BlockState state = worldState.getBlockState();
                        Material material = state.getMaterial();
                        VoxelShape collisionShape = state.getCollisionShape(world, prevSoundPos);

                        if (false) { // leaving this in for debugging
                            MC.world.addOptionalParticle(ParticleTypes.DOLPHIN, prevSound.getX(), prevSound.getY(), prevSound.getZ(), 0, 0, 0);
                        }

                        if (!state.isAir(world, prevSoundPos) && !state.getShape(world, prevSoundPos).isEmpty() && collisionShape != VoxelShapes.empty()) {
                            BlockRayTraceResult rayTrace = collisionShape.rayTrace(prevSound, listener, prevSoundPos);

                            if (rayTrace != null) {
                                double occlusionMultiplier = SoundFiltersConfig.OCCLUSION_MULTIPLIER.get();
                                double occlusionMax = SoundFiltersConfig.OCCLUSION_MAX.get();

                                // Check for custom occlusion blocks
                                Double customOcclusion = null;
                                if (rayTrace.getType() == RayTraceResult.Type.BLOCK) {
                                    customOcclusion = SoundFiltersConfig.getCustomBlockOcclusion(MC.world, new CachedBlockInfo(world, rayTrace.getPos(), false));
                                }

                                double newOcclusion;

                                if (customOcclusion != null) {
                                    newOcclusion = customOcclusion * occlusionMultiplier;
                                } else {
                                    newOcclusion = material.isOpaque() ? occlusionMultiplier : occlusionMultiplier / 2;
                                }

                                newOcclusion *= sound.distanceTo(prevSound);
                                occludedPercent += newOcclusion;

                                if (occludedPercent > occlusionMax) {
                                    return occlusionMax;
                                }
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
