package com.chillzone.combat;

import com.combat.ConfigManager;
import com.combat.DataManager;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Extra persistence safety for the original Combat config/data files.
 *
 * The original mod writes config/combat/*.json directly. This layer keeps
 * atomic shadow copies and restores them BEFORE the original Combat entrypoint
 * loads if a primary file is missing/empty/corrupt. It also periodically calls
 * the original save methods so GUI settings cannot live only in memory.
 */
final class CombatPersistence {
    private static final Path COMBAT_DIR = FabricLoader.getInstance().getConfigDir().resolve("combat");
    private static final Path SAFE_DIR = FabricLoader.getInstance().getConfigDir()
            .resolve("chillzone-combat").resolve("persistent-backup");

    private static final Path CONFIG = COMBAT_DIR.resolve("combat_config.json");
    private static final Path DATA = COMBAT_DIR.resolve("combat_data.json");
    private static final Path CONFIG_BACKUP = SAFE_DIR.resolve("combat_config.json");
    private static final Path DATA_BACKUP = SAFE_DIR.resolve("combat_data.json");

    private CombatPersistence() {}

    /** Runs on SERVER_STARTING before the original Combat callback is registered/executed. */
    static void restoreBeforeOriginalLoad() {
        try {
            Files.createDirectories(COMBAT_DIR);
            Files.createDirectories(SAFE_DIR);
            restoreIfNeeded(CONFIG, CONFIG_BACKUP, "Combat settings");
            restoreIfNeeded(DATA, DATA_BACKUP, "Combat player/rank data");
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Persistence pre-load check failed: " + e.getMessage());
        }
    }

    /** Force both original managers to disk, then keep atomic shadow copies. */
    static synchronized void flushAndSnapshot() {
        try {
            ConfigManager.save();
        } catch (Throwable t) {
            System.err.println("[ChillZoneCombat] Could not flush Combat settings: " + t.getMessage());
        }
        try {
            DataManager.save();
        } catch (Throwable t) {
            System.err.println("[ChillZoneCombat] Could not flush Combat player data: " + t.getMessage());
        }
        snapshotFiles();
    }

    /** Used immediately after rank mutations so the rank file's safe copy is current. */
    static synchronized void snapshotFiles() {
        try {
            Files.createDirectories(SAFE_DIR);
            copyIfValid(CONFIG, CONFIG_BACKUP);
            copyIfValid(DATA, DATA_BACKUP);
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not update persistence backups: " + e.getMessage());
        }
    }

    private static void restoreIfNeeded(Path primary, Path backup, String label) {
        if (isValidJson(primary)) return;
        if (!isValidJson(backup)) return;
        try {
            atomicCopy(backup, primary);
            System.out.println("[ChillZoneCombat] Restored " + label + " from the persistent backup.");
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not restore " + label + ": " + e.getMessage());
        }
    }

    private static void copyIfValid(Path source, Path destination) throws Exception {
        if (!isValidJson(source)) return;
        atomicCopy(source, destination);
    }

    private static void atomicCopy(Path source, Path destination) throws Exception {
        Files.createDirectories(destination.getParent());
        Path temp = destination.resolveSibling(destination.getFileName().toString() + ".tmp");
        Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static boolean isValidJson(Path file) {
        if (!Files.isRegularFile(file)) return false;
        try {
            if (Files.size(file) <= 2L) return false;
            try (Reader reader = Files.newBufferedReader(file)) {
                return JsonParser.parseReader(reader) != null;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
