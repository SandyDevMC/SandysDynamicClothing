package com.sandydev.dcs.clothing.client;

import com.sandydev.dcs.clothing.ClothingManager;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * Хуки для статуй Straw Statues, их вызывает {@code StrawStatueRendererMixin}. Статуя - это
 * {@code ArmorStand}, одежда из её слотов брони читается как у игрока. Параметры-статуи имеют тип
 * {@link Object}, потому что Straw Statues нет в compile-classpath.
 */
public final class ClothingStatueRenderHooks {

    private static final ResourceLocation STRAW_STATUE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("strawstatues", "textures/entity/straw_statue.png");

    private ClothingStatueRenderHooks() {
    }

    /** Текстура тела статуи с надетой одеждой, либо {@code original}, если подменять нечего. */
    public static ResourceLocation overrideTexture(Object statue, ResourceLocation original) {
        if (!(statue instanceof LivingEntity entity) || original == null) {
            return original;
        }
        ResourceLocation override = ClothingTextureCache.get().getOverrideLocation(entity, original);
        return override != null ? override : original;
    }

    /**
     * Подставляет плащ из одежды в {@link PlayerSkin} статуи, слой плаща Straw Statues берёт
     * текстуру оттуда. У статуи без профиля {@code original} пустой, тогда скин собираем сами.
     */
    public static Optional<PlayerSkin> overrideSkin(Object statue, Optional<PlayerSkin> original) {
        if (!(statue instanceof LivingEntity entity) || original == null) {
            return original;
        }
        ResourceLocation cape = ClothingManager.getEquippedCape(entity)
                .map(ClothingCapeTextureCache::getOrRegister)
                .orElse(null);
        if (cape == null) {
            return original;
        }
        if (original.isPresent()) {
            PlayerSkin skin = original.get();
            return Optional.of(new PlayerSkin(skin.texture(), skin.textureUrl(), cape,
                    skin.elytraTexture(), skin.model(), skin.secure()));
        }
        return Optional.of(new PlayerSkin(STRAW_STATUE_TEXTURE, null, cape, null,
                PlayerSkin.Model.WIDE, true));
    }
}
