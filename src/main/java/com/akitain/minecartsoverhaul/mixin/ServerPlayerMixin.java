package com.akitain.minecartsoverhaul.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void leaveTrainOnDisconnect(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Entity vehicle = self.getVehicle();
        if (vehicle != null && vehicle.entityTags().contains("train")) {
            self.stopRiding();
        }
    }
}
