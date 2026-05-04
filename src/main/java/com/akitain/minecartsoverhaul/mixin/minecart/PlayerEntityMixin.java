package com.akitain.minecartsoverhaul.mixin.minecart;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    // Stats track distance in centimetres; this window (1000m to 1100m) fires the rail-consumed
    // criterion once when the player crosses the 1km mark while riding a minecart so the
    // vanilla "On A Rail" advancement can trip without burning a real rail item.
    @Unique
    private static final int RAIL_ADVANCEMENT_MIN_CM = 100_000;
    @Unique
    private static final int RAIL_ADVANCEMENT_MAX_CM = 110_000;

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void railAdvancement(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!(self instanceof ServerPlayerEntity serverPlayer)) return;
        if (!serverPlayer.hasVehicle()) return;
        if (!(serverPlayer.getVehicle() instanceof MinecartEntity)) return;
        int travelledCm = serverPlayer.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.MINECART_ONE_CM));
        if (travelledCm <= RAIL_ADVANCEMENT_MIN_CM || travelledCm >= RAIL_ADVANCEMENT_MAX_CM) return;
        Criteria.CONSUME_ITEM.trigger(serverPlayer, Items.RAIL.getDefaultStack());
    }
}
