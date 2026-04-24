# Furnace Minecart

The vanilla furnace minecart is capped at half the normal minecart speed and only accepts coal. This mod reworks it into the primary way to push long-distance trains.

## Changes

- **Any fuel**: accepts anything registered as fuel: coal, charcoal, lava buckets, blaze rods, planks. Burn duration is the fuel's actual value, not a fixed 3600 ticks.
- **Face-away placement**: when placed by a player the cart faces away from you. When placed by a dispenser facing north or west the cart faces away from the dispenser. South/east dispensers use the default orientation.
- **Self-propulsion**: while lit the cart adds a small push along its facing direction each tick. Over a few seconds the cart reaches the max speed allowed by the rail it is on, up to the [[Copper Rails]] tier cap when applicable.
- **Higher speed cap**: vanilla's internal halving of the max speed is removed. Furnace minecarts use the same cap as any other cart.

## Using

Right-click an empty furnace minecart with a fuel item. The cart lights up and starts moving. Keep feeding fuel to extend the trip. Fuel tops up to a maximum of 32,000 ticks.

The cart pushes in its facing direction, regardless of the direction you interact from. If the cart is facing the wrong way, pick it up and place it again. Orientation is set at placement.

## Physics interactions

- On a **copper rail**: hits the tier cap over time (UNAFFECTED → 40 blocks/sec).
- On a **powered rail**: the non-furnace cap does not apply to furnace minecarts, so they pass through at full speed instead of being clamped to 8.
- In **water**: the cap is halved like any other cart.
- While **empty of fuel**: horizontal velocity decays at 0.75 per tick.
