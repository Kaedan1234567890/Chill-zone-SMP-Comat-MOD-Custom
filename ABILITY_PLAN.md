# Chill Zone PvP Rank Ability Plan — implemented target

## Health progression

| Rank | Max hearts |
|---|---:|
| #10 | 10.5 |
| #9 | 11.0 |
| #8 | 11.5 |
| #7 | 12.0 |
| #6 | 12.5 |
| #5 | 13.0 |
| #4 | 13.5 |
| #3 | 14.0 |
| #2 | 14.5 |
| #1 | 15.0 |

## Permanent Top-3 effects

- **#3:** permanent Fire Resistance.
- **#2:** permanent Fire Resistance + Speed I.
- **#1:** permanent Fire Resistance + Speed II.

These use short server-refreshed effect instances so they naturally disappear after losing the rank or disabling rank effects, without aggressively deleting unrelated potion effects.

## Rank perks / featured abilities

- **#10:** PvP kill -> Speed I for 5 seconds. 30-second cooldown.
- **#9:** 10% less fall damage.
- **#8:** 15% less sprint movement exhaustion/hunger.
- **#7:** 5% knockback resistance.
- **#6:** PvP kill -> restore 2 hearts. 30-second cooldown.
- **#5:** below 40% health -> Speed I + 3 absorption hearts for 10 seconds. 30-second cooldown.
- **#4:** significant PvP hit -> 15% damage refund. 30-second cooldown. Also, below 50% health -> 5 absorption hearts for 10 seconds. 45-second cooldown.
- **#3:** PvP kill -> restore 2 hearts + 5 absorption hearts for 10 seconds. 30-second cooldown.
- **#2:** below 25% health -> Speed I + Resistance I for 10 seconds. 45-second cooldown.
- **#1:** below 35% health -> Speed II + Resistance I for 10 seconds. 60-second cooldown. Separately, PvP kill -> restore 5 hearts. 30-second cooldown.

Lower passive unlocks carry upward. Same-family kill-heal upgrades replace the weaker heal so #1 does not stack every heal tier at once.

## Player visibility / explanation

A ranked player gets a concise PvP-rank summary on join and whenever their rank changes. It includes max hearts, permanent Top-3 effects (if applicable), and the featured custom ability for that rank. This does not add a public admin command; `/pvprank` remains OP/server-console only.
