package com.sandydev.dcs.clothing;

import com.sandydev.dcs.api.event.ClothingDefinitionsCollectedEvent;
import com.sandydev.dcs.api.event.ClothingPackDiscoveryEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Сканирует {@code .minecraft/clothes/} (предварительно устанавливая встроенные демонстрационные
 * паки, если их там ещё нет), плюс архивы, добавленные аддонами через {@link
 * ClothingPackDiscoveryEvent}, и наполняет {@link ClothingRegistry}.
 * Никакой регистрации Item и никакого Curios здесь нет - только чтение архивов.
 * <p>
 * Вызывается из обработчика {@code FMLConstructModEvent} мода: это уже после того, как
 * отработали конструкторы ВСЕХ модов (важно для хуков аддонов - см. {@code
 * ClothingPackDiscoveryEvent}/{@code ClothingDefinitionsCollectedEvent} и ADDON_API.md), но
 * всё ещё до RegisterEvent, которого требует {@link ClothingItems}, читающий {@link
 * ClothingRegistry} для постановки предметов в очередь DeferredRegister.
 */
public final class ClothingLoader {

    public static final String CLOTHES_FOLDER_NAME = "clothes";

    /** {@code .cloth} - текущий формат архива одежды, {@code .zip} - для обратной совместимости. */
    private static final List<String> ARCHIVE_EXTENSIONS = List.of(".cloth", ".zip");

    /** Паки-примеры, поставляемые прямо внутри jar, чтобы мод показывал себя без внешней загрузки. */
    private static final List<String> BUNDLED_EXAMPLE_PACKS = List.of(
            "sandys_classics_pack.cloth",
            "sandys_frak.cloth",
            "sandys_trousers.cloth"
    );
    private static final String BUNDLED_EXAMPLE_RESOURCE_ROOT = "/example_packs/";

    private ClothingLoader() {
    }

    public record LoadResult(int archivesScanned, int itemsLoaded, int itemsFailed) {
    }

    public static LoadResult loadAll(Logger logger) {
        Path clothesDir = resolveClothesDirectory(logger);
        List<Path> archives = new ArrayList<>();
        if (clothesDir != null) {
            installBundledExamplePacks(clothesDir, logger);
            archives.addAll(listArchives(clothesDir, logger));
        }

        // Хук для аддонов: подмешать свои архивы одежды в общий конвейер загрузки (см. ADDON_API.md).
        ClothingPackDiscoveryEvent discoveryEvent = new ClothingPackDiscoveryEvent(List.copyOf(archives));
        NeoForge.EVENT_BUS.post(discoveryEvent);
        for (Path extra : discoveryEvent.getExtraArchives()) {
            if (!archives.contains(extra)) {
                archives.add(extra);
            }
        }

        List<ClothingDefinition> collected = new ArrayList<>();
        int failed = 0;

        for (Path archive : archives) {
            List<ClothingArchiveParser.ParsedEntry> entries = ClothingArchiveParser.parse(archive);
            // Одно замечание про файл перевода/манифест может прийти с несколькими предметами - логируем один раз.
            java.util.Set<String> warnedInArchive = new java.util.HashSet<>();
            for (ClothingArchiveParser.ParsedEntry entry : entries) {
                if (!entry.isOk()) {
                    failed++;
                    logger.warn("[DynamicClothingSystem] Пропущен повреждённый предмет одежды: {}",
                            entry.error().getMessage());
                    continue;
                }
                for (String warning : entry.warnings()) {
                    if (warnedInArchive.add(warning)) {
                        logger.warn("[DynamicClothingSystem] Замечание по архиву '{}': {}",
                                archive.getFileName(), warning);
                    }
                }
                collected.add(entry.definition());
            }
        }

        // Хук для аддонов: добавить свои ClothingDefinition в обход архивов вовсе, либо
        // убрать/заменить то, что нашлось на диске (см. ADDON_API.md).
        ClothingDefinitionsCollectedEvent collectedEvent = new ClothingDefinitionsCollectedEvent(collected);
        NeoForge.EVENT_BUS.post(collectedEvent);

        // Совпавший id не отбрасывается, а переопределяется - последний в списке побеждает.
        // Это даёт аддонам возможность сознательно подменить чужое определение своим, а не
        // просто проиграть гонку с тем, что случайно оказалось раньше по алфавиту.
        Map<String, ClothingDefinition> byId = new LinkedHashMap<>();
        for (ClothingDefinition definition : collectedEvent.getDefinitions()) {
            ClothingDefinition previous = byId.put(definition.id(), definition);
            if (previous != null) {
                logger.warn("[DynamicClothingSystem] id '{}' переопределён: было из '{}', стало из '{}'",
                        definition.id(), previous.sourceArchive(), definition.sourceArchive());
            }
        }

        int loaded = 0;
        for (ClothingDefinition definition : byId.values()) {
            ClothingRegistry.get().register(definition);
            loaded++;
            logger.info("[DynamicClothingSystem] Загружена одежда '{}' (тип={}, слот={}, броня={}, твёрдость={}, "
                            + "priority={}, формат={}, переводы={}, стили={}) из '{}'",
                    definition.id(), definition.kind(), definition.slot(), definition.armor(), definition.toughness(),
                    definition.priority(), definition.packFormat(), definition.translations().keySet(),
                    definition.styles().keySet(), definition.sourceArchive());
        }

        ClothingRegistry.get().freeze();
        logger.info("[DynamicClothingSystem] Сканирование '{}' завершено: архивов={}, предметов загружено={}, "
                        + "предметов пропущено из-за ошибок={}",
                clothesDir != null ? clothesDir : "<недоступна>", archives.size(), loaded, failed);
        return new LoadResult(archives.size(), loaded, failed);
    }


    /**
     * Устанавливает встроенные демонстрационные паки в стандартную папку одежды только если
     * пользователь ещё не положил туда архив с таким именем. После этого они проходят ровно
     * через тот же загрузчик, что и обычные пользовательские паки.
     */
    private static void installBundledExamplePacks(Path clothesDir, Logger logger) {
        for (String packName : BUNDLED_EXAMPLE_PACKS) {
            Path target = clothesDir.resolve(packName);
            if (Files.isRegularFile(target)) {
                continue;
            }

            try (InputStream input = ClothingLoader.class.getResourceAsStream(
                    BUNDLED_EXAMPLE_RESOURCE_ROOT + packName)) {
                if (input == null) {
                    logger.warn("[DynamicClothingSystem] Встроенный пример '{}' не найден в jar", packName);
                    continue;
                }
                Files.copy(input, target);
                logger.info("[DynamicClothingSystem] Установлен встроенный демонстрационный пак одежды '{}' в '{}'",
                        packName, target);
            } catch (IOException e) {
                logger.warn("[DynamicClothingSystem] Не удалось установить встроенный демонстрационный пак '{}': {}",
                        packName, e.getMessage());
            }
        }
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

    private static List<Path> listArchives(Path clothesDir, Logger logger) {
        List<Path> result = new ArrayList<>();
        try (var stream = Files.list(clothesDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> hasArchiveExtension(path.getFileName().toString()))
                    .forEach(result::add);
        } catch (IOException e) {
            logger.error("[DynamicClothingSystem] Не удалось прочитать содержимое '{}': {}",
                    clothesDir, e.getMessage(), e);
        }
        // Сортировка нужна только для стабильного, предсказуемого порядка в логах между запусками.
        result.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
        return result;
    }

    private static boolean hasArchiveExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ext : ARCHIVE_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
