---
title: Functional Blocks (Crafting Table, Furnace, Chest, Brewing, Enchanting, Beacon, Hopper, Note Block, etc.)
version: 1.20.4
category: Blocks
keywords: [crafting table, furnace, chest, shulker box, enchanting table, beacon, hopper, dispenser, note block, composter, note block instrument]
summary: Appearance and traits of the core functional blocks used in survival gameplay - crafting, smelting, storage, enchanting, brewing, redstone, and note blocks.
---

# Functional Blocks

> Target version: Java Edition 1.20.4. Covers the appearance and traits of commonly used functional blocks in survival; dedicated redstone components are covered in `redstone-blocks-and-components.md` and `redstone-mechanism-blocks.md`.

## Crafting-Related

### Crafting Table
- Appearance: a square tabletop made of 4 planks, with a tool-icon texture.
- Traits: hardness 2.5, fastest with an axe; opens a 3×3 crafting grid; the core of survival-mode crafting.

### Stonecutter
- Appearance: a stone tabletop with a metal blade.
- Traits: hardness 3.5, requires a pickaxe; crafts stone-type blocks into slabs/stairs/walls/variants at a 1:1 ratio (more efficient than crafting by hand); can also cut copper blocks, purpur, etc.

### Loom
- Appearance: a wooden frame with a vertical spool.
- Traits: hardness 2.5, fastest with an axe; creates banner patterns (requires a banner + dye + an optional pattern template).

### Cartography Table
- Appearance: a wooden table with a rolled-up parchment.
- Traits: hardness 2.5, fastest with an axe; duplicates/scales maps, locks a map (with a glass pane), and creates explorer maps.

### Fletching Table
- Appearance: a wooden table with a bow and arrows on top.
- Traits: hardness 2.5, fastest with an axe; currently only serves as the fletcher villager's job site block, with no crafting function.

### Smithing Table
- Appearance: an obsidian and iron-ingot-trimmed wooden table.
- Traits: hardness 2.5, fastest with an axe; since 1.20, used for **smithing**: diamond gear + netherite upgrade smithing template → netherite gear; or applying a smithing template + material to add trims to armor; also the toolsmith villager's job site block.

### Grindstone
- Appearance: a stone base with a rotating grinding wheel disc.
- Traits: hardness 2, requires a pickaxe; repairs items and **removes all non-curse enchantments** (returning experience); can also combine two of the same item while repairing them.

## Smelting-Related

### Furnace
- Appearance: a gray stone-brick furnace body with a front opening.
- Traits: hardness 3.5, requires a pickaxe; crafted from 8 cobblestone; smelts ores, food, and wood into charcoal, etc.; fuel: coal, charcoal, sticks, hay bales, etc. Light level 13 (while lit).

### Blast Furnace
- Appearance: an iron-gray furnace body.
- Traits: hardness 3.5, requires a pickaxe; smelts ores and metals at 2x speed (can't smelt food); the armorer/weaponsmith villager's job site block.

### Smoker
- Appearance: a dark wood-grained chimney furnace.
- Traits: hardness 3.5, fastest with an axe; smelts food at 2x speed (can't smelt ores); the butcher villager's job site block.

### Campfire / Soul Campfire
- Appearance: orange flames / blue flames on a log pile, with smoke particles.
- Traits: hardness 2, fastest with an axe; light level 15 while lit (soul campfire is 10); can cook 4 food items at once (doesn't consume fuel, slow); standing on it deals fire damage but won't burn dropped items; can be extinguished (water/right-click with a shovel); can be used to collect honey and block mobs (immune to fall damage on top).

## Storage-Related

### Chest
- Appearance: a brown wooden box with a metal latch; trapped chests have red markings.
- Traits: hardness 2.5, fastest with an axe; 27-slot inventory; two chests placed side by side form a double chest (54 slots); can be fed by/emptied via hoppers. Trapped chests emit a redstone signal (up to 15) when opened, scaling with the number of players with it open.

### Barrel
- Appearance: a horizontal wooden barrel with dark hoops.
- Traits: hardness 2.5, fastest with an axe; 27 slots; more space-efficient than a chest and can be placed facing any direction; the fisherman villager's job site block.

### Shulker Box
- Appearance: a purple box with a mouth-like lid, dyeable; the body opens and closes.
- Traits: hardness 2, requires a pickaxe; 27-slot storage, **retains its contents when moved by a piston** (carries its contents along); keeps its contents when broken (they drop together); 16 color variants (dyeable in 1.20.4).

### Ender Chest
- Appearance: a dark box with purplish-brown markings, surrounded by a glowing halo.
- Traits: hardness 22.5, requires a pickaxe (drops nothing without one); each player has their own shared 27-slot ender storage space; drops 8 obsidian when broken (contents remain in the ender storage space).

### Composter
- Appearance: a barrel-like structure made of wooden slats.
- Traits: hardness 0.6, fastest with an axe; adding plant-type items accumulates layers (produces bone meal once the 8th layer is full); can be fed by/emptied via hoppers; the farmer villager's job site block; also usable as a "container signal" source (a comparator can read the layer count).

## Enchanting and Repair

### Enchanting Table
- Appearance: an obsidian base with a floating purple book, surrounded by glowing runes.
- Traits: hardness 5, requires a pickaxe; consumes experience and lapis lazuli to enchant items; bookshelves within 2 blocks (up to 15 of them) raise the level cap to 30.

### Anvil
- Appearance: a black anvil block with a metallic sheen; has damaged/cracked states.
- Traits: hardness 5, requires a pickaxe; repairs items, combines enchantments, and renames items; each use consumes experience and causes wear; has three damage states (anvil → slightly damaged → very damaged).

## Brewing and Potions

### Brewing Stand
- Appearance: a stone base with 3 glass bottles and a decorative flame on top.
- Traits: hardness 0.5, requires a pickaxe; brews potions (water bottle + nether wart → awkward potion → add ingredients); requires blaze powder as fuel; light level 1. The cleric villager's job site block.

### Cauldron
- Appearance: an iron pot.
- Traits: hardness 2, requires a pickaxe; can hold water/lava/powder snow (three states); interacted with via buckets or water bottles; rain/snowfall can fill it (with water or snow); a lava-filled cauldron can be used to destroy items; since 1.20.3+, decorated pots pair with it in certain interactions.

## Note and Mechanism Blocks

### Note Block
- Appearance: a wooden resonance box with a sound hole on top.
- Traits: hardness 0.8, fastest with an axe; plays a note when activated by redstone, right-clicked, or hit; the **instrument sound is determined by the block placed directly below it** (not above); pitch is determined by the block placed directly above it only in the sense that there must be an air block above for any sound to play at all — right-clicking the note block itself cycles its pitch (25 settings total, 2 octaves, F#–F#); produces no sound if a solid/opaque block sits directly above it.
- Instrument-by-block-below reference (partial list, most common builder blocks):
  - Harp (default piano tone): dirt, grass block, stone, or no block below (air).
  - Bass drum: stone, cobblestone, stone-type blocks, netherrack, stone pressure plate.
  - Snare drum: sand, gravel, concrete powder.
  - Clicks and sticks: glass, sea lantern.
  - Bass guitar: any wood planks, logs, wooden slabs, wooden doors.
  - Bell: block of gold.
  - Chime: packed ice.
  - Flute: clay block.
  - Guitar: any color of wool.
  - Xylophone: bone block.
  - Vibraphone (iron xylophone): block of iron.
  - Cow bell: soul sand.
  - Didgeridoo: pumpkin.
  - Bit (8-bit tone): block of emerald.
  - Banjo: hay bale.
  - Pling (electric piano-like): glowstone.
  - Mob head sounds: placing a mob head (zombie, skeleton, wither skeleton, creeper, piglin, dragon) directly on top of the note block (not below) makes it play that mob's sound instead of a musical note.
- A comparator facing a note block reads its current pitch setting as a signal (1–25, clamped to the 0–15 output range per normal comparator rules).

### Jukebox
- Appearance: a dark wooden box with a disc slot and orange trim on the front.
- Traits: hardness 2, fastest with an axe; insert a music disc to play music; light level 7 while playing; a redstone comparator can detect which disc is inserted (signal strength 1–15 corresponds to the disc).

### Bell
- Appearance: a golden copper bell, hung on a frame.
- Traits: hardness 5, requires a pickaxe; rings when struck (emits a redstone signal + alerts villagers); during a zombie siege, villagers will retreat indoors; ringing it causes villagers within a 32-block radius to head home.

### Lightning Rod (see the Naturally Occurring Blocks doc)
- Traits: attracts lightning + outputs a redstone signal.

### Target Block
- Appearance: a red-and-white concentric-ring target face.
- Traits: hardness 0.5, fastest with a hoe (breakable with any tool); outputs a redstone signal correlated with the hit location when struck by a projectile (0–15, stronger closer to the center); used for shooting ranges and redstone distance-measuring.

## Other Functional Blocks

### Beacon
- Appearance: a transparent, glowing block containing a rotating star-shaped energy core, shooting a beam of light into the sky.
- Traits: hardness 3, requires a pickaxe; activated by placing it atop a pyramid of iron/gold/diamond/netherite blocks (1–4 tiers); grants players buff effects (Speed, Haste, Resistance, Jump Boost, Strength, Regeneration), applied continuously; selects a primary effect + a secondary effect (requires consuming iron/gold/diamond/emerald/netherite ingots).

### Mob Spawner
- Appearance: an iron-gray cage-like structure, with a rotating miniature mob model inside.
- Traits: hardness 5, requires a pickaxe (requires Silk Touch to obtain; not obtainable as an item by default); spawns the corresponding mob around itself; see the Mobs docs for activation radius and conditions.

### Bee Nest / Beehive
- Appearance: a golden-brown, honeycomb-textured nest, with honey-drip textures.
- Traits: hardness 0.6, fastest with an axe (obtainable with Silk Touch, otherwise it still requires Silk Touch to drop itself); houses bees; harvest honey/honeycomb (using a glass bottle/shears, requires smoking it with a lit campfire first); the beekeeper villager's job site block.

### Scaffolding
- Appearance: a bamboo lattice frame.
- Traits: hardness 0, no tool requirement; allows quick climbing up/down (descend by sneaking); won't fall within 6 blocks (falls if unsupported beyond 6 blocks); useful for building at height.

### Ladder
- Appearance: a wooden rung-style ladder.
- Traits: hardness 0.4, fastest with an axe; attaches to the side of a block, climbable; can be waterlogged.
