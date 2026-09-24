package com.chillzone.combat;

import com.combat.CombatMod;
import com.combat.ConfigManager;
import com.combat.DataManager;
import com.combat.PlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

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

public final class RankManager {
    public record KnownPlayer(UUID uuid, String name) {}

    private static ExclusionStore exclusions;
    private static NametagSettings nametagSettings;

    private RankManager() {}

    static void initialize(ExclusionStore exclusionStore, NametagSettings settings) {
        exclusions = exclusionStore;
        nametagSettings = settings;
    }

    public static boolean isExcluded(UUID id) {
        return exclusions != null && exclusions.contains(id);
    }


    public static boolean isCompactNametagMode() {
        return nametagSettings == null || nametagSettings.isCompact();
    }

    public static void setCompactNametagMode(boolean compact) {
        if (nametagSettings != null) nametagSettings.setCompact(compact);
        MinecraftServer server = CombatMod.serverInstance;
        if (server != null) refreshNametags(server);
    }

    /** Keep players removed with /pvprank take from being automatically re-added. */
    public static boolean enforceExclusions() {
        if (exclusions == null) return false;
        boolean changed = false;
        for (Map.Entry<UUID, PlayerData> entry : DataManager.getAllPlayersData().entrySet()) {
            if (!exclusions.contains(entry.getKey())) continue;
            if (normalizePosition(entry.getValue().rankPosition) >= 1) {
                setPosition(entry.getValue(), -1);
                changed = true;
            }
        }
        if (changed) DataManager.save();
        return changed;
    }

    public static int getRank(UUID id) {
        PlayerData data = DataManager.getAllPlayersData().get(id);
        return data == null ? -1 : normalizePosition(data.rankPosition);
    }

    public static Optional<KnownPlayer> findByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        for (Map.Entry<UUID, PlayerData> entry : DataManager.getAllPlayersData().entrySet()) {
            String knownName = entry.getValue().playerName;
            if (knownName != null && knownName.equalsIgnoreCase(name)) {
                return Optional.of(new KnownPlayer(entry.getKey(), knownName));
            }
        }
        return Optional.empty();
    }

    public static Set<String> rememberedNames() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (PlayerData data : DataManager.getAllPlayersData().values()) {
            if (data.playerName != null && !data.playerName.isBlank()) names.add(data.playerName);
        }
        return names;
    }

    public static List<RankBackupStore.StoredRank> snapshotTop10() {
        List<RankBackupStore.StoredRank> out = new ArrayList<>();
        for (Map.Entry<UUID, PlayerData> entry : DataManager.getAllPlayersData().entrySet()) {
            int pos = normalizePosition(entry.getValue().rankPosition);
            if (pos >= 1 && pos <= 10) {
                out.add(new RankBackupStore.StoredRank(entry.getKey(), safeName(entry.getValue(), entry.getKey()), pos));
            }
        }
        out.sort(Comparator.comparingInt(rank -> rank.position));
        return out;
    }

    public static void restoreTop10(List<RankBackupStore.StoredRank> ranks) {
        for (PlayerData data : DataManager.getAllPlayersData().values()) {
            if (data.rankPosition >= 1 && data.rankPosition <= 10) setPosition(data, -1);
        }
        if (ranks != null) {
            for (RankBackupStore.StoredRank rank : ranks) {
                if (rank == null || rank.uuid == null || rank.position < 1 || rank.position > 10) continue;
                PlayerData data = DataManager.getOrCreatePlayerData(rank.uuid);
                if (rank.name != null && !rank.name.isBlank()) data.playerName = rank.name;
                setPosition(data, rank.position);
            }
        }
        DataManager.save();
    }

    /** Replacement for CombatMod.assignRankIfUnranked. Never creates Rank #11+. */
    public static void assignIfUnranked(ServerPlayer player) {
        if (!ConfigManager.getConfig().rankedSystemEnabled) return;
        UUID id = player.getUUID();
        PlayerData data = DataManager.getOrCreatePlayerData(id);
        data.playerName = player.getGameProfile().name();

        int current = normalizePosition(data.rankPosition);
        if (current >= 1 && current <= 10) return;
        if (isExcluded(id)) {
            if (data.rankPosition != -1) {
                setPosition(data, -1);
                DataManager.save();
            }
            updateNametag(player);
            return;
        }

        int open = firstOpenRank(id);
        if (open < 1) {
            setPosition(data, -1);
            DataManager.save();
            updateNametag(player);
            return;
        }

        setPosition(data, open);
        DataManager.save();
        player.sendSystemMessage(Component.literal("You joined the server and received Rank #" + open + "!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        updateNametag(player);
    }

    /** Replacement for CombatMod.handleKill with Top-10 cap and exclusion support. */
    public static void handleKill(ServerPlayer killer, ServerPlayer victim) {
        if (!ConfigManager.getConfig().rankedSystemEnabled) return;

        PlayerData killerData = DataManager.getOrCreatePlayerData(killer.getUUID());
        PlayerData victimData = DataManager.getOrCreatePlayerData(victim.getUUID());
        killerData.playerName = killer.getGameProfile().name();
        victimData.playerName = victim.getGameProfile().name();

        int killerRank = normalizePosition(killerData.rankPosition);
        int victimRank = normalizePosition(victimData.rankPosition);

        if (isExcluded(killer.getUUID())) {
            setPosition(killerData, -1);
            DataManager.save();
            updateNametag(killer);
            return;
        }

        // An unranked/lower-ranked killer takes the higher-ranked victim's exact slot.
        if (victimRank >= 1 && victimRank <= 10 && (killerRank < 1 || killerRank > victimRank)) {
            setPosition(killerData, victimRank);
            setPosition(victimData, killerRank >= 1 && killerRank <= 10 ? killerRank : -1);
            DataManager.save();

            String message = killer.getGameProfile().name() + " (now Rank #" + victimRank + ") killed "
                    + victim.getGameProfile().name() + " and took their rank!";
            MinecraftServer server = CombatMod.serverInstance;
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(Component.literal(message).withStyle(ChatFormatting.GOLD), false);
            }
            updateNametag(killer);
            updateNametag(victim);
            return;
        }

        // If both are unranked, award the FIRST empty Top-10 slot, never max+1.
        if (victimRank < 1 && killerRank < 1) {
            int open = firstOpenRank(killer.getUUID());
            if (open >= 1) {
                setPosition(killerData, open);
                DataManager.save();
                killer.sendSystemMessage(Component.literal("You received Rank #" + open + "!").withStyle(ChatFormatting.GOLD));
                updateNametag(killer);
            }
        }
    }

    public static void setRank(UUID id, String name, int rank) {
        if (rank < 1 || rank > 10) throw new IllegalArgumentException("Rank must be 1-10");
        PlayerData target = DataManager.getOrCreatePlayerData(id);
        target.playerName = name == null ? safeName(target, id) : name;
        exclusions.remove(id);

        // Clear the player's old slot first. Gaps are intentionally allowed.
        setPosition(target, -1);

        // Make the requested slot available by pushing only that slot/downward.
        PlayerData occupying = playerAtRank(rank, id);
        if (occupying != null) {
            int free = firstOpenAtOrAfter(rank, id);
            if (free < 1) {
                PlayerData rankTen = playerAtRank(10, id);
                if (rankTen != null) setPosition(rankTen, -1);
                free = 10;
            }
            for (int pos = free; pos > rank; pos--) {
                PlayerData previous = playerAtRank(pos - 1, id);
                if (previous != null) setPosition(previous, pos);
            }
        }

        setPosition(target, rank);
        DataManager.save();
    }

    /** Remove the rank WITHOUT compacting everyone below it. The slot stays open. */
    public static int takeRank(UUID id) {
        PlayerData data = DataManager.getOrCreatePlayerData(id);
        int old = normalizePosition(data.rankPosition);
        setPosition(data, -1);
        exclusions.add(id);
        DataManager.save();
        return old;
    }

    /** Re-enable a previously taken player and place them into the first open Top-10 slot. */
    public static int resetPlayer(UUID id, String name) {
        PlayerData data = DataManager.getOrCreatePlayerData(id);
        data.playerName = name == null ? safeName(data, id) : name;
        exclusions.remove(id);
        setPosition(data, -1);
        int open = firstOpenRank(id);
        if (open >= 1) setPosition(data, open);
        DataManager.save();
        return open;
    }

    public static void swap(UUID a, UUID b) {
        PlayerData first = DataManager.getOrCreatePlayerData(a);
        PlayerData second = DataManager.getOrCreatePlayerData(b);
        int firstPos = normalizePosition(first.rankPosition);
        int secondPos = normalizePosition(second.rankPosition);
        setPosition(first, secondPos);
        setPosition(second, firstPos);
        exclusions.remove(a);
        exclusions.remove(b);
        DataManager.save();
    }

    public static void clearAllRanks() {
        for (PlayerData data : DataManager.getAllPlayersData().values()) setPosition(data, -1);
        exclusions.clear();
        DataManager.save();
    }

    public static Map<Integer, KnownPlayer> rankedPlayers() {
        Map<Integer, KnownPlayer> result = new LinkedHashMap<>();
        DataManager.getAllPlayersData().entrySet().stream()
                .filter(e -> normalizePosition(e.getValue().rankPosition) >= 1 && normalizePosition(e.getValue().rankPosition) <= 10)
                .sorted(Comparator.comparingInt(e -> normalizePosition(e.getValue().rankPosition)))
                .forEach(e -> result.put(normalizePosition(e.getValue().rankPosition),
                        new KnownPlayer(e.getKey(), safeName(e.getValue(), e.getKey()))));
        return result;
    }

    public static int firstOpenRank(UUID ignore) {
        boolean[] used = new boolean[11];
        for (Map.Entry<UUID, PlayerData> entry : DataManager.getAllPlayersData().entrySet()) {
            if (ignore != null && ignore.equals(entry.getKey())) continue;
            int pos = normalizePosition(entry.getValue().rankPosition);
            if (pos >= 1 && pos <= 10) used[pos] = true;
        }
        for (int rank = 1; rank <= 10; rank++) if (!used[rank]) return rank;
        return -1;
    }

    private static int firstOpenAtOrAfter(int start, UUID ignore) {
        boolean[] used = new boolean[11];
        for (Map.Entry<UUID, PlayerData> entry : DataManager.getAllPlayersData().entrySet()) {
            if (ignore != null && ignore.equals(entry.getKey())) continue;
            int pos = normalizePosition(entry.getValue().rankPosition);
            if (pos >= 1 && pos <= 10) used[pos] = true;
        }
        for (int rank = Math.max(1, start); rank <= 10; rank++) if (!used[rank]) return rank;
        return -1;
    }

    private static PlayerData playerAtRank(int rank, UUID ignore) {
        for (Map.Entry<UUID, PlayerData> entry : DataManager.getAllPlayersData().entrySet()) {
            if (ignore != null && ignore.equals(entry.getKey())) continue;
            if (normalizePosition(entry.getValue().rankPosition) == rank) return entry.getValue();
        }
        return null;
    }

    private static void setPosition(PlayerData data, int position) {
        int normalized = normalizePosition(position);
        data.rankPosition = normalized;
        data.rank = normalized >= 1 ? "Rank #" + normalized : "unranked";
    }

    private static int normalizePosition(int position) {
        return position >= 1 && position <= 10 ? position : -1;
    }

    private static String safeName(PlayerData data, UUID id) {
        return data.playerName == null || data.playerName.isBlank()
                ? id.toString().substring(0, 8).toLowerCase(Locale.ROOT)
                : data.playerName;
    }

    public static void updateNametag(ServerPlayer player) {
        CombatMod.updatePlayerNametag(player);
        if (nametagSettings == null || !nametagSettings.isCompact()) return;
        if (!ConfigManager.getConfig().rankedSystemEnabled) return;

        int rank = getRank(player.getUUID());
        if (rank < 1) return; // Preserve Combat's native [Unranked].

        MinecraftServer server = CombatMod.serverInstance;
        if (server == null) return;
        PlayerTeam team = server.getScoreboard().getPlayersTeam(player.getScoreboardName());
        if (team == null) return;
        Component current = team.getPlayerPrefix();
        Component replacement = current == null
                ? Component.literal("[#" + rank + "] ")
                : Component.literal("[#" + rank + "] ").setStyle(current.getStyle());
        team.setPlayerPrefix(replacement);
    }

    public static void refreshNametags(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) updateNametag(player);
    }
}
