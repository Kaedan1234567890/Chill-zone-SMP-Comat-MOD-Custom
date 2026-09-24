package com.combat.mixin;

import com.combat.CombatMod;
import com.combat.ConfigManager;
import com.combat.DataManager;
import com.combat.PlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Chill Zone replacement for the original Combat LivingEntity mixin.
 *
 * Phase 1 changes:
 * - Rank #10 starts at 10.5 hearts and each step adds another half-heart.
 * - Rank #1 therefore caps at 15 hearts instead of 20.
 * - The original always-on Top-3 Speed/Strength/Fire Resistance loop is removed;
 *   the topRanksEffectsEnabled switch is reserved for Chill Zone's rank abilities.
 * - Disabling the ranked system removes the rank health modifier immediately.
 *
 * The original combat-tag/lunge UI and Elytra enforcement are preserved.
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    private static final Identifier HEALTH_MODIFIER_ID = Identifier.parse("combat:ranked_health_boost");

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ServerPlayer player)) return;
        if (player.isDeadOrDying() || player.getHealth() <= 0.0F || player.isRemoved()) return;

        UUID uuid = player.getUUID();
        if (player.tickCount % 20 == 0) {
            CombatMod.checkAndEnforceItemLimits(player);
        }

        long now = System.currentTimeMillis();
        Long combatExp = CombatMod.combatTagExpiration.get(uuid);
        Long lungeExp = CombatMod.spearLungeExpiration.get(uuid);
        boolean inCombat = combatExp != null && now < combatExp;
        boolean lungeCooling = lungeExp != null && now < lungeExp;

        if (combatExp != null && now >= combatExp) {
            CombatMod.combatTagExpiration.remove(uuid);
            player.sendSystemMessage(Component.literal("You are no longer in combat!").withStyle(ChatFormatting.GREEN), true);
            inCombat = false;
        }
        if (lungeExp != null && now >= lungeExp) {
            CombatMod.spearLungeExpiration.remove(uuid);
            lungeCooling = false;
        }

        if (inCombat || lungeCooling) {
            Component line = Component.empty();
            if (inCombat) {
                long seconds = (combatExp - now + 999L) / 1000L;
                line = line.copy().append(Component.literal("Combat Tagged: " + seconds + "s")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
            if (lungeCooling) {
                long seconds = (lungeExp - now + 999L) / 1000L;
                if (inCombat) line = line.copy().append(Component.literal("  | ").withStyle(ChatFormatting.DARK_GRAY));
                line = line.copy().append(Component.literal("Lunge Cooldown: " + seconds + "s").withStyle(ChatFormatting.GRAY));
            }
            player.sendSystemMessage(line, true);
        }

        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            if (ConfigManager.getConfig().rankedSystemEnabled) {
                PlayerData data = DataManager.getOrCreatePlayerData(uuid);
                int rank = data.rankPosition;
                if (rank >= 1 && rank <= 10) {
                    // Minecraft health uses 2 points per heart. Adding one health
                    // point per rank step = exactly half a heart per step.
                    double desiredBoost = 11 - rank; // #10 +1 HP ... #1 +10 HP.
                    AttributeModifier current = maxHealth.getModifier(HEALTH_MODIFIER_ID);
                    if (current == null || Double.compare(current.amount(), desiredBoost) != 0) {
                        if (current != null) maxHealth.removeModifier(HEALTH_MODIFIER_ID);
                        maxHealth.addPermanentModifier(new AttributeModifier(
                                HEALTH_MODIFIER_ID,
                                desiredBoost,
                                AttributeModifier.Operation.ADD_VALUE
                        ));
                        if (player.getHealth() > player.getMaxHealth()) {
                            player.setHealth(player.getMaxHealth());
                        }
                    }
                } else {
                    removeHealthModifier(player, maxHealth);
                }
            } else {
                removeHealthModifier(player, maxHealth);
            }
        }

        if (!ConfigManager.getConfig().allowElytraInCombat && inCombat) {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            if (chest.is(Items.ELYTRA)) {
                player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                player.getInventory().add(chest);
                if (!chest.isEmpty()) player.drop(chest, false);
                player.sendSystemMessage(Component.literal("Equipping Elytra is disabled during combat!")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    private static void removeHealthModifier(ServerPlayer player, AttributeInstance maxHealth) {
        if (maxHealth.getModifier(HEALTH_MODIFIER_ID) != null) {
            maxHealth.removeModifier(HEALTH_MODIFIER_ID);
            if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        }
    }
}
