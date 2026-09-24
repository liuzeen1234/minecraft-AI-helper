---
title: Creeper - Spawning, Explosion Mechanics, Charged Creepers
version: 1.20.4
category: Mobs
keywords: [creeper, explosion, charged creeper, hostile mob, gunpowder]
summary: Creeper spawning, drops, chase/explosion-countdown mechanics, cat-avoidance behavior, and charged creeper transformation and blast strength.
source: Chinese Minecraft Wiki (Fandom mirror), retrieved 2026-09-13; content paraphrased and translated for this reference doc.
---

# Creeper

The **Creeper** is a common hostile mob that quietly approaches players and begins an explosion countdown once within 3 blocks of its target. It's the main source of gunpowder and one way to obtain most music discs.

## Base Attributes

| Attribute | Value |
|---|---|
| Health | 20 (10 hearts) |
| Type | Hostile mob |
| Size (Java Edition) | 1.7 blocks tall, 0.6 blocks wide |
| Size (Bedrock Edition) | 1.8 blocks tall, 0.6 blocks wide |
| Spawn conditions | On spawnable blocks at light level 0 in the Overworld (except mushroom islands and the deep dark) |
| Explosion damage (normal, 100% exposure) | Easy 22.5 / Normal 43 / Hard 64.5 |
| Explosion damage (charged variant, 100% exposure) | Easy 43.5 / Normal 85 / Hard 127.5 |

## Spawning and Drops

- Spawns naturally on solid blocks at light level 0 in the Overworld; in Java Edition it can spawn in groups of up to 4, while in Bedrock Edition it spawns individually.
- Drops:
  - 0–2 gunpowder (+1 per level of Looting, up to 5 with Looting III).
  - When killed by a skeleton/wither skeleton (Java Edition only)/stray, drops a music disc (one of 13, cat, blocks, chirp, far, mall, mellohi, stal, strad, ward, 11, or wait).
  - Drops a creeper head when killed by a charged creeper.
  - Drops 5 experience when killed by a player or a tamed wolf.

## Behavior Mechanics

- Detection range: horizontally 16 blocks (±5%), vertically ±4 blocks from the player. In Java Edition, a player wearing a creeper head has their detection range halved (8 blocks).
- If damaged by another mob, the creeper ignores the player and chases that mob instead.
- Bedrock Edition: if the player is invisible and not wearing any armor, the creeper won't notice the player regardless of distance.
- Has no idle sound or footstep sound, making its approach silent and hard to notice.

### Explosion Countdown

- Begins a 30-game-tick (1.5 second) explosion countdown once within 3 blocks of its target, during which it hisses, swells, and flashes.
- Java Edition: pathfinding stops during the countdown (it doesn't move); Bedrock Edition: it keeps chasing during the countdown.
- If the distance to the target exceeds 7 blocks by the time the countdown finishes (e.g. due to knockback or the target moving away), it won't explode.
  - Java Edition: the countdown ticks back down to 0 over 30 game ticks, then movement resumes.
  - Bedrock Edition: the countdown resets to 0 immediately.
- A creeper with a target can tolerate a fall height of `3 + remaining health - 1`, and will actively jump off ledges as long as the fall isn't fatal.
- After taking fall damage, the explosion countdown immediately increases by `fall damage × 1.5` (capped at 25).
- Can be ignited directly with flint and steel or a fire charge (Java Edition only), starting a non-cancelable 1.5-second countdown.
- Creepers can climb ladders and vines, but won't do so on their own initiative.
- When within 6 blocks of a cat/ocelot and not yet in its explosion countdown, it will flee in a straight line until 16+ blocks away; a creeper already mid-countdown won't flee from a cat unless the player leaves the explosion radius.
- Creeper explosion blast strength is 3. In Java Edition, a creeper with an active status effect will leave behind an area effect cloud with that effect at the explosion site.
- Tamed wolves, iron golems, and zoglins won't attack creepers unprompted; however snow golems, the warden, a vindicator named Johnny, the wither, and shulkers on an opposing team will still attack creepers; goats will randomly ram creepers (without triggering their explosion).

## Charged Creeper

A regular creeper struck by lightning within 4 blocks (natural lightning, a trident with Channeling, a lightning rod redirecting a strike, or via `/summon`) transforms into a charged creeper, visually marked by blue electric arcs.

- Blast strength is 6 (1.5× TNT, 2× a regular creeper).
- Entity data `powered` is `1` (`0` for a regular creeper).
- When it blows up a creeper, skeleton, wither skeleton, zombie, or piglin, that mob drops its corresponding head; in Java Edition, if several are killed in the same blast, only one head drops. The charged creeper itself doesn't drop a head; players and the ender dragon never drop heads.
- Spawn commands:
  - Java Edition: `/summon creeper ~ ~ ~ {powered:1}`
  - Bedrock Edition: `/summon creeper ~ ~ ~ minecraft:become_charged`

## Data Values

| Name | Namespaced ID |
|---|---|
| Creeper | `creeper` |

Common entity data (Java Edition):

- `ExplosionRadius`: blast strength, default 3.
- `Fuse`: time from ignition to explosion (default 30, in game ticks).
- `ignited`: whether it was ignited by a player using an item.
- `powered`: whether it's a charged creeper (may be absent, equivalent to `0`).
