package com.chillzone.combat.mixin;

import com.chillzone.combat.RankAbilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Rank #8+ perk: reduce movement exhaustion while the player is sprinting. */
@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {
    @ModifyArg(
            method = "checkMovementStatistics",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"),
            index = 0
    )
    private float chillzone$reduceSprintExhaustion(float amount) {
        Object self = this;
        if (self instanceof ServerPlayer player && player.isSprinting() && RankAbilities.hasPerk(player, 8)) {
            return amount * 0.85F;
        }
        return amount;
    }
}
