---
title: Command List Overview and Syntax Rules (1.20.4 full command index)
version: 1.20.4
category: Commands
keywords: [commands, command, syntax, selectors, permission levels, 1.20.4]
summary: Full command index for Java Edition 1.20.4, general syntax rules, target selectors, coordinates, and data formats.
---

# Command List Overview and Syntax Rules

> Target version: Java Edition 1.20.4. For detailed syntax and examples, see `command-list-player-and-entity-commands.md`, `command-list-data-and-execute-commands.md`, and `command-list-world-and-server-commands.md` in this folder.

## General Rules

- Commands start with `/` typed into the chat bar; singleplayer requires "Allow Cheats" to be enabled; servers require the corresponding permission level.
- Commands are case-insensitive (command and argument names can be lowercase), but resource IDs, NBT, and JSON text are case-sensitive.
- Most commands only run server-side; client commands (like `/help`) return directly.
- Command blocks (impulse/chain/repeat) and functions (data packs) can execute commands automatically; `@s` inside a command block refers to "the most recently executed entity/the block's owner" (in 1.13+ it's the "executor" at the command block's own location).

## Permission Levels (Java Edition)

| Level | Description |
|---|---|
| 0 | All players (chat commands like /help, /msg, /me, etc.; `/seed` requires level 2 on servers) |
| 1 | Rarely used (originally things like /give? most modern commands use level 2) |
| 2 | Cheat commands (/gamemode, /tp, /give, /summon, /setblock, etc.) |
| 3 | Server administration (/ban, /kick, /op, /whitelist, /tick, etc.) |
| 4 | Server owner (/stop, /save-off? /stop is level 4, /debug, etc.) |

- Common permission requirements: command blocks need level 2; `/tick`, `/ban`, `/kick`, `/op`, `/whitelist`, etc. need level 3; `/stop`, `/save-all`, `/debug`, etc. need level 4. See each section for the exact permission level of a given command (actual server permission configuration takes precedence).

## Target Selectors

| Selector | Meaning |
|---|---|
| `@p` | Nearest player |
| `@a` | All players |
| `@r` | Random player |
| `@e` | All entities |
| `@s` | The executor itself |
| `@n` | Nearest mob? — Bedrock Edition only, doesn't exist in Java. Do not include it! |

Java Edition only has @p/@a/@r/@e/@s. Selector arguments (in square brackets):
- `[type=zombie]`, `[type=!zombie]` (exclude), `[type=#minecraft:skeletons]` (entity tag)
- `[distance=..5]`, `[x=..,y=..,z=..]` (coordinates and ranges), `[dx=..,dy=..,dz=..]` (bounding box)
- `[limit=3]`, `[sort=nearest|furthest|random|arbitrary]`
- `[name="Name"]`, `[nbt={...}]`, `[scores={obj=..5}]`, `[tag=tagname]`, `[team=teamname]`
- `[gamemode=creative]`, `[predicate=namespace:predicate]`, `[advancements={...}]`
- Range syntax: `..5` (≤5), `5..` (≥5), `1..5` (1–5).

## Coordinate Syntax

- Absolute coordinates: `100 64 -200`.
- Relative coordinates: `~ ~ ~` (relative to the execution position, `~5` means +5).
- Local coordinates: `^ ^ ^` (relative to the executor's facing direction: forward/up/right; used with `execute`'s `facing`/`rotated`).
- Most commands (/setblock, /fill, /summon, /tp, etc.) accept coordinates; `~` can be shortened to `~ ~` (e.g. `/tp @s ~ ~5 ~`).

## Data Formats

### NBT (Named Binary Tag)
- Represented as SNBT (String NBT): `{key:value, list:[...]}`; e.g. `{powered:1b, Health:20f, Inventory:[...]}`.
- Type suffixes: `b` (byte), `s` (short), `l` (long), `f` (float), `d` (double), `i` (int, can be omitted); strings `"text"`; lists `[]`; compounds `{}`.
- Used by `/summon`, `/data`, `/give` (item NBT), `/item`, etc.

### JSON Text Components
- JSON format for chat/title/sign text: `{"text":"...", "color":"red", "bold":true}`; can nest `"extra":[...]`.
- Standard format: `{"text":"Hello","color":"gold"}`; use the escape character `\"`.
- Supports: color (color name or #RRGGBB), bold, italic, underlined, strikethrough, obfuscated, clickEvent, hoverEvent, insertion, translate (translation key), score, selector, keybind, nbt.
- A target selector inside a text component displays the entity's name: `{"selector":"@p"}`.

### Resource Locations
- Format `namespace:path` (e.g. `minecraft:zombie`, `minecraft:sharpness`); the namespace defaults to `minecraft` if omitted.
- Used for entity IDs, block IDs, item IDs, advancements, functions, entity tags, etc.

## Full 1.20.4 Command Index

| Command | Purpose | See |
|---|---|---|
| advancement | Grant/revoke advancements | Player & Entity |
| attribute | Read/modify entity attributes | Data & Execute |
| ban / ban-ip / banlist / pardon / pardon-ip | Server ban management | World & Server |
| bossbar | Create/manage boss bars | Data & Execute |
| clear | Clear a player's inventory | Player & Entity |
| clone | Copy a region of blocks | Data & Execute |
| continue | Resume a paused save (singleplayer debugging) | World & Server |
| damage | Directly damage an entity | Data & Execute |
| data | Read/write block/entity/storage NBT | Data & Execute |
| datapack | Manage data packs | World & Server |
| debug | Record debug data | World & Server |
| defaultgamemode | Set the default game mode | World & Server |
| deop | Remove operator status | World & Server |
| difficulty | Set the difficulty | World & Server |
| effect | Add/remove status effects | Player & Entity |
| enchant | Enchant an item | Player & Entity |
| execute | Conditional/compound command execution | Data & Execute |
| experience / xp | Modify experience | Player & Entity |
| fill | Fill a region with blocks | Data & Execute |
| fillbiome | Fill a region with a biome | Data & Execute |
| forceload | Force-load chunks | World & Server |
| function | Run a function | Data & Execute |
| gamemode | Set the game mode | Player & Entity |
| gamerule | Modify game rules | World & Server |
| give | Give an item | Player & Entity |
| help / ? | Command help | World & Server |
| item | Modify inventory items | Data & Execute |
| jfr | JFR profiling | World & Server |
| kick | Kick a player | World & Server |
| kill | Kill an entity | Player & Entity |
| list | List online players | World & Server |
| locate | Find a structure's location | World & Server |
| loot | Generate dropped items | Data & Execute |
| me | Send an action message | Player & Entity |
| msg / tell / w | Private message | Player & Entity |
| op | Grant operator status | World & Server |
| particle | Spawn particles | Player & Entity |
| perf | Performance profiling | World & Server |
| place | Place a structure/feature | Data & Execute |
| playsound | Play a sound | Player & Entity |
| publish | Open to LAN | World & Server |
| recipe | Grant/revoke recipes | Player & Entity |
| reload | Reload data packs | World & Server |
| replaceitem | Replace inventory item (legacy) | Data & Execute |
| return | Return a function value / interrupt | Data & Execute |
| ride | Control riding relationships | Player & Entity |
| rotate | Set an entity's facing direction | Data & Execute |
| say | Broadcast a message | World & Server |
| schedule | Delay-execute a function | Data & Execute |
| scoreboard | Scoreboard system | Data & Execute |
| seed | Query the world seed | World & Server |
| setblock | Place a block | Data & Execute |
| setidletimeout | Set the AFK kick timeout | World & Server |
| setworldspawn | Set the world spawn point | World & Server |
| spawnpoint | Set a player's spawn point | Player & Entity |
| spectate | Spectate a specified player | Player & Entity |
| spreadplayers | Randomly spread entities | Data & Execute |
| stopsound | Stop sounds | Player & Entity |
| summon | Spawn an entity | Player & Entity |
| tag | Manage entity tags | Data & Execute |
| team | Manage teams | Data & Execute |
| teammsg / tm | Team private message | Player & Entity |
| teleport / tp | Teleport | Player & Entity |
| tellraw | Send a JSON message | Data & Execute |
| tick | Tick debugging (1.20.3+) | World & Server |
| time | Set/query the time | World & Server |
| title | Screen title | Player & Entity |
| toggledownfall | Toggle weather (legacy) | World & Server |
| trigger | Trigger a trigger objective | Data & Execute |
| weather | Set the weather | World & Server |
| whitelist | Whitelist management | World & Server |
| worldborder | World border | World & Server |
| wsserver | Connect to a WebSocket (debugging) | World & Server |

> Note: `/structure` was removed in 1.19.4 and replaced by `/place structure`; `/replaceitem` is kept for compatibility but `/item` is recommended.
