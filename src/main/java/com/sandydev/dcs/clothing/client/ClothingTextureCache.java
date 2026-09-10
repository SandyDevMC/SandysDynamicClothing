package com.sandydev.dcs.clothing.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.sandydev.dcs.SandysDynamicClothing;
import com.sandydev.dcs.Config;
import com.sandydev.dcs.clothing.ClothingDefinition;
import com.sandydev.dcs.clothing.ClothingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Field;
import java.util.Set;

/**
 * Кэш скомпонованных skin-текстур по игрокам.
 * <p>
 * "Никогда не изменять реальный skin игрока, никогда не сохранять поверх оригинала" (ТЗ) -
 * здесь в буквальном смысле: {@link #getOverrideLocation} каждый раз (дёшево) проверяет,
 * не поменялась ли надетая одежда/скин игрока, и только при реальном изменении (дорого)
 * перестраивает текстуру заново из оригинального скина - см.
 * {@link ClothingTextureComposer#compose}. Между изменениями возвращает закэшированный
 * {@link ResourceLocation} без какой-либо работы с пикселями.
 * <p>
 * Это же автоматически покрывает требование ТЗ об обновлении текстуры "при экипировке /
 * снятии одежды / смене скина / смене предмета одежды / изменении Curios slots" - все эти
 * события меняют "подпись" состояния, проверяемую каждый кадр, без отдельной подписки на
 * события Curios.
 */
@EventBusSubscriber(modid = SandysDynamicClothing.MODID, value = Dist.CLIENT)
public final class ClothingTextureCache {

    private static final ClothingTextureCache INSTANCE = new ClothingTextureCache();

    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();
    private final Set<String> skinLoadFailureLogged = ConcurrentHashMap.newKeySet();

    private static final class Entry {
        ResourceLocation location;
        String signature;
    }

    private ClothingTextureCache() {
    }

    public static ClothingTextureCache get() {
        return INSTANCE;
    }

    /**
     * @return {@code ResourceLocation} скомпонованной текстуры, которую рендереру следует
     * использовать ВМЕСТО обычного скина игрока, или {@code null}, если оверрайд не нужен
     * (одежда не надета, либо оригинальный скин прочитать не удалось - в этом случае мод
     * молча ничего не подменяет, игрок рендерится как обычно).
     */
    public ResourceLocation getOverrideLocation(AbstractClientPlayer player) {
        List<ClothingDefinition> equipped = ClothingManager.getEquippedClothingSortedByPriority(player);
        UUID playerId = player.getUUID();

        if (equipped.isEmpty()) {
            if (cache.remove(playerId) != null) {
                SandysDynamicClothing.LOGGER.debug("[DCS][CACHE] player={} clothing=EMPTY -> override cleared", playerId);
            }
            return null;
        }

        ResourceLocation baseSkin = player.getSkin().texture();
        String signature = buildSignature(baseSkin, equipped);

        Entry entry = cache.computeIfAbsent(playerId, id -> new Entry());
        if (signature.equals(entry.signature) && entry.location != null) {
            return entry.location;
        }
        if (!signature.equals(entry.signature) && Config.DEBUG_LOGGING.get()) {
            SandysDynamicClothing.LOGGER.info(
                    "[DCS][CACHE] player={} state changed: skin={} clothing={}",
                    player.getGameProfile().getName(), baseSkin, equipped.stream().map(ClothingDefinition::id).toList());
        }

        ResourceLocation rebuilt = rebuild(player, baseSkin, equipped, playerId, signature);
        if (rebuilt == null) {
            return null;
        }
        ResourceLocation previous = entry.location;
        entry.signature = signature;
        entry.location = rebuilt;
        skinLoadFailureLogged.remove(playerId + "|" + signature);
        if (previous != null && !previous.equals(rebuilt)) {
            Minecraft.getInstance().getTextureManager().release(previous);
        }
        return rebuilt;
    }

    /** Сбрасывает кэш конкретного игрока (например, при выходе с сервера / смене мира). */
    public void invalidate(UUID playerId) {
        Entry entry = cache.remove(playerId);
        release(entry);
    }

    public void invalidateAll() {
        cache.values().forEach(this::release);
        cache.clear();
        skinLoadFailureLogged.clear();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        INSTANCE.invalidateAll();
        SandysDynamicClothing.LOGGER.info("[DCS][NETWORK] Client logged in; clothing render cache cleared");
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        INSTANCE.invalidateAll();
        SandysDynamicClothing.LOGGER.info("[DCS][NETWORK] Client logged out; clothing render cache cleared");
    }

    private void release(Entry entry) {
        if (entry != null && entry.location != null) {
            Minecraft.getInstance().getTextureManager().release(entry.location);
        }
    }

    private ResourceLocation rebuild(AbstractClientPlayer player, ResourceLocation baseSkin,
                                      List<ClothingDefinition> equipped, UUID playerId, String signature) {
        BaseSkin base = loadBaseSkin(baseSkin);
        if (base == null) {
            String failureKey = player.getUUID() + "|" + signature;
            if (skinLoadFailureLogged.add(failureKey)) {
                SandysDynamicClothing.LOGGER.info(
                        "[DCS][SKIN] player={} skin={} FAILED to load pixels; will retry while skin becomes available",
                        player.getGameProfile().getName(), baseSkin);
            }
            return null;
        }
        SandysDynamicClothing.LOGGER.debug(
                "[DCS][SKIN] player={} skin={} loaded {}x{} ownedByMod={}",
                player.getGameProfile().getName(), baseSkin, base.image().getWidth(), base.image().getHeight(),
                base.ownedByUs());

        NativeImage composed;
        try {
            composed = ClothingTextureComposer.compose(base.image(), equipped);
        } finally {
            if (base.ownedByUs()) {
                base.image().close();
            }
        }

        String key = Integer.toUnsignedString(signature.hashCode(), 16);
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(SandysDynamicClothing.MODID,
                "dynamic_skin/" + playerId + "/" + key);
        // Пересоздаём DynamicTexture и регистрируем под тем же ResourceLocation - TextureManager
        // сам корректно освобождает предыдущую текстуру на этом пути. Это происходит только при
        // реальном изменении одежды/скина, а не каждый кадр.
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(composed));
        SandysDynamicClothing.LOGGER.info(
                "[DCS][RENDER] player={} composed skin={} clothing={}",
                player.getGameProfile().getName(), location, equipped.stream().map(ClothingDefinition::id).toList());
        return location;
    }

    private record BaseSkin(NativeImage image, boolean ownedByUs) {
    }

    private BaseSkin loadBaseSkin(ResourceLocation skinLocation) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(skinLocation);
        if (Config.DEBUG_LOGGING.get()) {
            SandysDynamicClothing.LOGGER.info(
                    "[DCS][SKIN] lookup skin={} textureClass={}", skinLocation, texture.getClass().getName());
        }

        if (texture instanceof DynamicTexture dynamicTexture && dynamicTexture.getPixels() != null) {
            return new BaseSkin(dynamicTexture.getPixels(), false);
        }

        // Player skins are HttpTexture instances. Their source file is private inside HttpTexture,
        // so obtain the single File field reflectively instead of guessing the cache path.
        if (texture instanceof HttpTexture httpTexture) {
            Path cachedFile = findHttpTextureCacheFile(httpTexture);
            if (cachedFile != null && Files.isRegularFile(cachedFile)) {
                try (InputStream in = Files.newInputStream(cachedFile)) {
                    SandysDynamicClothing.LOGGER.debug(
                            "[DCS][SKIN] reading HttpTexture cache file {}", cachedFile);
                    return new BaseSkin(NativeImage.read(in), true);
                } catch (IOException e) {
                    SandysDynamicClothing.LOGGER.debug(
                            "[DCS][SKIN] failed reading HttpTexture cache file {}", cachedFile, e);
                }
            } else {
                if (Config.DEBUG_LOGGING.get()) {
                    SandysDynamicClothing.LOGGER.info(
                            "[DCS][SKIN] HttpTexture cache file unavailable yet: {}", cachedFile);
                }
            }
        }

        // Fallback for minecraft:skins/<hash>, including a few custom launchers/resource layouts.
        String skinPath = skinLocation.getPath();
        if (skinLocation.getNamespace().equals("minecraft") && skinPath.startsWith("skins/")) {
            String hash = skinPath.substring("skins/".length());
            if (hash.length() >= 2) {
                Path cachedFile = Minecraft.getInstance().gameDirectory.toPath()
                        .resolve("assets").resolve("skins")
                        .resolve(hash.substring(0, 2))
                        .resolve(hash);
                if (Files.isRegularFile(cachedFile)) {
                    try (InputStream in = Files.newInputStream(cachedFile)) {
                        SandysDynamicClothing.LOGGER.debug(
                                "[DCS][SKIN] reading fallback skin cache file {}", cachedFile);
                        return new BaseSkin(NativeImage.read(in), true);
                    } catch (IOException e) {
                        SandysDynamicClothing.LOGGER.debug(
                                "[DCS][SKIN] fallback skin cache read failed: {}", cachedFile, e);
                    }
                }
            }
            return null;
        }

        // Фолбэк для встроенных vanilla/resource-pack скинов (например, Steve/Alex).
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(skinLocation)) {
            return new BaseSkin(NativeImage.read(in), true);
        } catch (IOException e) {
            return null;
        }
    }

    private Path findHttpTextureCacheFile(HttpTexture texture) {
        for (Field field : HttpTexture.class.getDeclaredFields()) {
            if (field.getType() != java.io.File.class) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(texture);
                if (value instanceof java.io.File file) {
                    return file.toPath();
                }
            } catch (ReflectiveOperationException | SecurityException e) {
                SandysDynamicClothing.LOGGER.debug(
                        "[DCS][SKIN] unable to inspect HttpTexture cache file field", e);
                return null;
            }
        }
        return null;
    }

    private String buildSignature(ResourceLocation baseSkin, List<ClothingDefinition> equipped) {
        StringBuilder sb = new StringBuilder(baseSkin.toString());
        for (ClothingDefinition definition : equipped) {
            sb.append('|').append(definition.id());
        }
        return sb.toString();
    }
}
