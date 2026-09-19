package com.sandydev.dcs.clothing;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Сканирует {@code .minecraft/clothes/} и наполняет {@link ClothingRegistry}.
 * Никакой регистрации Item и никакого Curios здесь нет - только чтение архивов.
 * <p>
 * Вызывается синхронно и максимально рано из конструктора мода: {@link ClothingItems}
 * читает {@link ClothingRegistry} для постановки предметов в очередь DeferredRegister,
 * а это должно произойти до RegisterEvent.
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
            // Одно замечание про файл перевода может прийти с несколькими предметами - логируем один раз.
            java.util.Set<String> warnedInArchive = new java.util.HashSet<>();
            for (ClothingArchiveParser.ParsedEntry entry : entries) {
                if (!entry.isOk()) {
                    failed++;
                    logger.warn("[DynamicClothingSystem] Пропущен повреждённый предмет одежды: {}",
                            entry.error().getMessage());
                    continue;
                }

                ClothingDefinition definition = entry.definition();
                for (String warning : entry.warnings()) {
                    if (warnedInArchive.add(warning)) {
                        logger.warn("[DynamicClothingSystem] Локализация в архиве '{}': {}",
                                archive.getFileName(), warning);
                    }
                }
                if (ClothingRegistry.get().getDefinition(definition.id()).isPresent()) {
                    failed++;
                    logger.warn("[DynamicClothingSystem] Пропущен предмет с дублирующимся id '{}' из архива '{}' "
                                    + "- такой id уже был загружен ранее",
                            definition.id(), definition.sourceArchive());
                    continue;
                }

                ClothingRegistry.get().register(definition);
                loaded++;
                logger.info("[DynamicClothingSystem] Загружена одежда '{}' (тип={}, слот={}, броня={}, твёрдость={}, "
                                + "priority={}, переводы={}) из '{}'",
                        definition.id(), definition.kind(), definition.slot(), definition.armor(), definition.toughness(),
                        definition.priority(), definition.translations().keySet(), definition.sourceArchive());
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
        // Сортировка нужна только для стабильного, предсказуемого порядка в логах между запусками.
        result.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
        return result;
    }
}
