---
title: Command List - World and Server Commands (time, weather, gamerule, worldborder, locate, op, whitelist, debug, tick, etc.)
version: 1.20.4
category: Commands
keywords: [time, weather, gamerule, worldborder, locate, op, whitelist, ban, debug, tick, datapack]
summary: Syntax and examples for world state, game rules, server management, and performance debugging commands.
---

# Command List - World and Server Commands

> Target version: Java Edition 1.20.4.

## Time and Weather

### `/time`
- Syntax: `/time <set|add|query> <time|day|noon|night|midnight|...>`.
- Time values: `day`=1000, `noon`=6000, `night`=13000, `midnight`=18000, `sunrise`=23000, `sunset`=12000.
- Examples: `/time set day`, `/time set 6000`, `/time add 1000`, `/time query daytime`.
- Permission level: 2.

### `/weather`
- Syntax: `/weather <clear|rain|thunder> [duration (seconds)]`.
- Example: `/weather thunder 100`.
- Permission level: 2.

### `/toggledownfall`
- Syntax: `/toggledownfall`; toggles clear/rain weather (legacy command, `/weather` is recommended). Permission level 2.

### `/seed`
- Syntax: `/seed`; displays the world seed (requires permission level 2 on servers, available in singleplayer). Permission level: 2 (server).

## Game Rules

### `/gamerule`
- Syntax: `/gamerule <rule> [value]`.
- Common rules:
  - `doDaylightCycle` (day/night cycle), `doWeatherCycle` (weather cycle)
  - `doMobSpawning`, `doMobLoot`, `mobGriefing` (mobs breaking blocks)
  - `doTileDrops` (block drops), `doFireTick` (fire spread)
  - `keepInventory` (keep items on death), `naturalRegeneration` (natural health regen)
  - `commandBlockOutput`, `sendCommandFeedback`
  - `randomTickSpeed` (default 3, see tick.md)
  - `playersSleepingPercentage` (percentage of players needed to skip the night)
  - `doLimitedCrafting` (only unlocked recipes)
  - `doVillagerTrading`, `doTraderSpawning`
  - `doPatrolSpawning`, `doInsomnia` (phantoms), `doWardenSpawning`
  - `fallDamage`, `fireDamage`, `freezeDamage`, `drowningDamage` (1.20.2+)
  - `tntExplosionDropDecay`, `mobExplosionDropDecay`, `lavaSourceConversion` (1.19.3+), `snowAccumulationHeight` (1.19.3+), `waterSourceConversion`
  - `announceAdvancements`, `forgiveDeadPlayers`, `universalAnger`
  - `spectatorsGenerateChunks`, `spawnRadius`
  - `maxCommandChainLength`, `maxEntityCramming`, `logAdminCommands`
- Examples: `/gamerule keepInventory true`, `/gamerule randomTickSpeed 0`.
- Permission level: 2.

## World Border and Spawn Point

### `/worldborder`
- Syntax:
  - `/worldborder add <distance>` (expand), `/worldborder set <distance> [time]` (set)
  - `/worldborder center <coordinates>`, `/worldborder damage amount|buffer <value>`
  - `/worldborder warning time|distance <value>`, `/worldborder get`
- Example: `/worldborder set 1000 30` (expand to 1000 over 30 seconds).
- Permission level: 2.

### `/forceload`
- Syntax: `/forceload add|remove|query <coordinates> [coordinates]`; force-loads chunks (keeps ticks running and entities active).
- Example: `/forceload add 0 0 10 10`.
- Permission level: 2.

### `/locate`
- Syntax: `/locate structure <structure ID>`, `/locate biome <biome ID>`, `/locate place <poi>`.
- Structure example: `/locate structure minecraft:ancient_city`, `/locate structure minecraft:stronghold`.
- Biome example: `/locate biome minecraft:cherry_grove`.
- Returns the nearest coordinates; permission level 2.

## Chunks and Maps

### `/datapack`
- Syntax: `/datapack <enable|disable|list> [name]`.
- Examples: `/datapack enable "file:my_datapack"`, `/datapack list available`.
- Permission level: 3.

### `/reload`
- Syntax: `/reload`; reloads all data packs (advancements, functions, recipes, etc.). Permission level 3 (the wiki lists 4, but most servers configure it as 3).

### `/debug`
- Syntax: `/debug start|stop|function <function ID>`; records tick performance debug data (generates a .nbt report).
- Permission level: 4.

### `/perf`
- Syntax: `/perf start|stop`; creates a performance profiling report. Permission level 4.

### `/jfr`
- Syntax: `/jfr start|stop`; Java Flight Recorder profiling. Permission level 4.

### `/tick`
- Syntax: `/tick query|rate <value>|freeze|step <count>|step stop|unfreeze`.
- Note: added in 1.20.3+; controls tick execution and performance measurement; see `mechanics/tick-applications-and-timing-cheatsheet.md` for details.
- Permission level: 3.

### `/wsserver`
- Syntax: `/wsserver <address>`; connects to a WebSocket debug server (for data pack development). Permission level 4.

## Server Management

### `/op` and `/deop`
- Syntax: `/op <player>`, `/deop <player>`; grants/revokes operator privileges. Permission level 3.

### `/ban`, `/ban-ip`, `/banlist`, `/pardon`, `/pardon-ip`
- Syntax: `/ban <player> [reason]`, `/ban-ip <IP> [reason]`, `/banlist [ips]`, `/pardon <player>`, `/pardon-ip <IP>`.
- Bans players/IPs and manages the ban list. Permission level 3.

### `/whitelist`
- Syntax: `/whitelist <on|off|list|add|remove|reload>`.
- Examples: `/whitelist add Steve`, `/whitelist on`.
- Permission level: 3.

### `/kick`
- Syntax: `/kick <player> [reason]`; kicks a player. Permission level 3.

### `/setidletimeout`
- Syntax: `/setidletimeout <minutes>`; sets the AFK auto-kick timeout (0 disables it). Permission level 3.

### `/publish`
- Syntax: `/publish [port]`; opens a singleplayer world as a LAN server. Permission level 4 (singleplayer).

### `/save-all`, `/save-off`, `/save-on`
- `/save-all [flush]`: saves the world; `/save-off` pauses autosave; `/save-on` resumes it. Permission level 4.

### `/stop`
- Syntax: `/stop`; shuts down the server and saves. Permission level 4.

### `/list`
- Syntax: `/list [uuids]`; lists online players. Permission level 0 (result returned to self).

### `/help` (/?)
- Syntax: `/help [command]`; shows command help. Permission level 0.

### `/difficulty`
- Syntax: `/difficulty <peaceful|easy|normal|hard>` (or 0/1/2/3).
- Example: `/difficulty hard`.
- Permission level: 2.

## Appendix: 1.20.4 Removal/Change Notes

- `/structure`: removed in 1.19.4, replaced by `/place structure`.
- `/replaceitem`: kept for compatibility but `/item` is recommended.
- `/tick`, `/continue`, `/ride`, `/rotate`, `/spectate`, `/damage`, `/return`, `/fillbiome`, etc. were added between 1.19.3 and 1.20.3, and are all available in 1.20.4.
- Macros (function arguments) and `/return` were added in 1.20.2; `execute positioned over` and scoreboard display names were added in 1.20.3.
