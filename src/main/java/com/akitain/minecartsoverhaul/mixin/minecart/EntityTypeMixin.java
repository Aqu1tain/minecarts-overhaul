package com.akitain.minecartsoverhaul.mixin.minecart;

import com.akitain.minecartsoverhaul.registry.other.FixedFurnaceMinecartEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Hijacks vanilla's EntityType.register call for "furnace_minecart" so the registered class is
// FixedFurnaceMinecartEntity instead of the vanilla one. Done at the registration call site
// (rather than swapping the class via reflection) so other mods that read the registered type
// see the substituted class straight away.
@Mixin(EntityType.class)
public class EntityTypeMixin {

    @Unique
    private static final String VANILLA_FURNACE_MINECART_ID = "furnace_minecart";

    @Inject(method = "register(Ljava/lang/String;Lnet/minecraft/world/entity/EntityType$Builder;)Lnet/minecraft/world/entity/EntityType;", at = @At(value = "HEAD"), cancellable = true)
    private static void useFixedFurnaceMinecarts(String id, EntityType.Builder<?> type, CallbackInfoReturnable<EntityType<?>> cir) {
        if (!id.contains(VANILLA_FURNACE_MINECART_ID)) return;
        EntityType.Builder<FixedFurnaceMinecartEntity> replacement = EntityType.Builder
                .of(FixedFurnaceMinecartEntity::new, MobCategory.MISC)
                .noLootTable()
                .sized(0.98F, 0.7F)
                .passengerAttachments(0.1875F)
                .clientTrackingRange(8);
        cir.setReturnValue(registerVanilla(VANILLA_FURNACE_MINECART_ID, replacement));
        cir.cancel();
    }

    @Unique
    private static <T extends Entity> EntityType<T> registerVanilla(String id, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.withDefaultNamespace(id));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
}
