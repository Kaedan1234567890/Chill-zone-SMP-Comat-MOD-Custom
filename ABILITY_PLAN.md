# Chill Zone PvP Rank Ability Plan

This is the current balance target. Phase 1 establishes the fork, Top-10 data/admin system, rank-gap behavior, and 10.5-to-15-heart progression. The active ability engine is the next implementation pass.

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

## Current perk targets

- **#10:** PvP kill -> Speed I for 5 seconds. 30-second cooldown.
- **#9:** 10% less fall damage.
- **#8:** 15% less hunger/exhaustion caused specifically by sprinting.
- **#7:** 5% knockback resistance.
- **#6:** PvP kill -> restore 2 hearts. 30-second cooldown.
- **#5:** below 40% health -> Speed I + 3 absorption hearts for 10 seconds. 30-second cooldown.
- **#4:** first significant PvP hit -> 15% less damage. 30-second cooldown. Also, below 50% health -> 5 absorption hearts for 10 seconds. Initial test cooldown: 45 seconds.
- **#3:** PvP kill -> restore 2 hearts + 5 absorption hearts for 10 seconds. 30-second cooldown.
- **#2:** below 25% health -> Speed I + Resistance I for 10 seconds. 45-second cooldown.
- **#1:** below 35% health -> Speed II + Resistance I for 10 seconds. 60-second cooldown. Separately, a PvP kill restores 5 hearts with a 30-second cooldown.

## Safeguards planned with the ability engine

- Only genuine player-vs-player kills trigger kill rewards.
- Cooldowns do not reset by logging out/rejoining.
- Low-health abilities trigger once per cooldown rather than every tick below the threshold.
- Temporary absorption cannot be infinitely stacked/refreshed.
- Same-player/alt farming protection can be added once the base ability engine is working.
- Rank changes update health/perks immediately.
- Combat's existing GUI `Top 3 Rank Effects` switch will be renamed/reworked into the global Chill Zone rank-abilities switch while keeping the existing GUI layout.
