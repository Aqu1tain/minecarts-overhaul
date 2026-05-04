package com.akitain.minecartsoverhaul.mixin.minecart;

import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRules.class)
public class GameRulesMixin {

    // Vanilla gates the new minecart-improvement gamerule behind a feature flag (the
    // experimental minecart toggle). We always-enable the new behaviour via
    // AbstractMinecartEntityMixin#improvedMinecarts, so the gamerule should be available in every
    // world unconditionally; an empty FeatureSet drops the flag requirement at registration.
    @Redirect(method = "<clinit>", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/flag/FeatureFlagSet;of(Lnet/minecraft/world/flag/FeatureFlag;)Lnet/minecraft/world/flag/FeatureFlagSet;"
    ))
    private static FeatureFlagSet alwaysExposeMinecartGameRule(FeatureFlag original) {
        return FeatureFlagSet.of();
    }
}
