package com.chillzone.combat.mixin;

import com.chillzone.combat.RankAbilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Rank #8+ perk: reduce sprint-related exhaustion by 15%.
 *
 * Minecraft 26.2 no longer exposes Player.checkMovementStatistics, so the old
 * injection target crashed at startup. 26.2 still funnels food exhaustion
 * through Player.causeFoodExhaustion(float); modify only the sprint/sprint-jump
 * exhaustion argument while the ranked player is actually sprinting.
 */
@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {
    @ModifyVariable(
            method = "causeFoodExhaustion",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float chillzone$reduceSprintExhaustion(float amount) {
        Object self = this;
        if (!(self instanceof ServerPlayer player)
                || !player.isSprinting()
                || !RankAbilities.hasPerk(player, 8)) {
            return amount;
        }

        // Only touch vanilla's sprint movement costs, not healing/mining/swimming.
        // EXHAUSTION_ATTACK currently shares the same vanilla numeric value as
        // EXHAUSTION_SPRINT; the sprint-state check keeps the scope as narrow as
        // the public 26.2 API allows without injecting into a removed method.
        if (Float.compare(amount, FoodConstants.EXHAUSTION_SPRINT) == 0
                || Float.compare(amount, FoodConstants.EXHAUSTION_SPRINT_JUMP) == 0) {
            return amount * 0.85F;
        }
        return amount;
    }
}
