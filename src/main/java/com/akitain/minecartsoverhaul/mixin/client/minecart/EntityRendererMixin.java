package com.akitain.minecartsoverhaul.mixin.client.minecart;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.UUID;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Unique
    private static final int minecarts_overhaul$UUID_STRING_LENGTH = 36;
    @Unique
    private static final double minecarts_overhaul$LINK_START_Y_OFFSET = 0.12;
    @Unique
    private static final double minecarts_overhaul$LINK_END_Y_OFFSET = -0.3;

    @Shadow
    protected abstract int getBlockLightLevel(T entity, BlockPos blockPos);

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void minecarts_overhaul$addMinecartLinks(T entity, S state, float partialTicks, CallbackInfo ci) {
        if (!(entity instanceof AbstractMinecart minecart)) return;
        AbstractMinecart linkedMinecart = minecarts_overhaul$getLinkedMinecart(minecart);
        if (linkedMinecart == null) return;

        Vec3 offset = new Vec3(0.0, minecarts_overhaul$LINK_START_Y_OFFSET, 0.0)
                .yRot((float) (-entity.getYRot(partialTicks) * Math.PI / 180.0));
        BlockPos startLightPos = BlockPos.containing(entity.getEyePosition(partialTicks));
        BlockPos endLightPos = BlockPos.containing(linkedMinecart.getEyePosition(partialTicks));

        EntityRenderState.LeashState linkState = new EntityRenderState.LeashState();
        linkState.offset = offset;
        linkState.start = entity.getPosition(partialTicks).add(offset);
        linkState.end = linkedMinecart.getRopeHoldPosition(partialTicks)
                .add(0.0, minecarts_overhaul$LINK_END_Y_OFFSET, 0.0);
        linkState.startBlockLight = this.getBlockLightLevel(entity, startLightPos);
        linkState.endBlockLight = linkState.startBlockLight;
        linkState.startSkyLight = entity.level().getBrightness(LightLayer.SKY, startLightPos);
        linkState.endSkyLight = entity.level().getBrightness(LightLayer.SKY, endLightPos);

        state.leashStates = new ArrayList<>(1);
        state.leashStates.add(linkState);
    }

    @Nullable
    @Unique
    private static AbstractMinecart minecarts_overhaul$getLinkedMinecart(AbstractMinecart minecart) {
        for (String tag : minecart.entityTags()) {
            if (tag.length() != minecarts_overhaul$UUID_STRING_LENGTH) continue;
            UUID uuid = minecarts_overhaul$parseUuid(tag);
            if (uuid == null) continue;
            Entity linkedEntity = minecart.level().getEntity(uuid);
            if (linkedEntity instanceof AbstractMinecart linkedMinecart) return linkedMinecart;
        }
        return null;
    }

    @Nullable
    @Unique
    private static UUID minecarts_overhaul$parseUuid(String tag) {
        try {
            return UUID.fromString(tag);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
