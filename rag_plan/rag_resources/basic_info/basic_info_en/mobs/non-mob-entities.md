---
title: Non-Mob Entities (Items, Experience Orbs, Projectiles, Vehicles, Display Entities)
version: 1.20.4
category: Mobs
keywords: [item entity, experience orb, arrow, trident, boat, minecart, armor stand, item frame, painting, TNT]
summary: Base info and traits for all non-mob entities - dropped items, projectiles, vehicles, and display/decorative entities.
---

# Non-Mob Entities

> Target version: Java Edition 1.20.4. Non-mob entities have no health (in most cases); they're item and mechanic entities.

## Dropped Items and Experience

### Item Entity `item`
- Appearance: a shrunken, spinning 3D model of the item.
- Traits: an item lying on the ground; despawns after 5 minutes (300 seconds) unless `PickupDelay` is reset; can be absorbed by hoppers, carried by water currents, or pushed by explosions; picked up when a player walks near it; relevant NBT: `Age`, `Item`, `Thrower`, `PickupDelay`.

### Experience Orb `experience_orb`
- Appearance: small, glowing yellow-green orb (size reflects its experience value).
- Traits: drifts toward players; merges with other orbs; increases experience when picked up (leveling requires progressively more points per level); NBT: `Value`.

## Projectiles

### Arrow `arrow`, Spectral Arrow `spectral_arrow`
- Appearance: a slender arrow shaft, spins while flying.
- Traits: fired by bows/crossbows/skeletons; deals 2–5 damage (depending on draw strength); can be blocked by a shield; spectral arrows apply the "Glowing" effect on hit (10 seconds). NBT: `inGround`, `damage`, `Potion` (for tipped arrows).

### Trident `trident`
- Appearance: a blue trident (as an entity).
- Traits: flies back to the thrower (with Loyalty) or stays where it lands; deals damage on hit; NBT: `Damage`, `Trident` (enchantment data).

### Snowball `snowball`, Egg `egg`
- Appearance: white snowball / white egg.
- Traits: deal no damage (just knockback); snowballs can extinguish fire/activate target blocks; eggs have a small chance of hatching a chick when thrown.

### Ender Pearl `ender_pearl`
- Appearance: cyan pearl.
- Traits: teleports the thrower to the landing point (does the thrower take 5 damage after teleporting? yes, ender pearls deal 5 fall-like damage on landing); hitting a mob will trigger that mob's teleport instead.

### Fireball (Ghast) `fireball`, Wither Skull `wither_skull`
- Appearance: a fiery orb / a black skull-shaped fireball.
- Traits: ghast fireballs can be deflected back; wither skulls explode and destroy blocks; blue wither skulls are even more destructive.

### Dragon Fireball `dragon_fireball`
- Appearance: glowing purple fireball.
- Traits: explodes and leaves behind an area effect cloud (a lingering purple damage cloud).

### Potion (thrown)
- Appearance: a colored potion bottle (as an entity).
- Traits: shatters on impact and creates an area of potion effect.

### Experience Bottle `experience_bottle`
- Appearance: glowing green potion bottle.
- Traits: shatters on impact and releases a large burst of experience orbs.

### Firework Rocket `firework_rocket`
- Appearance: a colorful firework (as an entity).
- Traits: can be fired from a crossbow; explodes into firework particles and deals damage; used to propel elytra flight.

### Shulker Bullet `shulker_bullet`
- Appearance: a purple, oval-shaped glowing projectile.
- Traits: fired by shulkers; applies the "Levitation" effect on hit.

### Fishing Bobber `fishing_bobber`
- Appearance: a red bobber (as an entity).
- Traits: the core mechanic behind fishing; relevant NBT includes `motion`, `in_open_water`, etc.

## Vehicles

### Boat `boat`, Chest Boat `chest_boat`, Raft (1.20)
- Appearance: a small wooden boat / a boat with a chest / a bamboo raft.
- Traits: carries passengers in water (2 riders); collides with blocks/slides on shorelines; chest boats carry a 27-slot chest; boats can be broken, dropping planks and items.

### Minecart `minecart` and Variants
- Appearance: a small iron-framed cart (rides on rails).
- Variants: regular minecart, chest minecart (carries a chest), hopper minecart (carries a hopper), TNT minecart, spawner minecart (a mob spawner + minecart), command block minecart.
- Traits: rides on rails (top speed determined by powered rails); can carry passengers/items; hopper minecarts collect and transfer nearby dropped items; TNT minecarts explode when activated.

## Display and Decorative Entities

### Armor Stand `armor_stand`
- Appearance: a wooden humanoid stand.
- Traits: can wear armor/hold items; pose can be adjusted (`Pose` NBT); has an invisible mode; can be equipped by a player via right-click.

### Item Frame `item_frame`, Glow Item Frame
- Appearance: a wooden frame with a floating item inside (glow frames have a glowing outline).
- Traits: displays items/maps; maps are rendered as a map within the frame; can be rotated (right-click); glow frames have a glowing outline.

### Painting `painting`
- Appearance: wall art in various sizes (e.g. 32×32, 64×64, etc.).
- Traits: hangs on the side of a block; 1.20 added new paintings (more sizes/themes).

### Leash Knot `leash_knot`
- Appearance: a lead attachment point.
- Traits: the fixed point formed when a lead tethers a mob to a fence; can be broken by attacking it.

### End Crystal `end_crystal`
- Appearance: a glass sphere on a base, glowing on top.
- Traits: placed on bedrock/obsidian; heals the ender dragon; deals explosive damage (blast strength 6 when destroyed in melee); destroyed by projectiles like arrows (snowballs don't work — it requires a projectile entity to break it); used to respawn the ender dragon (placing 4 on the exit portal).

### Area Effect Cloud `area_effect_cloud`
- Appearance: a colored particle cloud (a potion effect).
- Traits: continuously applies an effect to entities within range; created by lingering potions/creepers (in Java Edition, a creeper with a status effect leaves one behind when it explodes).

### Lightning Bolt `lightning_bolt`
- Appearance: a blue bolt of lightning.
- Traits: deals damage, ignites blocks, transforms mobs (pig → zombified piglin, creeper → charged creeper); NBT: `Channel`, `Entity`.

## Display Entities (1.19.4+, for data packs/commands)

### Interaction `interaction`
- Appearance: an invisible interactive point.
- Traits: used for custom interactions (right-click/left-click detection); NBT: `width`/`height`, `response`.

### Block Display `block_display`, Item Display `item_display`, Text Display `text_display`
- Appearance: an entity that displays an arbitrary block/item/text (supports 3D transforms, glowing, and animation).
- Traits: core building blocks for maps/animations; relevant NBT includes `transformation` (matrix transform), `billboard`, `glow_color_override`, `text` (for text displays), etc.; supports `interpolation` (smooth animation interpolation).

### Marker `marker`
- Appearance: completely invisible.
- Traits: a pure data entity (no collision, no interaction); used for marking coordinates/tags via commands.

## Falling Block `falling_block`
- Appearance: a block in mid-fall (sand, gravel, TNT, concrete powder, etc.).
- Traits: falls under gravity; becomes a placed block on landing; a falling TNT block explodes if activated; NBT: `BlockState`, `Time`, `CancelDrop`.

## Appendix: TNT Entity and TNT Minecart
- TNT itself is a block (see the Blocks doc); the TNT minecart is a vehicle entity; blast strength 4.
