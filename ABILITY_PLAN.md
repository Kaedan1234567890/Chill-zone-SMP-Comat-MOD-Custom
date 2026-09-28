# Chill Zone Combat 1.4.0-alpha — Selectable Rank Abilities Prototype

Rank health remains automatic and unchanged:
- #10: 10.5 hearts
- #9: 11 hearts
- #8: 11.5 hearts
- #7: 12 hearts
- #6: 12.5 hearts
- #5: 13 hearts
- #4: 13.5 hearts
- #3: 14 hearts
- #2: 14.5 hearts
- #1: 15 hearts

Permanent Top-3 effects also remain automatic:
- #3: Fire Resistance
- #2: Fire Resistance + Speed I
- #1: Fire Resistance + Speed II

## Ability slots
- #10–#6: 1 slot
- #5–#4: 2 slots
- #3–#2: 3 slots
- #1: 4 slots
- Unranked: 0 slots (menu is view-only)

## Ability unlock pool
Abilities carry upward. Example: Rank #1 can choose from the entire list.

### Rank #10
- First Blood — PvP kill: Speed I + Regeneration I for 8s. 30s cooldown.
- Featherstep — 30% reduced fall damage.

### Rank #9
- Runner's Instinct — 20% reduced sprint/sprint-jump hunger exhaustion.
- Steadfast — 10% knockback resistance.

### Rank #8
- Hunter's Recovery — PvP kill restores 2.5 hearts. 30s cooldown.
- Adrenaline Rush — PvP kill gives Speed II for 8s. 30s cooldown.

### Rank #7
- Fireborn — Fire Resistance while equipped.
- Last Stand — below 35% HP: Resistance I for 10s. 45s cooldown.

### Rank #6
- Absorption Guard — below 50% HP: +3 absorption hearts for 15s. 45s cooldown.
- Iron Heart — below 40% HP: Regeneration II for 10s. 45s cooldown.

### Rank #5
- Berserker — below 40% HP: Strength I + Speed I for 15s. 30s cooldown.
- Blood Feast — PvP kill: +3 hearts, +4 absorption hearts for 15s, Strength I for 30s. 30s cooldown.

### Rank #4
- Revenge — significant PvP hit: heal 15% of that hit + Resistance I for 15s. 30s cooldown.
- Vampiric Strike — PvP kill restores 5 hearts. 30s cooldown.

### Rank #3
- Second Wind — below 30% HP: Speed II + Resistance II + Regeneration III for 20s. 60s cooldown.
- Warrior's Momentum — PvP kill: Strength I + Speed II for 15s. 30s cooldown.

### Rank #2
- King's Wrath — below 35% HP: Strength II + Resistance II for 20s. 60s cooldown.
- Champion's Feast — PvP kill: +5 hearts and +5 absorption hearts for 15s. 45s cooldown.

### Rank #1
- Dominance — PvP kill: Strength II for 15s. 45s cooldown.
- Royal Guard — Resistance II while below 50% HP; fades after rising above 50%.
- Apex Predator — PvP kill: Speed II + Regeneration II + Strength I for 15s. 45s cooldown.

## GUI
Player command: `/ranked abilities`
Aliases: `/ranked ability` and `/ranked`

The menu is a 54-slot double chest with multiple pages.
- Ability item: hover for description, cooldown/passive info, and required rank.
- Green status pane: equipped.
- Yellow status pane: available to equip.
- Red status pane: locked or no free slots.
- Bottom row: Previous / Loadout Info / Next.
- Players cannot alter a loadout while combat-tagged.
- Loadouts save to `config/chillzone-combat/ability-loadouts.json`.
- If rank drops, abilities that are no longer legal are automatically unequipped.
