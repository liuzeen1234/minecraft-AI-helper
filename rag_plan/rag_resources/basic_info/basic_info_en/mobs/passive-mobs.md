---
title: Passive Mobs (Animals and Fish)
version: 1.20.4
category: Mobs
keywords: [passive mobs, animals, cow, pig, chicken, sheep, villager, fish, horse, camel, sniffer]
summary: Base info (health, spawning, behavior, drops) and traits for all passive mobs.
---

# Passive Mobs

> Target version: Java Edition 1.20.4. Passive mobs never attack players. Health values are Java Edition numbers.

## Livestock

### Cow `cow`
- Appearance: brown-and-white spotted cow, about 1.4 blocks tall as an adult.
- Health 10; spawns on grass with light level ≥9 in plains/forests, etc.
- Behavior: wanders slowly; attracted by wheat; can breed (wheat); turns into a mooshroom when struck by lightning.
- Drops: 1–3 raw beef, 0–2 leather; drops cooked beef if killed by fire.
- Uses: milk (right-click with a bucket), breeding, leather.

### Mooshroom `mooshroom`
- Appearance: a cow with a red/brown mushroom-covered hide, spawns on mushroom islands.
- Health 10; right-click with a bowl to get mushroom stew; shearing drops mushrooms and turns it into a regular cow; can breed (wheat).
- Drops: raw beef, leather (after shearing), mushrooms.

### Pig `pig`
- Appearance: pink pig, about 0.9 blocks tall as an adult.
- Health 10; spawns in plains/forests, etc.
- Behavior: attracted by carrots/potatoes/beetroots; can breed; turns into a zombified piglin when struck by lightning.
- Drops: 1–3 raw porkchop; drops cooked porkchop if killed by fire.

### Chicken `chicken`
- Appearance: white chicken, about 0.7 blocks tall.
- Health 4; spawns on grass; **lays eggs** (randomly drops an egg every 5–10 minutes).
- Behavior: attracted by wheat seeds/melon seeds/pumpkin seeds; can breed; can die from fall damage (gravity).
- Drops: 1 raw chicken, 0–2 feathers; drops cooked chicken if killed by fire.

### Sheep `sheep`
- Appearance: white (or another color with 1/16 chance) fluffy sheep.
- Health 8; spawns on grass.
- Behavior: eats grass (grazes on grass blocks and regrows wool); can be dyed; can breed (wheat); shearing drops 1–3 wool.
- Drops: wool (sheared/killed), 1–2 raw mutton; drops cooked mutton if killed by fire.

### Rabbit `rabbit`
- Appearance: short-eared rabbit in various fur colors.
- Health 3; spawns in deserts/flower forests/snowy areas, etc. (color varies by biome).
- Behavior: moves by hopping; attracted by carrots/dandelions; can breed; eats carrot crops.
- Drops: raw rabbit, 0–1 rabbit hide, 1/10 chance of a rabbit's foot (affected by Looting); drops cooked rabbit if killed by fire.

### Goat `goat`
- Appearance: gray-white horned goat, likes standing on high ground.
- Health 10; spawns in mountain biomes.
- Behavior: jumps and rams (knocks back entities, doesn't attack unprompted); **randomly rams players** (small chance); milk it with a bucket; breeds (wheat).
- Drops: raw mutton, 1–2 goat horns (dropped when ramming into a block).

### Panda `panda`
- Appearance: black-and-white panda, spawns in bamboo forests.
- Health 20; passive (but protective of food).
- Behavior: eats bamboo; has 7 personality types (normal, lazy, worried, playful, aggressive, weak, brown); aggressive pandas will bite players; breeds (bamboo, requires nearby bamboo).
- Drops: bamboo (dropped while eating).

### Bee `bee`
- Appearance: yellow-and-black striped bee.
- Health 10; spawns near beehives (flower forests/plains, etc.).
- Behavior: collects pollen (speeds up nearby crop growth), returns to the hive to make honey; **stings in groups** when attacked (inflicts poison, dies after stinging); breeds (flowers).
- Drops: none (no drops on death).

### Fox `fox`
- Appearance: red fox / snow fox (snowy biome variant).
- Health 10; passive (unless the player attacks first).
- Behavior: active at night; carries items in its mouth; attacks chickens/rabbits; can breed (sweet berries); trusts players who raised it from breeding it with sweet berries.
- Drops: none.

### Axolotl `axolotl`
- Appearance: small axolotl in pink/brown/cyan/gold/blue (blue is extremely rare), an aquatic mob.
- Health 14; spawns in the water of lush caves.
- Behavior: passive; plays dead (chance of feigning death when hurt); attacks fish and squid; **helps players in combat** (grants a brief buff after the player kills fish); can breed (bucket of tropical fish).
- Drops: none.

## Tameable Mounts

### Horse `horse`, Donkey `donkey`, Mule `mule`
- Appearance: horses come in various coat colors (white/black/brown/chestnut/spotted); donkeys are gray-brown with long ears; mules are a horse+donkey hybrid.
- Health 15–30 (random); spawns in plains/grasslands.
- Behavior: tameable (mount bare-handed, feed wheat/apples/sugar/golden apples to increase temper); horses can wear horse armor (iron/gold/diamond) and saddles; donkeys/mules can carry a chest (15 slots). Horse jump height and speed are randomized.
- Breeding: horse + horse (golden apple/golden carrot); horse + donkey → mule.
- Drops: 0–2 leather; drops cooked horse meat if killed by fire.

### Skeleton Horse `skeleton_horse`
- Appearance: pale gray skeletal horse.
- Health 15; spawns during thunderstorms (as a skeleton horse trap).
- Behavior: tameable (rideable), immune to drowning underwater; cannot breed.
- Drops: 0–2 bones.

### Zombie Horse `zombie_horse`
- Appearance: green decayed horse.
- Health 15; spawns only via commands/spawn eggs.
- Drops: 0–2 rotten flesh.

### Camel `camel`
- Appearance: yellow-brown two-humped camel, added in 1.20.
- Health 32; spawns in desert villages.
- Behavior: rideable (2 riders); **can dash** (jumps 2 blocks); can sit down; unaffected by sand's speed penalty; breeds (cactus).
- Drops: none.

### Llama `llama`, Trader Llama `trader_llama`
- Appearance: fluffy llama in various colors (white/gray/brown/cream).
- Health 15–30; spawns in savannas (wild llamas); trader llamas are led by wandering traders.
- Behavior: neutral (spits when struck); tameable (mount bare-handed, feed hay bales); can carry carpets and a chest (15 slots); follows the llama caravan.
- Drops: 0–2 leather.

### Cat `cat`, Ocelot `ocelot`
- Appearance: cats have 11 coat patterns (tabby, black-and-white, orange, white, black, siamese, etc.); ocelots are golden-spotted wild cats.
- Health: cat 10, ocelot 10; ocelots spawn in jungles; cats spawn in villages.
- Behavior: cats are tameable (feed raw fish), and once tamed they follow the player and scare off creepers (creepers fear cats); ocelots are passive (can be tamed into a cat with raw fish; in 1.20.4 a tamed ocelot becomes a cat).
- Drops: none.

### Wolf `wolf`
- Appearance: gray-white wolf (wears a red collar once tamed, collar can be dyed).
- Health: wild 8, tamed 20; spawns in forests/taiga/old-growth taiga.
- Behavior: neutral (wild wolves live in packs and retaliate as a group when struck); tameable (feed bones); tamed wolves attack whatever the player attacks; can breed (any meat, requires full health); attacks skeletons/sheep/rabbits, etc.
- Drops: none.

### Parrot `parrot`
- Appearance: red/blue/green/cyan/gray parrot (5 colors).
- Health 6; spawns in jungles.
- Behavior: tameable (wheat seeds), perches on the player's shoulder once tamed; mimics nearby mob sounds; can be "bred" (cookies will poison it, so avoid feeding those).
- Drops: 1–2 feathers.

## Aquatic Mobs

### Squid `squid`
- Appearance: dark blue squid, tentacles waving.
- Health 10; spawns in oceans/rivers.
- Behavior: swims underwater; squirts ink when attacked (ink sac particles); suffocates on land.
- Drops: 1–3 ink sacs.

### Glow Squid `glow_squid`
- Appearance: cyan-blue glowing squid (light level 10).
- Health 10; spawns in underground/lush water bodies (underground lakes).
- Drops: 1–3 glow ink sacs.

### Cod `cod`, Salmon `salmon`, Pufferfish `pufferfish`, Tropical Fish `tropical_fish`
- Appearance: cod are silver-white and slender; salmon are pink with spots; pufferfish are round and spiky; tropical fish are vividly colored (3000+ combinations).
- Health 3 for all; spawn in oceans/rivers (in their respective biomes).
- Behavior: swim underwater; pufferfish inflate when struck and poison nearby entities.
- Drops: raw cod/raw salmon/pufferfish/tropical fish (respectively); can be captured with a bucket (fish bucket).

### Sea Turtle `turtle`
- Appearance: green sea turtle.
- Health 30; spawns on beaches.
- Behavior: lays eggs on its home beach (turtle eggs, which can hatch); baby turtles return to the sea; breeds (seagrass); attracted by seagrass.
- Drops: seagrass (when eating); 0–1 scute drops on player kill (low chance); turtle eggs hatch into baby turtles.

### Dolphin `dolphin`
- Appearance: gray-blue dolphin.
- Health 10; spawns in oceans (not frozen oceans).
- Behavior: neutral (retaliates when attacked); likes to chase boats; feeding raw cod/raw salmon grants "Dolphin's Grace" (swim speed boost); can lead players to treasure (after being fed).
- Drops: 0–1 raw cod.

### Frog `frog`, Tadpole `tadpole`
- Appearance: green/orange/white frog (variant depends on biome); tadpoles are small black tadpoles.
- Health: frog 10, tadpole 5; frogs spawn in swamps; tadpoles hatch from frog eggs (in 1.20.4, frog eggs are laid when frogs breed).
- Behavior: frogs jump high and eat small mobs (eating a small magma cube drops a glow ink sac); can breed (slimeballs); tadpoles grow into the frog color variant matching their biome.
- Drops: none (frog); none (tadpole).

### Sniffer `sniffer`
- Appearance: dark brown shelled prehistoric creature, added in 1.20, fairly large (about 1.9 blocks tall).
- Health 14; doesn't spawn naturally, hatches from a sniffer egg (obtained via archaeology from shipwrecks/ocean ruins).
- Behavior: sniffs the ground while lowering its head, digging up torchflower seeds and pitcher pods; doesn't attack; can breed (torchflower seeds); lays eggs on beaches.
- Drops: none (no drops on kill).

## Others

### Bat `bat`
- Appearance: small brown bat (received a new model/texture update in 1.20.3).
- Health 6; spawns in dark caves.
- Behavior: non-aggressive; flies around randomly; can be killed.
- Drops: none.

### Allay `allay`
- Appearance: small blue sprite, flapping wings.
- Health 20; spawns in cages at pillager outposts/woodland mansions.
- Behavior: passive; picks up and delivers a specified item to the player (after being given an item); follows the player; can be duplicated (using a jukebox + any music disc); trusts the player after being fed.
- Drops: none.

### Wandering Trader `wandering_trader`
- Appearance: merchant in blue robes with a white turban.
- Health 20; randomly appears near villages/players (appears every 20–30 minutes, disappears after 2–3 days).
- Behavior: sells random items (for emeralds); drinks invisibility potions; brings 2 trader llamas.
- Drops: none (only experience).
