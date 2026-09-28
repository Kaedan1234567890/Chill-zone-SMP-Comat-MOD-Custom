package com.chillzone.combat;

import com.combat.CombatMod;
import com.combat.ConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
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

/**
 * Chill Zone selectable rank ability engine.
 * Rank health remains automatic elsewhere. Top-3 permanent effects also remain
 * automatic; this class only makes the configurable perk layer player-selectable.
 */
public final class RankAbilities {
    private static final Identifier FALL_REDUCTION_ID = Identifier.parse("chillzone:ability_fall_reduction");
    private static final Identifier KNOCKBACK_REDUCTION_ID = Identifier.parse("chillzone:ability_knockback_resistance");

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

    public static boolean hasAbility(ServerPlayer player, String abilityId) {
        if (!abilitiesEnabled()) return false;
        int rank = RankManager.getRank(player.getUUID());
        AbilityDefinition definition = AbilityDefinition.byId(abilityId);
        return definition != null
                && definition.unlockedFor(rank)
                && AbilityLoadoutStore.isEquipped(player.getUUID(), abilityId);
    }

    /** Compatibility helper retained for older internal callers. */
    public static boolean hasPerk(ServerPlayer player, int unlockRank) {
        return switch (unlockRank) {
            case 8 -> hasAbility(player, AbilityDefinition.RUNNERS_INSTINCT.id());
            default -> false;
        };
    }

    public static void tickPlayer(ServerPlayer player) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        int rank = RankManager.getRank(id);

        AbilityLoadoutStore.sanitize(id, rank);
        announceIfNeeded(player, rank, now);
        maintainAbsorption(player, now);
        updatePassiveAttributes(player, rank);

        if (!abilitiesEnabled() || rank < 1 || rank > 10 || player.isDeadOrDying()) {
            LAST_HEALTH.put(id, player.getHealth());
            return;
        }

        // These are intentionally NOT selectable. The user requested that the
        // established Top-3 permanent effects stay tied directly to rank.
        maintainPermanentTopThreeEffects(player, rank);

        maintainSelectablePermanentEffects(player);
        applyGuardAfterPvPHit(player, now);
        applyLowHealthAbilities(player, now);
        LAST_HEALTH.put(id, player.getHealth());
    }

    /** Called after Combat's rank swap/assignment logic completes for a genuine PvP kill. */
    public static void onPvPKill(ServerPlayer killer) {
        if (!abilitiesEnabled()) return;
        int rank = RankManager.getRank(killer.getUUID());
        if (rank < 1 || rank > 10) return;
        AbilityLoadoutStore.sanitize(killer.getUUID(), rank);
        long now = System.currentTimeMillis();
        UUID id = killer.getUUID();

        if (hasAbility(killer, AbilityDefinition.FIRST_BLOOD.id()) && ready(id, "first_blood", now, 30_000L)) {
            killer.addEffect(new MobEffectInstance(MobEffects.SPEED, 8 * 20, 0, false, false, true));
            killer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 8 * 20, 0, false, false, true));
            cue(killer, AbilityDefinition.FIRST_BLOOD, false);
        }
        if (hasAbility(killer, AbilityDefinition.HUNTERS_RECOVERY.id()) && ready(id, "hunters_recovery", now, 30_000L)) {
            killer.heal(5.0F);
            cue(killer, AbilityDefinition.HUNTERS_RECOVERY, false);
        }
        if (hasAbility(killer, AbilityDefinition.ADRENALINE_RUSH.id()) && ready(id, "adrenaline_rush", now, 30_000L)) {
            killer.addEffect(new MobEffectInstance(MobEffects.SPEED, 8 * 20, 1, false, false, true));
            cue(killer, AbilityDefinition.ADRENALINE_RUSH, false);
        }
        if (hasAbility(killer, AbilityDefinition.BLOOD_FEAST.id()) && ready(id, "blood_feast", now, 30_000L)) {
            killer.heal(6.0F);
            grantAbsorption(killer, 8.0F, 15_000L);
            killer.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 30 * 20, 0, false, false, true));
            cue(killer, AbilityDefinition.BLOOD_FEAST, true);
        }
        if (hasAbility(killer, AbilityDefinition.VAMPIRIC_STRIKE.id()) && ready(id, "vampiric_strike", now, 30_000L)) {
            killer.heal(10.0F);
            cue(killer, AbilityDefinition.VAMPIRIC_STRIKE, true);
        }
        if (hasAbility(killer, AbilityDefinition.WARRIORS_MOMENTUM.id()) && ready(id, "warriors_momentum", now, 30_000L)) {
            killer.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 15 * 20, 0, false, false, true));
            killer.addEffect(new MobEffectInstance(MobEffects.SPEED, 15 * 20, 1, false, false, true));
            cue(killer, AbilityDefinition.WARRIORS_MOMENTUM, true);
        }
        if (hasAbility(killer, AbilityDefinition.CHAMPIONS_FEAST.id()) && ready(id, "champions_feast", now, 45_000L)) {
            killer.heal(10.0F);
            grantAbsorption(killer, 10.0F, 15_000L);
            cue(killer, AbilityDefinition.CHAMPIONS_FEAST, true);
        }
        if (hasAbility(killer, AbilityDefinition.DOMINANCE.id()) && ready(id, "dominance", now, 45_000L)) {
            killer.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 15 * 20, 1, false, false, true));
            cue(killer, AbilityDefinition.DOMINANCE, true);
        }
        if (hasAbility(killer, AbilityDefinition.APEX_PREDATOR.id()) && ready(id, "apex_predator", now, 45_000L)) {
            killer.addEffect(new MobEffectInstance(MobEffects.SPEED, 15 * 20, 1, false, false, true));
            killer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 15 * 20, 1, false, false, true));
            killer.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 15 * 20, 0, false, false, true));
            cue(killer, AbilityDefinition.APEX_PREDATOR, true);
        }
    }

    private static void maintainPermanentTopThreeEffects(ServerPlayer player, int rank) {
        if (player.tickCount % 100 != 0) return;
        if (rank <= 3) refreshEffect(player, MobEffects.FIRE_RESISTANCE, 0, 300);
        if (rank == 2) refreshEffect(player, MobEffects.SPEED, 0, 300);
        else if (rank == 1) refreshEffect(player, MobEffects.SPEED, 1, 300);
    }

    private static void maintainSelectablePermanentEffects(ServerPlayer player) {
        if (player.tickCount % 40 != 0) return;
        if (hasAbility(player, AbilityDefinition.FIREBORN.id())) {
            refreshEffect(player, MobEffects.FIRE_RESISTANCE, 0, 100);
        }
        // Royal Guard is intentionally conditional rather than a timed proc.
        // A short refreshed instance fades quickly after the player rises above 50%.
        float fraction = player.getMaxHealth() <= 0.0F ? 1.0F : player.getHealth() / player.getMaxHealth();
        if (hasAbility(player, AbilityDefinition.ROYAL_GUARD.id()) && fraction < 0.50F) {
            refreshEffect(player, MobEffects.RESISTANCE, 1, 60);
        }
    }

    private static void refreshEffect(ServerPlayer player,
                                      net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                                      int amplifier,
                                      int duration) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getAmplifier() < amplifier || current.getDuration() <= 40) {
            player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, true));
        }
    }

    private static void updatePassiveAttributes(ServerPlayer player, int rank) {
        boolean enabled = abilitiesEnabled() && rank >= 1 && rank <= 10;
        setAttributeModifier(player.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER), FALL_REDUCTION_ID,
                enabled && hasAbility(player, AbilityDefinition.FEATHERSTEP.id()) ? -0.30D : 0.0D);
        setAttributeModifier(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KNOCKBACK_REDUCTION_ID,
                enabled && hasAbility(player, AbilityDefinition.STEADFAST.id()) ? 0.10D : 0.0D);
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

    private static void applyLowHealthAbilities(ServerPlayer player, long now) {
        float fraction = player.getMaxHealth() <= 0.0F ? 1.0F : player.getHealth() / player.getMaxHealth();
        UUID id = player.getUUID();

        if (hasAbility(player, AbilityDefinition.LAST_STAND.id())
                && fraction < 0.35F && ready(id, "last_stand", now, 45_000L)) {
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 10 * 20, 0, false, false, true));
            cue(player, AbilityDefinition.LAST_STAND, false);
        }
        if (hasAbility(player, AbilityDefinition.ABSORPTION_GUARD.id())
                && fraction < 0.50F && ready(id, "absorption_guard", now, 45_000L)) {
            grantAbsorption(player, 6.0F, 15_000L);
            cue(player, AbilityDefinition.ABSORPTION_GUARD, false);
        }
        if (hasAbility(player, AbilityDefinition.IRON_HEART.id())
                && fraction < 0.40F && ready(id, "iron_heart", now, 45_000L)) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 10 * 20, 1, false, false, true));
            cue(player, AbilityDefinition.IRON_HEART, false);
        }
        if (hasAbility(player, AbilityDefinition.BERSERKER.id())
                && fraction < 0.40F && ready(id, "berserker", now, 30_000L)) {
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 15 * 20, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 15 * 20, 0, false, false, true));
            cue(player, AbilityDefinition.BERSERKER, true);
        }
        if (hasAbility(player, AbilityDefinition.SECOND_WIND.id())
                && fraction < 0.30F && ready(id, "second_wind", now, 60_000L)) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 20, 1, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 20, 1, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 20, 2, false, false, true));
            cue(player, AbilityDefinition.SECOND_WIND, true);
        }
        if (hasAbility(player, AbilityDefinition.KINGS_WRATH.id())
                && fraction < 0.35F && ready(id, "kings_wrath", now, 60_000L)) {
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 20, 1, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 20, 1, false, false, true));
            cue(player, AbilityDefinition.KINGS_WRATH, true);
        }
    }

    /** Revenge triggers only from a significant hit attributed to another online player. */
    private static void applyGuardAfterPvPHit(ServerPlayer player, long now) {
        if (!hasAbility(player, AbilityDefinition.REVENGE.id())) return;
        UUID id = player.getUUID();
        Float previous = LAST_HEALTH.get(id);
        if (previous == null) return;
        float lost = previous - player.getHealth();
        if (lost < 2.0F) return;

        UUID attackerId = CombatMod.lastAttackerMap.get(id);
        if (attackerId == null || attackerId.equals(id)) return;
        MinecraftServer server = CombatMod.serverInstance;
        if (server == null || server.getPlayerList().getPlayer(attackerId) == null) return;

        if (ready(id, "revenge", now, 30_000L)) {
            player.heal(lost * 0.15F);
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 15 * 20, 0, false, false, true));
            cue(player, AbilityDefinition.REVENGE, true);
        }
    }

    private static void cue(ServerPlayer player, AbilityDefinition ability, boolean strong) {
        var level = player.level();
        var particle = switch (ability) {
            case KINGS_WRATH, BERSERKER, DOMINANCE -> ParticleTypes.FLAME;
            case SECOND_WIND, ROYAL_GUARD -> ParticleTypes.SOUL_FIRE_FLAME;
            case BLOOD_FEAST, VAMPIRIC_STRIKE, CHAMPIONS_FEAST -> ParticleTypes.DAMAGE_INDICATOR;
            default -> ParticleTypes.CRIT;
        };
        level.sendParticles(player, particle, true, true,
                player.getX(), player.getY() + 1.0, player.getZ(), strong ? 28 : 14,
                0.45, 0.70, 0.45, 0.02);
        playAbilitySound(player, strong ? "minecraft:item.totem.use" : "minecraft:entity.experience_orb.pickup",
                strong ? 0.9F : 0.55F, strong ? 1.15F : 1.25F);
        player.sendSystemMessage(Component.literal(ability.displayName() + " activated!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
    }

    private static void playAbilitySound(ServerPlayer player, String sound, float volume, float pitch) {
        MinecraftServer server = CombatMod.serverInstance;
        if (server == null) return;
        String position = String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f",
                player.getX(), player.getY(), player.getZ());
        String command = "playsound " + sound + " master " + player.getGameProfile().name() + " " + position
                + " " + volume + " " + pitch + " 1.0";
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
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
        int slots = AbilityDefinition.slotsForRank(rank);
        player.sendSystemMessage(Component.literal("Selectable abilities: " + slots + " slot" + (slots == 1 ? "" : "s")
                + " — use /ranked abilities")
                .withStyle(ChatFormatting.AQUA));
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
}
