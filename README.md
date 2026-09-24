# Chill Zone Combat — Phase 1 fork source

This is the first source pass toward replacing the separate Combat + PvP Rank Admin setup with one maintainable Chill Zone Combat mod.

The uploaded Combat 1.0.0 JAR declares an MIT license. Phase 1 therefore keeps that JAR as the binary baseline while Chill Zone progressively replaces specific classes with maintained source. The final server artifact is ONE `combat` mod JAR, not a second add-on.

## What Phase 1 already changes

- Keeps the original Combat GUI, weapon/item limits, enchant controls, potion controls, combat logging, cooldown controls, world limits, ranked GUI, and other original features by carrying forward the untouched MIT classes.
- Replaces the original ranked-health mixin:
  - #10 = 10.5 hearts
  - #9 = 11 hearts
  - ...
  - #1 = 15 hearts
- Removes the original always-on Top-3 Speed/Strength/Fire Resistance loop so it can be replaced by the custom Chill Zone ability system.
- Keeps Combat's built-in ranked-system ON/OFF setting; no duplicate Chill Zone ON/OFF command is added.
- Replaces Combat's automatic rank assignment/swap methods so the ranking is a real Top 10:
  - automatic assignment checks #1 -> #10 and uses the first empty slot;
  - it never creates #11, #12, etc.;
  - if an OP manually puts somebody at #4 while #1-#3 are empty, those gaps remain available and the next automatic assignment starts at #1.
- Adds `/pvprank take <player>` (and `/pvprank remove <player>` as an alias):
  - removes that player from their rank;
  - leaves the rank slot empty instead of compacting everyone;
  - keeps the removed player unranked until an OP uses `set` or `reset`.
- Adds remembered-player autocomplete using Combat's UUID/player-history data, so previously joined players can be targeted while offline.
- Preserves the existing `config/combat/combat_data.json` format/path so existing remembered players and ranks carry into the fork.
- Adds a second atomic rank backup at `config/chillzone-combat/ranks-backup.json` and can migrate the previous PvP Rank Admin backup.
- Keeps global nametag modes:
  - compact `[#2]`
  - full `[Rank #2]`
  - `Unranked` remains Combat's normal unranked display.
- All `/pvprank` admin commands are OP/server-console only.
- `/pvprank menu` opens the existing Combat ranked GUI.

## Commands in Phase 1

- `/pvprank menu`
- `/pvprank set <remembered-player> <1-10>`
- `/pvprank take <remembered-player>`
- `/pvprank remove <remembered-player>`
- `/pvprank reset <remembered-player>`
- `/pvprank swap <player1> <player2>`
- `/pvprank list`
- `/pvprank nametag`
- `/pvprank nametag compact`
- `/pvprank nametag full`
- `/pvprank nametag toggle`
- `/pvprank resetall confirm`

## Important: not the final server build yet

The custom rank abilities in `ABILITY_PLAN.md` are intentionally the next pass. This Phase 1 source establishes the fork and the ranking/health architecture first. Do not remove your known-good server setup in favor of Phase 1 until its GitHub build succeeds and we finish the ability/menu pass.

## Building on GitHub

Push the contents of this ZIP into a repository. The included GitHub Action runs:

`gradle clean forkJar`

The one-file server artifact will appear in:

`build/fork-libs/chill-zone-combat-1.1.0-chillzone-phase1.jar`

When testing the fork later, remove both the old standalone Combat JAR and the old PvP Rank Admin add-on JAR so there is only one `combat` mod ID on the server.


## Phase 1 Fix 1

- Fixed Gradle 9/Loom task-realization recursion in the custom `forkJar` task by making the `remapJar` archive lookup lazy.
- No gameplay/rank behavior was changed in this fix.


## FIX2 BUILD NOTE

FIX2 removes the Phase 1 build's dependency on a Loom `remapJar` task. The GitHub
workflow now builds `forkJar` from `sourceSets.main.output` plus the original
MIT Combat 1.0.0 JAR. This is required because the current Loom/Minecraft 26.2
project does not expose a `remapJar` task. The final fork manifest remains in the
`official` mapping namespace, matching the original Combat JAR.
