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
 * Persistence helpers for original Combat player/rank data.
 *
 * Combat SETTINGS are no longer restored from lifecycle callbacks. Version
 * 1.2.5 replaces com.combat.ConfigManager itself, so settings load/save is now
 * atomic and deterministic at the exact point Combat initializes.
 */
public final class CombatPersistence {
    private static Path combatDir = FabricLoader.getInstance().getConfigDir().resolve("combat");
    private static Path safeDir = FabricLoader.getInstance().getConfigDir()
            .resolve("chillzone-combat").resolve("persistent-backup");

    private static Path data = combatDir.resolve("combat_data.json");
    private static Path dataBackup = safeDir.resolve("combat_data.json");

    private CombatPersistence() {}

    private static synchronized void configureForServer(MinecraftServer server) {
        if (server == null) return;
        Path root = server.getServerDirectory().toAbsolutePath().normalize();
        combatDir = root.resolve("config").resolve("combat");
        safeDir = root.resolve("config").resolve("chillzone-combat").resolve("persistent-backup");
        data = combatDir.resolve("combat_data.json");
        dataBackup = safeDir.resolve("combat_data.json");
    }

    /**
     * Called directly from a mixin at the HEAD of DataManager.init(File).
     * This removes lifecycle ordering from player/rank restoration.
     */
    public static synchronized void restoreDataBeforeInit(java.io.File combatDirectory) {
        try {
            combatDir = combatDirectory.toPath().toAbsolutePath().normalize();
            Path configRoot = combatDir.getParent();
            if (configRoot == null) configRoot = combatDir;
            safeDir = configRoot.resolve("chillzone-combat").resolve("persistent-backup");
            data = combatDir.resolve("combat_data.json");
            dataBackup = safeDir.resolve("combat_data.json");

            Files.createDirectories(combatDir);
            Files.createDirectories(safeDir);

            // Only recover from backup when the primary is missing/corrupt.
            // The dedicated Top-10 backup remains the authority for rank slots.
            if (!isValidJson(data) && isValidJson(dataBackup)) {
                atomicCopy(dataBackup, data);
                System.out.println("[ChillZoneCombat] Restored protected Combat player-data backup.");
            } else if (isValidJson(data) && !isValidJson(dataBackup)) {
                atomicCopy(data, dataBackup);
                System.out.println("[ChillZoneCombat] Created initial protected Combat player-data backup.");
            }
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Player-data pre-load check failed: " + e.getMessage());
        }
    }

    /** Force current managers to disk. ConfigManager itself now writes two atomic copies. */
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
        snapshotDataFile();
    }

    /** Used immediately after rank mutations. */
    static synchronized void snapshotFiles() {
        try { ConfigManager.save(); } catch (Throwable ignored) {}
        snapshotDataFile();
    }

    /** Compatibility method retained for older calls; ConfigManager already owns its backup. */
    public static synchronized void snapshotConfigFile() {
        try { ConfigManager.save(); } catch (Throwable ignored) {}
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
