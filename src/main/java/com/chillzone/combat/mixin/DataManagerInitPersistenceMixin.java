package com.chillzone.combat.mixin;

import com.chillzone.combat.CombatPersistence;
import com.combat.DataManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

/** Restores Combat player data at the exact moment DataManager initializes. */
@Mixin(value = DataManager.class, remap = false)
public abstract class DataManagerInitPersistenceMixin {
    @Inject(method = "init", at = @At("HEAD"), remap = false)
    private static void chillzone$restoreDataBeforeInit(File directory, CallbackInfo ci) {
        CombatPersistence.restoreDataBeforeInit(directory);
    }
}
