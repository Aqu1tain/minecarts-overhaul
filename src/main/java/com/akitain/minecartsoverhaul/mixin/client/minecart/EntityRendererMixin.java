package com.akitain.minecartsoverhaul.mixin.client.minecart;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.UUID;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Shadow
    protected abstract int getBlockLight(T entity, BlockPos pos);

    @Inject(method = "updateRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;doesRenderOnFire()Z"))
    private void minecarts_overhaul$addMinecartLinks(T entity, S state, float tickProgress, CallbackInfo ci) {
        if (!(entity instanceof AbstractMinecartEntity minecart)) return;
        if (minecart.getCommandTags().isEmpty()) return;
        String tag = minecart.getCommandTags().toString();
        tag = tag.substring(1, tag.length() - 1);
        if (tag.length() != 36) return;

        UUID uuid = UUID.fromString(tag);
        Entity linkedEntity = entity.getEntityWorld().getEntity(uuid);
        if (!(linkedEntity instanceof AbstractMinecartEntity linkedMinecart)) return;

        World world = entity.getEntityWorld();
        float yaw = entity.lerpYaw(tickProgress) * (float) (Math.PI / 180.0);
        Vec3d offset = new Vec3d(0, 0.12, 0);
        BlockPos startLightPos = BlockPos.ofFloored(entity.getCameraPosVec(tickProgress));
        BlockPos endLightPos = BlockPos.ofFloored(linkedMinecart.getCameraPosVec(tickProgress));
        int blockLight = getBlockLight(entity, startLightPos);
        int startSkyLight = world.getLightLevel(LightType.SKY, startLightPos);
        int endSkyLight = world.getLightLevel(LightType.SKY, endLightPos);

        state.leashDatas = new ArrayList<>(1);
        state.leashDatas.add(new EntityRenderState.LeashData());

        Vec3d rotatedOffset = offset.rotateY(-yaw);
        EntityRenderState.LeashData leashData = state.leashDatas.get(0);
        leashData.offset = rotatedOffset;
        leashData.startPos = entity.getLerpedPos(tickProgress).add(rotatedOffset);
        leashData.endPos = linkedMinecart.getLeashPos(tickProgress).add(0, -0.3, 0);
        leashData.leashedEntityBlockLight = blockLight;
        leashData.leashHolderBlockLight = blockLight;
        leashData.leashedEntitySkyLight = startSkyLight;
        leashData.leashHolderSkyLight = endSkyLight;
    }
}
