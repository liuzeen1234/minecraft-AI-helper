---
title: Entity Overview and Common Mechanics
version: 1.20.4
category: Mobs
keywords: [entity, spawning, despawning, AI, NBT, mob spawning, entity ID]
summary: Entity classification, spawn/despawn rules, common AI and NBT, and a full entity ID index.
---

# Entity Overview and Common Mechanics

> Target version: Java Edition 1.20.4. Entities are divided into mobs (have health, can move) and non-mob entities (projectiles, items, vehicles, etc.).

## Entity Classification

| Category | Description | Examples |
|---|---|---|
| Passive mobs | Never attack players | Cow, pig, chicken, sheep, villager, fish, allay |
| Neutral mobs | Only attack once provoked | Enderman, piglin, wolf, iron golem, bear |
| Hostile mobs | Actively attack players | Zombie, skeleton, creeper, spider |
| Utility mobs | Player-created guardian/helper units | Iron golem, snow golem, allay (also passive) |
| Bosses | Large-scale boss fight units | Ender dragon, wither, warden |
| Non-mob entities | Inanimate interactive objects | Item, arrow, boat, minecart, armor stand, painting |

## Common Mechanics

### Health
- Every mob has a maximum health value (`MaxHealth` NBT). Damage is measured in "half-hearts" (1 point = half a heart).
- General armor/armor toughness, resistance, fire protection, etc. reduce damage; protection enchantments reduce magic/explosion/fire damage, etc.

### Spawning
- **Natural spawning conditions**: hostile mobs require **block light level 0** (in 1.18+ this uses a combined block light + sky light calculation; since 1.19.3 sky light is no longer directly counted, but a light level of 0 is still required) on a spawnable block; between 24–128 blocks from the player; not within the player's line of sight, etc.
- Passive mobs require sufficient light level (≥9) and space.
- Spawning is affected by the **mob cap** (70 hostile mobs per player per area) and by **spawn eggs/mob spawners/structure spawns** (villages, ruins, fortresses, etc.).
- Biomes determine which mobs can spawn (e.g. husks in deserts, ocelots in jungles).

### Despawning
- **Immediate despawn**: most mobs beyond 128 blocks (spherical radius) from a player despawn immediately.
- **Random despawn**: between 32–128 blocks from a player, there's a 1/800 chance per game tick of randomly despawning (determined by mob type and whether it holds items/has certain NBT).
- **Persistence**: mobs holding an item, with a name tag, fed/tamed by a player, or located in a village won't randomly despawn; persistence can be forced via `/summon ... {PersistenceRequired:1}`.
- Most mobs stop AI activity ("go idle") if they haven't moved and have no target for 30 seconds.

### Drops
- Items dropped on death are listed in each mob's entry; the Looting enchantment increases some drop quantities and probabilities (see each mob's entry for drops and probabilities).
- Only kills by a player or a tamed wolf count as a "player kill" (drops experience and triggers "player kill" conditional drops).
- Experience: passive mobs 1–3, neutral mobs 5, hostile mobs 5–20 (varies by mob); bosses: Ender Dragon 12000 on first kill, 500 afterward, Wither 50, Warden 5.


### AI and Pathfinding
- Mobs use a goal system (no target → wander → target-driven behavior chain); pathfinding is block-based (won't pass through walls); includes jump pathfinding, climbing (ladders), and swimming.
- Detection range: most hostile mobs have a 16-block detection radius (creeper 16, zombie 16, skeleton 16, etc.), enderman 64 blocks; vertical range ±4 blocks.
- Most mobs have their AI updated by "ticks" (20 times per second), so they're affected by TPS.
- Mob AI shutoff (dormancy) condition: beyond 32 blocks from a player with no target, AI updates are randomly skipped.

### Common Entity NBT (Java Edition 1.20.4 key points)
- Common: `id`, `Pos`, `Motion`, `Rotation`, `UUID`, `CustomName` (JSON text), `CustomNameVisible`, `Silent`, `NoGravity`, `Glowing`, `Invulnerable`, `Tags` (custom tags), `Passengers`, `Persistent`/`PersistenceRequired`, `DeathTime`, `Health`, `HandItems`/`ArmorItems`, `CanPickUpLoot`, `NoAI`, `LeftHanded`, etc.
- Mob-specific tags are listed in each entry (e.g. creeper `powered`, zombie `IsBaby`, villager `Profession`, tamed mob `Owner`, etc.).
- Read/write via `/summon <id> <pos> {NBT}` and `/data`; `/summon` supports spawn events such as `minecraft:become_charged` (a Bedrock-style concept; Java Edition uses NBT instead).

### Entity Hitbox Sizes (Java Edition)
- Different mobs have different collision box sizes; standard examples: player 0.6×1.8, zombie 0.6×1.95, skeleton 0.6×1.99, cow 0.9×1.4, chicken 0.4×0.7, spider 1.4×0.9, etc.; baby variants are roughly half the size of adults.

## Full Entity ID Index (Java 1.20.4)

Passive: `allay, axolotl, bat, bee, camel, cat, chicken, cod, cow, dolphin, donkey, fox, frog, glow_squid, goat, horse, llama, mooshroom, mule, ocelot, panda, parrot, pig, polar_bear, rabbit, salmon, sheep, skeleton_horse, sniffer, squid, strider, tadpole, trader_llama, tropical_fish, turtle, villager, wandering_trader, wolf (tamed), zombie_horse`

Neutral/utility: `dolphin (neutral), enderman, fox (neutral), goat (neutral), iron_golem, llama (neutral), ocelot, panda (neutral), piglin, polar_bear (neutral), snow_golem, spider (neutral), wolf, zombie_piglin`

Hostile: `blaze, cave_spider, creeper, drowned, elder_guardian, endermite, evoker, ghast, guardian, hoglin, husk, magma_cube, phantom, piglin_brute, pillager, ravager, shulker, silverfish, skeleton, slime, stray, vex, vindicator, warden, witch, wither_skeleton, zoglin, zombie, zombie_villager`

Bosses: `ender_dragon, wither`; special: `giant` (giant, spawnable only via commands)

Non-mob: `area_effect_cloud, armor_stand, arrow, block_display, boat, chest_boat, chest_minecart, command_block_minecart, dragon_fireball, egg, end_crystal, ender_pearl, evoker_fangs, experience_bottle, experience_orb, eye_of_ender, falling_block, fireball, firework_rocket, furnace_minecart, hopper_minecart, interaction, item, item_display, item_frame, leash_knot, lightning_bolt, llama_spit, marker, minecart, painting, snowball, spectral_arrow, spawner_minecart, text_display, tnt, tnt_minecart, trident, wither_skull`

## Special Mechanic Entities

- **Spawn Egg**: can spawn the corresponding mob in creative mode; spawn eggs ignore light level/spawn cap restrictions (space is still required).
- **Player**: 20 health, 1 attack damage (bare hands); `/summon` cannot spawn a player.
- **Experience Orb**: increases experience when picked up; drop sources are listed in each mob's entry.
- **Item (dropped item entity)**: the entity form of an item; despawns after 5 minutes (300 seconds); can be absorbed by hoppers; affected by explosions/water currents.
