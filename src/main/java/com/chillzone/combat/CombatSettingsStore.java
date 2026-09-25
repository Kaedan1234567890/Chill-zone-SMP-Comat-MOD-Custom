package com.chillzone.combat;

import com.combat.CombatConfig;
import com.combat.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;

/**
 * Chill Zone's authoritative persistence for the settings edited through the
 * Combat Settings Menu (Inventory Limits, Cooldowns, Combat Log, Ranked System,
 * World Limits, Enchant Limits, Potion Controls, rank buffs, etc.).
 *
 * This intentionally does NOT depend on Fabric startup ordering. Original
 * Combat initializes its own ConfigManager during SERVER_STARTING. We restore
 * this file during SERVER_STARTED, after Combat has initialized, directly into
 * the exact live CombatConfig object used by every original GUI.
 *
 * While the server is running we compare the entire live config every tick and
 * atomically save whenever anything changes. This also catches original GUI
 * code paths that mutate a Map/list without calling ConfigManager.save().
 */
public final class CombatSettingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path settingsFile;
    private static boolean initialized;
    private static String lastSavedJson;

    private CombatSettingsStore() {}

    public static synchronized void initializeAfterCombat(MinecraftServer server) {
        try {
            Path root = server.getServerDirectory().toAbsolutePath().normalize();
            Path dir = root.resolve("config").resolve("chillzone-combat");
            Files.createDirectories(dir);
            settingsFile = dir.resolve("combat-settings.json");

            CombatConfig live = ConfigManager.getConfig();
            if (live == null) {
                System.err.println("[ChillZoneCombat] Combat settings store could not initialize: live CombatConfig is null.");
                return;
            }

            CombatConfig saved = read(settingsFile);
            initialized = true;

            if (saved != null) {
                copyInto(saved, live);
                // Mirror the restored values into the normal Combat config too.
                ConfigManager.savePrimaryOnly();
                writeIfChanged(live, true);
                System.out.println("[ChillZoneCombat] Restored Combat Settings Menu values from Chill Zone authoritative store.");
            } else {
                // First run of this fixed system: preserve whatever Combat has
                // loaded right now. The admin can change the menu once and every
                // later change will be captured immediately.
                writeIfChanged(live, true);
                System.out.println("[ChillZoneCombat] Created authoritative Combat Settings Menu store from current live settings.");
            }
        } catch (Throwable t) {
            System.err.println("[ChillZoneCombat] Could not initialize authoritative Combat settings store: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /** Called every server tick; only touches disk if the live config changed. */
    public static synchronized void captureIfChanged() {
        if (!initialized || settingsFile == null) return;
        try {
            CombatConfig live = ConfigManager.getConfig();
            if (live != null) writeIfChanged(live, false);
        } catch (Throwable t) {
            System.err.println("[ChillZoneCombat] Could not monitor Combat settings: " + t.getMessage());
        }
    }

    /** Called by our ConfigManager whenever an original Combat GUI invokes save(). */
    public static synchronized void onCombatConfigSaved(CombatConfig live) {
        if (!initialized || settingsFile == null || live == null) return;
        try {
            writeIfChanged(live, false);
        } catch (Throwable t) {
            System.err.println("[ChillZoneCombat] Could not immediately protect Combat menu settings: " + t.getMessage());
        }
    }

    public static synchronized void forceSave() {
        if (!initialized || settingsFile == null) return;
        try {
            CombatConfig live = ConfigManager.getConfig();
            if (live != null) writeIfChanged(live, true);
        } catch (Throwable t) {
            System.err.println("[ChillZoneCombat] Could not force-save Combat menu settings: " + t.getMessage());
        }
    }

    private static CombatConfig read(Path file) {
        if (file == null || !Files.isRegularFile(file)) return null;
        try {
            if (Files.size(file) <= 2L) return null;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, CombatConfig.class);
            }
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Ignoring unreadable authoritative settings file: " + e.getMessage());
            return null;
        }
    }

    private static void writeIfChanged(CombatConfig value, boolean force) throws Exception {
        String json = GSON.toJson(value);
        if (!force && json.equals(lastSavedJson)) return;
        writeAtomic(settingsFile, json);
        lastSavedJson = json;
    }

    private static void writeAtomic(Path target, String json) throws Exception {
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, target.getFileName().toString() + ".", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Copy every field that the original Combat 1.0.0 settings GUIs use. */
    private static void copyInto(CombatConfig from, CombatConfig to) {
        to.combatLogSeconds = from.combatLogSeconds;
        to.immunitySeconds = from.immunitySeconds;
        to.allowEnderchestInCombat = from.allowEnderchestInCombat;
        to.allowElytraInCombat = from.allowElytraInCombat;
        to.allowFireworksInCombat = from.allowFireworksInCombat;
        to.topRanksEffectsEnabled = from.topRanksEffectsEnabled;
        to.limitsOnlyInCombat = from.limitsOnlyInCombat;
        to.rankedSystemEnabled = from.rankedSystemEnabled;

        to.itemLimits = from.itemLimits == null ? new LinkedHashMap<>() : new LinkedHashMap<>(from.itemLimits);
        to.itemCooldowns = from.itemCooldowns == null ? new LinkedHashMap<>() : new LinkedHashMap<>(from.itemCooldowns);
        to.enchantLimits = from.enchantLimits == null ? new LinkedHashMap<>() : new LinkedHashMap<>(from.enchantLimits);
        to.potionLimits = from.potionLimits == null ? new LinkedHashMap<>() : new LinkedHashMap<>(from.potionLimits);
        to.worldLimits = from.worldLimits == null ? new LinkedHashMap<>() : new LinkedHashMap<>(from.worldLimits);
        to.rankHealthBoosts = from.rankHealthBoosts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(from.rankHealthBoosts);
        to.rankPotionEffects = from.rankPotionEffects == null ? new LinkedHashMap<>() : new LinkedHashMap<>(from.rankPotionEffects);
    }
}
