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
 * Используется Mixin, а не события NeoForge: на 1.21.1 в NeoForge нет публичного события,
 * которое давало бы подменить именно выбор текстуры рендерера (RenderPlayerEvent.Pre/Post дают
 * доступ к PoseStack, но не к этому). Другие "skin changer"-моды для этой версии (Quick Skin,
 * CustomSkinLoader и т.п.) по той же причине трансформируют байткод того же класса. Сам Mixin
 * объявлен в {@code neoforge.mods.toml} ({@code [[mixins]]}) - штатный способ с NeoForge 20.3,
 * без отдельного Gradle-плагина.
 * <p>
 * {@code require = 0} у инжекта: если сигнатура метода в используемой версии Minecraft/NeoForge
 * отличается, инжект просто не применится (с предупреждением в логе), а не уронит игру. Если
 * подмена скина не работает - сначала смотреть лог на предупреждение Mixin по этому методу.
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
