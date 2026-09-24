---
title: Hostile Mobs (Zombies, Skeletons, Creepers, Spiders, Witches, Phantoms, Illagers, Nether and Ocean Monsters)
version: 1.20.4
category: Mobs
keywords: [hostile mobs, zombie, skeleton, witch, phantom, pillager, vindicator, guardian, blaze, ghast, hoglin]
summary: Base info (health, behavior, drops) and traits for all regular hostile mobs; bosses are covered separately in the bosses doc.
---

# Hostile Mobs

> Target version: Java Edition 1.20.4. Hostile mobs actively attack players; creepers are covered in a dedicated file, `creeper.md`.

## Undead (Overworld)

### Zombie `zombie`
- Appearance: green decayed humanoid, arms stretched forward.
- Health 20; spawns in the Overworld at night at light level ≤0.
- Behavior: chases players and villagers; burns in daylight (unless wearing a helmet); can infect villagers or convert into a husk or drowned; can form zombie sieges (at night near villages).
- Drops: 0–2 rotten flesh, iron ingot/carrot/potato/iron sword (rare); can drop enchanted equipment (rare).

### Husk `husk`
- Appearance: sandy-yellow, dried-out zombie.
- Health 20; spawns in deserts.
- Behavior: same as a zombie, but **doesn't burn in daylight**; its attacks inflict Hunger.
- Drops: rotten flesh, rare items.

### Drowned `drowned`
- Appearance: teal underwater zombie.
- Health 20; spawns underwater in oceans/rivers.
- Behavior: chases players underwater; doesn't burn in daylight; **throws tridents** (ranged, on Normal+ difficulty); holds a trident/fishing rod.
- Drops: rotten flesh, copper ingot (rare), trident (rare, if holding one), fishing rod.

### Skeleton `skeleton`
- Appearance: white skeleton archer.
- Health 20; spawns in the Overworld at night / in the Nether.
- Behavior: ranged bow attacks (8–10 block range, Normal difficulty); burns in daylight; strafes to dodge the player.
- Drops: 0–2 bones, 0–2 arrows; rare bow/armor.

### Stray `stray`
- Appearance: pale gray, frost-covered skeleton.
- Health 20; spawns in snowy/ice biomes.
- Behavior: its arrows inflict **Slowness** (30 seconds); doesn't burn in daylight.
- Drops: bones, arrows (tipped with Slowness).

### Wither Skeleton `wither_skeleton`
- Appearance: black skeleton holding a stone sword, about 2.4 blocks tall.
- Health 20; spawns in Nether fortresses.
- Behavior: attacks inflict **Wither** (10 seconds); slashes with a jump attack.
- Drops: bones, coal, wither skeleton skull (2.5% chance, increased by Looting).

## Arthropods and Cave Mobs

### Spider (see Neutral Mobs doc — hostile at night)

### Cave Spider `cave_spider`
- Appearance: small, blue-gray poisonous spider.
- Health 12; spawns in abandoned mineshafts (spawners) and lush caves.
- Behavior: attacks inflict **Poison** (7 seconds on Normal difficulty); can climb walls.
- Drops: string, spider eyes.

### Silverfish `silverfish`
- Appearance: small, silver-gray creeping bug.
- Health 8; spawns from monster eggs hidden in stone blocks in strongholds/mountains.
- Behavior: burrows into stone-type blocks; **summons nearby silverfish** to swarm-attack when struck.
- Drops: none.

### Endermite `endermite`
- Appearance: small, purple beetle-like creature with light purple spots.
- Health 8; has a chance to spawn when a player throws an ender pearl.
- Behavior: moves quickly; attacked by endermen (endermen are hostile toward endermites).
- Drops: none.

## Nether Mobs

### Blaze `blaze`
- Appearance: golden, glowing humanoid made of spinning fire rods, surrounded by ember particles.
- Health 20; spawns in Nether fortresses.
- Behavior: flies; fires **fireballs** (bursts of 3, dealing 5 damage + setting the target on fire); melee attacks also burn.
- Drops: 0–1 blaze rods (increased by Looting).

### Ghast `ghast`
- Appearance: white, tentacled ghost-like mob (4×4 blocks), with a sorrowful expression.
- Health 10; spawns in the Nether (except crimson forests).
- Behavior: flies; fires fireballs (can be deflected back by players); fireballs explode.
- Drops: 0–1 ghast tears, 0–2 gunpowder.

### Magma Cube `magma_cube`
- Appearance: orange-red, lava-textured bouncing cube mob (1/2/4-block sizes).
- Health: small 1, medium 4, large 16.
- Behavior: bounces around; large ones split when killed; contact attacks burn; drops magma cream (Looting).
- Drops: 0–1 magma cream.

### Piglin Brute `piglin_brute`
- Appearance: sturdy pig-man, holds a golden axe, wears gold armor.
- Health 50; spawns in bastion remnants.
- Behavior: **unconditionally hostile** (won't become neutral due to gold armor); high melee damage (about 13 on Normal difficulty).
- Drops: golden axe (rare), gold nuggets, gold gear.

### Hoglin `hoglin`
- Appearance: reddish-brown, tusked giant pig.
- Health 40; spawns in crimson forests/bastion remnants.
- Behavior: **actively attacks** players and piglins (except piglin brutes); can be knocked back off ledges; transforms into a zoglin in the Overworld/soul soil-lit areas (when teleported there).
- Drops: 2–4 raw porkchop, 0–2 leather (drops cooked porkchop if killed by fire).

### Zoglin `zoglin`
- Appearance: green, decayed hoglin.
- Health 40; formed when a hoglin transforms in the Overworld/outside the Nether.
- Behavior: unconditionally attacks nearly all mobs (attacks almost everything); cannot breed.
- Drops: 0–1 rotten flesh, 2–4 raw porkchop.

## Illagers

### Pillager `pillager`
- Appearance: gray-robed humanoid, holds a crossbow.
- Health 24; spawns at pillager outposts/during raids.
- Behavior: ranged crossbow attacks; participates in raids (the raid captain wears a banner).
- Drops: crossbow (chance), 0–2 arrows, emeralds (during raids).

### Vindicator `vindicator`
- Appearance: gray robe with a dark apron, holds an iron axe.
- Health 24; spawns at woodland mansions/during raids.
- Behavior: melee attacks (iron axe); a special vindicator named "Johnny" attacks everything.
- Drops: iron axe (chance), emeralds.

### Evoker `evoker`
- Appearance: dark gray robe, pale face.
- Health 32; spawns at woodland mansions/during raids.
- Behavior: summons **vexes** (3 of them); casts **fangs** (spikes from the ground, cast at players); always drops a totem of undying when killed by a player.
- Drops: 0–1 emeralds, totem of undying (guaranteed drop on player kill).

### Vex `vex`
- Appearance: small gray sprite holding an iron sword.
- Health 14; summoned by an evoker.
- Behavior: flies through walls (ignores blocks); attacks players.
- Drops: none (small chance of an iron sword).

### Ravager `ravager`
- Appearance: large gray beast (about 2.2 blocks tall).
- Health 100; spawns during raids.
- Behavior: charges (knocks back entities and destroys leaves/crops); can toss players into the air; can be briefly stunned.
- Drops: 1 saddle (chance).

### Witch `witch`
- Appearance: purple robe + pointed hat, hunched posture.
- Health 26; spawns at swamp huts/during raids/thunderstorms.
- Behavior: throws potions (harm/slowness/weakness/poison, etc.); drinks buff potions on itself (healing/fire resistance/swiftness); has a chance to drink a resistance potion when struck.
- Drops: redstone dust/glowstone dust/gunpowder/spider eye/sugar/glass bottle, 0–2 each (varying chances).

## Ocean and Cave Mobs

### Guardian `guardian`
- Appearance: gray-green spiky fish-like mob (2×1 blocks), glowing eyes.
- Health 30; spawns in ocean monuments.
- Behavior: fires a laser beam (damage over time, requires line of sight); a spiny attack when close; attacks players with lasers.
- Drops: 0–2 prismarine shards, 0–1 prismarine crystals, raw cod (40% on player kill), rare fish.

### Elder Guardian `elder_guardian`
- Appearance: larger gray-green guardian (4×2 blocks), scarred.
- Health 80; 3 spawn in each ocean monument.
- Behavior: attacks inflict **Mining Fatigue III** (5 minutes); stronger laser attack.
- Drops: 0–2 prismarine shards, 0–1 prismarine crystals, raw cod (40%), sponge (1, guaranteed on player kill).

### Phantom `phantom`
- Appearance: gray-blue winged reptilian mob, flies toward players who haven't slept in a long time, at night.
- Health 20; spawns at night once a player hasn't slept for 3 in-game days (72 in-game hours).
- Behavior: dive-bomb attacks (no damage during the dive itself, deals damage on hit and then retreats); burns in daylight.
- Drops: 0–1 phantom membrane (used to repair elytra).

### Slime `slime`
- Appearance: green, semi-transparent, jelly-like cube mob (1/2/4-block sizes).
- Health: small 1, medium 4, large 16.
- Behavior: moves by hopping; large/medium ones split into smaller ones when killed; only spawns in swamps (at specific elevations) and slime chunks.
- Drops: 0–4 slimeballs, quantity scales with size (small 0–1, medium 1, large 2–4).

## Warden (see the Bosses doc)
