---
title: Utility Mobs and Villagers (Villager, Wandering Trader, Iron Golem, Snow Golem, Allay)
version: 1.20.4
category: Mobs
keywords: [villager, wandering trader, iron golem, snow golem, allay, profession, trading]
summary: Base info and traits for villager professions and trading, the wandering trader, iron golem, snow golem, and allay.
---

# Utility Mobs and Villagers

> Target version: Java Edition 1.20.4. Villagers and utility mobs are central to village economies and player-built defenses.

## Villager `villager`

### Basic Info
- Appearance: green-robed villager with a prominent nose; clothing color varies by profession (librarians wear white robes, blacksmiths wear a black apron, etc.); skin tone/profession variant is determined by biome.
- Health 20; spawns in villages (naturally) or is cured from a zombie villager (Weakness potion + golden apple).
- Behavior: works/wanders during the day, returns to a bed at night; takes shelter from rain; protected by iron golems; can be attacked by zombies/vindicators; **trading** is the core gameplay loop.

### Professions and Job Site Blocks (1.20.4)
| Profession | Job site block | Trades |
|---|---|---|
| Farmer | Composter | Crops → emeralds, emeralds → bread/melon/pumpkin pie |
| Librarian | Lectern | Paper/books → emeralds; sells enchanted books |
| Cartographer | Cartography table | Map trades, explorer maps |
| Cleric | Brewing stand | Rotten flesh/gold ingots → emeralds; sells redstone/glowstone/ender pearls |
| Armorer | Blast furnace | Coal/iron → emeralds; sells iron/diamond armor |
| Weaponsmith | Grindstone | Sells swords/axes (iron to diamond) |
| Toolsmith | Smithing table | Sells pickaxes/axes/shovels |
| Butcher | Smoker | Meat → emeralds |
| Fisherman | Barrel | Fish → emeralds |
| Leatherworker | Cauldron | Leather → emeralds; sells leather gear |
| Mason | Stonecutter | Clay/stone → emeralds; sells terracotta/quartz |
| Nitwit / Unemployed | None | No trades |

- Villager tiers: Novice → Apprentice → Journeyman → Expert → Master; trading more unlocks higher tiers and more trades; Master tier has unique trades.
- Breeding: villager + villager (requires beds + food like bread/carrots, etc.); villagers breed when there are 3+ free beds; trading with the player raises reputation.
- Zombie Villager `zombie_villager`: 20 health, hostile; can be cured back into a villager with a splash Weakness potion + golden apple (about 2–5 minutes); retains its original profession after curing.

### Behavior Details
- Villagers have an inventory (8 slots); they'll pick up food like bread.
- Hide indoors when raided by illagers (pillagers, etc.); protected by iron golems.
- Use `/summon villager ~ ~ ~ {VillagerData:{profession:"minecraft:librarian", level:5, type:"minecraft:plains"}}` to specify profession/tier/biome variant.

## Wandering Trader `wandering_trader`
- Appearance: merchant in a blue robe with a white turban, wears a hood.
- Health 20; appears randomly in the world (every 20–30 minutes, stays for 2–3 days); brings 2 trader llamas.
- Behavior: drinks invisibility potions; sells random items (coral, tropical fish buckets, slimeballs, nautilus shells, and other rare items, etc.); can be killed (no drops).
- Trading: same interface as villagers, but the stock is random.

## Iron Golem `iron_golem`
- Appearance: roughly 2.7 blocks tall, iron-gray humanoid, vines climbing up its chest.
- Health 100; spawns naturally in villages (20+ villagers with 75% having worked recently, attempted every 35 seconds) or is built by a player (4 iron blocks in a T-shape + a carved pumpkin).
- Behavior:
  - Protects the village: actively attacks zombies, skeletons, spiders, slimes, and other hostile mobs (**doesn't attack creepers** unprompted, or ghasts).
  - Neutral: retaliates when a player attacks it (once provoked, it keeps chasing); also attacks a player who attacks a villager.
  - Tosses players/villagers into the air (attack effect); immune to drowning, fall damage, and fire (not immune to lava; it is immune to fire damage though).
  - Can be repaired by a player with iron ingots (right-click).
- Drops: 3–5 iron ingots, 0–2 poppies (on player kill).

## Snow Golem `snow_golem`
- Appearance: a snowman (2 snow blocks + a carved pumpkin), leaves a trail of snow while walking.
- Health 4; spawns when built (a stack of snow blocks + a carved pumpkin).
- Behavior: throws snowballs at hostile mobs (knockback, no damage); doesn't wander out of snowy areas by itself (automatically? no); melts near hot biomes/the Nether/lava (dies from heat damage).
- Drops: 0–15 snowballs.

## Allay `allay`
- Appearance: small blue sprite (about 0.35 blocks), glowing blue eyes, flapping wings.
- Health 20; spawns in cages/pillar structures at pillager outposts and woodland mansions.
- Behavior:
  - After a player gives it an item, the allay will go collect matching items and bring them back to the player (a block location can be designated for it to deposit them).
  - Follows the nearest player; can be "trusted" by a player (more obedient once fed).
  - **Duplication**: giving any music disc to a jukebox that's playing music causes nearby allays to dance and spawn a new allay (subject to a cooldown).
  - Takes no damage (unless attacked by a player); picks up dropped items.
- Drops: none.

## Utility Mob NBT Highlights
- Iron Golem: `PlayerCreated:1b` (marks it as player-built).
- Snow Golem: nothing special.
- Allay: `DuplicationCooldown`, `CanDuplicate`.
- Villager: `VillagerData` (type/profession/level), `Inventory`, `Xp`.
