package com.akitain.minecartsoverhaul.mixin.minecart;

import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    // Without this, a player who logs out while riding a train trailer leaves a ghost passenger
    // attached: the trailer keeps a reference to the disconnected player and the next tick's
    // cascade tries to drag them along, which corrupts the train chain on rejoin.
    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void dismountTrain(CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        Entity vehicle = self.getVehicle();
        if (!(vehicle instanceof AbstractMinecartEntity cart)) return;
        if (!cart.getCommandTags().contains("train")) return;
        self.stopRiding();
    }
}
