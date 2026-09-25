package com.chillzone.combat;

import com.combat.ConfigManager;
import com.combat.DataManager;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Protected persistence for the original Combat config/data files.
 *
 * 1.2.4 fix:
 * - Uses the exact server-directory path used by the original Combat mod.
 * - Once a protected backup exists, that backup is authoritative on startup.
 *   This prevents a host/mod update from replacing a valid customized config
 *   with a different-but-still-valid default JSON file.
 * - Original ConfigManager/DataManager saves are mirrored immediately by
 *   mixins, so GUI changes become protected as soon as Combat writes them.
 */
public final class CombatPersistence {
    private static Path combatDir = FabricLoader.getInstance().getConfigDir().resolve("combat");
    private static Path safeDir = FabricLoader.getInstance().getConfigDir()
            .resolve("chillzone-combat").resolve("persistent-backup");

    private static Path config = combatDir.resolve("combat_config.json");
    private static Path data = combatDir.resolve("combat_data.json");
    private static Path configBackup = safeDir.resolve("combat_config.json");
    private static Path dataBackup = safeDir.resolve("combat_data.json");

    private CombatPersistence() {}

    private static synchronized void configureForServer(MinecraftServer server) {
        if (server == null) return;
        Path root = server.getServerDirectory().toAbsolutePath().normalize();
        combatDir = root.resolve("config").resolve("combat");
        safeDir = root.resolve("config").resolve("chillzone-combat").resolve("persistent-backup");
        config = combatDir.resolve("combat_config.json");
        data = combatDir.resolve("combat_data.json");
        configBackup = safeDir.resolve("combat_config.json");
        dataBackup = safeDir.resolve("combat_data.json");
    }

    /**
     * Runs from Chill Zone's SERVER_STARTING callback, which is registered
     * before the embedded original Combat callback.
     *
     * A valid protected backup is authoritative. If no backup exists yet, a
     * valid existing primary is adopted as the first protected copy.
     */
    static synchronized void restoreBeforeOriginalLoad(MinecraftServer server) {
        configureForServer(server);
        try {
            Files.createDirectories(combatDir);
            Files.createDirectories(safeDir);
            restoreAuthoritative(config, configBackup, "Combat settings");
            restoreAuthoritative(data, dataBackup, "Combat player/rank data");
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Persistence pre-load check failed: " + e.getMessage());
        }
    }

    /** Force both original managers to disk, then keep atomic protected copies. */
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

    /** Used immediately after rank mutations so both safe copies remain current. */
    static synchronized void snapshotFiles() {
        snapshotConfigFile();
        snapshotDataFile();
    }

    /** Called after the original ConfigManager.save() returns. */
    public static synchronized void snapshotConfigFile() {
        try {
            Files.createDirectories(safeDir);
            copyIfValid(config, configBackup);
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not protect Combat settings: " + e.getMessage());
        }
    }

    /** Called after the original DataManager.save() returns. */
    public static synchronized void snapshotDataFile() {
        try {
            Files.createDirectories(safeDir);
            copyIfValid(data, dataBackup);
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not protect Combat player data: " + e.getMessage());
        }
    }

    private static void restoreAuthoritative(Path primary, Path backup, String label) {
        try {
            if (isValidJson(backup)) {
                // Important: restore even when the primary is valid JSON. A host
                // can regenerate a perfectly valid default file during restart.
                atomicCopy(backup, primary);
                System.out.println("[ChillZoneCombat] Loaded protected " + label + " backup.");
                return;
            }

            // First run of the protected system: adopt an existing good file.
            if (isValidJson(primary)) {
                atomicCopy(primary, backup);
                System.out.println("[ChillZoneCombat] Created initial protected " + label + " backup.");
            }
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not prepare " + label + ": " + e.getMessage());
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
