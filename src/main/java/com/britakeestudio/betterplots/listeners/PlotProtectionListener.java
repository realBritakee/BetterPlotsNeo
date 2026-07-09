package com.britakeestudio.betterplots.listeners;

import com.britakeestudio.betterplots.database.PlotDao;
import com.britakeestudio.betterplots.plot.Plot;
import com.britakeestudio.betterplots.plot.PlotArea;
import com.britakeestudio.betterplots.plot.PlotId;
import com.britakeestudio.betterplots.plot.PlotManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode.PermissionResolver;
import net.minecraft.resources.ResourceLocation;

public class PlotProtectionListener {

    public static final PermissionNode<Boolean> BUILD_ROAD = new PermissionNode<>(
            ResourceLocation.fromNamespaceAndPath("betterplots", "admin.build.road"),
            PermissionTypes.BOOLEAN,
            (player, playerUUID, contexts) -> false
    );

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!handleBlockAction(player, event.getPos(), (Level) event.getLevel())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!handleBlockAction(player, event.getPos(), (Level) event.getLevel())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!handleBlockAction(player, event.getPos(), event.getLevel())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!handleBlockAction(player, event.getPos(), event.getLevel())) {
            event.setCanceled(true);
        }
    }

    /**
     * @return true if the action is ALLOWED, false if it should be CANCELED
     */
    private boolean handleBlockAction(ServerPlayer player, BlockPos pos, Level level) {
        // If not in a plot world, allow
        String dimName = level.dimension().location().toString();
        
        PlotArea area = null;
        for (PlotArea p : PlotDao.loadAllPlotAreas()) {
            if (p.getDimension().location().toString().equals(dimName)) {
                area = p;
                break;
            }
        }
        
        if (area == null) {
            return true;
        }

        PlotId id = PlotManager.getPlotId(area, pos);
        if (id == null) {
            // It's on a road
            return PermissionAPI.getPermission(player, BUILD_ROAD);
        }

        Plot plot = PlotDao.loadPlot(area, id);
        if (plot == null) {
            // Unclaimed plot
            return false;
        }

        // Check if owner or trusted
        if (plot.getOwner().equals(player.getUUID())) {
            return true;
        }
        if (plot.isTrusted(player.getUUID())) {
            return true;
        }

        // Not owner, not trusted
        return false;
    }
}
