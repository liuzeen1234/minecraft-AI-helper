---
title: Tick - Game Tick, Chunk Tick, Random Tick, Scheduled Tick, Redstone Tick, Piston Tick
version: 1.20.4
category: Mechanics
keywords: [tick, game tick, redstone tick, random tick, scheduled tick, piston tick, TPS, MSPT]
summary: Definitions, trigger conditions, and processing order for game ticks, chunk ticks, random ticks, scheduled ticks, redstone ticks, and piston ticks; TPS/MSPT lag-related factors.
source: Chinese Minecraft Wiki (Fandom mirror), retrieved 2026-09-13; content paraphrased, condensed, and translated for this reference doc rather than reproduced verbatim.
---

# Tick

Minecraft runs on a large program loop, and one cycle of that loop is called a **tick**.

## Game Tick

The vast majority of the game's calculations happen within one big loop; one pass through that loop is one **game tick (gt)**.

- By default the game runs at 20 ticks per second, so one tick is 0.05 seconds (50 milliseconds).
- **Ticks per second (TPS)** measures how fast the game is actually running; the target value can be changed with `/tick rate`. TPS drops if the hardware can't keep up.
- **Milliseconds per tick (MSPT)** is how long the server actually takes to process one tick. TPS can only stay at 20 as long as MSPT stays at or below 50. The Java Edition debug screen (F3) shows MSPT, and Alt+F3 shows a TPS graph.

Common causes of server-side lag:

- Hoppers constantly checking whether there's an item above them — covering them with a container block or a composter stops that check, or you can transport loose items via water flow instead.
- Redstone contraptions, especially redstone dust, which can trigger a large number of block updates and light updates; turning off idle contraptions and clocks, and reducing open air space, can help.
- Mob AI — controlling mob spawns with lighting, and using more efficient farming/breeding setups, both help.

### Game Loop Order (Java Edition, processed in this order each cycle)

1. Run functions tagged with `tick` or `load`.
2. For each dimension, in the order Overworld, Nether, End:
   - Send the time to clients; update the world border; process weather logic; process player sleep logic.
   - If it's the Overworld: advance the in-game time and day count; run scheduled functions.
   - For each loaded chunk: send chunk data to clients; run chunk tick logic.
   - Attempt to spawn phantoms, illagers, cats, and zombie sieges.
   - Send entity changes to clients; attempt to unload chunks.
   - Process scheduled ticks (block scheduled ticks, fluid scheduled ticks).
   - Process raid logic; attempt to spawn a wandering trader; process block events; process entities; process block entities.
3. Process player entities.
4. If 6000 ticks have elapsed, attempt an autosave.
5. Process incoming packets from clients.

## Chunk Tick

Every game tick, a chunk tick is processed for certain chunks:

- Java Edition: chunks that are force-loaded, **and** whose center is within 128 blocks horizontally of a non-spectator player, are processed once per game tick (Y-axis position doesn't matter).
- Bedrock Edition: every loaded chunk is processed every game tick.

Behaviors triggered by a chunk tick:

- Natural mob spawning.
- During a thunderstorm, a 1/100,000 chance of a lightning strike occurring somewhere in the chunk.
- Each column's topmost block has a 1/16 chance of a weather update check: freezing in cold biomes, snow accumulating on snow layers (including cauldrons collecting snow/rainwater).
- A set number of blocks in the chunk receive a random tick (see below).

## Random Tick

- Java Edition: every game tick, each chunk section randomly selects `randomTickSpeed` blocks (default 3, adjustable via `/gamerule randomTickSpeed`) and gives each a random tick. The same block can be picked more than once.
- Bedrock Edition: every game tick, each chunk randomly selects `number of sections × 2.5 × randomTickSpeed` blocks for a random tick (default 1).

Block behaviors driven by random ticks:

- Crop growth/uprooting; mushroom spreading/uprooting; vine spreading.
- Fire starting or spreading.
- Ice and snow layers melting; leaves decaying; farmland hydration updates.
- Growth of cactus, sugar cane, kelp, bamboo, chorus flowers, and sweet berry bushes.
- Grass blocks and mycelium spreading or reverting.
- Saplings growing into trees; lava igniting nearby blocks.
- Lit redstone ore going dark; Nether portal blocks possibly spawning a zombified piglin.
- Turtle eggs cracking/hatching; campfires producing smoke.
- Budding amethyst growing amethyst clusters; copper blocks further oxidizing; dripstone filling a cauldron below it.

In Java Edition, the median interval between two random ticks on the same block is 47.30 seconds (946.03 game ticks), and a block is updated on average once every 68.27 seconds (1365.33 game ticks).

## Scheduled Tick

A block can request to update itself on a specific future game tick — this is called a **scheduled tick**, used for changes that are guaranteed to happen and are predictable (such as a redstone repeater's state change one tick later, or water's flow update).

Java Edition has two categories of scheduled ticks, processed first by priority and then by scheduled order (lower priority number runs first):

- Redstone repeater: priority -3 if it points at the back/side of a redstone diode; priority -2 if it's about to go dark; otherwise priority -1.
- Redstone comparator: priority -1 if it points at the back/side of a redstone diode.
- All other block scheduled ticks: priority 0.
- Fluid scheduled ticks are processed after block scheduled ticks (no priority tiers, only scheduled order).

Processing caps: Java Edition processes at most 65,536 scheduled ticks per game tick; Bedrock Edition processes at most 100 per chunk per game tick.

## Redstone Tick

A **redstone tick (rt)** equals 2 game ticks, roughly 1/10 of a second, originally defined as the delay of a repeater set to its shortest setting.

- Bedrock Edition: the redstone tick is a real, concurrently existing game mechanism that directly affects how redstone signals propagate.
- Java Edition: the redstone tick isn't an actual separate mechanism — it's a term the community coined because most redstone component delays happen to be multiples of 2 game ticks.

## Piston Tick (Java Edition only)

Under the "instant update" theory, a piston's activation delay is considered 0, and the span from the entity-processing phase to the end of the block-event phase is treated as one game tick — this way of dividing up the tick is called a **piston tick**. A note block's sound-emission delay follows the same timing as a piston (0 piston ticks). The delay of scheduled-tick components, measured in piston ticks, isn't fixed.
