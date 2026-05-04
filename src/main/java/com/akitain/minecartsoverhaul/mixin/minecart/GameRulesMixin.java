package com.akitain.minecartsoverhaul.mixin.minecart;

import net.minecraft.resource.featuretoggle.FeatureFlag;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.world.rule.GameRules;
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
            target = "Lnet/minecraft/resource/featuretoggle/FeatureSet;of(Lnet/minecraft/resource/featuretoggle/FeatureFlag;)Lnet/minecraft/resource/featuretoggle/FeatureSet;"
    ))
    private static FeatureSet alwaysExposeMinecartGameRule(FeatureFlag original) {
        return FeatureSet.empty();
    }
}
