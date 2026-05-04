package com.akitain.minecartsoverhaul.mixin.minecart;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin {

    // Without this, a player who logs out while riding a train trailer leaves a ghost passenger
    // attached: the trailer keeps a reference to the disconnected player and the next tick's
    // cascade tries to drag them along, which corrupts the train chain on rejoin.
    @Inject(method = "disconnect", at = @At("HEAD"))
    private void dismountTrain(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Entity vehicle = self.getVehicle();
        if (!(vehicle instanceof AbstractMinecart cart)) return;
        if (!cart.entityTags().contains("train")) return;
        self.stopRiding();
    }
}
