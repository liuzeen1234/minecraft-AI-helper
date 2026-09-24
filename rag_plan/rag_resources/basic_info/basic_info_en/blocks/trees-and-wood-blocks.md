---
title: Trees and Wood Blocks (Logs, Planks, Doors, Fences, Signs, Cherry Wood, Bamboo, Mangrove)
version: 1.20.4
category: Blocks
keywords: [log, planks, wooden door, fence, sign, cherry, bamboo, mangrove, slab, stairs]
summary: Appearance and traits of the full wood block family (logs, planks, slabs/stairs, doors, fences, signs, pressure plates, buttons, etc.) across nine tree types.
---

# Trees and Wood Blocks

> Target version: Java Edition 1.20.4. Covers 9 wood families: oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, and bamboo.

## Common Wood Block Traits

- Wood blocks are **flammable** (high ignition chance), with hardness 2 (logs 2, planks 2, doors 3, trapdoors 3, fences 2, signs 1).
- The correct tool is an **axe** (fastest mining; without a hard tool requirement, any tool can break them).
- Planks can be crafted into: doors, trapdoors, fences, fence gates, slabs, stairs, pressure plates, buttons, signs, hanging signs, boats, chests, crafting tables, wooden tools, etc.
- Buttons, pressure plates, doors, trapdoors, and fence gates can be activated by a redstone signal (see `redstone-mechanism-blocks.md`).

## Tree Families

### Oak
- Appearance: logs have brown bark with a dark-toned texture, a light brown core; leaves are bright green.
- Traits: generates in plains and forests; apple trees have a chance of dropping apples; oak grows into a regular tree or a large oak tree (auto-generates vines).

### Spruce
- Appearance: logs have dark brown bark, a light brown core; leaves are dark blue-green.
- Traits: generates in taiga, snowy tundra, and giant taiga (large variant).

### Birch
- Appearance: white bark with black horizontal streaks, very easy to identify; leaves are pale yellow-green.
- Traits: generates in birch forests and regular forests.

### Jungle
- Appearance: logs have mottled brown-green bark, a pinkish-orange core; leaves are deep green.
- Traits: generates in jungles; jungle saplings must be planted on dirt/grass blocks with 3 unobstructed blocks of space above to grow into a large tree; jungle log cores are used to craft cocoa-planting-related items.

### Acacia
- Appearance: bark is gray-brown with vertical streaks; leaves are yellow-green with a flat canopy.
- Traits: generates in savannas.

### Dark Oak
- Appearance: bark is dark brown, nearly black, with a dark brown core; leaves are dark green.
- Traits: generates in dark forests; its saplings require a 2×2 arrangement to grow into a large tree.

### Mangrove
- Appearance: bark is dark grayish-brown with a porous texture; leaves are dark green (with a reddish-brown tint); hanging roots grow from above.
- Traits: added in 1.19, generates in mangrove swamps; saplings can be planted on mud; hanging roots can grow downward from mangrove leaves.

### Cherry
- Appearance: bark is dark reddish-brown, nearly black, with streaks, and a **pink** core; leaves are pink, petal-like, and shed pink particles.
- Traits: added in 1.20, generates in cherry grove biomes (foothills); cherry is the only wood family with a pink core; its leaves shed pink particles.

### Bamboo
- Appearance: bamboo blocks have a yellow-green, jointed cane cross-section; bamboo shoots are green, pointed sprouts.
- Traits: added in 1.20, generates in jungles; a bamboo block is crafted from 9 bamboo canes; bamboo blocks can be stripped (right-click with an axe) into smooth bamboo blocks; bamboo can be crafted into rafts and scaffolding; the bamboo plant itself is a plant block (see `crops-and-plant-blocks.md`).

## Logs and Stripped Variants

- **Log**: has bark on its exterior, with an `axis` block state (x/y/z orientation).
- **Stripped Log**: obtained by right-clicking a log with an axe; the core is exposed.
- **Wood / Stripped Wood**: a log variant with bark texture on all six faces (crafted from 2×2 of the same log type).
- All variants have hardness 2, fastest with an axe; can be smelted into charcoal.

## Planks

- Appearance: smooth plank texture matching the wood's color (texture is the same across tree types, only the color differs).
- Traits: hardness 2, fastest with an axe; the base ingredient for all wood-based crafting; can be crafted into sticks (2 planks → 4 sticks).

## Wood Components (one set per family)

### Slab and Stairs
- Appearance: half-height/step-shaped plank texture.
- Traits: hardness 2; slabs can be stacked top and bottom; can be waterlogged; stairs can be placed to turn corners.

### Fence and Fence Gate
- Appearance: horizontal wooden slats with a gap in the middle; the fence gate is an openable fence segment.
- Traits: hardness 2; fences connect to adjacent fences/walls, are 1.5 blocks tall (entities can't jump over them, except with Jump Boost); fence gates can be opened/closed and controlled by redstone, and can be waterlogged.

### Door
- Appearance: a full wooden door, split into upper and lower halves.
- Traits: hardness 3; occupies 2 blocks of height; opens/closes on right-click; can be controlled by a redstone signal; can be opened by villagers. The open state can be waterlogged (at the bottom).

### Trapdoor
- Appearance: a horizontally hinged panel, stands vertically or lies flat when open.
- Traits: hardness 3; a 1×1 hinged door, can be placed on the side/top/bottom of a block; controlled by redstone; can be waterlogged when open.

### Pressure Plate (wooden)
- Appearance: a thin wooden slat.
- Traits: hardness 0.5; any entity (including dropped items and arrows) stepping on it emits a signal strength of 15; turns off when released.

### Button (wooden)
- Appearance: a small wooden knob.
- Traits: hardness 0.5; right-clicking it emits a signal for 1.5 seconds (30 game ticks); can be placed on any of the 6 faces.

### Sign and Hanging Sign
- Appearance: an upright wooden sign (writable on both sides since 1.20); hanging signs hang below a block, with chains.
- Traits: signs have hardness 1; can be written on (both front and back, 1.20+), text can be dyed; hanging signs can be waterlogged and placed on the underside or side of a block (requires support).

### Boat / Raft
- Appearance: a small wooden boat; the raft is a bamboo flat-bottomed craft.
- Traits: an entity vehicle, carries 2 passengers; cherry boats/bamboo rafts were added in 1.20. See `mobs/non-mob-entities.md` for details.

## Special Wooden Functional Blocks (general purpose, see the functional blocks doc for details)

- Chests, crafting tables, bookshelves, barrels, campfires, looms, cartography tables, fletching tables, smithing tables, composters, jukeboxes, and note blocks are all wood-based functional blocks (fastest with an axe); see `functional-blocks.md`.

## Fire and Wood Blocks

- All wood blocks are flammable; forest fires can spread along leaves and logs.
- Cherry leaves and bamboo blocks are also flammable; bamboo blocks smelt into charcoal.
- Mangrove hanging roots are flammable.
