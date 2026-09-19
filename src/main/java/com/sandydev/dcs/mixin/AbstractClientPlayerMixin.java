package com.sandydev.dcs.mixin;

import com.sandydev.dcs.clothing.ClothingManager;
import com.sandydev.dcs.clothing.client.ClothingCapeTextureCache;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Подменяет cape-текстуру в {@link PlayerSkin}, который возвращает {@link
 * AbstractClientPlayer#getSkin()}, если у игрока надет плащ (см. {@code
 * com.sandydev.dcs.clothing.ClothingKind#CAPE}).
 * <p>
 * В отличие от подмены обычной одежды ({@link PlayerRendererMixin}), здесь не нужно ничего
 * компоновать попиксельно - плащ просто заменяет собой одну ссылку на текстуру в записи {@link
 * PlayerSkin}, а рисует её уже штатный ванильный {@code CapeLayer} (и, если у игрока нет
 * собственной elytra-текстуры, тем же полотном может воспользоваться и {@code ElytraLayer} -
 * это ванильное поведение резервной elytra-текстуры, не специфичное для этого мода; на практике
 * заметно только если игрок одновременно наденет плащ и настоящую элитру).
 * <p>
 * {@code getSkin()} вызывается на каждый кадр для каждого видимого игрока, поэтому сама подмена
 * должна быть дешёвой - вся тяжёлая работа (декодирование PNG, регистрация DynamicTexture)
 * делается один раз на плащ в {@link ClothingCapeTextureCache}, а не на каждый вызов.
 * <p>
 * См. также {@link PlayerModelPartMixin} - без него игроки, отключившие в настройках показ
 * "Cape", не увидели бы плащ, даже с этой подменой.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(
            method = "getSkin()Lnet/minecraft/client/resources/PlayerSkin;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void dcs$overrideCapeTexture(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        ClothingManager.getEquippedCape(self).ifPresent(cape -> {
            ResourceLocation capeTexture = ClothingCapeTextureCache.getOrRegister(cape);
            if (capeTexture == null) {
                return;
            }
            PlayerSkin original = cir.getReturnValue();
            cir.setReturnValue(new PlayerSkin(
                    original.texture(), original.textureUrl(), capeTexture,
                    original.elytraTexture(), original.model(), original.secure()));
        });
    }
}
