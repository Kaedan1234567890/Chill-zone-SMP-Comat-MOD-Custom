package com.chillzone.combat.mixin;

import com.chillzone.combat.CombatState;
import com.mojang.brigadier.ParseResults;
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
 * IMPORTANT: player-entered commands in Minecraft 26.2 are already parsed by
 * ServerGamePacketListenerImpl and are sent directly to Commands#performCommand.
 * They do NOT necessarily pass through Commands#performPrefixedCommand first.
 * Therefore this guard must intercept performCommand, otherwise /home, /spawn,
 * etc. can bypass the restriction even while CombatState reports the player as
 * tagged.
 */
@Mixin(Commands.class)
public abstract class CombatCommandRestrictionMixin {

    @Inject(
            method = "performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void chillzone$blockEscapeCommandsDuringCombat(
            ParseResults<CommandSourceStack> parseResults,
            String commandString,
            CallbackInfo ci
    ) {
        if (parseResults == null || parseResults.getContext() == null) {
            return;
        }

        CommandSourceStack source = parseResults.getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!CombatState.isInCombat(player)) {
            return;
        }

        String normalized = commandString == null ? "" : commandString.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return;
        }

        String root = normalized.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);

        // Also handle namespaced command roots, e.g. modid:home.
        int namespaceSeparator = root.indexOf(':');
        String bareRoot = namespaceSeparator >= 0 ? root.substring(namespaceSeparator + 1) : root;

        if (!isBlockedRoot(bareRoot)) {
            return;
        }

        long seconds = CombatState.remainingSeconds(player);
        String timeText = seconds > 0 ? " (" + seconds + "s remaining)" : "";

        source.sendFailure(Component.literal(
                "You are in combat! You cannot use /" + bareRoot + " right now." + timeText
        ).withStyle(ChatFormatting.RED));

        ci.cancel();
    }

    private static boolean isBlockedRoot(String root) {
        return root.equals("spawn")
                || root.equals("home")
                || root.equals("rtp");
    }
}
