package com.britakeestudio.betterplots.plot;

import java.util.Objects;

/**
 * Represents a plot's unique grid coordinates (X, Z).
 */
public final class PlotId {
    private final int x;
    private final int z;

    private PlotId(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public static PlotId of(int x, int z) {
        return new PlotId(x, z);
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlotId plotId)) return false;
        return x == plotId.x && z == plotId.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return x + ";" + z;
    }
    
    public static PlotId fromString(String str) {
        String[] parts = str.split(";");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid PlotId format: " + str);
        return of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
}
