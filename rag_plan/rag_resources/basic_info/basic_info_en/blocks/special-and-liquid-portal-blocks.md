---
title: Special and Liquid/Portal Blocks (Bedrock, Command Block, Portals, Water, Lava, Slime Block, Honey Block, Ice)
version: 1.20.4
category: Blocks
keywords: [bedrock, command block, portal, water, lava, slime block, honey block, ice, barrier, mob spawner]
summary: Appearance and traits of indestructible blocks, portals, liquids, and other special-mechanic blocks.
---

# Special and Liquid/Portal Blocks

> Target version: Java Edition 1.20.4.

## Indestructible Blocks (unobtainable/unbreakable in survival mode)

### Bedrock
- Appearance: a dark grayish-black block with a grainy texture.
- Traits: hardness -1 (indestructible); forms the boundary at the bottom of the world (Y=-64) and the very top (Y=320); placeable in creative mode; can't be pushed by pistons or destroyed by explosions.

### Barrier
- Appearance: an **invisible** block, only shown as a red-slash circle icon while holding a barrier item.
- Traits: hardness -1; only obtainable in creative mode; blocks entities, light, and redstone signals; used for defining map boundaries.

### Light Block
- Appearance: an invisible block, shown as a glowing yellow dot (levels 0–15) while holding the item.
- Traits: hardness -1; placed in creative mode; only provides the specified light level without blocking anything; used for controlling map lighting.

### Command Block
- Appearance: a gray-green block with a slanted screen and a glowing edge (impulse/chain/repeat types); comes in 3 types.
- Traits: hardness -1; usable only in creative mode/by admins; executes commands; can be activated by redstone (impulse type), chained (executes after the previous command block fires, requires facing direction), or repeat (executes every tick). Obtained via `/give` or set after placement; type can be set with the `-{mode}` option (pulse/chain/repeat).

### Structure Block
- Appearance: a gray-white/blue block with a grid pattern (four modes: data/save/load/corner).
- Traits: hardness -1; saves and loads structures (.nbt); used for map making and structure duplication; requires admin access by default.

### Jigsaw Block
- Appearance: a green block with a puzzle-piece groove texture.
- Traits: hardness -1; connects structure pieces when generating structures (target/pool logic); used for custom structure generation (e.g. ancient cities, ruins).

### Reinforced Deepslate
- Appearance: dark gray deepslate with a riveted texture.
- Traits: hardness 55 (takes about 1200 seconds to mine, extremely difficult to break in survival); generates in ancient cities; used for structural protection, can't be pushed by pistons.

## Portal and Dimension Blocks

### Nether Portal
- Appearance: a glowing purple portal block (light level 11), with flowing purple particles.
- Traits: created by igniting an obsidian frame (at least 4×5) with flint and steel or a fire charge; entering it teleports you to the Nether (coordinate conversion: Overworld ÷ 8); portal blocks can't be broken by normal means (dousing the frame with water/lava causes the portal blocks to disappear); emits purple particles.

### End Portal
- Appearance: a black portal block with starry light effects.
- Traits: forms within an End portal frame in a stronghold (12 frame blocks + activated with eyes of ender); entering it teleports you to the End; indestructible; the frame blocks (End Portal Frame) have green carved patterns with eye sockets.

### End Gateway
- Appearance: a black, thin-framed portal with a purple beam of light in the center.
- Traits: provides fast travel between the End's outer islands and the main island; indestructible; only activates after the ender dragon has been killed.

### End Crystal (entity)
- Appearance: a glass sphere on a base, glowing on top.
- Traits: see the Mobs docs; heals the ender dragon; can be placed/destroyed by players.

## Liquid Blocks

### Water
- Appearance: a translucent blue liquid, with ripple animations while flowing.
- Traits: hardness 100 (special case), no tool (not mineable); flows (up to 7 blocks horizontally, infinitely downward); a source block can be collected with a bucket; other blocks can be waterlogged (see the overview doc); entities are slowed and can swim in it; pouring lava on it forms obsidian/stone; flow speed affects transport.

### Lava
- Appearance: a glowing orange-red liquid (light level 15), with a rippling surface.
- Traits: can't be collected infinitely with a bucket (only the source can); flows more slowly than water (up to 4 blocks); contact deals fire damage and ignites flammable materials; pouring water on lava: source → obsidian, flowing → stone; lava + soul sand → bubble column; a lava-filled cauldron can be used to destroy items.

### Bubble Column
- Appearance: an upward/downward stream of bubbles in water.
- Traits: produced by soul sand (rising column) or magma blocks (descending column); can push entities up/down; can be broken by a boat; used for water elevators.

### Powder Snow (see the Naturally Occurring Blocks doc)

## Special Mechanic Blocks

### Slime Block
- Appearance: a translucent green, jelly-like block.
- Traits: hardness 0, any tool; **bouncy**: entities landing on it bounce back (retaining some momentum, affected by Jump Boost); **can be pushed by pistons** and drags adjacent blocks along (transfers movement, enabling "flying machine" contraptions); can serve as a "movement carrier" for redstone contraptions; doesn't conduct redstone.

### Honey Block
- Appearance: a translucent golden, jelly-like block with a honeycomb-textured surface.
- Traits: hardness 0, any tool; entities on it are slowed (can barely move) and **take no fall damage** (immune to fall damage); can be pushed/pulled by pistons (**pulls** adjacent blocks along, unlike slime blocks); sticks to entities; used in redstone contraptions (pull-based transfer).

### Ice family (Ice / Packed Ice / Blue Ice)
- Appearance: see the Naturally Occurring Blocks doc; ice surfaces are slippery, entities slide on them; packed ice and blue ice can be pushed by pistons (all ice-type blocks can be pushed by pistons); blue ice is used for high-speed "blue ice boat highways."

### Packed Ice/Ice and Redstone
- Packed ice and blue ice are **not redstone conductors** (signals can't pass through them).

### TNT
- Appearance: a red block with white strap textures.
- Traits: hardness 0, any tool; explodes 4 seconds (80 game ticks) after being activated by a redstone signal/fire/explosion/flint and steel; blast strength 4; can be pushed out by a piston (still explodes while falling); see the Mobs docs for the TNT minecart variant.

### Mob Spawner (see the Functional Blocks doc)

### Bee Nest / Beehive (see the Functional Blocks doc)

### Smithing Table / Stonecutter, etc. (see the Functional Blocks doc)

## Appendix: 1.20.4-Specific/Updated Mechanic Blocks

- **Decorated Pot**: 1.20 archaeology content; crafted from pottery sherds/bricks; can hold an item (displayed on top in 1.20, with full container functionality added in 1.20.3+); can't store regular items directly before 1.20.3 (items can be inserted and removed starting 1.20.3). Appearance: a clay pot with pottery sherd patterns.
- **Cherry Leaves/Cherry Wood** (see the Trees and Wood Blocks doc).
- **Sniffer Egg/Torchflower/Pitcher Plant** (see the Crop and Plant Blocks doc — torchflower and pitcher plant are new 1.20 crops: torchflower is a small orange flower, pitcher plant is a purple, pitcher-shaped carnivorous plant).
