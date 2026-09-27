package com.chillzone.combat;

import com.combat.CombatMod;
import net.minecraft.server.level.ServerPlayer;

/**
 * Single Chill Zone API for checking the original Combat mod's live combat tag.
 * Other Chill Zone systems should use this instead of reimplementing timers.
 */
public final class CombatState {
    private CombatState() {}

    public static boolean isInCombat(ServerPlayer player) {
        Long expires = CombatMod.combatTagExpiration.get(player.getUUID());
        if (expires == null) return false;
        long now = System.currentTimeMillis();
        if (now >= expires) {
            CombatMod.combatTagExpiration.remove(player.getUUID());
            return false;
        }
        return true;
    }

    public static long remainingSeconds(ServerPlayer player) {
        Long expires = CombatMod.combatTagExpiration.get(player.getUUID());
        if (expires == null) return 0L;
        long remainingMs = expires - System.currentTimeMillis();
        if (remainingMs <= 0L) {
            CombatMod.combatTagExpiration.remove(player.getUUID());
            return 0L;
        }
        return (remainingMs + 999L) / 1000L;
    }
}
