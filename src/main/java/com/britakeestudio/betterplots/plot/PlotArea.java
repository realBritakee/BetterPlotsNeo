package com.britakeestudio.betterplots.plot;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * Represents a registered plot world/area.
 * For now, this is 1:1 with a dimension, but in the future multiple areas could
 * share a dimension via grid offsets.
 */
public class PlotArea {
    private final String name;
    private final ResourceKey<Level> dimension;
    private final int plotSize;
    private final int roadWidth;

    public PlotArea(String name, ResourceKey<Level> dimension, int plotSize, int roadWidth) {
        this.name = name;
        this.dimension = dimension;
        this.plotSize = plotSize;
        this.roadWidth = roadWidth;
    }

    public String getName() {
        return name;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public int getPlotSize() {
        return plotSize;
    }

    public int getRoadWidth() {
        return roadWidth;
    }
    
    public int getPeriod() {
        return plotSize + roadWidth;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlotArea plotArea)) return false;
        return name.equals(plotArea.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
