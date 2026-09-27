package com.chillzone.combat.mixin;

import com.chillzone.combat.CombatState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

/**
 * Central combat-command guard.
 *
 * The Combat mod is the authority for whether a player is combat-tagged, so
 * teleport/escape commands are blocked here rather than each separate mod
 * maintaining its own combat timer.
 */
@Mixin(Commands.class)
public abstract class CombatCommandRestrictionMixin {

    @Inject(method = "performPrefixedCommand", at = @At("HEAD"), cancellable = true)
    private void chillzone$blockEscapeCommandsDuringCombat(
            CommandSourceStack source,
            String command,
            CallbackInfo ci
    ) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!CombatState.isInCombat(player)) {
            return;
        }

        String normalized = command == null ? "" : command.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return;
        }

        String root = normalized.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!isBlockedRoot(root)) {
            return;
        }

        long seconds = CombatState.remainingSeconds(player);
        String timeText = seconds > 0 ? " (" + seconds + "s remaining)" : "";

        source.sendFailure(Component.literal(
                "You are in combat! You cannot use /" + root + " right now." + timeText
        ).withStyle(ChatFormatting.RED));

        ci.cancel();
    }

    private static boolean isBlockedRoot(String root) {
        return root.equals("spawn")
                || root.equals("home")
                || root.equals("homes")
                || root.equals("rtp");
    }
}
