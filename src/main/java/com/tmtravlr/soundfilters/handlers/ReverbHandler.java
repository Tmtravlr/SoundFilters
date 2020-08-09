package com.tmtravlr.soundfilters.handlers;

import com.tmtravlr.soundfilters.SoundFiltersConfig;
import com.tmtravlr.soundfilters.utils.ComparablePosition;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.CachedBlockInfo;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.*;

/**
 * Holds the methods for calculating and managing the reverb
 * @since December 2019
 */
public class ReverbHandler {
    private static final List<Material> HIGH_REVERB_MATERIALS = Arrays.asList(Material.ROCK, Material.GLASS, Material.ICE, Material.PACKED_ICE, Material.IRON);
    private static final List<Material> LOW_REVERB_MATERIALS = Arrays.asList(Material.CARPET, Material.WOOL, Material.WEB, Material.CAKE, Material.ORGANIC, Material.PLANTS, Material.TALL_PLANTS, Material.GOURD, Material.BAMBOO, Material.BAMBOO_SAPLING, Material.LEAVES, Material.CACTUS, Material.OCEAN_PLANT, Material.SEA_GRASS, Material.CORAL, Material.SPONGE, Material.SNOW, Material.SNOW_BLOCK);
    private static final Minecraft MC = Minecraft.getInstance();

    private static int profileTickCount = 0;
    private static float prevDecayFactor = 0.0F;
    private static float prevRoomFactor = 0.0F;
    private static float prevSkyFactor = 0.0F;

    /**
     * Sets the initial reverb values
     */
    public static void initializeReverb() {
        FilterHandler.initializeReverbFilter();
    }

    /**
     * Stops the reverb, for instance in menus
     */
    public static void stopReverb() {
        initializeReverb();
    }

    /**
     * Update the reverb for the player. Flood fill the area
     * the player is in to find the size and materials around
     * them, and also check to see if the sky is visible.
     */
    public static void updateReverb() {
        // Create a profile of the reverb in the area.
        if (MC.world != null && MC.player != null && Optional.ofNullable(SoundFiltersConfig.REVERB_ENABLED.get()).orElse(true)) {
            // Only run every 13 ticks.
            if (++profileTickCount >= 13) {
                profileTickCount = 0;

                Random rand = new Random();
                int maxBlocks = Optional.ofNullable(SoundFiltersConfig.REVERB_MAX_BLOCKS.get()).orElse(1024);
                float baseReverb = 0;
                Double customDimensionReverb = SoundFiltersConfig.getCustomDimensionReverb(MC.world.func_234923_W_().getRegistryName());

                if (customDimensionReverb != null) {
                    baseReverb = customDimensionReverb.floatValue();
                }

                TreeSet<ComparablePosition> visited = new TreeSet<>();
                ArrayList<CachedBlockInfo> blocksFound = new ArrayList<>();

                LinkedList<ComparablePosition> toVisit = new LinkedList<>();
                toVisit.add(new ComparablePosition(MathHelper.floor(MC.player.getPositionVec().getX()), MathHelper.floor(MC.player.getPositionVec().getY() + MC.player.getEyeHeight()), MathHelper.floor(MC.player.getPositionVec().getZ())));

                // Flood fill through the area the player is in, with maximum size
                // of maxBlocks (the size in the config file)
                int i;
                for (i = 0; i < maxBlocks && !toVisit.isEmpty(); i++) {
                    ComparablePosition current = toVisit.remove(rand.nextInt(toVisit.size()));
                    visited.add(current);

                    if (false) { // leaving this in for debugging
                        MC.world.addOptionalParticle(ParticleTypes.MYCELIUM, current.x() + 0.5, current.y() + 0.5, current.z() + 0.5, 0, 0, 0);
                    }

                    // Check for valid neighbours in 6 directions (valid
                    // means a non-solid block that hasn't been visited)

                    // South
                    findBlock(visited, toVisit, blocksFound, new ComparablePosition(current.x(), current.y(), current.z() + 1));

                    // North
                    findBlock(visited, toVisit, blocksFound, new ComparablePosition(current.x(), current.y(), current.z() - 1));

                    // Up
                    findBlock(visited, toVisit, blocksFound, new ComparablePosition(current.x(), current.y() + 1, current.z()));

                    // Down
                    findBlock(visited, toVisit, blocksFound, new ComparablePosition(current.x(), current.y() - 1, current.z()));

                    // East
                    findBlock(visited, toVisit, blocksFound, new ComparablePosition(current.x() + 1, current.y(), current.z()));

                    // West
                    findBlock(visited, toVisit, blocksFound, new ComparablePosition(current.x() - 1, current.y(), current.z()));
                }

                // Now we've gone through the whole room, or we hit the size limit.
                // Figure out the reverb from the blocks found.

                int roomSize = visited.size();
                double highReverb = 0;
                double midReverb = 0;
                double lowReverb = 0;

                for (CachedBlockInfo worldState : blocksFound) {

                    // Check for custom reverb blocks
                    Double customReverb = SoundFiltersConfig.getCustomBlockReverb(MC.world, worldState);

                    if (customReverb != null) {
                        lowReverb += customReverb >= 1.0 || customReverb < 0.0 ? 0.0 : 1.0 - customReverb;
                        midReverb += customReverb >= 2.0 || customReverb <= 0.0 ? 0.0 : 1.0 - Math.abs(customReverb - 1.0);
                        highReverb += customReverb <= 1.0 ? 0.0 : customReverb > 2.0 ? 1.0 : customReverb - 1.0;
                    } else {
                        BlockState state = worldState.getBlockState();

                        if (!HIGH_REVERB_MATERIALS.contains(state.getMaterial())) {
                            if (!LOW_REVERB_MATERIALS.contains(state.getMaterial())) {
                                // Generic materials that don't fall into either category (wood, dirt, etc.)
                                ++midReverb;
                            } else {
                                // Sound-absorbing materials (like wool and plants)
                                ++lowReverb;
                            }
                        } else {
                            // Materials that reflect sound (smooth and solid like stone, glass, and ice)
                            ++highReverb;
                        }
                    }
                }

                float skyFactor = 0.0F;

                if (Optional.ofNullable(SoundFiltersConfig.REVERB_SKY_CHECKS.get()).orElse(true) && roomSize == maxBlocks) {
                    /*
                     * Check if you and blocks around you can see the sky in
                     * a pattern like so:
                     *
                     * B  B  B
                     *
                     * B  P  B
                     *
                     * B  B  B
                     *
                     * With distances of random from 5 to 10, and also above
                     * 5 for the B's
                     */

                    int x = MathHelper.floor(MC.player.getPositionVec().getX());
                    int y = MathHelper.floor(MC.player.getPositionVec().getY() + MC.player.getEyeHeight());
                    int z = MathHelper.floor(MC.player.getPositionVec().getZ());

                    if (onlySkyAboveBlock(MC.world, x, y, z))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x + rand.nextInt(5) + 5, y, z))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x - rand.nextInt(5) - 5, y, z))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x, y, z + rand.nextInt(5) + 5))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x, y, z - rand.nextInt(5) - 5))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x + rand.nextInt(5) + 5, y, z + rand.nextInt(5) + 5))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x - rand.nextInt(5) - 5, y, z + rand.nextInt(5) + 5))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x + rand.nextInt(5) + 5, y, z - rand.nextInt(5) - 5))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x - rand.nextInt(5) - 5, y, z - rand.nextInt(5) - 5))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x + rand.nextInt(5) + 5, y + 5, z))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x - rand.nextInt(5) - 5, y + 5, z))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x, y + 5, z + rand.nextInt(5) + 5))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x, y + 5, z - rand.nextInt(5) - 5))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x + rand.nextInt(5) + 5, y + 5, z + rand.nextInt(5) + 5))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x - rand.nextInt(5) - 5, y + 5, z + rand.nextInt(5) + 5))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x + rand.nextInt(5) + 5, y + 5, z - rand.nextInt(5) - 5))
                        skyFactor++;
                    if (onlySkyAboveBlock(MC.world, x - rand.nextInt(5) - 5, y + 5, z - rand.nextInt(5) - 5))
                        skyFactor++;
                }

                skyFactor = 1.0F - Math.min(skyFactor, 12F) / 12F;
                skyFactor *= skyFactor;

                float decayFactor = baseReverb;
                float roomFactor = (float) roomSize / (float) maxBlocks;

                if (highReverb + midReverb + lowReverb > 0) {
                    decayFactor += (float) (highReverb - lowReverb) / (float) (highReverb + midReverb + lowReverb);
                }

                if (decayFactor < 0.0F) {
                    decayFactor = 0.0F;
                }

                if (decayFactor > 1.0F) {
                    decayFactor = 1.0F;
                }

                decayFactor = (decayFactor + prevDecayFactor) / 2.0F;
                roomFactor = (roomFactor + prevRoomFactor) / 2.0F;
                skyFactor = (skyFactor + prevSkyFactor) / 2.0F;

                prevDecayFactor = decayFactor;
                prevRoomFactor = roomFactor;
                prevSkyFactor = skyFactor;

                FilterHandler.updateReverbFilter(decayFactor, roomFactor, skyFactor);
            }
        }
    }

    private static boolean onlySkyAboveBlock(World world, int x, int y, int z) {
        if (false) { // leaving this in for debugging
            MC.world.addOptionalParticle(ParticleTypes.FIREWORK, x + 0.5, y + 0.5, z + 0.5, 0, 0.5, 0);
        }

        for (int i = y; i < 256; i++) {
            BlockState state = world.getBlockState(new BlockPos(x, i, z));

            if (state.getMaterial().blocksMovement()) {
                return false;
            }
        }

        return true;
    }

    private static void findBlock(TreeSet<ComparablePosition> visited, LinkedList<ComparablePosition> toVisit, ArrayList<CachedBlockInfo> blocksFound, ComparablePosition pos) {
        CachedBlockInfo worldState = new CachedBlockInfo(MC.world, new BlockPos(pos.x(), pos.y(), pos.z()), true);
        Material material = worldState.getBlockState().getMaterial();

        if (!material.blocksMovement()) {
            if (!visited.contains(pos) && !toVisit.contains(pos)) {
                toVisit.add(pos);
            }

            if (material != Material.AIR && material != Material.WATER) {
                blocksFound.add(worldState);
            }
        } else {
            blocksFound.add(worldState);
        }
    }
}
