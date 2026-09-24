package com.chillzone.combat;

import com.combat.CombatMod;
import com.combat.ConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Chill Zone rank perks/abilities. All timers are server-time millis and survive relogs. */
public final class RankAbilities {
    private static final Identifier FALL_REDUCTION_ID = Identifier.parse("chillzone:rank_fall_reduction");
    private static final Identifier KNOCKBACK_REDUCTION_ID = Identifier.parse("chillzone:rank_knockback_resistance");

    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Float> LAST_HEALTH = new HashMap<>();
    private static final Map<UUID, Long> LAST_SEEN_TICK = new HashMap<>();
    private static final Map<UUID, Integer> LAST_ANNOUNCED_RANK = new HashMap<>();
    private static final Map<UUID, AbsorptionGrant> ABSORPTION = new HashMap<>();

    private record AbsorptionGrant(float healthPoints, long expiresAt) {}

    private RankAbilities() {}

    public static boolean abilitiesEnabled() {
        return ConfigManager.getConfig().rankedSystemEnabled && ConfigManager.getConfig().topRanksEffectsEnabled;
    }

    public static boolean hasPerk(ServerPlayer player, int unlockRank) {
        if (!abilitiesEnabled()) return false;
        int rank = RankManager.getRank(player.getUUID());
        return rank >= 1 && rank <= unlockRank;
    }

    public static void tickPlayer(ServerPlayer player) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        int rank = RankManager.getRank(id);

        announceIfNeeded(player, rank, now);
        maintainAbsorption(player, now);
        updatePassiveAttributes(player, rank);

        if (!abilitiesEnabled() || rank < 1 || rank > 10 || player.isDeadOrDying()) {
            LAST_HEALTH.put(id, player.getHealth());
            return;
        }

        maintainPermanentTopThreeEffects(player, rank);
        applyGuardAfterPvPHit(player, rank, now);
        applyLowHealthAbilities(player, rank, now);
        LAST_HEALTH.put(id, player.getHealth());
    }

    /** Called after Combat's rank swap/assignment logic completes for a genuine PvP kill. */
    public static void onPvPKill(ServerPlayer killer) {
        if (!abilitiesEnabled()) return;
        int rank = RankManager.getRank(killer.getUUID());
        if (rank < 1 || rank > 10) return;
        long now = System.currentTimeMillis();

        // #10 unlock: all ranked players keep the kill-speed perk as they climb.
        if (rank <= 10 && ready(killer.getUUID(), "kill_speed", now, 30_000L)) {
            killer.addEffect(new MobEffectInstance(MobEffects.SPEED, 5 * 20, 0, false, false, true));
        }

        // Kill-heal family upgrades rather than stacking duplicate lower-tier heals.
        if (rank == 1) {
            if (ready(killer.getUUID(), "kill_heal", now, 30_000L)) {
                killer.heal(10.0F); // 5 hearts
                killer.sendSystemMessage(Component.literal("Champion kill perk: restored 5 hearts.")
                        .withStyle(ChatFormatting.GOLD));
            }
        } else if (rank <= 3) {
            if (ready(killer.getUUID(), "kill_heal", now, 30_000L)) {
                killer.heal(4.0F); // 2 hearts
                grantAbsorption(killer, 10.0F, 10_000L); // 5 hearts
                killer.sendSystemMessage(Component.literal("Rank kill perk: +2 hearts and +5 absorption hearts for 10s.")
                        .withStyle(ChatFormatting.GOLD));
            }
        } else if (rank <= 6) {
            if (ready(killer.getUUID(), "kill_heal", now, 30_000L)) {
                killer.heal(4.0F); // 2 hearts
                killer.sendSystemMessage(Component.literal("Rank kill perk: restored 2 hearts.")
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }

    private static void maintainPermanentTopThreeEffects(ServerPlayer player, int rank) {
        // Refreshed short-duration effects behave as permanent while eligible and
        // naturally expire shortly after a rank/settings change without deleting
        // unrelated long-duration potion effects.
        if (player.tickCount % 100 != 0) return;

        if (rank <= 3) {
            refreshEffect(player, MobEffects.FIRE_RESISTANCE, 0, 300);
        }
        if (rank == 2) {
            refreshEffect(player, MobEffects.SPEED, 0, 300); // Speed I
        } else if (rank == 1) {
            refreshEffect(player, MobEffects.SPEED, 1, 300); // Speed II
        }
    }

    private static void refreshEffect(ServerPlayer player,
                                      net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                                      int amplifier,
                                      int duration) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getAmplifier() < amplifier || current.getDuration() <= 120) {
            player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, true));
        }
    }

    private static void updatePassiveAttributes(ServerPlayer player, int rank) {
        boolean enabled = abilitiesEnabled();
        setAttributeModifier(player.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER), FALL_REDUCTION_ID,
                enabled && rank >= 1 && rank <= 9 ? -0.10D : 0.0D);
        setAttributeModifier(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KNOCKBACK_REDUCTION_ID,
                enabled && rank >= 1 && rank <= 7 ? 0.05D : 0.0D);
    }

    private static void setAttributeModifier(AttributeInstance attribute, Identifier id, double desired) {
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(id);
        if (desired == 0.0D) {
            if (current != null) attribute.removeModifier(id);
            return;
        }
        if (current != null && Double.compare(current.amount(), desired) == 0) return;
        if (current != null) attribute.removeModifier(id);
        attribute.addPermanentModifier(new AttributeModifier(id, desired, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void applyLowHealthAbilities(ServerPlayer player, int rank, long now) {
        float fraction = player.getMaxHealth() <= 0.0F ? 1.0F : player.getHealth() / player.getMaxHealth();
        UUID id = player.getUUID();

        // #4 unlock remains cumulative for Top 4.
        if (rank <= 4 && fraction < 0.50F && ready(id, "below_50_absorption", now, 45_000L)) {
            grantAbsorption(player, 10.0F, 10_000L); // 5 hearts
            player.sendSystemMessage(Component.literal("Rank perk activated: +5 absorption hearts for 10s.")
                    .withStyle(ChatFormatting.AQUA));
        }

        // #5 unlock remains cumulative for Top 5.
        if (rank <= 5 && fraction < 0.40F && ready(id, "below_40_rush", now, 30_000L)) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 10 * 20, 0, false, false, true));
            grantAbsorption(player, 6.0F, 10_000L); // 3 hearts; won't lower a stronger active shield
            player.sendSystemMessage(Component.literal("Rank perk activated: Speed I + 3 absorption hearts for 10s.")
                    .withStyle(ChatFormatting.AQUA));
        }

        // #1 is the upgraded version of the Top-2 emergency effect; do not double-trigger #2.
        if (rank == 1 && fraction < 0.35F && ready(id, "top_emergency", now, 60_000L)) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 10 * 20, 1, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 10 * 20, 0, false, false, true));
            player.sendSystemMessage(Component.literal("Champion's Resolve: Speed II + Resistance I for 10s.")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else if (rank == 2 && fraction < 0.25F && ready(id, "top_emergency", now, 45_000L)) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 10 * 20, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 10 * 20, 0, false, false, true));
            player.sendSystemMessage(Component.literal("Last Stand: Speed I + Resistance I for 10s.")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }
    }

    /** Approximate the #4 Guard from the actual health lost on a significant PvP hit. */
    private static void applyGuardAfterPvPHit(ServerPlayer player, int rank, long now) {
        if (rank > 4) return;
        UUID id = player.getUUID();
        Float previous = LAST_HEALTH.get(id);
        if (previous == null) return;
        float lost = previous - player.getHealth();
        if (lost < 2.0F) return; // "significant" = at least one full heart of real health lost

        UUID attackerId = CombatMod.lastAttackerMap.get(id);
        if (attackerId == null || attackerId.equals(id)) return;
        MinecraftServer server = CombatMod.serverInstance;
        if (server == null || server.getPlayerList().getPlayer(attackerId) == null) return;

        if (ready(id, "guard", now, 30_000L)) {
            float refund = lost * 0.15F;
            player.heal(refund);
            player.sendSystemMessage(Component.literal("Guard reduced that PvP hit by 15%.")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    private static void grantAbsorption(ServerPlayer player, float healthPoints, long durationMs) {
        UUID id = player.getUUID();
        long expires = System.currentTimeMillis() + durationMs;
        float current = player.getAbsorptionAmount();
        float target = Math.max(current, healthPoints);
        player.setAbsorptionAmount(target);

        AbsorptionGrant old = ABSORPTION.get(id);
        if (old == null || healthPoints >= old.healthPoints || expires > old.expiresAt) {
            ABSORPTION.put(id, new AbsorptionGrant(Math.max(healthPoints, old == null ? 0.0F : old.healthPoints),
                    Math.max(expires, old == null ? 0L : old.expiresAt)));
        }
    }

    private static void maintainAbsorption(ServerPlayer player, long now) {
        AbsorptionGrant grant = ABSORPTION.get(player.getUUID());
        if (grant == null || now < grant.expiresAt) return;
        // Only remove up to the amount our perk supplied. Damage may already have consumed it.
        player.setAbsorptionAmount(Math.max(0.0F, player.getAbsorptionAmount() - grant.healthPoints));
        ABSORPTION.remove(player.getUUID());
    }

    private static boolean ready(UUID id, String key, long now, long cooldownMs) {
        Map<String, Long> player = COOLDOWNS.computeIfAbsent(id, ignored -> new HashMap<>());
        long readyAt = player.getOrDefault(key, 0L);
        if (now < readyAt) return false;
        player.put(key, now + cooldownMs);
        return true;
    }

    private static void announceIfNeeded(ServerPlayer player, int rank, long now) {
        UUID id = player.getUUID();
        long previousTick = LAST_SEEN_TICK.getOrDefault(id, 0L);
        int previousRank = LAST_ANNOUNCED_RANK.getOrDefault(id, Integer.MIN_VALUE);
        boolean rejoined = now - previousTick > 5_000L;
        boolean changed = previousRank != rank;
        LAST_SEEN_TICK.put(id, now);

        if (rank < 1 || rank > 10 || (!rejoined && !changed)) {
            LAST_ANNOUNCED_RANK.put(id, rank);
            return;
        }

        LAST_ANNOUNCED_RANK.put(id, rank);
        sendRankSummary(player, rank);
    }

    public static void sendRankSummary(ServerPlayer player, int rank) {
        player.sendSystemMessage(Component.literal("PvP Rank #" + rank + " — " + heartsForRank(rank) + " hearts")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        String permanent = permanentForRank(rank);
        if (!permanent.isEmpty()) {
            player.sendSystemMessage(Component.literal("Permanent: " + permanent).withStyle(ChatFormatting.RED));
        }
        player.sendSystemMessage(Component.literal("Rank ability: " + featuredAbility(rank)).withStyle(ChatFormatting.AQUA));
        if (!ConfigManager.getConfig().topRanksEffectsEnabled) {
            player.sendSystemMessage(Component.literal("Rank abilities/effects are currently disabled by the server setting.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static String heartsForRank(int rank) {
        return String.format(java.util.Locale.ROOT, "%.1f", 10.0D + (11 - rank) * 0.5D);
    }

    private static String permanentForRank(int rank) {
        return switch (rank) {
            case 1 -> "Fire Resistance + Speed II";
            case 2 -> "Fire Resistance + Speed I";
            case 3 -> "Fire Resistance";
            default -> "";
        };
    }

    private static String featuredAbility(int rank) {
        return switch (rank) {
            case 10 -> "PvP kill gives Speed I for 5s (30s cooldown).";
            case 9 -> "10% less fall damage.";
            case 8 -> "15% less sprint exhaustion/hunger.";
            case 7 -> "5% knockback resistance.";
            case 6 -> "PvP kill restores 2 hearts (30s cooldown).";
            case 5 -> "Below 40% HP: Speed I + 3 absorption hearts for 10s (30s cooldown).";
            case 4 -> "Guard: 15% refund on a significant PvP hit (30s) + below 50% HP gives 5 absorption hearts for 10s (45s).";
            case 3 -> "PvP kill restores 2 hearts + gives 5 absorption hearts for 10s (30s cooldown).";
            case 2 -> "Below 25% HP: Speed I + Resistance I for 10s (45s cooldown).";
            case 1 -> "Below 35% HP: Speed II + Resistance I for 10s (60s); PvP kill restores 5 hearts (30s).";
            default -> "Unranked.";
        };
    }
}
