package com.britakeestudio.betterplots.generator;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;
/**
 * PlotChunkGenerator — generates the classic plot-world grid.
 *
 * <p>Layout (all determined by (x, z) math, no noise):
 * <ul>
 *   <li>Y = minBuildHeight (-64): bedrock floor</li>
 *   <li>Y = SURFACE_Y (64): surface layer — road / plot-wall / plot-floor</li>
 *   <li>Everything else: air</li>
 * </ul>
 *
 * <p>Grid pattern (repeats every PERIOD = PLOT_SIZE + ROAD_WIDTH blocks):
 * <ul>
 *   <li>Road (first ROAD_WIDTH columns of each period): gray concrete</li>
 *   <li>Plot wall (outermost ring of the plot cell): stone bricks</li>
 *   <li>Plot interior: grass block</li>
 * </ul>
 *
 * <p>Codec registered in {@link com.britakeestudio.betterplots.BetterPlots} via
 * DeferredRegister&lt;MapCodec&lt;? extends ChunkGenerator&gt;&gt;.
 * The dimension JSON (data/betterplots/dimension/plots_N.json) references
 * "type": "betterplots:plot_generator".
 *
 * @see com.britakeestudio.betterplots.BetterPlots#PLOT_GENERATOR
 */
public class PlotChunkGenerator extends ChunkGenerator {

    // ── Grid Settings ───────────────────────────────────────────────────────
    /** Width/depth of each plot cell in blocks (excluding walls). */
    private final int plotSize;
    /** Width of roads that separate each plot cell. */
    private final int roadWidth;
    /** The full repeating period of the grid (plot + road). */
    private final int period;
    /** Y level of the visible surface layer. */
    private static final int SURFACE_Y = 64;

    // ── Codec ───────────────────────────────────────────────────────────────
    /**
     * MapCodec for this generator. The dimension JSON must supply a biome_source,
     * e.g.:
     * <pre>
     * {
     *   "type": "betterplots:plot_generator",
     *   "biome_source": { "type": "minecraft:fixed", "biome": "minecraft:plains" }
     * }
     * </pre>
     */
    public static final MapCodec<PlotChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(gen -> gen.biomeSource),
                    com.mojang.serialization.Codec.INT.fieldOf("plot_size").forGetter(gen -> gen.plotSize),
                    com.mojang.serialization.Codec.INT.fieldOf("road_width").forGetter(gen -> gen.roadWidth)
            ).apply(instance, PlotChunkGenerator::new)
    );

    // ── Constructor ─────────────────────────────────────────────────────────
    public PlotChunkGenerator(BiomeSource biomeSource, int plotSize, int roadWidth) {
        super(biomeSource);
        this.plotSize = plotSize;
        this.roadWidth = roadWidth;
        this.period = plotSize + roadWidth;
    }

    // ── Getters ─────────────────────────────────────────────────────────────
    public int getPlotSize() {
        return plotSize;
    }

    public int getRoadWidth() {
        return roadWidth;
    }

    // ── ChunkGenerator API ──────────────────────────────────────────────────

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /**
     * Main terrain generation: places bedrock floor + the surface grid.
     * All work is done synchronously; returns a completed future.
     */
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunk) {

        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        int minY   = chunk.getMinBuildHeight();

        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = startX + lx;
                int wz = startZ + lz;

                // Bedrock floor
                chunk.setBlockState(new BlockPos(wx, minY, wz), bedrock, false);

                // Dirt fill
                for (int y = minY + 1; y < SURFACE_Y; y++) {
                    chunk.setBlockState(new BlockPos(wx, y, wz), dirt, false);
                }

                // Surface layer
                BlockState surface = getSurfaceBlock(wx, wz);
                chunk.setBlockState(new BlockPos(wx, SURFACE_Y, wz), surface, false);
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * Returns the block that should appear at the surface Y for world position (x, z).
     *
     * <p>The grid repeats with period {@code ROAD_WIDTH + PLOT_SIZE}:
     * <ul>
     *   <li>localX or localZ &lt; ROAD_WIDTH → road (gray concrete)</li>
     *   <li>outermost ring of remaining area → wall (stone bricks)</li>
     *   <li>inner area → plot interior (grass block)</li>
     * </ul>
     */
    private BlockState getSurfaceBlock(int x, int z) {
        int localX = Math.floorMod(x, period);
        int localZ = Math.floorMod(z, period);

        // Road bands
        if (localX < roadWidth || localZ < roadWidth) {
            return Blocks.GRAY_CONCRETE.defaultBlockState();
        }

        // Remaining area — offset into plot space
        int px = localX - roadWidth; // 0 .. plotSize-1
        int pz = localZ - roadWidth; // 0 .. plotSize-1

        // Wall: outermost ring of the plot cell
        boolean isWall = px == 0 || px == plotSize - 1
                      || pz == 0 || pz == plotSize - 1;
        if (isWall) {
            return Blocks.STONE_BRICKS.defaultBlockState();
        }

        // Interior
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }

    // ── No-op / stub overrides ──────────────────────────────────────────────

    @Override
    public void applyCarvers(
            WorldGenRegion region,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            GenerationStep.Carving step) {
        // No carvers — plot world is fully flat
    }

    @Override
    public void buildSurface(
            WorldGenRegion region,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess chunk) {
        // Surface is handled entirely in fillFromNoise
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // No mob spawning on initial generation
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return -63; // Effectively no sea level; -63 is vanilla overworld default
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(
            int x,
            int z,
            Heightmap.Types heightmapType,
            LevelHeightAccessor levelHeightAccessor,
            RandomState randomState) {
        return SURFACE_Y;
    }

    @Override
    public NoiseColumn getBaseColumn(
            int x,
            int z,
            LevelHeightAccessor levelHeightAccessor,
            RandomState randomState) {
        return new NoiseColumn(0, new BlockState[0]);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        int localX = Math.floorMod(pos.getX(), period);
        int localZ = Math.floorMod(pos.getZ(), period);
        int plotIdX = Math.floorDiv(pos.getX() - roadWidth, period);
        int plotIdZ = Math.floorDiv(pos.getZ() - roadWidth, period);
        info.add("BetterPlots PlotChunkGenerator | plotSize=" + plotSize + " roadWidth=" + roadWidth);
        info.add("Plot cell approx: [" + plotIdX + "," + plotIdZ + "] | localX=" + localX + " localZ=" + localZ);
    }

    // getNoiseBiome: ChunkGenerator already provides a default implementation that
    // delegates to this.biomeSource.getNoiseBiome — no override needed.

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        // No-op to prevent vanilla features (trees, grass, ores, etc.) from generating on the plots
    }
}
