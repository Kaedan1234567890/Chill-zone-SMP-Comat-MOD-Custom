package com.chillzone.combat.mixin;

import com.combat.CombatMod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the original Combat mod's /combat command tree.
 *
 * CombatMod still initializes all of its gameplay systems, GUI classes, config,
 * data manager, events, etc. Only its command-registration callback is cancelled.
 * Chill Zone administration remains under /pvprank.
 */
@Mixin(value = CombatMod.class, remap = false)
public abstract class CombatCommandMixin {
    @Inject(method = "lambda$onInitialize$13", at = @At("HEAD"), cancellable = true, remap = false)
    private static void chillzone$disableOriginalCombatCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment,
            CallbackInfo ci) {
        ci.cancel();
    }
}
