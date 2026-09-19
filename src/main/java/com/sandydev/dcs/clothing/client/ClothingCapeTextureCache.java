package com.sandydev.dcs.clothing.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.sandydev.dcs.SandysDynamicClothing;
import com.sandydev.dcs.clothing.ClothingDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Текстуры плащей ({@link com.sandydev.dcs.clothing.ClothingKind#CAPE}), зарегистрированные
 * как обычные {@link DynamicTexture} под стабильным {@link ResourceLocation}.
 * <p>
 * В отличие от {@link ClothingTextureCache}, здесь нечего кэшировать по игроку: текстура плаща
 * не зависит от скина владельца и не требует попиксельной композиции - это просто готовая
 * 64x32 картинка в ванильной раскладке cape/elytra (уже провалидированной {@code
 * ClothingArchiveParser} при загрузке). Достаточно один раз декодировать и зарегистрировать её
 * на id предмета одежды - дальше эта же текстура переиспользуется для любого игрока, надевшего
 * тот же плащ, пока не завершится текущая сессия клиента.
 */
public final class ClothingCapeTextureCache {

    private static final Map<String, ResourceLocation> REGISTERED = new ConcurrentHashMap<>();

    private ClothingCapeTextureCache() {
    }

    /**
     * @return {@code ResourceLocation} текстуры плаща, готовой к использованию в качестве
     * cape-текстуры игрока, или {@code null}, если декодировать текстуру не удалось (не должно
     * происходить - PNG уже провалидирован при загрузке архива).
     */
    public static ResourceLocation getOrRegister(ClothingDefinition cape) {
        return REGISTERED.computeIfAbsent(cape.id(), id -> register(cape));
    }

    private static ResourceLocation register(ClothingDefinition cape) {
        NativeImage image;
        try {
            image = NativeImage.read(new ByteArrayInputStream(cape.textureBytes()));
        } catch (IOException e) {
            SandysDynamicClothing.LOGGER.error(
                    "[DCS][CAPE] Не удалось декодировать текстуру плаща '{}': {}", cape.id(), e.getMessage(), e);
            return null;
        }
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(SandysDynamicClothing.MODID,
                "dynamic_cape/" + cape.id());
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
        SandysDynamicClothing.LOGGER.debug(
                "[DCS][CAPE] Зарегистрирована текстура плаща '{}' -> {}", cape.id(), location);
        return location;
    }
}
