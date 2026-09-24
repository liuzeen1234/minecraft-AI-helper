---
title: Redstone Blocks and Components (Block of Redstone, Pressure Plates, Repeater, Observer, Comparator)
version: 1.20.4
category: Blocks
keywords: [block of redstone, pressure plate, redstone repeater, observer, redstone comparator, container signal strength, comparator formula, fullness]
summary: Traits and mechanics of the block of redstone, pressure plates, redstone repeaters, observers, redstone comparators, and the precise container fill-level signal formula.
source: Chinese Minecraft Wiki (Fandom mirror); content paraphrased, condensed, and translated for this reference doc rather than reproduced verbatim.
---

# Redstone Blocks and Components

> Target version: Java Edition 1.20.1 / 1.20.4.

## Block of Redstone

The block of redstone is crafted from 9 redstone dust, and functions as an always-on power source that can be pushed by pistons.

- Namespaced ID: `redstone_block`; max stack size 64; renewable; the correct mining tool is a pickaxe (any tier); drops itself when broken.
- Generates naturally in the central basement room of ancient cities.

Key behaviors (it's always powered and can't be turned off):

- Powers any adjacent redstone dust (including dust above or below it) at the maximum signal strength of 15.
- Powers repeaters and comparators facing away from it at signal strength 15.
- Extinguishes any redstone torch attached to it.
- Activates adjacent mechanism blocks (doors, redstone lamps, etc.) above and below it, though a piston directly in front of it still won't activate from it, following normal piston activation rules.
- Adjacent redstone conductors are not powered by it.
- Can feed a side input into a comparator (Java Edition only).

It behaves similarly to a strongly powered block, but technically isn't a redstone conductor itself — for example, placing it above a chest won't prevent that chest from being opened. A redstone torch placed on top of it will go out after 1 redstone tick and won't relight. It can be moved by pistons, and water/lava will flow around it without affecting it.

## Pressure Plate

Pressure plates detect players, mobs, and other entities. There are four variants:

- **Wooden**: detects all entities, outputs signal strength 15; comes in 11 wood-type variants.
- **Stone, Polished Blackstone**: only detects players and mobs, outputs signal strength 15.
- **Light Weighted (gold)**: detects all entities, signal strength increases with entity count (+1 per entity, up to 15).
- **Heavy Weighted (iron)**: similar to the light-weighted plate, but requires more entities to raise the signal (+1 per 10 entities, up to 15).

Pressure plates can be used to craft detector rails or smelted as a fuel item, and also function as redstone components. A pressure plate is destroyed and drops as an item if the block it's attached to moves, disappears, or is destroyed, or if a piston tries to push it or push a block into its space. Water and lava flow around a pressure plate without affecting it.

**Activation condition**: a pressure plate activates when an entity is present within the space directly above it (specifically, within the bounding box from (0.125, 0, 0.125) to (0.875, 0.25, 0.875) relative to its block), even if only a small part of the entity's hitbox overlaps that space. Stone-type pressure plates only activate for mobs (including players), while wooden pressure plates activate for nearly any entity (players, mobs, items, arrows, experience orbs, fishing bobbers, etc.), except projectiles and falling blocks.

## Redstone Repeater

A redstone repeater is used in redstone circuits to relay and amplify a signal, block backward signal flow, or "lock" a signal's state.

- A repeater can only be placed on a block with a full, flat top surface, including hoppers, composters, and upside-down slabs. In Bedrock Edition, it can also be placed on stone walls and fences.
- A repeater has a front and a back — the direction its arrow texture points is the front. It also has two small redstone torches: their color indicates whether the output is on (dark red when off, bright red when on), and the gap between them indicates the delay the repeater introduces to the signal (1–4 redstone ticks, i.e. 2–8 game ticks).
- A repeater can't be activated by a signal from above, below, the sides, or from its front (backward). Its height is 0.125 (1/8) of a block.
- A redstone repeater is destroyed and drops as an item if the block it's attached to moves, disappears, or is destroyed; in Java Edition, it's also destroyed if water flows over it. If lava flows over a redstone repeater, it's destroyed but doesn't drop anything.

**Signal carrying**: a repeater can only carry a signal from its back to its front, though this behavior can be modified via a side input (see signal locking below).

A redstone repeater can be activated via its back input by: an active power source component (exception: an observer only activates a repeater it directly points at); active redstone dust; a redstone comparator or another repeater that outputs a signal and points at it; or a powered block (including any powered mechanism block, such as a dispenser or a redstone lamp).

A redstone repeater can activate or power the following through its front: active redstone dust; an active comparator, or another repeater whose back faces it; strongly powers any redstone conductor (including mechanism blocks); activates any adjacent redstone mechanism that faces it, except a piston; and can lock another repeater whose side faces it.

A redstone conductor powered by a redstone repeater is generally called "**strongly powered**," while one powered by redstone dust is called "**weakly powered**." A strongly powered redstone conductor can activate adjacent redstone dust as well as other redstone components.

## Observer

An observer emits a redstone pulse when the block it faces receives a block update.

- In Java Edition, an observer detects changes to the target block's block state, or the placement/removal of that block (i.e. block state changes, not block entity data changes). This means crop growth can be detected, since growth stages are part of the block state. It essentially acts as a block-update detector that responds to relevant events happening in front of it.
- In Bedrock Edition, an observer functions as a block update detector (BUD) and can detect essentially any block change. The events that trigger updates, and how they propagate, differ significantly between the two editions, so each edition can detect some changes the other can't.
- When it detects something, an observer sends a pulse of signal strength 15 out its output face, lasting 2 game ticks (1 redstone tick). This pulse can activate redstone dust, repeaters, or mechanism blocks on the output side, and also strongly powers the redstone conductor it faces to level 15.
- In Java Edition, an observer has a 1-redstone-tick delay. In Bedrock Edition, it's also supposed to have a 1-redstone-tick delay, but due to bug MCPE-15793 it actually has a 2-redstone-tick delay.
- When an observer itself is moved into place by a piston, that counts as both a block state change and a block update. After being pushed or pulled into place, an observer (except one already outputting a signal in Java Edition) emits a pulse 2 game ticks later (Java Edition only) or 4 game ticks later (Bedrock Edition only).
- Although an observer blocks light, it's not a redstone conductor: it can't be powered by an external redstone signal, nor by its own output signal.
- Because Java Edition observers detect block state changes rather than block updates in the strict sense, they can pick up on a broader range of changes than a BUD would (since some block state changes don't trigger a block update). In Bedrock Edition, observers detect block updates rather than block state changes, so they behave the same as other BUD-type detection.

## Redstone Comparator

A redstone comparator is a component that can hold, compare, subtract, or read specific data values from a redstone signal (most notably, a container's fill level).

- A redstone comparator can be placed on any block with a full, flat top surface (including upside-down slabs, stairs, and hoppers). In Bedrock Edition, it can also be placed on stone walls and fences.
- A comparator has a front and back — the triangle on top points toward the front. When placed, its back (the input side) faces the player. It has two small redstone torches on the back and one on the front. When the input signal into the comparator is greater than zero, the back torches light up and the triangle turns red. The front torch's state can be toggled with the "use item" key: off/unlit (indicating "comparison mode"), or on/lit (indicating "subtraction mode").
- A redstone comparator accepts signals from its back and its sides (left and right). Side inputs can only come from a block of redstone (Java Edition only), redstone dust, a repeater, an observer (Java Edition only), a lightning rod (Java Edition only), or another comparator. The comparator's front is its output.
- A signal passing through a redstone comparator takes 1 redstone tick (2 game ticks, or 0.1 seconds ignoring lag), regardless of whether the input comes from the back or the side. A redstone comparator typically won't react to an input change lasting only 1 tick — for example, a single-tick pulse generator feeding a side input is treated as always having no signal, while feeding the back input is treated as staying on.
- A redstone comparator has four main uses: holding a signal strength, comparing signal strengths, subtracting signal strengths, and reading a block's state (most commonly, a container's fill level).

### Container Fill-Level Formula (Precise)

When a comparator faces the back of a container block (chest, barrel, hopper, dispenser, dropper, furnace, brewing stand, shulker box, composter, chiseled bookshelf, etc.), it reads that container's fill level as a signal strength:

- **Empty container → signal 0.** Otherwise: `signal = 1 + floor(14 × average_fullness)`, where `average_fullness` is computed by taking, for every slot in the inventory, `(item count in that slot) / (that item's max stack size)`, summing across all slots, and dividing by the total number of slots (empty slots count as 0 toward the sum but still count toward the total slot count).
- This means signal strength is **not** simply "items ÷ some fixed number" — it depends on how full each occupied slot is relative to that item's own max stack size (64 for most items, 16 for e.g. eggs/signs, 1 for e.g. buckets/tools). A container holding a few non-stackable items (max stack 1) can already read close to full.
- **Precise 1-item counters**: a hopper (5 slots) with 4 slots filled with a non-stackable item (max stack 1, permanently full) and only the 5th slot used to hold the item being counted will output signal 15 once that 5th slot has even 1 item, since the other 4 slots are always at 100% fullness — a common trick for exact single-item detection.
- Composters use their layer count (0–8) directly as a simplified version of this fullness reading; chiseled bookshelves use "occupied slots ÷ 6" in the same formula (no per-item stack ratio, since each slot holds exactly one book-type item).
- Jukeboxes and note blocks are special cases: they don't use the fullness formula — a jukebox outputs a fixed signal per inserted disc, and a note block outputs its current pitch (1–25), not a fullness ratio.
