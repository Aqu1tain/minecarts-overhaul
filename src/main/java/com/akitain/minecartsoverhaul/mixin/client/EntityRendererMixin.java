package com.akitain.minecartsoverhaul.mixin.client;

import com.akitain.minecartsoverhaul.network.TrainClientCache;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.UUID;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void addTrainLeashState(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (!(entity instanceof AbstractMinecart minecart)) return;
        UUID aheadId = TrainClientCache.aheadOf(minecart.getUUID());
        if (aheadId == null) return;
        Level level = minecart.level();
        Entity ahead = level.getEntity(aheadId);
        if (ahead == null) return;

        Vec3 attachOffset = new Vec3(0.0, 0.4, 0.0);
        Vec3 start = minecart.getPosition(partialTicks).add(attachOffset);
        Vec3 end = ahead.getPosition(partialTicks).add(attachOffset);
        int startLight = 15;
        int endLight = 15;

        EntityRenderState.LeashState leash = new EntityRenderState.LeashState();
        leash.offset = attachOffset;
        leash.start = start;
        leash.end = end;
        leash.startBlockLight = startLight;
        leash.endBlockLight = endLight;
        leash.startSkyLight = startLight;
        leash.endSkyLight = endLight;
        leash.slack = true;

        state.leashStates = new ArrayList<>(1);
        state.leashStates.add(leash);
    }
}
