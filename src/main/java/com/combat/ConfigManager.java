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
 * This class intentionally owns Combat settings persistence directly instead
 * of trying to repair the original manager from lifecycle callbacks.
 *
 * Every save writes BOTH:
 *   config/combat/combat_config.json
 *   config/chillzone-combat/persistent-backup/combat_config.json
 *
 * Every load chooses the newest valid copy and immediately re-syncs both files.
 * This means normal restarts, mod updates, stale/default primaries, and a single
 * damaged copy cannot silently reset the Combat settings menu anymore.
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
            if (configRoot == null) {
                configRoot = combatDirectory;
            }
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
        CombatConfig primary = read(configFile);
        CombatConfig protectedCopy = read(backupFile);

        long primaryTime = validFileTime(configFile, primary);
        long backupTime = validFileTime(backupFile, protectedCopy);

        if (primary != null || protectedCopy != null) {
            // Use the newest valid copy. This is deliberately different from
            // the old 1.2.4 "backup always wins" behavior, which could restore
            // an older/default backup over newer settings.
            if (protectedCopy != null && (primary == null || backupTime > primaryTime)) {
                config = protectedCopy;
                System.out.println("[ChillZoneCombat] Loaded Combat settings from protected backup.");
            } else {
                config = primary;
                System.out.println("[ChillZoneCombat] Loaded Combat settings from primary config.");
            }

            // Heal/synchronize both copies immediately from the chosen config.
            persistCurrentConfig();
            return;
        }

        config = new CombatConfig();
        persistCurrentConfig();
        System.out.println("[ChillZoneCombat] Created new Combat settings files.");
    }

    public static synchronized void save() {
        if (config == null) {
            config = new CombatConfig();
        }
        persistCurrentConfig();
    }

    public static CombatConfig getConfig() {
        return config;
    }

    private static CombatConfig read(File file) {
        if (file == null || !file.isFile() || file.length() <= 2L) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, CombatConfig.class);
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Ignoring invalid Combat settings file "
                    + file.getPath() + ": " + e.getMessage());
            return null;
        }
    }

    private static long validFileTime(File file, CombatConfig parsed) {
        return parsed == null || file == null ? Long.MIN_VALUE : file.lastModified();
    }

    private static void persistCurrentConfig() {
        // Write the primary first. If the process is interrupted between the
        // two writes, the next load picks the newest valid copy.
        writeAtomic(configFile, config);
        writeAtomic(backupFile, config);
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
                Files.move(temp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
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
