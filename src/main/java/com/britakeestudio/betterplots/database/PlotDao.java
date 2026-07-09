package com.britakeestudio.betterplots.database;

import com.britakeestudio.betterplots.plot.Plot;
import com.britakeestudio.betterplots.plot.PlotArea;
import com.britakeestudio.betterplots.plot.PlotId;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Access Object for Plots and PlotAreas.
 */
public class PlotDao {
    
    // In-memory cache to map DB IDs to PlotAreas
    private static final Map<Integer, PlotArea> areaCache = new HashMap<>();

    public static void savePlotArea(PlotArea area) {
        String sql = "INSERT OR IGNORE INTO plot_areas (name, dimension, plot_size, road_width) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, area.getName());
            stmt.setString(2, area.getDimension().location().toString());
            stmt.setInt(3, area.getPlotSize());
            stmt.setInt(4, area.getRoadWidth());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<PlotArea> loadAllPlotAreas() {
        List<PlotArea> areas = new ArrayList<>();
        String sql = "SELECT * FROM plot_areas";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
             
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String dimString = rs.getString("dimension");
                int plotSize = rs.getInt("plot_size");
                int roadWidth = rs.getInt("road_width");
                
                ResourceKey<net.minecraft.world.level.Level> dim = ResourceKey.create(
                        Registries.DIMENSION, ResourceLocation.parse(dimString));
                        
                PlotArea area = new PlotArea(name, dim, plotSize, roadWidth);
                areas.add(area);
                areaCache.put(id, area);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return areas;
    }

    public static void savePlot(Plot plot) {
        Integer areaId = getAreaId(plot.getArea());
        if (areaId == null) return;

        String sql = "INSERT INTO plots (area_id, plot_x, plot_z, owner_uuid) VALUES (?, ?, ?, ?) " +
                     "ON CONFLICT(area_id, plot_x, plot_z) DO UPDATE SET owner_uuid=excluded.owner_uuid";
                     
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, areaId);
            stmt.setInt(2, plot.getId().getX());
            stmt.setInt(3, plot.getId().getZ());
            stmt.setString(4, plot.getOwner().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static Plot loadPlot(PlotArea area, PlotId id) {
        Integer areaId = getAreaId(area);
        if (areaId == null) return null;

        String sql = "SELECT id, owner_uuid FROM plots WHERE area_id = ? AND plot_x = ? AND plot_z = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, areaId);
            stmt.setInt(2, id.getX());
            stmt.setInt(3, id.getZ());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int dbId = rs.getInt("id");
                    UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                    Plot plot = new Plot(id, area, owner);
                    
                    // Load trusted
                    String trustSql = "SELECT trusted_uuid FROM plot_trusted WHERE plot_id = ?";
                    try (PreparedStatement trustStmt = conn.prepareStatement(trustSql)) {
                        trustStmt.setInt(1, dbId);
                        try (ResultSet trustRs = trustStmt.executeQuery()) {
                            while (trustRs.next()) {
                                plot.addTrusted(UUID.fromString(trustRs.getString("trusted_uuid")));
                            }
                        }
                    }
                    
                    return plot;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void addTrusted(Plot plot, UUID trusted) {
        Integer dbId = getPlotDbId(plot);
        if (dbId == null) return;
        
        String sql = "INSERT OR IGNORE INTO plot_trusted (plot_id, trusted_uuid) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, dbId);
            stmt.setString(2, trusted.toString());
            stmt.executeUpdate();
            plot.addTrusted(trusted);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void removeTrusted(Plot plot, UUID trusted) {
        Integer dbId = getPlotDbId(plot);
        if (dbId == null) return;
        
        String sql = "DELETE FROM plot_trusted WHERE plot_id = ? AND trusted_uuid = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, dbId);
            stmt.setString(2, trusted.toString());
            stmt.executeUpdate();
            plot.removeTrusted(trusted);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void deletePlot(Plot plot) {
        Integer dbId = getPlotDbId(plot);
        if (dbId == null) return;
        
        // This will cascade delete plot_trusted due to ON DELETE CASCADE
        String sql = "DELETE FROM plots WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, dbId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<Plot> getPlotsByOwner(UUID owner) {
        List<Plot> ownedPlots = new ArrayList<>();
        List<PlotArea> areas = loadAllPlotAreas();
        String sql = "SELECT area_id, plot_x, plot_z FROM plots WHERE owner_uuid = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, owner.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int areaId = rs.getInt("area_id");
                    PlotArea area = areas.stream().filter(a -> {
                        Integer id = getAreaId(a);
                        return id != null && id == areaId;
                    }).findFirst().orElse(null);
                    
                    if (area != null) {
                        PlotId id = PlotId.of(rs.getInt("plot_x"), rs.getInt("plot_z"));
                        Plot p = loadPlot(area, id);
                        if (p != null) {
                            ownedPlots.add(p);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ownedPlots;
    }

    private static Integer getPlotDbId(Plot plot) {
        Integer areaId = getAreaId(plot.getArea());
        if (areaId == null) return null;
        
        String sql = "SELECT id FROM plots WHERE area_id = ? AND plot_x = ? AND plot_z = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, areaId);
            stmt.setInt(2, plot.getId().getX());
            stmt.setInt(3, plot.getId().getZ());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Integer getAreaId(PlotArea area) {
        for (Map.Entry<Integer, PlotArea> entry : areaCache.entrySet()) {
            if (entry.getValue().equals(area)) {
                return entry.getKey();
            }
        }
        // If not cached, try to fetch it
        String sql = "SELECT id FROM plot_areas WHERE name = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, area.getName());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    areaCache.put(id, area);
                    return id;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
