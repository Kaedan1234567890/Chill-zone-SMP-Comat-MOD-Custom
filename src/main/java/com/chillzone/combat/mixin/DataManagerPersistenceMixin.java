package com.chillzone.combat.mixin;

import com.chillzone.combat.CombatPersistence;
import com.combat.DataManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Immediately mirrors every original Combat player/rank save to the protected copy. */
@Mixin(value = DataManager.class, remap = false)
public abstract class DataManagerPersistenceMixin {
    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private static void chillzone$protectCombatData(CallbackInfo ci) {
        CombatPersistence.snapshotDataFile();
    }
}
