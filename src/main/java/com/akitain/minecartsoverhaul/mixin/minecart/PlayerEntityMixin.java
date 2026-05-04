package com.akitain.minecartsoverhaul.mixin.minecart;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerEntityMixin {

    // Stats track distance in centimetres; this window (1000m to 1100m) fires the rail-consumed
    // criterion once when the player crosses the 1km mark while riding a minecart so the
    // vanilla "On A Rail" advancement can trip without burning a real rail item.
    @Unique
    private static final int RAIL_ADVANCEMENT_MIN_CM = 100_000;
    @Unique
    private static final int RAIL_ADVANCEMENT_MAX_CM = 110_000;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void railAdvancement(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!(self instanceof ServerPlayer serverPlayer)) return;
        if (!serverPlayer.isPassenger()) return;
        if (!(serverPlayer.getVehicle() instanceof Minecart)) return;
        int travelledCm = serverPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.MINECART_ONE_CM));
        if (travelledCm <= RAIL_ADVANCEMENT_MIN_CM || travelledCm >= RAIL_ADVANCEMENT_MAX_CM) return;
        CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, Items.RAIL.getDefaultInstance());
    }
}
