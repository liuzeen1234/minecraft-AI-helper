---
title: Command List - Player and Entity Commands (gamemode, tp, give, summon, effect, kill, title, etc.)
version: 1.20.4
category: Commands
keywords: [gamemode, tp, give, summon, effect, kill, title, spawnpoint, spectate, ride]
summary: Syntax and examples for common player- and entity-facing commands - game modes, teleportation, items, spawning, effects, and messages.
---

# Command List - Player and Entity Commands

> Target version: Java Edition 1.20.4. For general syntax (selectors, coordinates, NBT), see `command-list-overview-and-syntax-rules.md`.

## Game Mode and Players

### `/gamemode`
- Syntax: `/gamemode <mode> [player]`
- Modes: `survival`, `creative`, `adventure`, `spectator` (or 0/1/2/3, s/c/a/sp).
- Examples: `/gamemode creative @p`, `/gamemode spectator`.
- Permission level: 2.

### `/defaultgamemode`
- Syntax: `/defaultgamemode <mode>`; sets the default game mode for new players. Permission level 2.

### `/spawnpoint`
- Syntax: `/spawnpoint [player] [coords]`; sets a player's spawn point (an optional `[facing]` angle can be included).
- Examples: `/spawnpoint @s 100 64 200`, `/spawnpoint @a ~ ~ ~ facing 0 90`.

### `/setworldspawn`
- Syntax: `/setworldspawn [coords]`; sets the world spawn point and respawn area. Permission level 2.

### `/spectate`
- Syntax: `/spectate [target] [player]`; lets a player (who must be in spectator mode) spectate a specified entity; `/spectate` with no arguments exits spectating. Permission level 2. Added in 1.19.3+.

### `/ride`
- Syntax: `/ride <entity> mount|dismount|evict|summon <...>`; controls riding: mount, dismount, evict (kick off passengers), summon (spawn a vehicle/passenger). Permission level 2. Added in 1.19.4+.
- Example: `/ride @e[type=zombie,limit=1] mount @s` (makes a zombie ride you).

### `/rotate`
- Syntax: `/rotate <entity> <angle>` or `facing <coords>`; sets an entity's facing direction. Permission level 2. Added in 1.19.4+.

### `/continue`
- Syntax: `/continue`; resumes a paused save (used together with F3+Esc pause). For singleplayer debugging. Permission level 2? (singleplayer only). Added in 1.20.2+.

## Teleportation and Killing

### `/tp` (teleport)
- Syntax:
  - `/tp [player] <coords>` (e.g. `/tp @s 100 64 200`)
  - `/tp [player] <target player>` (teleports to the target's location)
  - `/tp <entity> <coords> [facing]`, `/tp <entity> <target> [facing]`
  - Supports relative/local coordinates: `/tp @s ~ ~5 ~`, `/tp @s ^ ^ ^1`.
- Permission level: 2.

### `/kill`
- Syntax: `/kill [target]`; instantly removes an entity (drops occur normally).
- Example: `/kill @e[type=!player]` (kills every entity except players).
- Permission level: 2.

## Items

### `/give`
- Syntax: `/give <player> <item> [count]`.
- Examples: `/give @p minecraft:diamond_sword 1`, `/give @s minecraft:oak_log{...} 64` (with NBT).
- Items can carry NBT: `/give @s minecraft:enchanted_book{StoredEnchantments:[{id:"minecraft:sharpness",lvl:5s}]}`.
- Permission level: 2.

### `/clear`
- Syntax: `/clear [player] [item] [count] [data components]`; clears/removes items.
- Examples: `/clear @p` (clears everything), `/clear @a minecraft:stone 10`.
- Permission level: 2.

### `/enchant`
- Syntax: `/enchant <player> <enchantment> [level]`; enchants the held item (no experience/lapis consumed).
- Example: `/enchant @p minecraft:sharpness 5`.
- Permission level: 2.

### `/recipe`
- Syntax: `/recipe <give|take> <player> <recipe>`; grants/removes recipes; `*` means all.
- Example: `/recipe give @a *`.
- Permission level: 2.

### `/item`
- Syntax: `/item <entity|block> <slot> <replace|modify> <item> [count]`; precisely modifies the item in a specified slot (can include NBT).
- Slots: `weapon.mainhand`, `armor.head`, `container.0`, etc.
- Example: `/item replace entity @p armor.head minecraft:golden_helmet`.
- Permission level: 2.

### `/replaceitem`
- Legacy item replacement command (kept for compatibility): `/replaceitem entity <target> <slot> <item> [count]`; `/item` is recommended for newer versions.

## Effects and Spawning

### `/effect`
- Syntax:
  - `/effect give <entity> <effect> [seconds] [amplifier] [hide]` (amplifier 0 = level 1)
  - `/effect clear <entity> [effect]`
- Examples: `/effect give @p minecraft:regeneration 30 2`, `/effect clear @e`.
- Common effect IDs: `speed`, `slowness`, `haste`, `strength`, `jump_boost`, `regeneration`, `resistance`, `fire_resistance`, `water_breathing`, `invisibility`, `night_vision`, `poison`, `wither`, `weakness`, `luck`, `glowing`, `levitation`, `slow_falling`, `absorption`, `saturation`, `darkness` (1.19+), `infested`/`oozing`/`weaving` (1.21 experimental, not available in the default version).
- Permission level: 2.

### `/summon`
- Syntax: `/summon <entity> [coords] [NBT]`.
- Examples: `/summon zombie ~ ~ ~ {IsBaby:1b}`, `/summon lightning_bolt ~ ~ ~`, `/summon minecraft:item ~ ~ ~ {Item:{id:"minecraft:diamond",Count:1b}}`.
- `/summon` can spawn any entity ID (see the entities index); common NBT tags like `NoAI`, `Silent`, `CustomName` apply.
- Permission level: 2.

## Messages and UI

### `/say`
- Syntax: `/say <message>`; broadcasts to all players (with a [Server] prefix). Permission level 2.

### `/msg` (/tell /w)
- Syntax: `/msg <player> <message>`; sends a private message. Permission level 0.

### `/teammsg` (/tm)
- Syntax: `/teammsg <message>`; sends a message to teammates. Permission level 0.

### `/me`
- Syntax: `/me <action>`; sends an action message (*name action*). Permission level 0.

### `/title`
- Syntax: `/title <target> <title|subtitle|actionbar|times|clear|reset> <content>`
  - `title` main title, `subtitle` subtitle, `actionbar` action bar, `times` display duration (fadeIn stay fadeOut), `clear`/`reset` clears it.
  - Content is a JSON text component.
- Examples: `/title @a title {"text":"Welcome","color":"gold"}`, `/title @a times 10 70 20`.
- Permission level: 2.

### `/tellraw`
- Syntax: `/tellraw <target> <JSON text>`; sends a JSON message (can be clickable/hoverable). Permission level 2.

### `/playsound`
- Syntax: `/playsound <sound> <source> [player] [coords] [volume] [pitch] [min volume]`.
- Example: `/playsound minecraft:entity.experience_orb.pickup master @p`.
- Permission level: 2.

### `/stopsound`
- Syntax: `/stopsound [player] [source] [sound]`; stops sounds. Permission level 2.

### `/particle`
- Syntax: `/particle <particle> [coords] [velocity params] [count] [player]`.
- Example: `/particle minecraft:heart ~ ~1 ~ 0.5 0.5 0.5 1 10 @a`.
- Permission level: 2.

### `/experience` (/xp)
- Syntax: `/experience <add|set|query> <player> <amount> [points|levels]`.
- Examples: `/xp add @p 100 levels`, `/xp set @p 0 points`, `/xp query @p levels`.
- Permission level: 2.

### `/advancement`
- Syntax: `/advancement <grant|revoke> <player> <everything|from|until|only> <advancement>`.
- Examples: `/advancement grant @p everything`, `/advancement revoke @p only minecraft:story/mine_stone`.
- Permission level: 2.
