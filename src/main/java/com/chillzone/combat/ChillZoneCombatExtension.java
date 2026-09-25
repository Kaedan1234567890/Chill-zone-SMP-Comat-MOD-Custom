package com.chillzone.combat;

import com.combat.gui.CombatMenuGui;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Chill Zone-owned layer inside the single Combat fork JAR.
 *
 * The original MIT Combat classes stay in the same JAR for features that have
 * not been ported yet; this entrypoint owns the Top-10 admin/persistence layer.
 */
public final class ChillZoneCombatExtension implements ModInitializer {
    private static final ExclusionStore EXCLUSIONS = new ExclusionStore();
    private static final NametagSettings NAMETAGS = new NametagSettings();
    private static final RankBackupStore RANK_BACKUP = new RankBackupStore();
    private static final KnownPlayerStore KNOWN_PLAYERS = new KnownPlayerStore();
    private static int housekeepingTicks;
    private static int persistenceTicks;

    private static final SuggestionProvider<CommandSourceStack> REMEMBERED_PLAYERS = (ctx, builder) -> {
        String typed = builder.getRemaining();
        Set<String> names = new LinkedHashSet<>();
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            String name = player.getGameProfile().name();
            if (name.regionMatches(true, 0, typed, 0, typed.length())) names.add(name);
        }
        names.addAll(KNOWN_PLAYERS.namesStartingWith(typed));
        for (String name : names) builder.suggest(name);
        return builder.buildFuture();
    };

    @Override
    public void onInitialize() {
        EXCLUSIONS.load();
        NAMETAGS.load();
        RANK_BACKUP.load();
        KNOWN_PLAYERS.loadLocal();
        RankManager.initialize(EXCLUSIONS, NAMETAGS, RANK_BACKUP);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("pvprank")
                        .requires(Permissions::canAdmin)
                        .executes(ctx -> status(ctx.getSource()))
                        .then(Commands.literal("menu")
                                .executes(ctx -> openMenu(ctx.getSource())))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(REMEMBERED_PLAYERS)
                                        .then(Commands.argument("rank", IntegerArgumentType.integer(1, 10))
                                                .executes(ctx -> setRank(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "rank"))))))
                        .then(Commands.literal("take")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(REMEMBERED_PLAYERS)
                                        .executes(ctx -> takeRank(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(REMEMBERED_PLAYERS)
                                        .executes(ctx -> takeRank(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(REMEMBERED_PLAYERS)
                                        .executes(ctx -> resetPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.literal("swap")
                                .then(Commands.argument("player1", StringArgumentType.word()).suggests(REMEMBERED_PLAYERS)
                                        .then(Commands.argument("player2", StringArgumentType.word()).suggests(REMEMBERED_PLAYERS)
                                                .executes(ctx -> swap(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player1"),
                                                        StringArgumentType.getString(ctx, "player2"))))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listRanks(ctx.getSource())))
                        .then(Commands.literal("nametag")
                                .executes(ctx -> nametagStatus(ctx.getSource()))
                                .then(Commands.literal("status").executes(ctx -> nametagStatus(ctx.getSource())))
                                .then(Commands.literal("toggle").executes(ctx -> toggleNametag(ctx.getSource())))
                                .then(Commands.literal("compact").executes(ctx -> setNametag(ctx.getSource(), true)))
                                .then(Commands.literal("full").executes(ctx -> setNametag(ctx.getSource(), false))))
                        .then(Commands.literal("resetall")
                                .then(Commands.literal("confirm").executes(ctx -> resetAll(ctx.getSource()))))
                ));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            KNOWN_PLAYERS.bootstrapAfterCombatLoad(server);

            // ranks-backup.json is the authoritative safety copy. Rank changes now
            // update it immediately, so a reset/defaulted combat_data.json must not
            // silently replace a known-good Top 10 on startup.
            var live = RankManager.snapshotTop10();
            var saved = RANK_BACKUP.snapshot();
            if (RANK_BACKUP.hasRanks() && !live.equals(saved)) {
                RankManager.restoreTop10(saved);
                System.out.println("[ChillZoneCombat] Restored the protected Top 10 ranking list.");
            } else if (!RANK_BACKUP.hasSnapshot() || (!live.isEmpty() && !RANK_BACKUP.hasRanks())) {
                RANK_BACKUP.replace(live);
            }

            RankManager.enforceExclusions();
            RankManager.refreshNametags(server);
            CombatPersistence.flushAndSnapshot();
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Fast housekeeping: nametags and exclusions.
            if (++housekeepingTicks >= 20) {
                housekeepingTicks = 0;
                boolean changed = RankManager.enforceExclusions();
                KNOWN_PLAYERS.rememberOnline(server);
                RankManager.refreshNametags(server);
                if (changed) RankManager.refreshNametags(server);
            }

            // Every five seconds, force the original Combat config/data managers
            // to disk and update atomic safety copies. This protects GUI settings
            // even if a host restarts shortly after a menu change.
            if (++persistenceTicks >= 100) {
                persistenceTicks = 0;
                CombatPersistence.flushAndSnapshot();
                // Do NOT overwrite ranks-backup.json from a periodic snapshot.
                // The protected Top 10 is updated only by intentional rank
                // mutations, so a reset/defaulted live DataManager cannot erase it.
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            KNOWN_PLAYERS.rememberOnline(server);
            // Do not replace the protected Top 10 during shutdown. If live
            // Combat data was reset/corrupted, shutdown must not bless that bad
            // state as the new backup. Intentional rank mutations save it already.
            CombatPersistence.flushAndSnapshot();
        });
    }

    private static Optional<RankManager.KnownPlayer> resolve(CommandSourceStack source, String name) {
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (player.getGameProfile().name().equalsIgnoreCase(name)) {
                return Optional.of(new RankManager.KnownPlayer(player.getUUID(), player.getGameProfile().name()));
            }
        }
        Optional<RankManager.KnownPlayer> remembered = KNOWN_PLAYERS.findByName(name);
        if (remembered.isPresent()) return remembered;
        return RankManager.findByName(name);
    }

    private static int openMenu(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            new CombatMenuGui(player).open();
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("The Combat settings menu can only be opened by an in-game OP."));
            return 0;
        }
    }

    private static int setRank(CommandSourceStack source, String name, int rank) {
        Optional<RankManager.KnownPlayer> target = resolve(source, name);
        if (target.isEmpty()) return unknown(source, name);
        RankManager.KnownPlayer player = target.get();
        RankManager.setRank(player.uuid(), player.name(), rank);
        RANK_BACKUP.replace(RankManager.snapshotTop10());
        RankManager.refreshNametags(source.getServer());
        source.sendSuccess(() -> Component.literal("Set " + player.name() + " to PvP Rank #" + rank + "."), false);
        return 1;
    }

    private static int takeRank(CommandSourceStack source, String name) {
        Optional<RankManager.KnownPlayer> target = resolve(source, name);
        if (target.isEmpty()) return unknown(source, name);
        RankManager.KnownPlayer player = target.get();
        int old = RankManager.takeRank(player.uuid());
        RANK_BACKUP.replace(RankManager.snapshotTop10());
        RankManager.refreshNametags(source.getServer());
        source.sendSuccess(() -> Component.literal(old >= 1
                ? "Removed " + player.name() + " from PvP Rank #" + old + ". That slot is now open."
                : player.name() + " is now kept unranked."), false);
        return 1;
    }

    private static int resetPlayer(CommandSourceStack source, String name) {
        Optional<RankManager.KnownPlayer> target = resolve(source, name);
        if (target.isEmpty()) return unknown(source, name);
        RankManager.KnownPlayer player = target.get();
        int rank = RankManager.resetPlayer(player.uuid(), player.name());
        RANK_BACKUP.replace(RankManager.snapshotTop10());
        RankManager.refreshNametags(source.getServer());
        source.sendSuccess(() -> Component.literal(rank >= 1
                ? "Re-enabled " + player.name() + " and placed them in the first open slot: #" + rank + "."
                : "Re-enabled " + player.name() + ", but the Top 10 is currently full."), false);
        return 1;
    }

    private static int swap(CommandSourceStack source, String firstName, String secondName) {
        Optional<RankManager.KnownPlayer> first = resolve(source, firstName);
        Optional<RankManager.KnownPlayer> second = resolve(source, secondName);
        if (first.isEmpty()) return unknown(source, firstName);
        if (second.isEmpty()) return unknown(source, secondName);
        RankManager.swap(first.get().uuid(), second.get().uuid());
        RANK_BACKUP.replace(RankManager.snapshotTop10());
        RankManager.refreshNametags(source.getServer());
        source.sendSuccess(() -> Component.literal("Swapped the PvP ranks of " + first.get().name()
                + " and " + second.get().name() + "."), false);
        return 1;
    }

    private static int listRanks(CommandSourceStack source) {
        Map<Integer, RankManager.KnownPlayer> ranks = RankManager.rankedPlayers();
        source.sendSystemMessage(Component.literal("--- Chill Zone PvP Top 10 ---"));
        for (int rank = 1; rank <= 10; rank++) {
            RankManager.KnownPlayer player = ranks.get(rank);
            source.sendSystemMessage(Component.literal("#" + rank + " - " + (player == null ? "EMPTY" : player.name())));
        }
        return 1;
    }

    private static int status(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal("PvP Rank Admin: OP-only | Nametag: "
                + (RankManager.isCompactNametagMode() ? "COMPACT [#1]" : "FULL [Rank #1]")));
        source.sendSystemMessage(Component.literal("Use /pvprank menu for the Combat settings menu."));
        return 1;
    }

    private static int nametagStatus(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal("PvP nametag style: "
                + (RankManager.isCompactNametagMode() ? "COMPACT ([#2])" : "FULL ([Rank #2])")));
        return 1;
    }

    private static int toggleNametag(CommandSourceStack source) {
        return setNametag(source, !RankManager.isCompactNametagMode());
    }

    private static int setNametag(CommandSourceStack source, boolean compact) {
        RankManager.setCompactNametagMode(compact);
        source.sendSuccess(() -> Component.literal("PvP nametags are now "
                + (compact ? "COMPACT ([#2])." : "FULL ([Rank #2]).")), false);
        return 1;
    }

    private static int resetAll(CommandSourceStack source) {
        RankManager.clearAllRanks();
        RANK_BACKUP.clear();
        RankManager.refreshNametags(source.getServer());
        source.sendSuccess(() -> Component.literal("Cleared all PvP ranks. Automatic assignment can begin again."), false);
        return 1;
    }

    private static int unknown(CommandSourceStack source, String name) {
        source.sendFailure(Component.literal("I do not remember a player named '" + name + "'."));
        return 0;
    }
}
