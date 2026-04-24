package com.akitain.minecartsoverhaul.mixin;

import com.akitain.minecartsoverhaul.entity.TrainLocomotive;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityTeleportMixin {

    @Inject(method = "teleport", at = @At("HEAD"))
    private void breakTrainBeforeTeleport(TeleportTransition transition, CallbackInfoReturnable<Entity> cir) {
        if (this instanceof TrainLocomotive locomotive) {
            locomotive.dropTrain();
        }
    }
}
