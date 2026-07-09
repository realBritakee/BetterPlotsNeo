package com.britakeestudio.betterplots.plot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;

/**
 * Handles grid math for converting block positions to PlotIds and vice-versa.
 */
public class PlotManager {

    /**
     * Determines which PlotId contains the given block coordinate in the specified PlotArea.
     * Returns null if the position falls on a road or wall instead of inside a plot cell.
     */
    public static PlotId getPlotId(PlotArea area, BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        int period = area.getPeriod();
        
        // Find which grid cell we are in
        int cellX = Math.floorDiv(x, period);
        int cellZ = Math.floorDiv(z, period);
        
        // Find local coordinates within the cell
        int localX = Math.floorMod(x, period);
        int localZ = Math.floorMod(z, period);
        
        // If we are on the road, it's not inside a plot
        if (localX < area.getRoadWidth() || localZ < area.getRoadWidth()) {
            return null;
        }
        
        // Offset for plot interior
        int px = localX - area.getRoadWidth();
        int pz = localZ - area.getRoadWidth();
        
        // If we are on the wall (outermost ring), it's not inside the buildable plot area
        if (px == 0 || px == area.getPlotSize() - 1 || pz == 0 || pz == area.getPlotSize() - 1) {
            return null; // Note: PlotSquared sometimes includes the wall in the plot, but we keep it simple for now
        }
        
        return PlotId.of(cellX, cellZ);
    }
    
    /**
     * Returns the bottom-center block position of the plot for teleporting.
     */
    public static BlockPos getTeleportPos(PlotArea area, PlotId id) {
        int period = area.getPeriod();
        int baseX = id.getX() * period + area.getRoadWidth();
        int baseZ = id.getZ() * period + area.getRoadWidth();
        
        // Center of the plot
        int centerOffset = area.getPlotSize() / 2;
        return new BlockPos(baseX + centerOffset, 65, baseZ + centerOffset);
    }
    
    /**
     * Resets the plot back to its default state.
     * WARNING: Runs synchronously on the main thread.
     */
    public static void clearPlot(ServerLevel level, PlotArea area, PlotId id) {
        int period = area.getPeriod();
        int minX = id.getX() * period + area.getRoadWidth() + 1;
        int minZ = id.getZ() * period + area.getRoadWidth() + 1;
        int maxX = id.getX() * period + area.getRoadWidth() + area.getPlotSize() - 2;
        int maxZ = id.getZ() * period + area.getRoadWidth() + area.getPlotSize() - 2;
        
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Clear above ground
                for (int y = level.getMaxBuildHeight() - 1; y >= 65; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, 2); // 2 = send to client, no neighbor updates
                    }
                }
                
                // Surface
                BlockPos surfacePos = new BlockPos(x, 64, z);
                if (!level.getBlockState(surfacePos).is(Blocks.GRASS_BLOCK)) {
                    level.setBlock(surfacePos, grass, 2);
                }
                
                // Underground
                for (int y = 63; y > level.getMinBuildHeight(); y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).is(Blocks.DIRT)) {
                        level.setBlock(pos, dirt, 2);
                    }
                }
            }
        }
    }
}
