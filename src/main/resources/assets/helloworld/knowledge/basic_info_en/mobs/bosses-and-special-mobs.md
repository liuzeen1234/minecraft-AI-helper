---
title: Bosses and Special Mobs (Ender Dragon, Wither, Warden, Giant)
version: 1.20.4
category: Mobs
keywords: [ender dragon, wither, warden, giant, boss]
summary: Base info, combat mechanics, drops, and summoning methods for the three major bosses and other special mobs.
---

# Bosses and Special Mobs

> Target version: Java Edition 1.20.4.

## Ender Dragon `ender_dragon`
- Appearance: black dragon with glowing purple eyes, huge wingspan (roughly 8 blocks long).
- Health **200**; spawns in the End (as part of the Ender Dragon fight).
- Behavior and mechanics:
  - Heals by flying around end crystals (the dragon can't truly die until the crystals are destroyed); rams players and blocks (deals 10 damage and knocks back).
  - Breathes **dragon fireballs** (destroy blocks, deal damage).
  - Immune to fire, lava, and projectiles (arrows have no effect); only takes melee damage and block-collision damage (the collision damage source is "player movement").
  - Does it summon endermen as guards? No (endermen simply spawn naturally in the End).
- Drops and rewards:
  - First kill: **12000 experience**, a dragon egg (spawns on top of the exit portal), and a large burst of experience orbs on death; 500 experience on subsequent kills.
  - After death, an End gateway spawns (leads to the outer islands).
- Summoning/respawning: placing 4 end crystals on the exit portal respawns the ender dragon (killing the respawned dragon again only grants 500 experience).

## Wither `wither`
- Appearance: three-headed black skeletal beast (about 3.5 blocks tall), surrounded by black particles, has a health bar (purple, boss bar).
- Health **300** (Java Edition; Bedrock Edition displays 600 differently).
- Summoning: 4 soul sand in a T-shape + 3 wither skeleton skulls.
- Behavior and mechanics:
  - Causes a large explosion upon spawning; fires **wither skulls** (explode and destroy blocks); blue skulls can destroy obsidian (but not bedrock).
  - Attacks inflict the **Wither** effect (damage over time, black potion particles); immune to fire, lava, and most damage types; not immune to water (will submerge/dive).
  - Gains "armor" at half health (150) (becomes immune to arrows, though blue skulls still work).
  - Rages and attacks nearby mobs (except the undead).
- Drops: 1 nether star (its only source; used for beacons/nether star-based crafting).
- Experience: 50.

## Warden `warden`
- Appearance: dark blue giant humanoid (about 3 blocks tall), glowing chest, long arms, spawns in the deep dark.
- Health **500** (one of the highest mob health totals in the game, 500 in Java Edition).
- Spawning: spawns after a sculk shrieker in the deep dark is activated 4 times (requires no warden already nearby); can also be spawned via spawn egg or command.
- Behavior and mechanics:
  - **Blind**: relies entirely on sound and vibrations to locate targets (sniffing, listening); can detect vibrations (the sculk sensor mechanic).
  - Extremely high attack damage: Easy 16, Normal 30, Hard 45; melee attacks can hit multiple times.
  - A ranged "sonic boom" attack (a shockwave that ignores armor, roughly 10 damage).
  - Will dig through blocks and jump; **won't attack a sneaking player who isn't generating vibrations** (sneaking + moving slowly can avoid detection).
  - Drops nothing (only grants 5 experience when killed).
- Traits: immune to fire/lava; ignores most damage reduction; relentlessly pursues sound sources.

## Giant `giant`
- Appearance: oversized zombie (about 12 blocks tall).
- Health 100; **can only be spawned via commands** (`/summon giant`).
- Behavior: has no AI goal system (doesn't attack, doesn't move, essentially dormant).
- Drops: none.

## Boss Fight Quick-Reference

| Mob | Health | Spawn method | Key mechanics | Drops |
|---|---|---|---|---|
| Ender Dragon | 200 | Naturally present in the End | Crystal healing, fireballs, immune to projectiles | Dragon egg (first kill), 12000 XP (first kill) / 500 XP |
| Wither | 300 | Player-summoned | Wither skulls, Wither effect, half-health armor | Nether star |
| Warden | 500 | Deep dark shriekers | Sonic boom, vibration detection, no drops | 5 XP |
