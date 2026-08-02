package com.ghostslotecho.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class GhostSlotConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("ghostslotecho.json").toFile();

    public int echoDurationSeconds = 20;
    public float ghostOpacity = 0.25f;
    public boolean persistOnClose = true;
    public boolean enableSmartQuickMove = true;
    public boolean enablePinning = true;

    private static GhostSlotConfig INSTANCE = new GhostSlotConfig();

    public static GhostSlotConfig get() {
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, GhostSlotConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new GhostSlotConfig();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
