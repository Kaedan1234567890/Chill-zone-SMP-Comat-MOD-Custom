package com.chillzone.combat;

import com.combat.DataManager;
import com.combat.PlayerData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Persistent Chill Zone player registry for PvP-rank autocomplete/targeting.
 *
 * Sources, in order of usefulness:
 *  - this mod's own remembered-player file;
 *  - the Homes/Shard /baltop data file (chill-zone-shards.json);
 *  - vanilla usercache.json;
 *  - Combat's existing player history;
 *  - currently-online players.
 *
 * Importing /baltop means staff can immediately rank people who have already
 * played on the server without waiting for them to rejoin after a reset/update.
 */
final class KnownPlayerStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("chillzone-combat");
    private static final Path FILE = DIR.resolve("known-players.json");
    private static final Path TEMP = DIR.resolve("known-players.json.tmp");
    private static final Path SHARDS_FILE = FabricLoader.getInstance().getConfigDir().resolve("chill-zone-shards.json");
    private static final Path USER_CACHE = FabricLoader.getInstance().getGameDir().resolve("usercache.json");

    private final Map<UUID, String> players = new LinkedHashMap<>();

    synchronized void loadLocal() {
        players.clear();
        if (!Files.isRegularFile(FILE)) return;
        try (BufferedReader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonObject()) return;
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                try {
                    UUID id = UUID.fromString(entry.getKey());
                    String name = entry.getValue().getAsString();
                    put(id, name);
                } catch (Exception ignored) {
                    // Ignore one malformed row instead of losing the registry.
                }
            }
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not load remembered players: " + e.getMessage());
        }
    }

    synchronized void bootstrapAfterCombatLoad(MinecraftServer server) {
        boolean changed = false;
        changed |= importShardBaltop();
        changed |= importVanillaUserCache();
        changed |= importCombatData();
        changed |= rememberOnline(server);
        if (changed || !Files.exists(FILE)) save();
        System.out.println("[ChillZoneCombat] Remembered " + players.size()
                + " player(s) for /pvprank, including /baltop history.");
    }

    synchronized boolean remember(UUID id, String name) {
        boolean changed = put(id, name);
        if (changed) save();
        return changed;
    }

    synchronized Optional<RankManager.KnownPlayer> findByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        for (Map.Entry<UUID, String> entry : players.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return Optional.of(new RankManager.KnownPlayer(entry.getKey(), entry.getValue()));
            }
        }
        return Optional.empty();
    }

    synchronized Set<String> namesStartingWith(String typed) {
        String prefix = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : players.values()) {
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(name);
        }
        return out;
    }

    synchronized boolean rememberOnline(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) return false;
        boolean changed = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            changed |= put(player.getUUID(), player.getGameProfile().name());
        }
        if (changed) save();
        return changed;
    }

    private boolean importCombatData() {
        boolean changed = false;
        try {
            for (Map.Entry<UUID, PlayerData> entry : DataManager.getAllPlayersData().entrySet()) {
                PlayerData data = entry.getValue();
                if (data != null) changed |= put(entry.getKey(), data.playerName);
            }
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not import Combat player history: " + e.getMessage());
        }
        return changed;
    }

    /** Import every UUID/name remembered by the Homes/Shard system used by /baltop. */
    private boolean importShardBaltop() {
        if (!Files.isRegularFile(SHARDS_FILE)) return false;
        boolean changed = false;
        try (BufferedReader reader = Files.newBufferedReader(SHARDS_FILE, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonObject()) return false;
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                try {
                    UUID id = UUID.fromString(entry.getKey());
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject record = entry.getValue().getAsJsonObject();
                    if (!record.has("lastKnownName")) continue;
                    changed |= put(id, record.get("lastKnownName").getAsString());
                } catch (Exception ignored) {
                    // Continue importing the remaining leaderboard history.
                }
            }
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not import /baltop player list: " + e.getMessage());
        }
        return changed;
    }

    private boolean importVanillaUserCache() {
        if (!Files.isRegularFile(USER_CACHE)) return false;
        boolean changed = false;
        try (BufferedReader reader = Files.newBufferedReader(USER_CACHE, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonArray()) return false;
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject row = element.getAsJsonObject();
                if (!row.has("uuid") || !row.has("name")) continue;
                try {
                    changed |= put(UUID.fromString(row.get("uuid").getAsString()), row.get("name").getAsString());
                } catch (Exception ignored) {
                    // Ignore malformed/expired cache rows.
                }
            }
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not import usercache.json: " + e.getMessage());
        }
        return changed;
    }

    private boolean put(UUID id, String name) {
        if (id == null || name == null || name.isBlank()) return false;
        String clean = name.trim();
        String previous = players.put(id, clean);
        return !clean.equals(previous);
    }

    private void save() {
        try {
            Files.createDirectories(DIR);
            List<Map.Entry<UUID, String>> rows = new ArrayList<>(players.entrySet());
            rows.sort(Comparator.comparing(Map.Entry<UUID, String>::getValue, String.CASE_INSENSITIVE_ORDER));
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, String> row : rows) root.addProperty(row.getKey().toString(), row.getValue());
            try (BufferedWriter writer = Files.newBufferedWriter(TEMP, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(TEMP, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(TEMP, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not save remembered players: " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(TEMP); } catch (Exception ignored) {}
        }
    }
}
