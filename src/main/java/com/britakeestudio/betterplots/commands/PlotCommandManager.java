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

import net.minecraft.commands.arguments.GameProfileArgument;
import com.mojang.authlib.GameProfile;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class PlotCommandManager {

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Base command builder
        LiteralArgumentBuilder<CommandSourceStack> basePlot = Commands.literal("plot")
                .then(Commands.literal("claim").executes(PlotCommandManager::claimPlot))
                .then(Commands.literal("auto").executes(PlotCommandManager::autoClaim))
                .then(Commands.literal("info").executes(PlotCommandManager::plotInfo))
                .then(Commands.literal("home").executes(PlotCommandManager::plotHome))
                .then(Commands.literal("visit")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(PlotCommandManager::visitPlot)))
                .then(Commands.literal("clear").executes(PlotCommandManager::clearPlot))
                .then(Commands.literal("delete").executes(PlotCommandManager::deletePlot))
                .then(Commands.literal("trust")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(PlotCommandManager::trustPlayer)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(PlotCommandManager::removeTrusted)))
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

    private static int trustPlayer(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!source.isPlayer()) return 0;
        ServerPlayer player = source.getPlayer();
        
        Plot plot = getCurrentPlot(player);
        if (plot == null) {
            source.sendFailure(Component.literal("§cYou are not standing in a claimed plot."));
            return 0;
        }
        
        if (!plot.getOwner().equals(player.getUUID())) {
            source.sendFailure(Component.literal("§cOnly the plot owner can trust players."));
            return 0;
        }
        
        try {
            Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
            if (profiles.isEmpty()) {
                source.sendFailure(Component.literal("§cPlayer not found."));
                return 0;
            }
            GameProfile target = profiles.iterator().next();
            
            PlotDao.addTrusted(plot, target.getId());
            source.sendSuccess(() -> Component.literal("§aAdded " + target.getName() + " as a trusted member."), false);
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cFailed to trust player."));
        }
        return 1;
    }

    private static int removeTrusted(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!source.isPlayer()) return 0;
        ServerPlayer player = source.getPlayer();
        
        Plot plot = getCurrentPlot(player);
        if (plot == null) {
            source.sendFailure(Component.literal("§cYou are not standing in a claimed plot."));
            return 0;
        }
        
        if (!plot.getOwner().equals(player.getUUID())) {
            source.sendFailure(Component.literal("§cOnly the plot owner can remove trusted players."));
            return 0;
        }
        
        try {
            Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
            if (profiles.isEmpty()) {
                source.sendFailure(Component.literal("§cPlayer not found."));
                return 0;
            }
            GameProfile target = profiles.iterator().next();
            
            PlotDao.removeTrusted(plot, target.getId());
            source.sendSuccess(() -> Component.literal("§aRemoved " + target.getName() + " from trusted members."), false);
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cFailed to remove trusted player."));
        }
        return 1;
    }

    private static int plotInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!source.isPlayer()) return 0;
        ServerPlayer player = source.getPlayer();
        
        Plot plot = getCurrentPlot(player);
        if (plot == null) {
            source.sendFailure(Component.literal("§cYou are not standing in a claimed plot."));
            return 0;
        }
        
        source.sendSuccess(() -> Component.literal("§b=== Plot Info ==="), false);
        source.sendSuccess(() -> Component.literal("§7ID: §f" + plot.getId()), false);
        
        // Very basic resolution (UUID might not resolve instantly in singleplayer for offline players)
        // For production, we'd use a Username cache
        source.sendSuccess(() -> Component.literal("§7Owner: §f" + plot.getOwner().toString().substring(0, 8)), false);
        
        if (!plot.getTrusted().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Trusted Members: §f" + plot.getTrusted().size()), false);
        }
        
        return 1;
    }

    private static Plot getCurrentPlot(ServerPlayer player) {
        List<PlotArea> areas = PlotDao.loadAllPlotAreas();
        PlotArea currentArea = areas.stream()
                .filter(a -> a.getDimension().equals(player.serverLevel().dimension()))
                .findFirst()
                .orElse(null);
        if (currentArea == null) return null;
        
        PlotId id = PlotManager.getPlotId(currentArea, player.blockPosition());
        if (id == null) return null;
        
        return PlotDao.loadPlot(currentArea, id);
    }

    private static int plotHome(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!source.isPlayer()) return 0;
        ServerPlayer player = source.getPlayer();
        
        List<Plot> plots = PlotDao.getPlotsByOwner(player.getUUID());
        if (plots.isEmpty()) {
            source.sendFailure(Component.literal("§cYou don't own any plots."));
            return 0;
        }
        
        Plot plot = plots.get(0);
        BlockPos pos = PlotManager.getTeleportPos(plot.getArea(), plot.getId());
        
        player.teleportTo(player.serverLevel().getServer().getLevel(plot.getArea().getDimension()), pos.getX(), pos.getY(), pos.getZ(), player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal("§aTeleported to your plot."), false);
        return 1;
    }

    private static int visitPlot(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!source.isPlayer()) return 0;
        ServerPlayer player = source.getPlayer();
        
        try {
            Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
            if (profiles.isEmpty()) {
                source.sendFailure(Component.literal("§cPlayer not found."));
                return 0;
            }
            GameProfile target = profiles.iterator().next();
            List<Plot> plots = PlotDao.getPlotsByOwner(target.getId());
            if (plots.isEmpty()) {
                source.sendFailure(Component.literal("§cThat player doesn't own any plots."));
                return 0;
            }
            
            Plot plot = plots.get(0);
            BlockPos pos = PlotManager.getTeleportPos(plot.getArea(), plot.getId());
            player.teleportTo(player.serverLevel().getServer().getLevel(plot.getArea().getDimension()), pos.getX(), pos.getY(), pos.getZ(), player.getYRot(), player.getXRot());
            source.sendSuccess(() -> Component.literal("§aTeleported to " + target.getName() + "'s plot."), false);
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cFailed to visit plot."));
        }
        return 1;
    }

    private static int clearPlot(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!source.isPlayer()) return 0;
        ServerPlayer player = source.getPlayer();
        
        Plot plot = getCurrentPlot(player);
        if (plot == null) {
            source.sendFailure(Component.literal("§cYou are not standing in a claimed plot."));
            return 0;
        }
        if (!plot.getOwner().equals(player.getUUID())) {
            source.sendFailure(Component.literal("§cOnly the plot owner can clear the plot."));
            return 0;
        }
        
        PlotManager.clearPlot(player.serverLevel(), plot.getArea(), plot.getId());
        source.sendSuccess(() -> Component.literal("§aPlot cleared."), false);
        return 1;
    }

    private static int deletePlot(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!source.isPlayer()) return 0;
        ServerPlayer player = source.getPlayer();
        
        Plot plot = getCurrentPlot(player);
        if (plot == null) {
            source.sendFailure(Component.literal("§cYou are not standing in a claimed plot."));
            return 0;
        }
        if (!plot.getOwner().equals(player.getUUID())) {
            source.sendFailure(Component.literal("§cOnly the plot owner can delete the plot."));
            return 0;
        }
        
        PlotManager.clearPlot(player.serverLevel(), plot.getArea(), plot.getId());
        PlotDao.deletePlot(plot);
        source.sendSuccess(() -> Component.literal("§aPlot completely deleted and unclaimed."), false);
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
