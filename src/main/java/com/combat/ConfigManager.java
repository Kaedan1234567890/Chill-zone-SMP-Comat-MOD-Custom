package com.combat;

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
 * Chill Zone replacement for the original Combat ConfigManager.
 *
 * IMPORTANT PERSISTENCE RULE:
 * Once the protected backup exists and is valid, it is the authoritative copy
 * on startup. We do NOT choose by timestamp. A host/server/mod update can
 * recreate a perfectly valid default combat_config.json with a newer timestamp;
 * choosing the newest file would silently wipe the real settings again.
 *
 * Every successful save writes the protected copy FIRST and then mirrors the
 * same config to the normal Combat path.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File configFile;
    private static File backupFile;
    private static CombatConfig config = new CombatConfig();

    public ConfigManager() {}

    public static synchronized void init(File combatDirectory) {
        try {
            Files.createDirectories(combatDirectory.toPath());
            configFile = new File(combatDirectory, "combat_config.json");

            File configRoot = combatDirectory.getParentFile();
            if (configRoot == null) configRoot = combatDirectory;
            File protectedDir = new File(configRoot, "chillzone-combat/persistent-backup");
            Files.createDirectories(protectedDir.toPath());
            backupFile = new File(protectedDir, "combat_config.json");

            load();
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not initialize Combat settings: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static synchronized void load() {
        CombatConfig protectedCopy = read(backupFile);
        CombatConfig primary = read(configFile);

        // The protected copy is intentionally authoritative once it exists.
        // This prevents a newly regenerated/default primary from ever winning
        // merely because it has a newer timestamp.
        if (protectedCopy != null) {
            config = protectedCopy;
            persistCurrentConfig();
            System.out.println("[ChillZoneCombat] Loaded authoritative Combat settings backup and re-synced primary.");
            return;
        }

        // First migration/start only: seed the protected copy from the existing
        // original Combat file if that file is valid.
        if (primary != null) {
            config = primary;
            persistCurrentConfig();
            System.out.println("[ChillZoneCombat] Seeded protected Combat settings from existing primary config.");
            return;
        }

        config = new CombatConfig();
        persistCurrentConfig();
        System.out.println("[ChillZoneCombat] Created new Combat settings and protected backup.");
    }

    public static synchronized void save() {
        if (config == null) config = new CombatConfig();
        persistCurrentConfig();
    }

    public static CombatConfig getConfig() {
        return config;
    }

    private static CombatConfig read(File file) {
        if (file == null || !file.isFile() || file.length() <= 2L) return null;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            CombatConfig parsed = GSON.fromJson(reader, CombatConfig.class);
            return parsed;
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Ignoring invalid Combat settings file "
                    + file.getPath() + ": " + e.getMessage());
            return null;
        }
    }

    private static void persistCurrentConfig() {
        // Protected copy FIRST because it is the startup authority.
        writeAtomic(backupFile, config);
        writeAtomic(configFile, config);
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
            System.err.println("[ChillZoneCombat] Could not save Combat settings to "
                    + target + ": " + e.getMessage());
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
            }
        }
    }
}
