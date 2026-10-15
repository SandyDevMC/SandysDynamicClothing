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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
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
import java.util.function.Supplier;

/**
 * Кэш скомпонованных skin-текстур по игрокам (и по статуям Straw Statues).
 * <p>
 * Мы никогда не трогаем оригинальный скин игрока и не перезаписываем его - вместо этого
 * {@link #getOverrideLocation} на каждый кадр дёшево проверяет, не изменилась ли надетая
 * одежда или сам скин, и только при реальном изменении (уже не дёшево) пересобирает текстуру
 * заново из оригинала - см. {@link ClothingTextureComposer#compose}. Пока ничего не поменялось,
 * отдаётся закэшированный {@link ResourceLocation} без единого обращения к пикселям.
 * <p>
 * Благодаря этому же механизму текстура сама обновляется при экипировке/снятии одежды, смене
 * скина или смене Curios-слотов - все эти события меняют "подпись" состояния, которая
 * сверяется каждый кадр, без отдельной подписки на события Curios.
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
     * использовать вместо обычного скина игрока, или {@code null}, если подменять нечего
     * (одежда не надета, либо оригинальный скин прочитать не удалось - в этом случае мод
     * молча ничего не подменяет, игрок рендерится как обычно).
     */
    public ResourceLocation getOverrideLocation(AbstractClientPlayer player) {
        return getOverrideLocation(player, () -> player.getSkin().texture());
    }

    /**
     * Для не-игроков, которые рисуются скином (статуи Straw Statues).
     *
     * @param baseSkin текстура, которую рендерер использовал бы без этого мода
     */
    public ResourceLocation getOverrideLocation(LivingEntity entity, ResourceLocation baseSkin) {
        return getOverrideLocation(entity, () -> baseSkin);
    }

    private ResourceLocation getOverrideLocation(LivingEntity entity, Supplier<ResourceLocation> baseSkinSupplier) {
        List<ClothingDefinition> equipped = ClothingManager.getEquippedClothingSortedByPriority(entity);
        UUID playerId = entity.getUUID();

        if (equipped.isEmpty()) {
            if (cache.remove(playerId) != null) {
                SandysDynamicClothing.LOGGER.debug("[DCS][CACHE] player={} одежды нет -> подмена снята", playerId);
            }
            return null;
        }

        ResourceLocation baseSkin = baseSkinSupplier.get();
        String signature = buildSignature(baseSkin, equipped);

        Entry entry = cache.computeIfAbsent(playerId, id -> new Entry());
        if (signature.equals(entry.signature) && entry.location != null) {
            return entry.location;
        }
        if (!signature.equals(entry.signature) && Config.DEBUG_LOGGING.get()) {
            SandysDynamicClothing.LOGGER.info(
                    "[DCS][CACHE] player={} состояние изменилось: skin={} clothing={}",
                    describe(entity), baseSkin, equipped.stream().map(ClothingDefinition::id).toList());
        }

        ResourceLocation rebuilt = rebuild(entity, baseSkin, equipped, playerId, signature);
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

    /** Сбрасывает кэш конкретного игрока (например, при выходе с сервера или смене мира). */
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
        SandysDynamicClothing.LOGGER.info("[DCS][NETWORK] Клиент вошёл в игру, кэш composited-текстур сброшен");
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        INSTANCE.invalidateAll();
        SandysDynamicClothing.LOGGER.info("[DCS][NETWORK] Клиент вышел из игры, кэш composited-текстур сброшен");
    }

    // Статуи выгружаются вместе с чанками - освобождаем их текстуры. Игроков не трогаем.
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() || event.getEntity() instanceof Player) {
            return;
        }
        INSTANCE.invalidate(event.getEntity().getUUID());
    }

    private static String describe(LivingEntity entity) {
        if (entity instanceof AbstractClientPlayer player) {
            return player.getGameProfile().getName();
        }
        return entity.getName().getString() + "@" + entity.getUUID();
    }

    private void release(Entry entry) {
        if (entry != null && entry.location != null) {
            Minecraft.getInstance().getTextureManager().release(entry.location);
        }
    }

    private ResourceLocation rebuild(LivingEntity entity, ResourceLocation baseSkin,
                                      List<ClothingDefinition> equipped, UUID playerId, String signature) {
        BaseSkin base = loadBaseSkin(baseSkin);
        if (base == null) {
            String failureKey = entity.getUUID() + "|" + signature;
            if (skinLoadFailureLogged.add(failureKey)) {
                SandysDynamicClothing.LOGGER.info(
                        "[DCS][SKIN] player={} skin={} не удалось загрузить пиксели, будем пробовать дальше",
                        describe(entity), baseSkin);
            }
            return null;
        }
        SandysDynamicClothing.LOGGER.debug(
                "[DCS][SKIN] player={} skin={} загружен {}x{} ownedByMod={}",
                describe(entity), baseSkin, base.image().getWidth(), base.image().getHeight(),
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
        // Регистрируем новую DynamicTexture под тем же путём - TextureManager сам освобождает
        // то, что было привязано к этому ResourceLocation раньше. Происходит только при реальном
        // изменении одежды или скина, не каждый кадр.
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(composed));
        SandysDynamicClothing.LOGGER.info(
                "[DCS][RENDER] player={} composed skin={} clothing={}",
                describe(entity), location, equipped.stream().map(ClothingDefinition::id).toList());
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

        // Скины игроков - это HttpTexture, а путь к файлу-кэшу у неё приватный. Проще достать
        // единственное поле типа File рефлексией, чем угадывать структуру кэша вручную.
        if (texture instanceof HttpTexture httpTexture) {
            Path cachedFile = findHttpTextureCacheFile(httpTexture);
            if (cachedFile != null && Files.isRegularFile(cachedFile)) {
                try (InputStream in = Files.newInputStream(cachedFile)) {
                    SandysDynamicClothing.LOGGER.debug(
                            "[DCS][SKIN] читаем файл кэша HttpTexture {}", cachedFile);
                    return new BaseSkin(NativeImage.read(in), true);
                } catch (IOException e) {
                    SandysDynamicClothing.LOGGER.debug(
                            "[DCS][SKIN] не удалось прочитать файл кэша HttpTexture {}", cachedFile, e);
                }
            } else {
                if (Config.DEBUG_LOGGING.get()) {
                    SandysDynamicClothing.LOGGER.info(
                            "[DCS][SKIN] файл кэша HttpTexture пока недоступен: {}", cachedFile);
                }
            }
        }

        // Фолбэк на minecraft:skins/<hash> - покрывает и некоторые нестандартные лаунчеры.
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
                                "[DCS][SKIN] читаем резервный файл кэша скина {}", cachedFile);
                        return new BaseSkin(NativeImage.read(in), true);
                    } catch (IOException e) {
                        SandysDynamicClothing.LOGGER.debug(
                                "[DCS][SKIN] не удалось прочитать резервный файл кэша скина {}", cachedFile, e);
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
                        "[DCS][SKIN] не удалось получить доступ к полю файла кэша HttpTexture", e);
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
