package com.britakeestudio.betterplots.plot;

import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/**
 * Represents a claimed Plot.
 */
public class Plot {
    private final PlotId id;
    private final PlotArea area;
    private UUID owner;
    private final Set<UUID> trusted = new HashSet<>();

    public Plot(PlotId id, PlotArea area, UUID owner) {
        this.id = id;
        this.area = area;
        this.owner = owner;
    }

    public PlotId getId() {
        return id;
    }

    public PlotArea getArea() {
        return area;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }
    
    public boolean hasOwner() {
        return this.owner != null;
    }
    
    public Set<UUID> getTrusted() {
        return trusted;
    }
    
    public void addTrusted(UUID uuid) {
        this.trusted.add(uuid);
    }
    
    public void removeTrusted(UUID uuid) {
        this.trusted.remove(uuid);
    }
    
    public boolean isTrusted(UUID uuid) {
        return this.trusted.contains(uuid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Plot plot)) return false;
        return id.equals(plot.id) && area.equals(plot.area);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, area);
    }
}
