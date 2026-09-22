package com.sandydev.dcs.api.event;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Отправляется на клиенте каждый раз, когда рендер игрока запрашивает его cape-текстуру
 * ({@code AbstractClientPlayer#getSkin()}), после того как этот мод разрешил её на основе
 * надетого через Curios плаща (см. {@code com.sandydev.dcs.clothing.ClothingKind#CAPE}).
 * <p>
 * {@link #getCapeTexture()} - что уже разрешено модом; {@code null}, если у игрока сейчас не
 * надет плащ. Аддон может переопределить это через {@link #setCapeTexture(ResourceLocation)} -
 * например, подставить другую текстуру независимо от того, что надето в Curios (сезонный плащ,
 * анимация через смену ResourceLocation по таймеру и т.п.), или, наоборот, скрыть уже выбранный
 * плащ, передав {@code null}.
 * <p>
 * Событие горячее: отправляется потенциально каждый кадр на каждого видимого игрока, поэтому
 * обработчик должен быть дешёвым - без сети, диска или тяжёлых вычислений. Отправляется на
 * {@link NeoForge#EVENT_BUS}.
 */
public final class ClothingCapeTextureEvent extends Event {

    private final AbstractClientPlayer player;
    private ResourceLocation capeTexture;

    public ClothingCapeTextureEvent(AbstractClientPlayer player, ResourceLocation capeTexture) {
        this.player = player;
        this.capeTexture = capeTexture;
    }

    public AbstractClientPlayer getPlayer() {
        return player;
    }

    /** Итоговая cape-текстура для этого игрока, или {@code null}, если плащ показывать не нужно. */
    public ResourceLocation getCapeTexture() {
        return capeTexture;
    }

    public void setCapeTexture(ResourceLocation capeTexture) {
        this.capeTexture = capeTexture;
    }
}
