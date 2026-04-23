# Physics

Minor changes to off-rail minecart behaviour so high-speed carts survive gaps and slopes.

## Horizontal velocity in the air

When a minecart leaves the rail with a mild downward velocity (faster than -0.7 blocks/sec on Y), horizontal velocity is preserved. Vanilla applies a 5% drag per tick the moment the cart leaves the track, which erased the momentum built up on a copper rail. Now the cart keeps going until it lands.

Carts falling faster than -0.7 (large drops) apply normal drag again.

## Ice sliding

Off-rail minecarts now use the slipperiness of the block below them instead of a fixed 0.5 ground friction. Landing on ice preserves nearly all the horizontal velocity, so you can shoot a minecart off a rail onto an ice stretch and keep going.

## Off-track speed clamp

Vanilla clamps off-rail horizontal velocity at the current max speed (game rule, default 8). That capped aerial minecarts the same as grounded ones. The off-track clamp is now 40 blocks/sec, so a cart launched off a copper rail at tier speed does not get slowed to 8 mid-flight.

## Unchanged

- Rail-to-rail movement uses vanilla physics otherwise. The speed cap (game rule / copper tier) still applies on rails.
- Collision and cart-to-cart pushing remain vanilla.
