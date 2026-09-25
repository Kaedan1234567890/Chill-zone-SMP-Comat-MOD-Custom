package com.chillzone.combat.mixin;

import com.chillzone.combat.CombatPersistence;
import com.combat.ConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Immediately mirrors every original Combat settings save to the protected copy. */
@Mixin(value = ConfigManager.class, remap = false)
public abstract class ConfigManagerPersistenceMixin {
    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private static void chillzone$protectCombatSettings(CallbackInfo ci) {
        CombatPersistence.snapshotConfigFile();
    }
}
