# Getting Started

## What changes

- New rail family: [[Copper Rails]] with four oxidation tiers giving maximum speeds of 40, 20, 10 and 5 blocks per second.
- [[Furnace Minecart]] accepts any fuel, faces away from the placer, and self-propels along its facing while lit.
- Minecarts keep horizontal velocity in the air and slide on ice instead of stopping on landing.
- Minecraft's experimental minecart improvements are always on. No game rule toggle required.

## First copper rails

Craft a set like a vanilla rail, but with copper ingots: six copper ingots around one stick yields twelve copper rails. Place them like any other rail. The fresh (unaffected) tier moves carts at 40 blocks/sec.

Copper rails oxidize naturally over time, losing a tier every few in-game days. Wax a stretch with honeycomb to preserve it, or scrape with an axe to restore it.

## First powered trip

Place a [[Furnace Minecart]] on a copper rail and right-click it with any fuel. It will light up and start pushing itself along its facing direction. The direction it faces is set when it is placed:

- Placed by a player: faces away from you.
- Placed by a dispenser facing north or west: faces away from the dispenser.
- Placed by a dispenser facing south or east: default orientation.

Right-click to top up the fuel at any time.

## Tuning the cap

The `max_minecart_speed` game rule is always available (no experimental toggle needed). Copper rails override it with their tier cap. On vanilla rails the game rule still applies.
