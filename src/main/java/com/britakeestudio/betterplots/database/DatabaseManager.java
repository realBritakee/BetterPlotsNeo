package com.britakeestudio.betterplots.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the SQLite database connection pool using HikariCP.
 */
public class DatabaseManager {

    private static HikariDataSource dataSource;

    public static void init(MinecraftServer server) {
        // Save the database inside the world folder so it rolls back with world backups
        Path dbPath = server.getWorldPath(LevelResource.ROOT).resolve("betterplots.db");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath.toAbsolutePath().toString());
        config.setPoolName("BetterPlots-DB");
        config.setMaximumPoolSize(10);
        
        // SQLite-specific optimizations
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");

        dataSource = new HikariDataSource(config);
        
        createTables();
    }

    private static void createTables() {
        String createAreas = """
            CREATE TABLE IF NOT EXISTS plot_areas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                dimension TEXT NOT NULL,
                plot_size INTEGER NOT NULL,
                road_width INTEGER NOT NULL
            );
        """;

        String createPlots = """
            CREATE TABLE IF NOT EXISTS plots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                area_id INTEGER NOT NULL,
                plot_x INTEGER NOT NULL,
                plot_z INTEGER NOT NULL,
                owner_uuid TEXT NOT NULL,
                UNIQUE(area_id, plot_x, plot_z),
                FOREIGN KEY(area_id) REFERENCES plot_areas(id) ON DELETE CASCADE
            );
        """;

        String createTrusted = """
            CREATE TABLE IF NOT EXISTS plot_trusted (
                plot_id INTEGER NOT NULL,
                trusted_uuid TEXT NOT NULL,
                UNIQUE(plot_id, trusted_uuid),
                FOREIGN KEY(plot_id) REFERENCES plots(id) ON DELETE CASCADE
            );
        """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createAreas);
            stmt.execute(createPlots);
            stmt.execute(createTrusted);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize BetterPlots database schema", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("DatabaseManager is not initialized.");
        }
        return dataSource.getConnection();
    }

    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
