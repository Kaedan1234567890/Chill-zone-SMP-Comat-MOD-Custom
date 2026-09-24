package com.chillzone.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class RankBackupStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<List<StoredRank>>(){}.getType();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("chillzone-combat");
    private static final Path FILE = DIR.resolve("ranks-backup.json");
    private static final Path LEGACY_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("chillzone-pvprank-admin").resolve("saved-ranks.json");
    private static final Path TEMP = DIR.resolve("ranks-backup.json.tmp");

    static final class StoredRank {
        UUID uuid;
        String name;
        int position;

        StoredRank(UUID uuid, String name, int position) {
            this.uuid = uuid;
            this.name = name;
            this.position = position;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof StoredRank rank)) return false;
            return position == rank.position && Objects.equals(uuid, rank.uuid) && Objects.equals(name, rank.name);
        }

        @Override
        public int hashCode() { return Objects.hash(uuid, name, position); }
    }

    private final List<StoredRank> snapshot = new ArrayList<>();
    private boolean loadedSnapshot;

    void load() {
        snapshot.clear();
        loadedSnapshot = false;
        Path source = Files.exists(FILE) ? FILE : (Files.exists(LEGACY_FILE) ? LEGACY_FILE : null);
        if (source == null) return;
        try (Reader reader = Files.newBufferedReader(source)) {
            List<StoredRank> loaded = GSON.fromJson(reader, TYPE);
            if (loaded != null) snapshot.addAll(normalize(loaded));
            loadedSnapshot = true;
            if (!source.equals(FILE)) save();
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not load PvP rank backup: " + e.getMessage());
        }
    }

    boolean hasSnapshot() { return loadedSnapshot; }
    boolean hasRanks() { return !snapshot.isEmpty(); }

    List<StoredRank> snapshot() {
        return copy(snapshot);
    }

    void replace(List<StoredRank> ranks) {
        List<StoredRank> normalized = normalize(ranks);
        if (loadedSnapshot && normalized.equals(snapshot)) return;
        snapshot.clear();
        snapshot.addAll(normalized);
        loadedSnapshot = true;
        save();
    }

    void clear() {
        snapshot.clear();
        loadedSnapshot = true;
        save();
    }

    private void save() {
        try {
            Files.createDirectories(DIR);
            try (Writer writer = Files.newBufferedWriter(TEMP)) {
                GSON.toJson(snapshot, TYPE, writer);
            }
            try {
                Files.move(TEMP, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(TEMP, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[ChillZoneCombat] Could not save PvP rank backup: " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(TEMP); } catch (IOException ignored) {}
        }
    }

    private static List<StoredRank> normalize(List<StoredRank> source) {
        List<StoredRank> out = copy(source);
        out.removeIf(r -> r.uuid == null || r.position < 1 || r.position > 10);
        out.sort(Comparator.comparingInt(r -> r.position));
        Set<UUID> seenPlayers = new HashSet<>();
        Set<Integer> seenSlots = new HashSet<>();
        out.removeIf(r -> !seenPlayers.add(r.uuid) || !seenSlots.add(r.position));
        return out;
    }

    private static List<StoredRank> copy(List<StoredRank> source) {
        List<StoredRank> out = new ArrayList<>();
        if (source == null) return out;
        for (StoredRank rank : source) {
            if (rank != null) out.add(new StoredRank(rank.uuid, rank.name, rank.position));
        }
        return out;
    }
}
