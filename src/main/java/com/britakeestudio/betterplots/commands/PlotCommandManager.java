package com.britakeestudio.betterplots.commands;

import com.britakeestudio.betterplots.database.PlotDao;
import com.britakeestudio.betterplots.plot.Plot;
import com.britakeestudio.betterplots.plot.PlotArea;
import com.britakeestudio.betterplots.plot.PlotId;
import com.britakeestudio.betterplots.plot.PlotManager;
import com.britakeestudio.betterplots.generator.DimensionGenerator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import net.minecraft.world.level.chunk.ChunkGenerator;

import java.util.List;

public class PlotCommandManager {

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Base command builder
        LiteralArgumentBuilder<CommandSourceStack> basePlot = Commands.literal("plot")
                .then(Commands.literal("claim").executes(PlotCommandManager::claimPlot))
                .then(Commands.literal("auto").executes(PlotCommandManager::autoClaim))
                .then(Commands.literal("area")
                        .then(Commands.literal("create").requires(src -> src.hasPermission(2)).executes(PlotCommandManager::createArea)))
                .then(Commands.literal("setup").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("plotSize", IntegerArgumentType.integer(1, 256))
                                        .then(Commands.argument("roadWidth", IntegerArgumentType.integer(1, 256))
                                                .executes(PlotCommandManager::setupDimension)))));

        // Register main command
        dispatcher.register(basePlot);

        // Register aliases
        dispatcher.register(Commands.literal("p").redirect(dispatcher.getRoot().getChild("plot")));
        dispatcher.register(Commands.literal("plots").redirect(dispatcher.getRoot().getChild("plot")));
        dispatcher.register(Commands.literal("2").redirect(dispatcher.getRoot().getChild("plot")));
    }

    private static int createArea(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();

        String areaName = level.dimension().location().getPath();
        
        int plotSize = 64;
        int roadWidth = 7;
        
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (generator instanceof com.britakeestudio.betterplots.generator.PlotChunkGenerator plotGen) {
            plotSize = plotGen.getPlotSize();
            roadWidth = plotGen.getRoadWidth();
        }
        
        PlotArea area = new PlotArea(areaName, level.dimension(), plotSize, roadWidth);
        PlotDao.savePlotArea(area);

        source.sendSuccess(() -> Component.literal("§aRegistered current dimension as PlotArea: " + areaName), false);
        return 1;
    }

    private static int setupDimension(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        
        if (!name.equals(name.toLowerCase())) {
            source.sendFailure(Component.literal("§cDimension name must be all lowercase! (e.g. 'test_world')"));
            return 0;
        }
        
        int plotSize = IntegerArgumentType.getInteger(ctx, "plotSize");
        int roadWidth = IntegerArgumentType.getInteger(ctx, "roadWidth");

        try {
            DimensionGenerator.generateDimensionDatapack(source.getServer(), name, plotSize, roadWidth);
            source.sendSuccess(() -> Component.literal("§aSuccessfully generated datapack for dimension: §f" + name), false);
            source.sendSuccess(() -> Component.literal("§e§lPLEASE RESTART THE SERVER§r§e to load the new dimension."), false);
            source.sendSuccess(() -> Component.literal("§7After restarting, teleport with: §f/execute in betterplots:" + name + " run tp @s 0 65 0"), false);
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cFailed to generate dimension: " + e.getMessage()));
            e.printStackTrace();
        }

        return 1;
    }

    private static int claimPlot(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!source.isPlayer()) {
            source.sendFailure(Component.literal("§cOnly players can claim plots."));
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        BlockPos pos = player.blockPosition();

        List<PlotArea> areas = PlotDao.loadAllPlotAreas();
        PlotArea currentArea = areas.stream()
                .filter(a -> a.getDimension().equals(player.serverLevel().dimension()))
                .findFirst()
                .orElse(null);

        if (currentArea == null) {
            source.sendFailure(Component.literal("§cYou are not in a registered PlotArea."));
            return 0;
        }

        PlotId id = PlotManager.getPlotId(currentArea, pos);
        if (id == null) {
            source.sendFailure(Component.literal("§cYou are standing on a road or wall. Stand inside a plot to claim it."));
            return 0;
        }

        Plot existing = PlotDao.loadPlot(currentArea, id);
        if (existing != null && existing.hasOwner()) {
            source.sendFailure(Component.literal("§cThis plot is already claimed by someone else."));
            return 0;
        }

        Plot newPlot = new Plot(id, currentArea, player.getUUID());
        PlotDao.savePlot(newPlot);
        
        BlockPos tpPos = PlotManager.getTeleportPos(currentArea, id);
        player.teleportTo(player.serverLevel(), tpPos.getX(), tpPos.getY(), tpPos.getZ(), player.getYRot(), player.getXRot());

        source.sendSuccess(() -> Component.literal("§aSuccessfully claimed plot " + id + "!"), false);
        return 1;
    }

    private static int autoClaim(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!source.isPlayer()) {
            source.sendFailure(Component.literal("§cOnly players can claim plots."));
            return 0;
        }

        ServerPlayer player = source.getPlayer();

        List<PlotArea> areas = PlotDao.loadAllPlotAreas();
        PlotArea currentArea = areas.stream()
                .filter(a -> a.getDimension().equals(player.serverLevel().dimension()))
                .findFirst()
                .orElse(null);

        if (currentArea == null) {
            source.sendFailure(Component.literal("§cYou are not in a registered PlotArea. Use /plot area create first."));
            return 0;
        }

        // Extremely simple spiral search for an empty plot
        int radius = 0;
        while (radius < 100) { // Limit search radius
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) == radius || Math.abs(z) == radius) {
                        PlotId id = PlotId.of(x, z);
                        Plot existing = PlotDao.loadPlot(currentArea, id);
                        if (existing == null || !existing.hasOwner()) {
                            // Found empty plot
                            Plot newPlot = new Plot(id, currentArea, player.getUUID());
                            PlotDao.savePlot(newPlot);
                            
                            BlockPos tpPos = PlotManager.getTeleportPos(currentArea, id);
                            player.teleportTo(player.serverLevel(), tpPos.getX(), tpPos.getY(), tpPos.getZ(), player.getYRot(), player.getXRot());

                            source.sendSuccess(() -> Component.literal("§aFound and claimed plot " + id + " for you!"), false);
                            return 1;
                        }
                    }
                }
            }
            radius++;
        }

        source.sendFailure(Component.literal("§cCould not find an empty plot nearby."));
        return 0;
    }
}
