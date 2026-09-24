---
title: Neutral Mobs (Enderman, Piglin, Zombified Piglin, Spider, Wolf, Bear, Panda, Dolphin, etc.)
version: 1.20.4
category: Mobs
keywords: [neutral mobs, enderman, piglin, zombified piglin, spider, wolf, polar bear, panda, dolphin]
summary: Base info and traits for neutral mobs (don't attack normally, retaliate once provoked).
---

# Neutral Mobs

> Target version: Java Edition 1.20.4. Neutral mobs don't attack players by default, but retaliate once provoked (attacked/stared at, etc.).

## Overworld Neutral Mobs

### Enderman `enderman`
- Appearance: tall, thin black humanoid (2.9 blocks), purple eyes, surrounded by purple particles.
- Health 40; spawns in the End (in large numbers) and in the Overworld/Nether at night (in areas with light level ≤0).
- Behavior:
  - **Provoked by staring**: a player staring at its head (crosshair aimed at it) provokes it (its eyes glow green); wearing a carved pumpkin prevents this.
  - Once provoked, it teleports (up to 32 blocks) to pursue; attack damage: Easy 4.5, Normal 7, Hard 10.5.
  - Afraid of water (takes damage from water/rain contact and teleports away); afraid of projectiles.
  - Can pick up and place blocks; takes damage from ender pearls.
- Drops: 0–1 ender pearls (Looting increases drop chance).
- Note: ender pearls can be thrown to teleport.

### Piglin `piglin`
- Appearance: pink pig-nosed humanoid, wears gold gear (Nether).
- Health 16; spawns in Nether crimson forests/bastion remnants.
- Behavior:
  - **Neutral**: neutral toward players by default (players must wear gold armor to trade); if not wearing gold armor, staring at one or opening its chests/mining gold ore will provoke an attack.
  - Picks up and equips gold items (gold ingots/gold swords, etc.); loves gold ingots (can trade: gold ingots for items); fears soul fire/zombified piglins (flees).
  - Hunts hoglins and eats pork.
- Drops: gold sword/gold gear (chance), 0–1 gold nuggets, 0–1 rotten flesh (rare).

### Zombified Piglin `zombified_piglin`
- Appearance: green decayed pig-man, holds a gold sword.
- Health 20; spawns in the Nether (in groups) or from a pig struck by lightning.
- Behavior: neutral (attacking one triggers a **group retaliation** — nearby zombified piglins all join the chase); infights (attacks the nearest of its own kind when it has no target).
- Drops: 0–1 rotten flesh, 0–1 gold nuggets, gold sword (small chance).

### Spider `spider`
- Appearance: black/dark brown eight-legged spider, red eyes.
- Health 16; spawns in the Overworld at night (light level ≤0).
- Behavior: neutral (actively attacks at night, passive during the day); can climb walls/webs; leaps to attack; cave spiders are a poisonous variant (inflicts poison).
- Drops: 0–2 string, 1/3 chance of a spider eye (on player kill); cave spiders drop the same.

### Wolf (wild)
- See the Passive Mobs doc; wild wolves are neutral (don't attack unprompted) and **retaliate as a pack** when struck.

### Polar Bear `polar_bear`
- Appearance: large white bear.
- Health 30; spawns in snowy tundra/ice spikes.
- Behavior: neutral (retaliates when struck; **adult bears retaliate if a cub is attacked**); weak swimmer.
- Drops: 0–2 raw cod/raw salmon.

### Panda (see Passive Mobs doc — wild pandas are neutral)

### Dolphin (see Passive Mobs doc — neutral)

### Goat (see Passive Mobs doc — neutral)

### Fox (see Passive Mobs doc — neutral)

### Iron Golem `iron_golem`
- Appearance: tall, iron-gray humanoid (2.7 blocks), vines on its chest.
- Health 100; naturally spawns in villages (when villager population is high) or built from iron blocks + a carved pumpkin.
- Behavior: **neutral** (protects villagers and the village; retaliates if attacked by a player); actively attacks most hostile mobs (won't attack creepers unprompted); can be repaired by players with iron ingots; immune to drowning and fall damage.
- Drops: 3–5 iron ingots (on player kill), 0–2 poppies.

### Snow Golem `snow_golem`
- Appearance: a snowman (2 snow blocks + a carved pumpkin), wears a carved pumpkin.
- Health 4; spawns when built (snow blocks + pumpkin).
- Behavior: neutral; throws snowballs at hostile mobs (no damage, but knockback); leaves a trail of snow while walking; melts and dies from heat in hot biomes/the Nether.
- Drops: 0–15 snowballs.

### Allay (see Passive Mobs doc — passive)

## Nether Neutral Mobs

### Hoglin `hoglin`
- Appearance: reddish-brown giant pig (about 1.5 blocks tall), tusks exposed.
- Health 40; spawns in Nether crimson forests/bastion remnants.
- Behavior: hoglins are actually hostile mobs (they actively attack players) and have been moved to `hostile-mobs.md`.

Correction noted: hoglins, zoglins, and piglin brutes are all hostile mobs and belong in the hostile mobs doc.

### Piglin Brute `piglin_brute`
- Hostile; see the Hostile Mobs doc.

## Neutral Mobs Summary Table

| Mob | ID | Health | Provocation condition | Notes |
|---|---|---|---|---|
| Enderman | enderman | 40 | Stared at / attacked | Afraid of water, teleports |
| Piglin | piglin | 16 | Stared at without gold armor / mining gold ore / opening chests | Can trade with gold ingots |
| Zombified Piglin | zombified_piglin | 20 | Attacking one | Group retaliation |
| Spider (night) | spider | 16 | Actively attacks at night | Neutral during the day |
| Wolf | wolf | 8 | Attacked | Group retaliation |
| Polar Bear | polar_bear | 30 | Attacked / cub attacked | — |
| Panda | panda | 20 | Aggressive personality / attacked | Mostly passive |
| Dolphin | dolphin | 10 | Attacked | Feeding fish earns favor |
| Goat | goat | 10 | Random ramming | Not aggressive unprompted |
| Fox | fox | 10 | Attacked | Passive |
| Iron Golem | iron_golem | 100 | Attacked | Protects villagers |
| Snow Golem | snow_golem | 4 | Doesn't attack | Throws snowballs |

(This document classifies hoglins and piglin brutes under `hostile-mobs.md`, since they actively attack players.)
