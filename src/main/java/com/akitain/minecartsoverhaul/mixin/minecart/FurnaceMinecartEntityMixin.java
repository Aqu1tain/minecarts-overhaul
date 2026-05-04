package com.akitain.minecartsoverhaul.mixin.minecart;

import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// FixedFurnaceMinecartEntity owns its own tick/NBT lifecycle; these injects short-circuit the
// vanilla implementations at the points where they would compete with the subclass and double
// up on save data or movement updates.
@Mixin(MinecartFurnace.class)
public class FurnaceMinecartEntityMixin {

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isClientSide()Z"), cancellable = true)
    private void cancelTick(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "addAdditionalSaveData", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/ValueOutput;putDouble(Ljava/lang/String;D)V"), cancellable = true)
    private void cancelWrite(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "readAdditionalSaveData", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/ValueInput;getDoubleOr(Ljava/lang/String;D)D"), cancellable = true)
    private void cancelRead(CallbackInfo ci) {
        ci.cancel();
    }

    // Vanilla halves the furnace minecart's max speed via this 0.5 constant; rewriting it to 1.0
    // lets the locomotive use the same cap as any other cart so trains keep up on copper rails.
    @ModifyConstant(method = "getMaxSpeed", constant = @Constant(doubleValue = 0.5))
    private double notReducedSpeed(double constant) {
        return 1.0;
    }
}
