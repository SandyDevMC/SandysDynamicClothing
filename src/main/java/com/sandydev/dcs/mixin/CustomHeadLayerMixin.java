package com.sandydev.dcs.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sandydev.dcs.clothing.ClothingItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Одежда из слота шлема уже нарисована на скине, поэтому ванильный слой головы не должен
 * рисовать поверх неё иконку предмета.
 */
@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin {

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void dcs$skipClothingOnHead(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                        LivingEntity entity, float limbSwing, float limbSwingAmount,
                                        float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
                                        CallbackInfo ci) {
        if (entity.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof ClothingItem) {
            ci.cancel();
        }
    }
}
