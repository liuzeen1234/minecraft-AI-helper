---
title: Tick Applications and Timing Cheatsheet (Delay Conversion, Common Timings, Scheduled Tick Examples, Zero-Tick and BUD, /tick Command)
version: 1.20.4
category: Mechanics
keywords: [tick, delay, timing, redstone tick, scheduled tick, zero-tick, BUD, tick command]
summary: A practical cheatsheet for applying tick knowledge to redstone and contraptions - unit conversions, component delays, scheduled tick examples, zero-tick theory, and /tick command usage.
---

# Tick Applications and Timing Cheatsheet

> Target version: Java Edition 1.20.4. For base definitions (game tick/chunk tick/random tick/scheduled tick/redstone tick/piston tick), see `tick.md`. This document provides application-level conversion tables, timing examples, and usage for the `/tick` command added in 1.20.3+.

## Unit Conversion Quick Reference

| Unit | Duration | Relationship |
|---|---|---|
| Game tick (gt) | 0.05 seconds (50ms) | 1 second = 20gt |
| Redstone tick (rt) | 0.1 seconds (100ms) | 1rt = 2gt |
| Piston tick | 0.05 seconds | Equal to 1gt (divided by block-event phase) |
| 1 second | 20gt | 10rt |
| 1 minute | 1200gt | 600rt |
| 1 in-game day (day/night cycle) | 24000gt = 20 minutes | — |

## Common Redstone Component Delay Table (Java Edition 1.20.4)

| Component | Delay | Notes |
|---|---|---|
| Redstone dust | 0 | Signal propagates instantly (no delay) |
| Redstone torch | 1rt (2gt) | 1rt each to go out/relight |
| Redstone repeater | 1–4rt (2–8gt) | Adjustable setting, right-click to cycle |
| Redstone comparator | 1rt (2gt) | Same for back/side input |
| Observer | 1rt (2gt) | Fixed at 1rt in Java Edition |
| Piston (push/pull completion) | 0–2gt | Activates instantly; completes within ~2gt (see piston tick theory in `tick.md`) |
| Dispenser/Dropper | 1gt | Dispenses an item 1gt after activation |
| Lever/Button | 0 | Lever 0; wooden button 30gt pulse, stone button 20gt pulse |
| Pressure plate | 0 | Resets about 1rt (2gt) after the entity leaves |
| Hopper | Transfers 1 item every 8 ticks | Attempts to pull/transfer once every 8gt |
| Note block | 0 | Plays immediately when activated (activation delay matches a piston's) |
| Door/Trapdoor/Fence gate | 0 | Opens/closes instantly |
| Target block | 2gt | Outputs 2gt after being hit |
| Lightning rod | 0 (8gt pulse) | Outputs an 8gt signal when struck |
| Copper bulb (1.21 experimental) | 1gt to activate | Latches state, toggles on pulse |

## Signal Propagation and Decay

- Redstone dust signal strength starts at 15, decays by 1 per block, and stops once it hits 0; maximum signal travel distance is 15 blocks.
- Redstone repeaters/comparators can "relay" a signal back up to full strength 15.
- Strong power vs. weak power: see `blocks-overview-and-common-traits.md` (repeater/comparator outputs are strong power, redstone dust output is weak power).

## Scheduled Tick Examples (behavior timing)

- **TNT**: explodes 4 seconds (80gt) after activation.
- **Sand/Gravel**: becomes a falling block entity once the block below it is destroyed or it's pushed by a piston, then turns back into a placed block on landing.
- **Water and Lava**: fluid flow is processed via scheduled ticks (flow direction updates every gt; see the Blocks docs for flow speed).
- **Redstone Repeater**: in Java Edition, a repeater's state change has a 1rt delay, with "going dark" at priority -2 and "lighting up" at priority -1 (see the priority table in `tick.md`).
- **Command Block (repeat type)**: executes once per game tick.
- **Scheduled tick priority** (Java Edition): -3 (repeater pointing at the back/side of a redstone diode) → -2 (repeater going dark) → -1 (repeater lighting up, or comparator pointing at a diode) → 0 (everything else). Up to 65,536 scheduled ticks are processed per tick.

## Zero-Tick and BUD

### Zero-Tick
- Within the first 0–1gt of a piston extending (during the block-event processing phase), the piston head hasn't fully arrived yet, but "instant update" components like observers/repeaters have already responded — this can produce a zero-tick pulse or zero-tick movement.
- Common applications: zero-tick sugar cane/bamboo/basalt farms (most of these have been patched as of 1.16+ for crop farms specifically, though zero-tick piston-chain movement is still usable for entity/block "teleport" contraptions — verify against the specific version).
- Note: "zero-tick" is a community-coined term (based on the instant-update theory); see the piston tick section in `tick.md` for details.

### BUD (Block Update Detector)
- Uses observers/pistons, etc. to detect "unexpected block updates" as a way of sensing changes.
- Java Edition 1.20.4: observers detect **block state changes** (not generic updates), making them the primary BUD method; piston-based BUD (quasi-connectivity) has largely been patched since 1.19+, though some quasi-connectivity scenarios still exist in 1.20 and should be tested case by case.
- Uses: detecting crop growth, TNT activation, otherwise-invisible updates, etc.

## Random Tick Applications (Crops and Copper Oxidation)

- Default `randomTickSpeed=3`: each chunk section randomly selects 3 blocks per gt. See `tick.md`.
- Average crop growth cycle (Java Edition, factoring in light/hydration conditions): wheat takes roughly 5–35 minutes; the exact figure varies heavily with conditions; the median random tick interval is 47.3 seconds.
- Copper oxidation: each random tick has a chance of advancing to the next oxidation stage (if unwaxed); applying wax with honeycomb locks the current stage.
- `/gamerule randomTickSpeed 0` disables random ticks (crops stop growing, copper stops oxidizing); setting it to 0 does NOT prevent phantom spawning, since phantom spawning isn't governed by random ticks.

## The /tick Command (added in 1.20.3+, available in 1.20.4)

- Purpose: lets admins debug tick execution and performance; **requires permission level 2+**; not usable from command blocks or data packs by default.
- Syntax:
  - `/tick query`: queries the current target tick rate and performance info.
  - `/tick rate <value>`: sets the target TPS (e.g. `20`, `100`, `1`); the game advances at this rate.
  - `/tick freeze`: freezes game ticks (entities/blocks stop updating; players can still move and place blocks).
  - `/tick step <count>`: only usable while frozen; advances the specified number of ticks one at a time, then freezes again (for debugging).
  - `/tick step stop`: stops stepping and returns to the frozen state.
  - `/tick unfreeze`: unfreezes, resuming normal operation of all game systems.
- Note: changing TPS affects every tick-dependent mechanism (redstone, crops, spawning), so it should only be used for testing/debugging; setting `/tick rate` too high can cause TPS to drop (a performance bottleneck).

## TPS and MSPT in Practice

- Normal operation is 20 TPS; each tick has a 50ms budget. TPS automatically drops once MSPT exceeds 50ms.
- The debug screen (F3) shows MSPT ("ms ticks"); Alt+F3 shows a TPS graph.
- Lag troubleshooting: check hopper counts, redstone clocks, large amounts of mob AI, and chunk generation (see the common causes listed in `tick.md`).
- Server commands: `/perf` (performance profiling), `/debug` (debug recording), `/jfr` (JFR profiling) — see the command list docs.
