package com.sandydev.dcs.mixin;

import com.sandydev.dcs.clothing.ClothingManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Плащ должен быть виден независимо от того, включена ли у игрока в настройках "Skin
 * Customization - Cape" - эта настройка предназначена для ванильных Mojang-капюшонов, а не для
 * одежды этого мода (см. {@code com.sandydev.dcs.clothing.ClothingKind#CAPE}).
 * <p>
 * Без этой подмены штатный {@code CapeLayer} молча ничего не рисует для тех игроков, кто когда-то
 * отключил себе показ капюшона в настройках - даже если cape-текстура была успешно подменена в
 * {@link AbstractClientPlayerMixin}.
 * <p>
 * {@code Player} - общий для клиента и сервера класс, но сам этот mixin применяется только на
 * клиенте (объявлен в секции "client" {@code dynamic_clothing_system.mixins.json}) - серверную
 * логику эта настройка не затрагивает.
 */
@Mixin(Player.class)
public abstract class PlayerModelPartMixin {

    @Inject(method = "isModelPartShown", at = @At("RETURN"), cancellable = true, require = 0)
    private void dcs$forceShowEquippedCape(PlayerModelPart part, CallbackInfoReturnable<Boolean> cir) {
        if (part != PlayerModelPart.CAPE || cir.getReturnValue()) {
            return;
        }
        Player self = (Player) (Object) this;
        if (ClothingManager.getEquippedCape(self).isPresent()) {
            cir.setReturnValue(true);
        }
    }
}
