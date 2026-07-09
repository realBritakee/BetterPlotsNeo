package com.britakeestudio.betterplots.generator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles dynamically generating a datapack to register new Plot dimensions.
 */
public class DimensionGenerator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void generateDimensionDatapack(MinecraftServer server, String dimensionName, int plotSize, int roadWidth) throws IOException {
        Path datapackDir = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("betterplots_setup");
        
        // Ensure directories exist
        Path dimensionDir = datapackDir.resolve("data").resolve("betterplots").resolve("dimension");
        Files.createDirectories(dimensionDir);
        
        // Write pack.mcmeta
        Path mcmeta = datapackDir.resolve("pack.mcmeta");
        if (!Files.exists(mcmeta)) {
            JsonObject pack = new JsonObject();
            JsonObject packData = new JsonObject();
            packData.addProperty("pack_format", 48); // 1.21.1 pack format
            packData.addProperty("description", "Dynamically generated BetterPlots dimensions.");
            pack.add("pack", packData);
            Files.writeString(mcmeta, GSON.toJson(pack));
        }

        // Write dimension JSON
        JsonObject dimensionJson = new JsonObject();
        dimensionJson.addProperty("type", "betterplots:plot_dimension_type");

        JsonObject generatorJson = new JsonObject();
        generatorJson.addProperty("type", "betterplots:plot_generator");
        generatorJson.addProperty("plot_size", plotSize);
        generatorJson.addProperty("road_width", roadWidth);

        JsonObject biomeSourceJson = new JsonObject();
        biomeSourceJson.addProperty("type", "minecraft:fixed");
        biomeSourceJson.addProperty("biome", "minecraft:plains");

        generatorJson.add("biome_source", biomeSourceJson);
        dimensionJson.add("generator", generatorJson);

        Path dimensionFile = dimensionDir.resolve(dimensionName + ".json");
        Files.writeString(dimensionFile, GSON.toJson(dimensionJson));
    }
}
