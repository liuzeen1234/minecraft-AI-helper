---
title: Redstone Mechanism Blocks (Redstone Dust, Torch, Lever, Button, Tripwire, Redstone Lamp, Dispenser, Dropper, Hopper, Note Block, Target Block)
version: 1.20.4
category: Blocks
keywords: [redstone dust, redstone torch, lever, button, tripwire, redstone lamp, dispenser, dropper, hopper, target block, TNT, daylight detector, sculk sensor]
summary: Appearance and traits of redstone power sources, transmission, and mechanism blocks - redstone dust, torches, levers, buttons, tripwires, lamps, dispensers, droppers, hoppers, TNT, daylight detector, sculk sensor, etc.
---

# Redstone Mechanism Blocks

> Target version: Java Edition 1.20.4. Block of redstone/pressure plates/repeaters/observers/comparators are covered in `redstone-blocks-and-components.md`; pistons are covered in `pistons.md`; ticks and timing are covered in `mechanics/tick.md`.

## Power Sources

### Redstone Dust
- Appearance: thin red lines laid flat on a block's surface, with glowing red particles.
- Traits: hardness 0, any tool; the basic transmission wire of redstone circuits; signal propagates along adjacent redstone dust, decaying by 1 per block (up to 15 blocks); can climb non-opaque blocks diagonally; **weakly powers** adjacent redstone conductors; can be washed away by water flow; redstone dust itself can be right-clicked to adjust its shape (cross/straight line/dot).
- Output targets: adjacent redstone dust, mechanism blocks, the side/back of repeaters/comparators, etc.

### Redstone Torch
- Appearance: a thin stick with a red flame.
- Traits: hardness 0; continuously outputs a signal (15) to the component above it and on all four sides; **goes out** when powered by an adjacent powered block (relights after 1 redstone tick); can act as an "inverter" (high input → low output); used to craft repeaters/comparators. Outputs nothing while unlit.

### Lever
- Appearance: a gray handle mounted on a small block base.
- Traits: hardness 0.5, any tool; right-click to toggle on/off, outputs signal 15; can be placed on any of the 6 faces; a manual switch.

### Button (wooden/stone)
- Appearance: a small wooden/stone knob.
- Traits: a wooden button stays active for 1.5 seconds (30 game ticks), a stone button for 1 second (20 game ticks); right-clicking outputs signal 15; a stone button can only be activated by a player (or another "player action"). Being hit by an arrow can also activate a button (true in both Bedrock and Java Edition, for both wood and stone buttons — arrow activation is a trait shared by both types).

### Tripwire Hook + Tripwire
- Appearance: a tripwire hook is a metal hook mounted on the side of a block; tripwire is an extremely thin white string.
- Traits: tripwire is strung between two tripwire hooks; activates (outputs signal 15) when an entity passes through it; tripwire can be removed with shears without triggering it; the wire briefly "snaps" when an entity passes through (detectable by an observer); used for traps. Both wood- and stone-anchored tripwires trigger for all entities (including dropped items and arrows).

## Output and Mechanism Blocks

### Redstone Lamp
- Appearance: a dark orange-red block when unlit, bright yellow when active (light level 15).
- Traits: hardness 0.3, any tool; lights up when activated by a redstone signal; goes dark once the signal is removed; used for lighting and decoration.

### Piston and Sticky Piston
- Appearance: a wood/stone-textured block whose top can extend a piston arm; a sticky piston's top has a glue-like texture.
- Traits: see `pistons.md`; pushes/pulls blocks (up to 12); 0-game-tick activation delay (in the sense of instant scheduling, per the timing rules described there).

### Dispenser
- Appearance: a gray stone block with a hole in the front face.
- Traits: hardness 3.5, requires a pickaxe; when activated, launches an item out its front (arrows, snowballs, water buckets, TNT, bone meal, spawn eggs, etc. each have unique behaviors); a "throwing"-type container (9 slots); can be fed by/emptied via hoppers; each redstone activation dispenses one item.

### Dropper
- Appearance: a gray stone block with a hole in the front face (visually similar to a dispenser).
- Traits: hardness 3.5, requires a pickaxe; when activated, drops a single item out its front (no special behavior); a 9-slot container; used for item transport and sorting.

### Hopper
- Appearance: a metal funnel, wide at the top and narrow at the bottom, with an output spout on the side.
- Traits: hardness 3, requires a pickaxe; **pulls one item per game tick (20 times per second) from a container/entity above it**, and transfers it downward/sideways into another container; can be locked by a redstone signal (stops while activated); the core mechanism for item transport and sorting; **hoppers are a significant performance cost** (see the tick performance section in `mechanics/tick.md`). A comparator facing a hopper reads its fill level using the standard container fullness formula (see `redstone-blocks-and-components.md` → "Container Fill-Level Formula") — **not** simply item-count-divided-by-5; with only 5 slots, a hopper is commonly used for precise single-item counters by padding 4 slots with non-stackable filler items.

### Dropper/Dispenser and Comparators
- Dispensers/droppers (9 slots) can have their fill level read by a comparator using the same standard container fullness formula (see `redstone-blocks-and-components.md`), not a simple linear item count.

### Note Block
- See `functional-blocks.md`; produces a note when activated by redstone; a comparator can read its pitch (1–25).

### Target Block
- See `functional-blocks.md`; outputs a signal (0–15) when struck by a projectile.

### Bell
- Outputs a redstone signal (1 redstone tick) when struck; see `functional-blocks.md`.

### Doors / Trapdoors / Fence Gates / Pressure Plates / Buttons
- All can be opened/closed by a redstone signal; doors and similar blocks open while the signal is active (staying open as long as the signal holds) and close automatically once the signal is removed (the same applies to trapdoors).

### TNT
- Appearance: a block with red-and-white striped texture, flashing once activated.
- Traits: hardness 0, any tool (mining it is not recommended, as it may detonate); activated by a redstone signal, fire, an explosion, or a burning projectile, turning into a movable TNT entity that explodes 40 game ticks (4 seconds) later; blast strength 4; a dispenser also ignites it when activated. The `unstable` block state: when `true`, any block update directly ignites it (generally not used in blueprints unless building a trap).

### Daylight Detector
- Appearance: a wooden disc-shaped sensor with its sensing face pointed at the sky.
- Traits: hardness 0.2, axe is fastest; outputs a signal based on the sun's altitude (15 at noon, weaker at sunrise/sunset, 0 at night); right-click to switch to "inverted mode" (outputs at night, 0 during the day); weakened by rain/thunderstorms; must have a clear view of the sky (no opaque block above it); can only directly power adjacent redstone dust/components, not power the block it sits on.

### Sculk Sensor / Calibrated Sculk Sensor
- Appearance: a dark teal block with veins that glow briefly and emit a signal when a vibration is detected.
- Traits: a Deep Dark-related block; `sculk_sensor` detects vibration events within an 8-block radius (footsteps, block placement/breaking, opening containers, projectile impacts, etc.), with different vibration types mapping to different output strengths (1–15); enters a 40-game-tick cooldown after detecting; sneaking players are not detected; wool-type blocks can block vibration propagation. The calibrated variant `calibrated_sculk_sensor` extends detection range to 16 blocks and can be tuned via a side redstone signal to respond only to a specific vibration frequency, useful for more precise triggers.

## Signal Transmission and Insulation

- **Redstone conductors**: most full blocks can be powered (strongly/weakly), and signals can pass across blocks through them; see `blocks-overview-and-common-traits.md`.
- **Non-conductors**: glass, glowstone, ice, wool, pistons, hoppers, stairs, slabs, walls, fences, signs, etc. don't conduct signals; however, they can still be **strongly powered** (e.g. by a repeater pointing at them) to activate adjacent components (this "bridging" behavior needs case-by-case analysis).
- Non-full blocks like stairs/slabs: signals can't pass through them; redstone dust placed on top of or below them follows specific placement rules.

## Common Redstone Block States Quick Reference

| Component | Activation output | Delay | Waterloggable |
|---|---|---|---|
| Redstone dust | 15 (decaying) | 0 | No (washed away) |
| Redstone torch | 15 | 1 redstone tick to go out/relight | No |
| Lever/Button | 15 | 0 | Button can be waterlogged |
| Tripwire hook | 15 | 0 | Yes |
| Redstone lamp | — | 0 | No |
| Piston | — | 0 (near-instant) | No |
| Dispenser/Dropper | — | 1 game tick response delay | No |
| Hopper | — | 1 game tick per transfer | No |
| Target block | 0–15 | 2 game ticks | No |
| Lightning rod | 15 (8-game-tick pulse) | 0 | No |
