package com.chillzone.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class NametagSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("chillzone-combat");
    private static final Path FILE = DIR.resolve("settings.json");
    private static final Path LEGACY_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("chillzone-pvprank-admin").resolve("settings.json");
    private static final Path TEMP = DIR.resolve("settings.json.tmp");

    private static final class Config {
        boolean compactNametags = true;
    }

    private boolean compact = true;

    void load() {
        compact = true;
        Path source = Files.exists(FILE) ? FILE : (Files.exists(LEGACY_FILE) ? LEGACY_FILE : null);
        if (source == null) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(source)) {
            Config config = GSON.fromJson(reader, Config.class);
            if (config != null) compact = config.compactNametags;
            if (!source.equals(FILE)) save();
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not load nametag settings: " + e.getMessage());
        }
    }

    boolean isCompact() { return compact; }

    void setCompact(boolean value) {
        compact = value;
        save();
    }

    private void save() {
        try {
            Files.createDirectories(DIR);
            Config config = new Config();
            config.compactNametags = compact;
            try (Writer writer = Files.newBufferedWriter(TEMP)) {
                GSON.toJson(config, writer);
            }
            try {
                Files.move(TEMP, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(TEMP, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[ChillZoneCombat] Could not save nametag settings: " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(TEMP); } catch (IOException ignored) {}
        }
    }
}
