package com.sandydev.dcs.clothing;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Отвечает ТОЛЬКО за сканирование {@code .minecraft/clothes/} и заполнение
 * {@link ClothingRegistry}. Никакой регистрации Item, никакого Curios, никакого рендера -
 * см. заголовок класса в архитектуре мода.
 * <p>
 * Должен быть вызван синхронно из конструктора мода, до того как
 * {@code ITEMS.register(modEventBus)} получит событие {@code RegisterEvent} - то есть
 * до того как {@link ClothingItems} начнёт создавать реальные предметы. Именно поэтому
 * загрузка полностью синхронна и происходит максимально рано.
 */
public final class ClothingLoader {

    public static final String CLOTHES_FOLDER_NAME = "clothes";

    private ClothingLoader() {
    }

    public record LoadResult(int archivesScanned, int itemsLoaded, int itemsFailed) {
    }

    public static LoadResult loadAll(Logger logger) {
        Path clothesDir = resolveClothesDirectory(logger);
        if (clothesDir == null) {
            return new LoadResult(0, 0, 0);
        }

        List<Path> archives = listZipArchives(clothesDir, logger);
        int loaded = 0;
        int failed = 0;

        for (Path archive : archives) {
            List<ClothingArchiveParser.ParsedEntry> entries = ClothingArchiveParser.parse(archive);
            for (ClothingArchiveParser.ParsedEntry entry : entries) {
                if (!entry.isOk()) {
                    failed++;
                    logger.warn("[DynamicClothingSystem] Пропущен повреждённый предмет одежды: {}",
                            entry.error().getMessage());
                    continue;
                }

                ClothingDefinition definition = entry.definition();
                if (ClothingRegistry.get().getDefinition(definition.id()).isPresent()) {
                    failed++;
                    logger.warn("[DynamicClothingSystem] Пропущен предмет с дублирующимся id '{}' из архива '{}' "
                                    + "- такой id уже был загружен ранее",
                            definition.id(), definition.sourceArchive());
                    continue;
                }

                ClothingRegistry.get().register(definition);
                loaded++;
                logger.info("[DynamicClothingSystem] Загружена одежда '{}' (слот={}, защита={}, слой/priority={}) из '{}'",
                        definition.id(), definition.slot(), definition.armor(), definition.toughness(), definition.priority(),
                        definition.sourceArchive());
            }
        }

        ClothingRegistry.get().freeze();
        logger.info("[DynamicClothingSystem] Сканирование '{}' завершено: архивов={}, предметов загружено={}, "
                        + "предметов пропущено из-за ошибок={}",
                clothesDir, archives.size(), loaded, failed);
        return new LoadResult(archives.size(), loaded, failed);
    }

    private static Path resolveClothesDirectory(Logger logger) {
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path clothesDir = gameDir.resolve(CLOTHES_FOLDER_NAME);
        try {
            Files.createDirectories(clothesDir);
        } catch (IOException e) {
            logger.error("[DynamicClothingSystem] Не удалось создать папку '{}': {}", clothesDir, e.getMessage(), e);
            return null;
        }
        return clothesDir;
    }

    private static List<Path> listZipArchives(Path clothesDir, Logger logger) {
        List<Path> result = new ArrayList<>();
        try (var stream = Files.list(clothesDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip"))
                    .forEach(result::add);
        } catch (IOException e) {
            logger.error("[DynamicClothingSystem] Не удалось прочитать содержимое '{}': {}",
                    clothesDir, e.getMessage(), e);
        }
        result.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
        return result;
    }
}
