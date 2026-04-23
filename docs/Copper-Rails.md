# Copper Rails

Copper rails are a new rail family with tiered speed caps tied to oxidation. They slot into the vanilla rail system — regular rails connect to them, minecarts ride them, the `minecraft:rails` block tag includes them.

## Tiers

| State | Max speed |
|---|---|
| Unaffected | 40 blocks/sec |
| Exposed | 20 blocks/sec |
| Weathered | 10 blocks/sec |
| Oxidized | 5 blocks/sec |

The cap replaces the vanilla `max_minecart_speed` game rule while the cart is on a copper rail. On regular rails the game rule still applies.

The 90% momentum floor also applies: `max(tierCap, currentSpeed × 0.9)`. Once a cart is moving fast, a single lower-tier rail in the middle of the track does not hard-cap it.

In water, the cap is halved. On a powered rail, non-furnace carts are forced back to 8 blocks/sec.

## Crafting

Six copper ingots around one stick. Yields twelve copper rails.

## Oxidation

Fresh copper rails weather naturally via random ticks, the same way vanilla copper blocks do. The progression is `Unaffected → Exposed → Weathered → Oxidized`. Oxidized is the final stage and does not degrade further.

Neighbouring oxidized copper speeds up the process. Isolated rails oxidize very slowly.

## Waxing

Surround one honeycomb with eight copper rails of any tier to lock that tier in. Waxed rails do not oxidize and appear identical to their unwaxed counterpart.

Using an axe on an oxidized rail removes the oxidation by one tier. An axe on a waxed rail removes the wax, making it oxidize again.

## Design note

Copper rails raise the ceiling but do not actively accelerate carts. Pair them with a powered rail kick, a [[Furnace Minecart]], or a downhill slope to actually hit the tier cap.
