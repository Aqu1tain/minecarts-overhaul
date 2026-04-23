# FAQ

## Minecarts stop on copper rails

Make sure you are on 0.2.0 or later. Before the fix, copper rails were not in the `minecraft:rails` block tag, so minecarts could not ride them. Current versions include the tag.

## How do I actually reach 40 blocks/sec?

Copper rails raise the cap but do not push the cart. To hit the tier speed:

- Launch it with a powered rail at the start of the run.
- Use a [[Furnace Minecart]] with fuel — it self-propels along its facing.
- Run the cart downhill.

Once the cart is moving, the 90% momentum floor keeps it above 90% of its current speed, so a single low-tier rail between two high-tier ones does not crash the speed.

## Why did my copper rail turn green?

It oxidized. Copper rails weather over time like vanilla copper blocks: `Unaffected → Exposed → Weathered → Oxidized`. Each tier caps the max speed at 40, 20, 10, 5 blocks/sec respectively. Wax a rail with honeycomb to freeze its current tier.

## Can I scrape oxidation?

Yes. An axe removes one tier of oxidation. An axe on a waxed rail removes the wax.

## Does the furnace minecart still accept only coal?

No. It accepts any item registered as fuel — coal, charcoal, lava buckets, blaze rods, planks. Burn duration matches the fuel's actual value.

## My furnace minecart pushes the wrong direction

The direction is set at placement. Player-placed carts face away from you. Dispenser-placed carts face away from north/west dispensers, or use the default orientation from south/east dispensers. Pick up and place again to reorient.

## Does this work with other minecart mods?

The mod changes vanilla minecart behavior via mixins. Mods that also mixin the same methods may conflict. Files touched: `AbstractMinecart`, `MinecartFurnace`, `NewMinecartBehavior`, `MinecartDispenseItemBehavior`, `GameRules`, `WeatheringCopper`, `HoneycombItem`.

## Where is the source code / wiki?

- [GitHub repo](https://github.com/Aqu1tain/minecarts-overhaul)
- [Wiki](https://github.com/Aqu1tain/minecarts-overhaul/wiki)
