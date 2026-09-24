---
title: Blocks Overview and Common Traits
version: 1.20.4
category: Blocks
keywords: [block, hardness, mining, blast resistance, waterlogged, redstone conductor]
summary: Common block properties (hardness, mining tools, blast resistance, waterlogging, light, redstone traits) and a full block category index.
---

# Blocks Overview and Common Traits

> Target version: Java Edition 1.20.4 (mechanics also apply to 1.20.x).
> Note: this document is the master reference for block knowledge, explaining the properties shared by all blocks; specific blocks are covered in the other files in this folder.

## Common Properties

### Hardness and Mining

- Every block has a hardness value that determines how long it takes to mine. Higher hardness means slower mining; blocks with hardness -1 (bedrock, barriers, command blocks, etc.) cannot be broken in survival mode.
- Blocks are categorized by their "appropriate mining tool": **pickaxe** (stone, metal, ore blocks), **axe** (wood blocks), **shovel** (dirt, sand, snow blocks), **hoe** (crops, hay bales, target blocks, fungus blocks, etc.), **shears** (leaves, wool, cobwebs), **sword** (cobwebs, bamboo, etc., but with heavy durability loss).
- Using the correct tool significantly speeds up mining (up to about 6x); using the wrong tool drastically slows it down (e.g. mining diamond ore by hand takes several minutes).
- Many blocks only drop items when mined with the correct tool (e.g. ore must be mined with a pickaxe, otherwise nothing drops).
- Efficiency enchantment and the Haste effect further reduce mining time; if "speed > block hardness × 30" at the moment of breaking, the block is destroyed instantly (instant mining).

### Blast Resistance

- Determines a block's resistance to explosions. Common values: dirt/sand 0.5, wood 2, stone-type 6, obsidian/netherite block 1200 (extremely high), bedrock -1 (indestructible).
- Blast resistance is unrelated to hardness: e.g. TNT (hardness 0) also has 0 blast resistance and destroys itself when it explodes.
- Actual blast resistance also depends on block shape; full blocks resist explosions better than partial blocks (slabs, rails, etc.).

### Replaceable Blocks

- Replaceable blocks (such as air, water, grass, flowers, fire, snow layers, redstone dust, etc.) get directly replaced when a new block is placed there, without needing to be broken first.
- Regular blocks are not replaceable: placing a new block where one already exists will fail (except in special cases like placing underwater).

### Waterlogged

- Since Java Edition 1.13, many non-full blocks (slabs, stairs, fences, walls, signs, rails, flower pots, lanterns, trapdoors, copper grates, etc.) can be waterlogged, meaning the block and a water current occupy the same space.
- Waterlogged blocks are affected by water flow, and their insulation from redstone signals varies; being waterlogged doesn't affect the block's own function (e.g. waterlogged stairs are still passable).
- Full blocks (like stone, dirt) can't be waterlogged; scooping with a bucket removes the waterlogged state.

### Light

- Every block has two light-related properties: **its own light emission** (0–15, e.g. torch 14, furnace 13, redstone lamp 15, beacon 15, lava 15, glowstone 15, sea lantern 15, jack o'lantern 15) and **blocking/light transmission** (opaque blocks block skylight, transparent blocks like glass and ice don't block it but may reduce it slightly).
- Light level is used to determine mob spawning (hostile mobs require areas with light level ≤0), crop growth, plant survival, etc.

### Redstone Conductors

- Redstone conductor blocks (like stone, dirt, wood, and most full blocks other than wool) can be **powered** by a redstone signal (weak/strong) and pass that power to adjacent conductor blocks, as well as activate adjacent redstone mechanisms.
- Non-conductor blocks (glass, glowstone, ice, wool, pistons, hoppers, stairs, slabs, fences, etc.) can't be powered, and signals can't pass through them.
- Strong power vs. weak power: power output from repeaters/comparators/mechanism outputs counts as strong power (can activate adjacent redstone dust), while power from redstone dust itself is weak power (can only activate mechanisms, not other redstone dust).

### Stickiness and Piston Interaction

- Whether a block can be pushed/pulled by a piston is determined by its "stickiness" property: most blocks can be pushed, while bedrock, obsidian, End portal frames, reinforced deepslate, chests, etc. cannot.
- Slime blocks and honey blocks can drag adjacent blocks along with them (transferring movement), and have bounce/slowdown effects.
- When a piston moves blocks, waterlogged blocks, falling block entities (sand, TNT), etc. have special behavior.

### Flammability

- Some blocks have an ignition chance and a burn-away chance (wood blocks, leaves, wool, hay bales, bookshelves, TNT, etc.). Ignited blocks (fire) spread to adjacent flammable blocks.
- Fire on netherrack burns forever; fire on soul sand/soul soil becomes soul fire (blue, doesn't spread).

### Sound Groups

- Each block category has distinctive placement/break/walking sounds: stone-type, wood-type, gravel, wool, grass, metal, glass, slime block, etc. Since 1.19.3+, custom block sounds can be defined via sound groups.

## Block Category Index (1.20.4)

| Category | Representative blocks | See file |
|---|---|---|
| Naturally occurring blocks | Stone, deepslate, dirt, sand, ore, clay, basalt, end stone, netherrack | `naturally-occurring-blocks.md` |
| Wood and lumber blocks | Logs, stripped logs, planks, slabs/stairs, doors, fences, signs (6 tree types + cherry + bamboo + mangrove) | `trees-and-wood-blocks.md` |
| Building and decorative blocks | Bricks, terracotta, concrete, wool, glass, prismarine, purpur, lanterns, banners, carpets | `building-and-decorative-blocks.md` |
| Functional blocks | Crafting table, furnace, chest, brewing stand, enchanting table, anvil, beacon, hopper, jukebox | `functional-blocks.md` |
| Crop and plant blocks | Wheat, carrots, flowers, grass, saplings, fungi, vines, sugar cane, kelp | `crops-and-plant-blocks.md` |
| Redstone components and mechanisms | Redstone dust, torch, lever, button, tripwire, redstone lamp, dispenser, dropper, lightning rod, target block | `redstone-blocks-and-components.md`, `redstone-mechanism-blocks.md`, `pistons.md` |
| Special and indestructible blocks | Bedrock, command block, structure block, barrier, mob spawner, portals, light-source blocks | `special-and-liquid-portal-blocks.md` |
| Liquids | Water, lava, bubble column, powder snow, slime block, honey block, ice | `special-and-liquid-portal-blocks.md` |
| Copper family | Copper block, cut copper, stairs/slabs and their oxidized/waxed variants (1.20.4 default; copper lamps etc. are 1.21 experimental) | `naturally-occurring-blocks.md` |

## Block State

- Every block is described by its **block state**: e.g. orientation, waterlogged, oxidation level, open/closed, layer count (snow layers), growth stage (crops), etc.
- Block states can be specified in commands using square brackets: `/setblock ~ ~ ~ minecraft:oak_log[axis=y]`.
- Block state changes can be detected by comparators/observers (Java Edition detects block state changes; Bedrock Edition detects block updates).

## Block Entities

- Some blocks carry extra data (inventory, custom name, text, etc.): chests, furnaces, signs, command blocks, structure blocks, jukeboxes, mob spawners, beacons, etc.
- Block entity data can be read/written via the `/data` command; NBT changes typically don't trigger a block update (observers won't detect them).

## Common Hardness Quick Reference

| Block | Hardness | Correct tool |
|---|---|---|
| Bedrock/barrier/command block/structure block/light block | -1 (indestructible) | — |
| Reinforced deepslate | 55 (1200 seconds) | Pickaxe |
| Obsidian/netherite block/crying obsidian/respawn anchor | 50 | Pickaxe |
| Ancient debris | 30 | Pickaxe (diamond or better) |
| Iron/gold/emerald/diamond blocks, deepslate ore | 4.5–5 | Pickaxe |
| Common ore (coal/iron/copper/gold/redstone/lapis) | 3 | Pickaxe |
| Stone, deepslate, bricks, stone bricks, end stone | 1.5–3 | Pickaxe |
| Logs, planks, wood furniture | 2–3 | Axe |
| Dirt, sand, gravel, clay, farmland | 0.5–0.6 | Shovel |
| Glass, glowstone | 0.3 | Any (with Silk Touch) |
| Wool, carpet | 0.8 | Shears |
| Crops, flowers, grass | 0 | Any (instant) |
