---
title: Redstone Circuit Design and Logic Gates (Logic Gates, Pulse Circuits, Clock Circuits, Storage Circuits, Common Circuit Patterns)
version: 1.20.4
category: Blocks
keywords: [logic gate, NOT gate, OR gate, AND gate, NOR gate, XOR gate, XNOR gate, pulse circuit, clock circuit, latch, T flip-flop, counter, BUD, redstone circuit]
summary: Design patterns and building techniques for logic gates, pulse/clock/storage circuits, and common practical circuit patterns built from basic redstone components, including a clock-startup technique.
---

# Redstone Circuit Design and Logic Gates

> Target version: Java Edition 1.20.4. This document covers circuit design patterns and building techniques; the block states and basic behavior of individual components are covered in `redstone-blocks-and-components.md`, `redstone-mechanism-blocks.md`, and `pistons.md`; timing values are covered in `mechanics/tick-applications-and-timing-cheatsheet.md`.

## Basic Logic Gates

- **NOT gate (inverter)**: a redstone torch attached to a block that gets powered by the input signal. Input ON → that block gets powered → the torch goes out → output OFF; input OFF → the torch relights → output ON. This is the most fundamental inverting component in redstone circuits.
- **OR gate**: two or more signal lines merge directly into the same redstone dust line; if any input is high, the output is high.
- **AND gate**: each of the two input signals passes through its own NOT gate, then both feed into a NOR gate, and inverting that result once more yields AND (alternatively, a comparator can be used to implement a combined-logic version where output is only high when both inputs are full strength).
- **NOR gate**: the two inputs merge first (OR), then the merged signal passes through a NOT gate.
- **XOR gate**: implemented using a redstone comparator's "subtract mode": A minus B or B minus A, outputting whenever either result is > 0, and 0 when both inputs are equal.
- **XNOR gate**: the output of an XOR gate passed through an additional NOT gate.

## Pulse Circuits

- **Pulse extender**: multiple repeaters in series, each adding 1–4 redstone ticks of delay, used to lengthen a short pulse or offset timing between stages.
- **Single pulse / edge detector**: the signal is delayed through a repeater, then compared against the original (undelayed) signal using a comparator in subtract mode; the output is non-zero only at the instant the signal changes, producing a single short pulse (useful for converting a held/continuous signal into a "fires once" pulse).
- **Pulse limiter**: a comparator with a side input restricts the width of the main signal's pulse, preventing an overly long response from a held input.
- **Monostable circuit**: outputs a fixed-width pulse regardless of how long the input signal is held, useful for normalizing pulse durations coming from different input sources.

## Clock Circuits

- **Repeater ring clock**: N repeaters connected end-to-end in a closed loop; the shortest version uses 2 repeaters for a 4-redstone-tick oscillation period.
- **Observer face-to-face clock**: two observers facing each other will continuously trigger one another, forming a clock with a period of about 2 redstone ticks (0.2 seconds) — the fastest stable clock in redstone, and **it oscillates automatically as soon as it's placed, requiring no separate startup step**.
- **Hopper clock**: two hoppers transferring items back and forth, with a comparator reading the changing item count to produce a periodic signal; slower, but the period is adjustable.
- **Piston clock**: a sticky piston repeatedly pushes and pulls a block, and the block's back-and-forth motion produces a periodic signal change.

### ⚠️ Clock Circuit Startup Mechanism (redstone torch usage caveat)

This is an easy pitfall worth calling out explicitly:

- **The wrong way**: placing a `redstone_torch` directly on the redstone dust of a ring circuit. A redstone torch constantly outputs a full-strength (15) signal, which keeps the whole loop continuously powered, so every repeater's input stays at a constant high level — no rising or falling edge ever occurs, and the circuit can never oscillate; it just becomes a static, always-on wire.
- **The correct way (NOT-gate feedback startup)**:
  1. Place a solid block (such as `stone`) next to the loop.
  2. Attach a `redstone_wall_torch` to the side of that block (facing toward the loop).
  3. The torch's signal enters the loop through redstone dust/repeaters.
  4. After the signal travels once around the loop, it powers the block the torch is attached to → the torch goes out → the loop's signal disappears → the block loses power → the torch relights → and the cycle repeats.

  This forms the oscillating cycle: "torch lit → loop conducts → block gets powered → torch goes out → loop breaks → block loses power → torch relights", which is the key technique for making a redstone torch participate in a clock circuit.
- **Alternative**: if you'd rather not deal with startup logic at all, use an observer face-to-face clock instead — it's already oscillating the moment it's placed, with no startup circuit needed.
- **Underlying principle**: a redstone torch is fundamentally a NOT gate (input ON → output OFF). For it to participate in oscillation, the circuit must be able to turn the torch itself off (by powering the block the torch is attached to) — you cannot simply place the torch directly on the redstone line it's driving.

## Storage Circuits

- **RS latch**: two NOR gates cross-feeding into each other, with separate Set and Reset inputs; it remembers whichever state it was last set or reset to, until the next input changes it.
- **T flip-flop**: a sticky piston repeatedly pushing and pulling a redstone block, toggling the output state once per pulse received.
- **D latch**: a data storage element with a separate "enable" input; it only updates its stored value while enabled, and holds its value the rest of the time.
- **Counter**: uses the accumulated item count inside a hopper, read via a comparator, to implement simple counting and threshold-triggered behavior.

## Common Practical Circuit Patterns

- **Automatic farm core**: an observer detects when a crop has matured (a block state change) → triggers a piston to harvest it → a hopper collects the drops. This is the basic skeleton behind most automated farms.
- **Item sorter**: a chain of hoppers combined with comparators reading item counts in containers, plus redstone torches for inverted logic, to route items by type.
- **Hidden door / piston door**: an array of pistons, with repeaters offsetting their timing, retracting or extending blocks in sequence to make an entire wall "disappear" or "appear".
- **Combination lock**: multiple levers or buttons combined through an AND gate, so the output only fires when the specific correct combination is satisfied.
- **BUD (Block Update Detector)**: uses a piston's quasi-connectivity or an observer to detect block updates that would otherwise go unnoticed; this is the detection technique behind many hidden mechanisms and automated devices.

## Guidance for Complex Redstone Machines

The following types of machines are structurally complex and vary significantly between versions; they should not be built from memory. Confirm the latest working design first: World Eater, TNT Duper, Flying Machine, fully automatic farms, iron farms, mob-grinding towers, 3x3-or-larger piston doors, hidden staircases, automatic brewers, TNT cannons, redstone computers, and similar builds. These designs often rely on version-specific update quirks or extremely precise timing, so text descriptions are error-prone — it's best to work from a verified up-to-date tutorial or schematic data.
