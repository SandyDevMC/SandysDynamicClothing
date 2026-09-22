package com.sandydev.dcs.clothing.client;

import com.sandydev.dcs.api.event.ClothingCapeTextureEvent;
import com.sandydev.dcs.clothing.ClothingManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Разрешает cape-текстуру игрока: сперва по надетому через Curios плащу (см. {@link
 * ClothingManager#getEquippedCape}), затем даёт аддонам последнее слово через {@link
 * ClothingCapeTextureEvent} на {@link NeoForge#EVENT_BUS}.
 * <p>
 * Вынесено в отдельный метод, а не продублировано в каждом mixin'е, чтобы {@code
 * AbstractClientPlayerMixin} (сама подмена текстуры) и {@code PlayerModelPartMixin}
 * (принудительный показ слота CAPE) видели совершенно одинаковый результат - иначе плащ,
 * подставленный аддоном через событие, мог бы не воспроизвестись, потому что
 * {@code isModelPartShown} проверял бы только Curios и ничего не знал бы о переопределении.
 */
public final class ClothingCapeResolver {

    private ClothingCapeResolver() {
    }

    /** @return итоговая cape-текстура для этого игрока, или {@code null}, если плащ показывать не нужно. */
    public static ResourceLocation resolve(AbstractClientPlayer player) {
        ResourceLocation resolved = ClothingManager.getEquippedCape(player)
                .map(ClothingCapeTextureCache::getOrRegister)
                .orElse(null);

        ClothingCapeTextureEvent event = new ClothingCapeTextureEvent(player, resolved);
        NeoForge.EVENT_BUS.post(event);
        return event.getCapeTexture();
    }
}
