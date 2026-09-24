---
title: Pistons and Sticky Pistons (Push/Pull Mechanics, Activation Delay, Movable Block List)
version: 1.20.4
category: Blocks
keywords: [piston, sticky piston, push pull, activation delay, quasi-connectivity, instant placement]
summary: Crafting, activation methods, activation delay, block push/pull structure rules, and immovable conditions for pistons/sticky pistons, including a Java Edition block push/pull behavior table.
source: Chinese Minecraft Wiki (Fandom mirror), retrieved 2026-09-13; content paraphrased and translated for this reference doc. The original source text was cut off at the "technical components" section, so sections on sounds and data values are missing.
---

# Pistons

A **piston** is a block that, when activated by a redstone signal, can push most blocks and entities.

A **sticky piston** functions the same as a regular piston, but can also pull back the block it moved once the redstone signal ends; a regular piston simply leaves the block in place.

## Base Attributes

| Attribute | Value |
|---|---|
| Hardness | 1.5 |
| Blast resistance | 0.5 |
| Correct mining tool | Pickaxe |
| Redstone conductor | No |
| Suffocates mobs | While retracted: yes; while extended: no |
| Max stack size | 64 |
| Renewable | Yes |

## Spawning and Obtaining

- Regular pistons don't generate naturally in the world.
- Natural generation: 3 sticky pistons generate inside jungle temples; 5 sticky pistons generate in the central redstone room of ancient cities.
- Crafting: 3 planks + 4 cobblestone + 1 iron ingot + 1 redstone dust → 1 piston.
- Sticky piston: 1 piston + 1 slimeball → 1 sticky piston.
- Breaking: the correct tool is a pickaxe; a piston mid-movement can't be mined. Breaking a piston/sticky piston drops itself and destroys its extended piston head; breaking the piston head destroys the piston that spawned it; breaking a piston mid-movement drops the corresponding block.

## Placement and Activation

- A piston always faces the player when placed. Attachable blocks (like levers) can be placed on top of a piston or sticky piston, but not on the face of an activated piston.
- Once activated, the piston head extends after the activation delay and moves the block in front of it; once deactivated, the piston head retracts (a sticky piston will attempt to pull back the block in front of it).
- A sticky piston is only sticky while retracting; a block resting on its head while stationary isn't stuck to it.

Activation methods:

- An active redstone wire pointing at the piston (unless the piston faces the wire).
- An adjacent powered block (strongly or weakly powered), unless the piston faces that block.
- An adjacent redstone torch, unless the piston faces the torch or the torch is attached to the piston.
- A repeater, comparator, or observer that points at the piston and outputs a signal, unless the piston faces that component.
- Any other adjacent, activated power source, unless the piston faces that component.
- **Quasi-connectivity** (Java Edition only): a piston can be activated by anything capable of activating the block space directly above it, regardless of what block occupies that space or which way the piston faces.
- A piston itself isn't a redstone conductor and can't have power conducted through it.

## Activation Delay

The delay from activation to the piston actually beginning to extend/retract:

- Bedrock Edition: a fixed 2 game ticks (0.1 seconds).
- Java Edition:
  - If the piston is activated during a scheduled tick or a block event → 0 game ticks delay (activates the same tick).
  - If the piston is activated due to an entity, block entity update, or player action → 1 game tick delay (activates the next tick).

## Block Movement Structure Rules

A block will attempt to be **pushed** when:

- It's positioned in front of a piston that's about to extend.
- It's positioned in front of another block that's about to be pushed, in the direction of that block's movement.

A block will attempt to be **pulled** (only for sticky pistons/slime blocks/honey blocks) when:

- It's positioned in front of a sticky piston head that's about to retract.
- It's positioned to the side or behind a slime block/honey block that's about to move, in the direction of that block's movement.

If a block meets both the push and pull conditions simultaneously, it's treated as being pushed.

Block push/pull behavior categories:

- Can be pushed and can be pulled.
- Can be pushed but can't be pulled.
- Gets destroyed when pushed, and can't be pulled.
- Can't be pushed and can't be pulled.

### Common Java Edition Block Push/Pull Behaviors

| Block category | Behavior |
|---|---|
| Barrier, beacon, bedrock, command block, enchanting table, End portal/gateway, ender chest, grindstone, jigsaw block, jukebox, light block, lodestone, mob spawner, a piston mid-movement, Nether portal, obsidian, an extended piston, piston head, reinforced deepslate, respawn anchor, sculk-family blocks, structure block | Can't be pushed or pulled |
| Barrel, beehive, bee nest, blast furnace, brewing stand, chest, chiseled bookshelf, daylight detector, dispenser, dropper, furnace, hopper, lectern, smoker, trapped chest | Can't be pushed or pulled (Java Edition); pushable/pullable in Bedrock Edition |
| Glazed terracotta | Can be pushed, can't be pulled |
| Carpet | Can be pushed and pulled; destroyed and drops as an item when pulled downward |
| Bell, candle, lantern, soul lantern | Destroyed when pushed (may drop an item), can't be pulled |
| Amethyst cluster, bamboo, bed, button, cactus, cake, carved pumpkin, cocoa pod, door, fire, flower pot, mob head, item frame, jack o'lantern, ladder, lava, leaves, lever, lily pad, melon, moss block, mushroom, nether wart, painting, dripstone, pressure plate, redstone comparator/dust/repeater/torch, sapling, scaffolding, sea pickle, shulker box, snow, sugar cane, torch, tripwire, tripwire hook, turtle egg, vines, water, and many other "attachable/non-solid" blocks | Destroyed when pushed and may drop an item, can't be pulled |
| Anvil | Can't be pulled; can only be pushed while in a falling state |
| Concrete powder, gravel, red sand, sand | Can be pushed and pulled normally, but falls instead of sticking to a sticky piston if unsupported below |
| Rail | Can be pushed and pulled normally, but is destroyed and drops as an item if unsupported below |

> Note: a piston itself can't be pushed or pulled while extending/retracting or already extended; it can be pushed or pulled while retracted. Slime blocks and honey blocks can't pull each other. In Java Edition, all block entities can't be pushed or pulled.

### Conditions Where a Block Can't Be Moved

In the following cases, a piston can't push a block (a sticky piston can't pull a block back, but can still retract its own head):

- The total number of blocks in the movement structure exceeds 12.
- A sticky piston attempts to pull back an unpullable block.
- Moving the block structure would push it beyond the world's height or depth limit.
- An unpushable block blocks the way in front of the movement direction.

### Timing Details

- When a block structure moves, any attachable blocks on it are destroyed, as are any destructible blocks in the way in front of the push.
- The block structure typically arrives at its new position after 2 game ticks (0.1 seconds); in certain cases it can arrive faster, known as **instant placement**.
- If a sticky piston receives a positive pulse signal shorter than 3 game ticks (0.15 seconds), it may fail to pull back the block it had pushed out once the signal ends.
- Extending/retracting a piston produces a sound audible within a 31×31×31 area centered on itself.

## Pushing Entities

- A moving block structure exerts a push force on entities within its range, carrying them along.
- If an entity can't move, the block may push into the entity; if that block is a suffocating block, it can cause a mob to suffocate to death.
- The vast majority of entities can be pushed; area effect clouds, armor stands with `Marker=true`, display entities (Java Edition only), interaction entities (Java Edition only), and markers (Java Edition only) can't be pushed.
- An entity pushed by a moving slime block gets launched away at higher speed.
