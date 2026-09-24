package com.chillzone.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class ExclusionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Set<UUID>>(){}.getType();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("chillzone-combat");
    private static final Path FILE = DIR.resolve("excluded-players.json");
    private static final Path LEGACY_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("chillzone-pvprank-admin").resolve("excluded-players.json");

    private final Set<UUID> excluded = new HashSet<>();

    void load() {
        excluded.clear();
        Path source = Files.exists(FILE) ? FILE : (Files.exists(LEGACY_FILE) ? LEGACY_FILE : null);
        try {
            Files.createDirectories(DIR);
            if (source != null) {
                try (Reader reader = Files.newBufferedReader(source)) {
                    Set<UUID> loaded = GSON.fromJson(reader, TYPE);
                    if (loaded != null) excluded.addAll(loaded);
                }
            }
            save();
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not load rank exclusions: " + e.getMessage());
        }
    }

    boolean contains(UUID id) { return excluded.contains(id); }
    void add(UUID id) { if (excluded.add(id)) save(); }
    void remove(UUID id) { if (excluded.remove(id)) save(); }
    void clear() { excluded.clear(); save(); }

    private void save() {
        try {
            Files.createDirectories(DIR);
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(excluded, TYPE, writer);
            }
        } catch (IOException e) {
            System.err.println("[ChillZoneCombat] Could not save rank exclusions: " + e.getMessage());
        }
    }
}
