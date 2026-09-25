package com.combat;

import com.chillzone.combat.CombatSettingsStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Chill Zone-owned replacement of the original Combat ConfigManager.
 *
 * Keep the original Combat path simple and predictable. The separate
 * CombatSettingsStore owns restart-proof menu persistence. This class writes
 * the normal config atomically and notifies the authoritative store after every
 * GUI save.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File configFile;
    private static CombatConfig config = new CombatConfig();

    public ConfigManager() {}

    public static synchronized void init(File combatDirectory) {
        try {
            Files.createDirectories(combatDirectory.toPath());
            configFile = new File(combatDirectory, "combat_config.json");
            load();
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not initialize Combat config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static synchronized void load() {
        CombatConfig parsed = read(configFile);
        if (parsed != null) {
            config = parsed;
        } else {
            config = new CombatConfig();
            savePrimaryOnly();
        }
    }

    public static synchronized void save() {
        savePrimaryOnly();
        CombatSettingsStore.onCombatConfigSaved(config);
    }

    /** Used by the authoritative store while restoring; avoids recursive callback. */
    public static synchronized void savePrimaryOnly() {
        if (config == null) config = new CombatConfig();
        writeAtomic(configFile, config);
    }

    public static CombatConfig getConfig() {
        return config;
    }

    private static CombatConfig read(File file) {
        if (file == null || !file.isFile() || file.length() <= 2L) return null;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, CombatConfig.class);
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Ignoring invalid Combat config " + file.getPath() + ": " + e.getMessage());
            return null;
        }
    }

    private static void writeAtomic(File file, CombatConfig value) {
        if (file == null) return;
        Path target = file.toPath();
        Path parent = target.getParent();
        Path temp = null;
        try {
            if (parent != null) Files.createDirectories(parent);
            temp = Files.createTempFile(parent, target.getFileName().toString() + ".", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not save Combat config to " + target + ": " + e.getMessage());
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
            }
        }
    }
}
