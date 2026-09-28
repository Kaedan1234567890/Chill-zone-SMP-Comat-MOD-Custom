package com.chillzone.combat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Definition of every selectable Chill Zone PvP rank ability. */
public enum AbilityDefinition {
    FIRST_BLOOD("first_blood", "First Blood", 10, Items.IRON_SWORD,
            "PvP kill: Speed I + Regeneration I for 8 seconds.", "Cooldown: 30 seconds"),
    FEATHERSTEP("featherstep", "Featherstep", 10, Items.FEATHER,
            "Reduces fall damage by 30% while equipped.", "Passive"),

    RUNNERS_INSTINCT("runners_instinct", "Runner's Instinct", 9, Items.COOKED_BEEF,
            "Reduces sprint and sprint-jump hunger exhaustion by 20%.", "Passive"),
    STEADFAST("steadfast", "Steadfast", 9, Items.SHIELD,
            "Grants 10% knockback resistance while equipped.", "Passive"),

    HUNTERS_RECOVERY("hunters_recovery", "Hunter's Recovery", 8, Items.GOLDEN_APPLE,
            "PvP kill: restore 2.5 hearts.", "Cooldown: 30 seconds"),
    ADRENALINE_RUSH("adrenaline_rush", "Adrenaline Rush", 8, Items.SUGAR,
            "PvP kill: gain Speed II for 8 seconds.", "Cooldown: 30 seconds"),

    FIREBORN("fireborn", "Fireborn", 7, Items.BLAZE_POWDER,
            "Grants Fire Resistance while equipped.", "Passive"),
    LAST_STAND("last_stand", "Last Stand", 7, Items.IRON_CHESTPLATE,
            "Below 35% HP: Resistance I for 10 seconds.", "Cooldown: 45 seconds"),

    ABSORPTION_GUARD("absorption_guard", "Absorption Guard", 6, Items.GOLDEN_CARROT,
            "Below 50% HP: gain 3 absorption hearts for 15 seconds.", "Cooldown: 45 seconds"),
    IRON_HEART("iron_heart", "Iron Heart", 6, Items.IRON_INGOT,
            "Below 40% HP: Regeneration II for 10 seconds.", "Cooldown: 45 seconds"),

    BERSERKER("berserker", "Berserker", 5, Items.NETHERITE_AXE,
            "Below 40% HP: Strength I + Speed I for 15 seconds.", "Cooldown: 30 seconds"),
    BLOOD_FEAST("blood_feast", "Blood Feast", 5, Items.REDSTONE,
            "PvP kill: restore 3 hearts, gain 4 absorption hearts for 15s,", "and Strength I for 30s. Cooldown: 30 seconds"),

    REVENGE("revenge", "Revenge", 4, Items.IRON_AXE,
            "After losing at least 1 heart to a PvP hit: heal back 15%", "and gain Resistance I for 15s. Cooldown: 30 seconds"),
    VAMPIRIC_STRIKE("vampiric_strike", "Vampiric Strike", 4, Items.GLISTERING_MELON_SLICE,
            "PvP kill: restore 5 hearts.", "Cooldown: 30 seconds"),

    SECOND_WIND("second_wind", "Second Wind", 3, Items.TOTEM_OF_UNDYING,
            "Below 30% HP: Speed II + Resistance II + Regeneration III", "for 20 seconds. Cooldown: 60 seconds"),
    WARRIORS_MOMENTUM("warriors_momentum", "Warrior's Momentum", 3, Items.DIAMOND_SWORD,
            "PvP kill: Strength I + Speed II for 15 seconds.", "Cooldown: 30 seconds"),

    KINGS_WRATH("kings_wrath", "King's Wrath", 2, Items.NETHERITE_SWORD,
            "Below 35% HP: Strength II + Resistance II for 20 seconds.", "Cooldown: 60 seconds"),
    CHAMPIONS_FEAST("champions_feast", "Champion's Feast", 2, Items.ENCHANTED_GOLDEN_APPLE,
            "PvP kill: restore 5 hearts + gain 5 absorption hearts for 15s.", "Cooldown: 45 seconds"),

    DOMINANCE("dominance", "Dominance", 1, Items.NETHERITE_CHESTPLATE,
            "PvP kill: gain Strength II for 15 seconds.", "Cooldown: 45 seconds"),
    ROYAL_GUARD("royal_guard", "Royal Guard", 1, Items.NETHERITE_HELMET,
            "While below 50% HP: gain Resistance II.", "Fades shortly after you rise above 50% HP"),
    APEX_PREDATOR("apex_predator", "Apex Predator", 1, Items.DRAGON_HEAD,
            "PvP kill: Speed II + Regeneration II + Strength I for 15s.", "Cooldown: 45 seconds");

    private final String id;
    private final String displayName;
    private final int unlockRank;
    private final Item icon;
    private final List<String> description;

    AbilityDefinition(String id, String displayName, int unlockRank, Item icon, String... description) {
        this.id = id;
        this.displayName = displayName;
        this.unlockRank = unlockRank;
        this.icon = icon;
        this.description = List.of(description);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public int unlockRank() { return unlockRank; }
    public Item icon() { return icon; }
    public List<String> description() { return description; }

    /** Rank numbers improve toward #1, so #1 can use every ability. */
    public boolean unlockedFor(int rank) {
        return rank >= 1 && rank <= unlockRank;
    }

    public static AbilityDefinition byId(String id) {
        if (id == null) return null;
        String normalized = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(value -> value.id.equals(normalized)).findFirst().orElse(null);
    }

    public static int slotsForRank(int rank) {
        if (rank < 1 || rank > 10) return 0;
        if (rank == 1) return 4;
        if (rank <= 3) return 3;
        if (rank <= 5) return 2;
        return 1;
    }
}
