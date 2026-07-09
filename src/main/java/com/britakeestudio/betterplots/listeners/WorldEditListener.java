package com.britakeestudio.betterplots.listeners;

import com.britakeestudio.betterplots.database.PlotDao;
import com.britakeestudio.betterplots.plot.Plot;
import com.britakeestudio.betterplots.plot.PlotArea;
import com.britakeestudio.betterplots.plot.PlotId;
import com.britakeestudio.betterplots.plot.PlotManager;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.EventHandler.Priority;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import net.minecraft.core.BlockPos;

import java.util.UUID;

public class WorldEditListener {

    @Subscribe(priority = Priority.VERY_EARLY)
    public void onEditSession(EditSessionEvent event) {
        if (event.getWorld() == null) return;
        String worldName = event.getWorld().getName();
        
        PlotArea area = null;
        for (PlotArea p : PlotDao.loadAllPlotAreas()) {
            if (p.getDimension().location().toString().equals(worldName)) {
                area = p;
                break;
            }
        }
        
        if (area == null) return; // Not a plot world
        
        Actor actor = event.getActor();
        if (actor == null || !actor.isPlayer()) return;
        
        Player player = (Player) actor;
        UUID uuid = player.getUniqueId();
        
        event.setExtent(new PlotExtent(event.getExtent(), area, uuid));
    }

    private static class PlotExtent extends AbstractDelegateExtent {
        private final PlotArea area;
        private final UUID uuid;

        protected PlotExtent(Extent extent, PlotArea area, UUID uuid) {
            super(extent);
            this.area = area;
            this.uuid = uuid;
        }

        @Override
        public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 location, T block) throws WorldEditException {
            BlockPos pos = new BlockPos(location.getX(), location.getY(), location.getZ());
            PlotId id = PlotManager.getPlotId(area, pos);
            
            if (id == null) {
                // It's a road
                return false;
            }
            
            Plot plot = PlotDao.loadPlot(area, id);
            if (plot == null) {
                // Unclaimed
                return false;
            }
            
            if (plot.getOwner().equals(uuid) || plot.isTrusted(uuid)) {
                return super.setBlock(location, block);
            }
            
            // Not owner/trusted
            return false;
        }
    }
}
