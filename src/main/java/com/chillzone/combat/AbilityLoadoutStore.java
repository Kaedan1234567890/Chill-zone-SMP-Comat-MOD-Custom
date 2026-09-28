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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent per-player selectable ability loadouts. */
public final class AbilityLoadoutStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<UUID, List<String>>>(){}.getType();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("chillzone-combat");
    private static final Path FILE = DIR.resolve("ability-loadouts.json");
    private static final Path TEMP = DIR.resolve("ability-loadouts.json.tmp");

    private static final Map<UUID, LinkedHashSet<String>> LOADOUTS = new LinkedHashMap<>();

    private AbilityLoadoutStore() {}

    public static synchronized void load() {
        LOADOUTS.clear();
        try {
            Files.createDirectories(DIR);
            if (Files.exists(FILE)) {
                try (Reader reader = Files.newBufferedReader(FILE)) {
                    Map<UUID, List<String>> loaded = GSON.fromJson(reader, TYPE);
                    if (loaded != null) {
                        for (Map.Entry<UUID, List<String>> entry : loaded.entrySet()) {
                            LinkedHashSet<String> ids = new LinkedHashSet<>();
                            if (entry.getValue() != null) {
                                for (String id : entry.getValue()) {
                                    if (AbilityDefinition.byId(id) != null) ids.add(id);
                                }
                            }
                            LOADOUTS.put(entry.getKey(), ids);
                        }
                    }
                }
            }
            save();
        } catch (Exception e) {
            System.err.println("[ChillZoneCombat] Could not load ability loadouts: " + e.getMessage());
        }
    }

    public static synchronized Set<String> equipped(UUID playerId) {
        return Set.copyOf(LOADOUTS.getOrDefault(playerId, new LinkedHashSet<>()));
    }

    public static synchronized boolean isEquipped(UUID playerId, String abilityId) {
        return LOADOUTS.getOrDefault(playerId, new LinkedHashSet<>()).contains(abilityId);
    }

    /**
     * Toggle an ability. Returns true when the loadout changed. The caller is
     * responsible for showing the reason when it cannot be equipped.
     */
    public static synchronized boolean toggle(UUID playerId, int rank, AbilityDefinition ability) {
        if (rank < 1 || rank > 10 || ability == null || !ability.unlockedFor(rank)) return false;
        LinkedHashSet<String> loadout = LOADOUTS.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>());
        if (loadout.remove(ability.id())) {
            save();
            return true;
        }
        int slots = AbilityDefinition.slotsForRank(rank);
        if (loadout.size() >= slots) return false;
        loadout.add(ability.id());
        save();
        return true;
    }

    public static synchronized boolean sanitize(UUID playerId, int rank) {
        LinkedHashSet<String> loadout = LOADOUTS.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>());
        boolean changed = loadout.removeIf(id -> {
            AbilityDefinition ability = AbilityDefinition.byId(id);
            return ability == null || !ability.unlockedFor(rank);
        });
        int slots = AbilityDefinition.slotsForRank(rank);
        while (loadout.size() > slots) {
            String last = null;
            for (String id : loadout) last = id;
            if (last == null) break;
            loadout.remove(last);
            changed = true;
        }
        if (changed) save();
        return changed;
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(DIR);
            Map<UUID, List<String>> output = new LinkedHashMap<>();
            for (Map.Entry<UUID, LinkedHashSet<String>> entry : LOADOUTS.entrySet()) {
                output.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            try (Writer writer = Files.newBufferedWriter(TEMP)) {
                GSON.toJson(output, TYPE, writer);
            }
            try {
                Files.move(TEMP, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(TEMP, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[ChillZoneCombat] Could not save ability loadouts: " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(TEMP); } catch (IOException ignored) {}
        }
    }
}
