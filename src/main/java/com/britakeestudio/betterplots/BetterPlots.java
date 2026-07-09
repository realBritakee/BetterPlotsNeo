package com.britakeestudio.betterplots;

import com.britakeestudio.betterplots.commands.PlotCommandManager;
import com.britakeestudio.betterplots.database.DatabaseManager;
import com.britakeestudio.betterplots.database.PlotDao;
import com.britakeestudio.betterplots.generator.PlotChunkGenerator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BetterPlotsNeo — main mod entrypoint.
 *
 * <p>Milestone 1: registers the PlotChunkGenerator codec so the dimension
 * JSON (data/betterplots/dimension/plots_N.json) can reference it as
 * "type": "betterplots:plot_generator".
 *
 * <p>Future milestones will wire:
 * <ul>
 *   <li>PlotArea binding + DB persistence (Milestone 3/4)</li>
 *   <li>Command registration via Cloud (Milestone 6)</li>
 *   <li>Protection event adapters (Milestone 7)</li>
 *   <li>Flags, trust/deny, merge/clear (Milestones 8–10)</li>
 * </ul>
 */
@Mod(BetterPlots.MODID)
public class BetterPlots {

    public static final String MODID = "betterplots";
    public static final Logger LOGGER = LoggerFactory.getLogger(BetterPlots.class);

    // ── Registry: ChunkGenerator codecs ─────────────────────────────────────
    /**
     * DeferredRegister for ChunkGenerator MapCodecs.
     * Our PlotChunkGenerator codec is registered here so NeoForge can
     * deserialize "type": "betterplots:plot_generator" from the dimension JSON.
     */
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATOR_TYPES =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);

    /**
     * The registered codec for {@link PlotChunkGenerator}.
     * Referenced in data/betterplots/dimension/plots_N.json via
     * "generator": { "type": "betterplots:plot_generator", ... }
     */
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<PlotChunkGenerator>>
            PLOT_GENERATOR = CHUNK_GENERATOR_TYPES.register("plot_generator", () -> PlotChunkGenerator.CODEC);

    // ── Constructor (called by NeoForge on mod load) ─────────────────────────
    public BetterPlots(IEventBus modEventBus, ModContainer modContainer) {
        // Register ChunkGenerator codec on the mod bus (registry events fire on mod bus)
        CHUNK_GENERATOR_TYPES.register(modEventBus);

        // Register server lifecycle listeners on the NeoForge bus
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        LOGGER.info("[BetterPlotsNeo] Mod initializing — Milestone 1 (scaffold + dimension pool)");
    }

    private void onServerStarting(ServerStartingEvent event) {
        var server = event.getServer();
        LOGGER.info("[BetterPlotsNeo] Server starting. Registered dimensions:");
        // Log all dimensions so we can confirm betterplots:plots_0 loaded
        server.levelKeys().forEach(key ->
                LOGGER.info("  → {}", key.location())
        );

        // Initialize Database
        DatabaseManager.init(server);
        PlotDao.loadAllPlotAreas();
        LOGGER.info("[BetterPlotsNeo] Database initialized and PlotAreas loaded.");
    }

    private void onServerStopping(ServerStoppingEvent event) {
        DatabaseManager.close();
        LOGGER.info("[BetterPlotsNeo] Database closed.");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        PlotCommandManager.register(event);
    }
}
