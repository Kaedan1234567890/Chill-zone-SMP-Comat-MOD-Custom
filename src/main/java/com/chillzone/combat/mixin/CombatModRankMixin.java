package com.chillzone.combat.mixin;

import com.chillzone.combat.RankManager;
import com.combat.CombatMod;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only Combat's automatic rank assignment/swap methods. */
@Mixin(value = CombatMod.class, remap = false)
public abstract class CombatModRankMixin {
    @Inject(method = "assignRankIfUnranked", at = @At("HEAD"), cancellable = true, remap = false)
    private static void chillzone$assignRankIfUnranked(ServerPlayer player, CallbackInfo ci) {
        RankManager.assignIfUnranked(player);
        ci.cancel();
    }

    @Inject(method = "handleKill", at = @At("HEAD"), cancellable = true, remap = false)
    private static void chillzone$handleKill(ServerPlayer killer, ServerPlayer victim, CallbackInfo ci) {
        RankManager.handleKill(killer, victim);
        ci.cancel();
    }
}
