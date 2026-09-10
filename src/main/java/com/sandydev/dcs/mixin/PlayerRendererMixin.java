package com.sandydev.dcs.mixin;

import com.sandydev.dcs.clothing.client.ClothingTextureCache;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Подменяет текстуру, которую {@link PlayerRenderer} использует для рендера тела игрока,
 * на скомпонованную (скин + одежда), если она есть - см. {@link ClothingTextureCache}.
 * <p>
 * Почему Mixin, а не событие NeoForge: на момент 1.21.1 в NeoForge нет публичного события,
 * позволяющего подменить именно то, какую текстуру рендерер реально забиндит для тела игрока
 * (RenderPlayerEvent.Pre/Post дают доступ к PoseStack, но не к выбору текстуры). Это
 * подтверждается тем, что все существующие "skin changer"-моды для этой версии игры
 * (Quick Skin, CustomSkinLoader и т.п.) используют байткод-трансформацию того же класса.
 * Сам Mixin объявлен в {@code neoforge.mods.toml} ({@code [[mixins]]}) - начиная с NeoForge 20.3
 * это штатный способ, не требующий отдельного Gradle-плагина.
 * <p>
 * {@code require = 0} у инжекта - если точная сигнатура метода в используемой версии
 * Minecraft/NeoForge отличается, инжект просто не применится (с предупреждением в лог),
 * вместо краша игры. Если подмена скина не работает - в первую очередь проверить лог на
 * предупреждение Mixin об этом методе и при необходимости подправить дескриптор.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(
            method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void dcs$overrideComposedSkin(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation override = ClothingTextureCache.get().getOverrideLocation(player);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
