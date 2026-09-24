---
title: Command List - Data and Execute Commands (execute, data, scoreboard, tag, team, bossbar, fill, clone, etc.)
version: 1.20.4
category: Commands
keywords: [execute, data, scoreboard, tag, team, bossbar, fill, clone, function, return, damage]
summary: Syntax and examples for conditional execution and data manipulation commands - the full execute family, NBT data, scoreboards, tags, teams, fill/clone, and more.
---

# Command List - Data and Execute Commands

> Target version: Java Edition 1.20.4.

## `/execute` (conditional execution, the most powerful command)

### Basic Syntax
- `/execute <subcommands>... run <command>`: runs a command after transforming the "executor, position, facing" through the chain of subcommands.
- Subcommands can be chained in any combination; if a condition is not met, the whole command does not run (unless it's an `if/unless` failure case).

### Core Subcommands
| Subcommand | Effect | Example |
|---|---|---|
| `as <entity>` | Sets the executor to an entity | `as @e[type=zombie]` |
| `at <entity>` | Sets the position to an entity's position | `at @s` |
| `positioned <coords>` | Sets the position | `positioned 0 64 0` |
| `positioned as <entity>` | Sets position to an entity | `positioned as @p` |
| `positioned over <height>` | 1.20.3+, gets the block position at a given height above | `positioned over 64` |
| `anchored <eyes|feet>` | Anchors to eyes/feet | `anchored eyes` |
| `facing <coords>` / `facing entity <entity>` | Sets facing direction | `facing 0 64 0` |
| `rotated <angle>` / `rotated as <entity>` | Sets rotation angle | `rotated 0 90` |
| `align <axis>` | Rounds coordinates (x/y/z/xyz) | `align xz` |
| `in <dimension>` | Switches dimension (overworld/the_nether/the_end) | `in the_nether` |
| `on <relation>` | Executes as a related entity (attacker/owner/vehicle/...) | `on vehicle` |
| `if <condition>` / `unless <condition>` | Conditional check | `if entity @e[type=creeper]` |
| `store <storage>` | Stores the command's result as a value | `store result score @s obj` |
| `summon <entity> <coords>` | 1.20.2+, directly spawns an entity | `summon zombie ~ ~1 ~` |

### Condition Types (if/unless)
- `if entity <entity>`: entity exists.
- `if block <coords> <block>`: block matches (supports states `[lit=true]` and tags `#minecraft:...`).
- `if blocks <region> <position> <mode>`: region block matching (all/masked).
- `if score <target> <target> <op> <source>`: scoreboard value comparison (matches `<range>` or `<comparison>`).
- `if data block|entity|storage <target> <path>`: NBT data exists.
- `if predicate <predicate>`: data pack predicate check.
- `if loaded <coords>` / `if biome <coords>` (1.19.4+): chunk loaded / biome check.

### Store Types
- `store result score <entity> <objective>`: stores an integer result.
- `store result|success bossbar <id> <value|max>`: stores into a boss bar.
- `store result|success block <coords> <path> <type> <scale>`: writes to block NBT.
- `store result|success entity <entity> <path> <type> <scale>`: writes to entity NBT.
- `store result|success storage <storage id> <path> <type> <scale>`: writes to storage.
- Types: byte/short/int/long/float/double.

### Typical Usage
- `execute as @a at @s run say hi` (every player says hi).
- `execute if entity @a[distance=..10] run say A player is nearby`.
- `execute store result score @s x run data get entity @s Pos[0]` (stores the X coordinate into a scoreboard).
- `execute at @e[type=zombie] run summon lightning_bolt ~ ~ ~` (strikes lightning at every zombie).
- `execute as @a[nbt={SelectedItem:{id:"minecraft:stick"}}] run effect give @s speed 5` (grants speed to anyone holding a stick).

## `/data` (NBT data read/write)

- Syntax:
  - `/data get block|entity|storage <target> [path] [scale]`
  - `/data merge block|entity|storage <target> <NBT>`
  - `/data remove block|entity|storage <target> <path>`
  - `/data modify block|entity|storage <target> <path> <operation>`
- Examples: `/data get entity @e[type=zombie,limit=1] Health`, `/data merge block ~ ~-1 ~ {CustomName:'"Renamed"'}`.
- Note: `/data` modifications don't trigger block updates (detector rails/comparators won't detect NBT changes other than block state changes).

## `/scoreboard`

### Objectives
- `/scoreboard objectives add <name> <criteria> [display name]`: creates a scoreboard objective.
- Criteria: `dummy` (manual), `health`, `kills`, `deaths`, `totalKillCount`, `playerKillCount`, `minecraft.used:<item>`, `minecraft.mined:<block>`, `minecraft.crafted:<item>`, `minecraft.broken:<item>`, `minecraft.killed:<entity>`, `minecraft.killed_by:<entity>`, `minecraft.custom:<stat>` (e.g. `minecraft.custom:minecraft.walk_one_cm`), `teamkill.<color>`, `trigger`, etc.
- `/scoreboard objectives remove <name>`, `list`, `setdisplay <slot> <name>` (slots: list/sidebar/belowName).
- 1.20.3+ supports scoreboard display names (`setdisplay` can include colored text components, etc.).

### Player Score Operations
- `/scoreboard players add|remove|set|reset|get <player> <objective> <amount>`
- `/scoreboard players operation <target> <objective> <operation> <source> <objective>` (operations: +=, -=, *=, /=, %=, =, ><, <, >)
- Examples: `/scoreboard players set @p coins 100`, `/scoreboard players operation @a total += @s coins`.

### Tags and Listing
- `/scoreboard players list [player]`, `/scoreboard objectives list`, `/scoreboard players tag` (superseded by the standalone `/tag` command; use `/tag` in 1.13+).

## `/tag`

- Syntax: `/tag <entity> add|remove|list <tag>`.
- Examples: `/tag @e[type=zombie] add boss`, `/tag @s remove temp`.
- Permission level: 2.

## `/team`

- Syntax: `/team add <name> [display name]`, `/team remove|empty|join|leave|modify|list <...>`.
- `join` example: `/team join red @p`; `modify`: color, friendly fire, collision rules, death message, prefix/suffix, etc.
- Permission level: 2.

## `/bossbar`

- Syntax:
  - `/bossbar add <id> <display name>` (display name is JSON)
  - `/bossbar set <id> <max|value|name|color|style|visible|players> <...>`
  - `/bossbar remove <id>`
- Examples: `/bossbar add boss_health {"text":"Boss"}`, `/bossbar set boss_health players @a`.
- Permission level: 2.

## `/damage`

- Syntax: `/damage <entity> <amount> [<damage type> [at|by <source>]]`.
- Damage types: `minecraft:generic`, `minecraft:explosion`, `minecraft:fire`, `minecraft:fall`, `minecraft:wither`, `minecraft:drown`, etc.
- Example: `/damage @e[type=pig] 5 minecraft:lightning_bolt`.
- Permission level: 2.

## `/function` and `/schedule`

- `/function <function ID>`: runs a function from a data pack (`namespace:path` or `#tag`).
- `/function <function ID> {arguments}`: 1.20.2+ macro invocation (function arguments).
- `/schedule function <function ID> <delay> [append|replace]`: delayed execution (in game ticks); `/schedule clear <function ID>` cancels it.
- Example: `/schedule function mypack:tick 20` (runs after 1 second).
- Permission level: 2.

## `/return` (1.20.2+)

- Syntax: `/return <run <command>|fail|value <value>>`; returns from within a function (interrupts function execution and can return a value to `execute store`).
- Example: `/return value 1`.
- Permission level: 2.

## `/trigger`

- Syntax: `/trigger <objective> [add|set] <value>`; lets players trigger a scoreboard objective via command (requires `scoreboard objectives add x trigger`).
- Used for adventure map player interactions. Permission level: 0 (usable by players).

## `/fill`, `/clone`, `/setblock`, `/place`

### `/setblock`
- Syntax: `/setblock <coords> <block> [destroy|keep|replace]`.
- Examples: `/setblock ~ ~1 ~ minecraft:oak_log[axis=x]`, `/setblock ~ ~ ~ minecraft:air destroy`.
- Permission level: 2.

### `/fill`
- Syntax: `/fill <start> <end> <block> [replace|destroy|keep|outline|hollow] [filter]`.
- Examples: `/fill 0 60 0 10 70 10 minecraft:stone`, `/fill ~-5 ~-1 ~-5 ~5 ~-1 ~5 minecraft:water`, `/fill x1 y1 z1 x2 y2 z2 minecraft:oak_planks replace minecraft:dirt`.
- Block count limit: `/fill` defaults to 32768 blocks (errors if exceeded). Permission level 2.

### `/clone`
- Syntax: `/clone <source start> <source end> <destination> [replace|masked|filtered] [force|move|normal]`.
- Example: `/clone 0 60 0 10 70 10 20 60 20`.
- Permission level: 2.

### `/place`
- Syntax: `/place <feature|jigsaw|structure|template> <type> [coords]`.
- Examples: `/place structure minecraft:village_plains ~ ~ ~`, `/place feature minecraft:ruined_portal`.
- Permission level: 2. Replaces `/structure` as of 1.19.4+.

## `/fillbiome`

- Syntax: `/fillbiome <start> <end> <biome>`.
- Example: `/fillbiome 0 0 0 100 10 100 minecraft:desert`.
- Permission level: 2.

## `/loot`

- Syntax:
  - `/loot spawn <coords> <source>` (spawns dropped items)
  - `/loot give <player> <source>`
  - `/loot insert <container> <source>`, `/loot replace <entity> <slot> <source>`
- Sources: `kill <entity>`, `mine <block>`, `loot <loot table>`, `fish`.
- Examples: `/loot spawn ~ ~ ~ loot minecraft:chests/simple_dungeon`, `/loot give @p kill @e[type=creeper,limit=1]`.
- Permission level: 2.

## `/spreadplayers`

- Syntax: `/spreadplayers <center coords> <min distance> <max distance> <respectTeams> <entity>`.
- Example: `/spreadplayers 0 0 5 50 true @a`.
- Permission level: 2.

## `/attribute`

- Syntax: `/attribute <entity> <attribute> <get|base get|base set|modifier add|modifier remove|modifier value get> <...>`.
- Attributes: `minecraft:generic.max_health`, `minecraft:generic.movement_speed`, `minecraft:generic.attack_damage`, `minecraft:generic.armor`, `minecraft:generic.knockback_resistance`, `minecraft:generic.follow_range`, `minecraft:generic.flying_speed`, `minecraft:generic.luck`, `minecraft:generic.attack_knockback`, `minecraft:generic.attack_speed`, `minecraft:player.block_break_speed`, `minecraft:zombie.spawn_reinforcements`, etc.
- Example: `/attribute @e[type=zombie,limit=1] minecraft:generic.max_health set 50`.
- Permission level: 2.
