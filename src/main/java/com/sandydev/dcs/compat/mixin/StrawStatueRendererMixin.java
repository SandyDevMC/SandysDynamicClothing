package com.sandydev.dcs.compat.mixin;

import com.sandydev.dcs.clothing.client.ClothingStatueRenderHooks;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Одежда на статуях Straw Statues. Цель задана строкой, а статуя в обработчиках - {@code @Coerce
 * Object}, потому что мода нет в compile-classpath. Конфиг подключается только если мод
 * установлен, см. {@code StrawStatuesMixinPlugin}.
 */
@Mixin(targets = "fuzs.strawstatues.client.renderer.entity.StrawStatueRenderer", remap = false)
public abstract class StrawStatueRendererMixin {

    @Inject(
            method = "getTextureLocation(Lfuzs/strawstatues/world/entity/decoration/StrawStatue;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void dcs$overrideStatueTexture(@Coerce Object statue, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation original = cir.getReturnValue();
        ResourceLocation override = ClothingStatueRenderHooks.overrideTexture(statue, original);
        if (override != original) {
            cir.setReturnValue(override);
        }
    }

    @Inject(
            method = "getPlayerProfileTexture(Lfuzs/strawstatues/world/entity/decoration/StrawStatue;)Ljava/util/Optional;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private static void dcs$overrideStatueCape(@Coerce Object statue,
                                               CallbackInfoReturnable<Optional<PlayerSkin>> cir) {
        Optional<PlayerSkin> original = cir.getReturnValue();
        Optional<PlayerSkin> override = ClothingStatueRenderHooks.overrideSkin(statue, original);
        if (override != original) {
            cir.setReturnValue(override);
        }
    }
}
