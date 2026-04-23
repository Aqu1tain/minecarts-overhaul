package com.akitain.minecartsoverhaul.mixin;

import com.akitain.minecartsoverhaul.block.CopperRailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NewMinecartBehavior.class)
public abstract class NewMinecartBehaviorMixin extends MinecartBehavior {

    protected NewMinecartBehaviorMixin(AbstractMinecart minecart) {
        super(minecart);
    }

    @Inject(method = "getMaxSpeed", at = @At("HEAD"), cancellable = true)
    private void applyCopperRailAndMomentum(ServerLevel level, CallbackInfoReturnable<Double> cir) {
        BlockState state = level.getBlockState(minecart.blockPosition());
        double perSecond = baseSpeedFor(state);
        if (minecart.isInWater()) perSecond *= 0.5;

        double perTick = perSecond / 20.0;
        double momentum = minecart.getDeltaMovement().horizontalDistance() * 0.9;
        double result = Math.max(perTick, momentum);

        if (state.getBlock() instanceof PoweredRailBlock && !(minecart instanceof MinecartFurnace)) {
            result = 8.0 / 20.0;
        }

        cir.setReturnValue(result);
    }

    private static double baseSpeedFor(BlockState state) {
        if (state.getBlock() instanceof CopperRailBlock) return CopperRailBlock.getMaxSpeed(state);
        if (state.getBlock() instanceof BaseRailBlock) return 8.0;
        return 40.0;
    }

    @Inject(method = "calculateBoostTrackSpeed", at = @At("HEAD"), cancellable = true)
    private void selfPropelOnCopperRail(Vec3 deltaMovement, BlockPos pos, BlockState state, CallbackInfoReturnable<Vec3> cir) {
        if (!(state.getBlock() instanceof CopperRailBlock)) return;

        double capPerTick = CopperRailBlock.getMaxSpeed(state) / 20.0;
        double boost = capPerTick * 0.05;
        double currentLength = deltaMovement.length();

        if (currentLength > 0.01) {
            double newLength = Math.min(currentLength + boost, capPerTick);
            cir.setReturnValue(deltaMovement.normalize().scale(newLength));
            return;
        }

        Vec3 facing = minecart.getRedstoneDirection(pos);
        if (facing.lengthSqr() > 0.0) {
            cir.setReturnValue(facing.scale(boost));
        }
    }
}
