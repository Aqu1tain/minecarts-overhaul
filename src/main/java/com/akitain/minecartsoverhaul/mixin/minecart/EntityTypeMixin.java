package com.akitain.minecartsoverhaul.mixin.minecart;

import com.akitain.minecartsoverhaul.registry.other.FixedFurnaceMinecartEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
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

    @Inject(method = "register(Ljava/lang/String;Lnet/minecraft/entity/EntityType$Builder;)Lnet/minecraft/entity/EntityType;", at = @At(value = "HEAD"), cancellable = true)
    private static void useFixedFurnaceMinecarts(String id, EntityType.Builder<?> type, CallbackInfoReturnable<EntityType<?>> cir) {
        if (!id.contains(VANILLA_FURNACE_MINECART_ID)) return;
        EntityType.Builder<FixedFurnaceMinecartEntity> replacement = EntityType.Builder
                .create(FixedFurnaceMinecartEntity::new, SpawnGroup.MISC)
                .dropsNothing()
                .dimensions(0.98F, 0.7F)
                .passengerAttachments(0.1875F)
                .maxTrackingRange(8);
        cir.setReturnValue(registerVanilla(VANILLA_FURNACE_MINECART_ID, replacement));
        cir.cancel();
    }

    @Unique
    private static <T extends Entity> EntityType<T> registerVanilla(String id, EntityType.Builder<T> builder) {
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.ofVanilla(id));
        return Registry.register(Registries.ENTITY_TYPE, key, builder.build(key));
    }
}
