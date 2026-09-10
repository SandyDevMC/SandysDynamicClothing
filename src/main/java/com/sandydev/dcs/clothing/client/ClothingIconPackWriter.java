package com.sandydev.dcs.clothing.client;

import com.sandydev.dcs.SandysDynamicClothing;
import com.sandydev.dcs.clothing.ClothingDefinition;
import com.sandydev.dcs.clothing.ClothingRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.util.Optional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Генерирует обычный (loose-файловый) resource pack с иконками и моделями предметов одежды
 * в {@code .minecraft/resourcepacks/dynamic_clothing_system_dynamic/}.
 * <p>
 * Почему не через {@code AddPackFindersEvent} + кастомный {@code PackResources} (что позволило
 * бы включать пак автоматически, без действий пользователя): точная сигнатура методов
 * {@code PackResources} в 1.21.1 (getResource/listResources/getMetadataSection/location/...)
 * не может быть на 100% надёжно подтверждена без доступа к самому jar-файлу Minecraft, а ошибка
 * в ней сломала бы КОМПИЛЯЦИЮ ВСЕГО МОДА, а не только иконок. Вместо этого используется только
 * {@code java.nio.file} - ноль риска для сборки. Расплата - пользователю (или сборщику модпака)
 * пак автоматически добавляется в выбранные ресурспаки после генерации.
 * <p>
 * Каждый запуск игры пак перегенерируется с нуля, чтобы всегда отражать актуальное содержимое
 * {@code .minecraft/clothes/}.
 */
public final class ClothingIconPackWriter {

    public static final String PACK_DIR_NAME = "dynamic_clothing_system_dynamic";
    private static final int PACK_FORMAT = 34; // соответствует 1.21-1.21.1

    private ClothingIconPackWriter() {
    }

    public static void writePack() {
        Path packRoot = FMLPaths.GAMEDIR.get().resolve("resourcepacks").resolve(PACK_DIR_NAME);
        Path assetsDir = packRoot.resolve("assets").resolve(SandysDynamicClothing.MODID);
        Path texturesDir = assetsDir.resolve("textures").resolve("item");
        Path modelsDir = assetsDir.resolve("models").resolve("item");
        Path langDir = assetsDir.resolve("lang");

        try {
            Files.createDirectories(texturesDir);
            Files.createDirectories(modelsDir);
            Files.createDirectories(langDir);
            writePackMcmeta(packRoot);
            deleteGeneratedFiles(texturesDir, ".png");
            deleteGeneratedFiles(modelsDir, ".json");
            deleteGeneratedFiles(langDir, ".json");

            int written = 0;
            java.util.Map<String, java.util.Map<String, String>> generatedNames = new java.util.LinkedHashMap<>();
            for (ClothingDefinition definition : ClothingRegistry.get().getAllDefinitions()) {
                Files.write(texturesDir.resolve(definition.id() + ".png"), definition.iconBytes());
                Files.writeString(modelsDir.resolve(definition.id() + ".json"), itemModelJson(definition.id()),
                        StandardCharsets.UTF_8);
                String key = "item." + SandysDynamicClothing.MODID + "." + definition.id();
                generatedNames.computeIfAbsent("en_us", locale -> new java.util.LinkedHashMap<>()).put(key, definition.name());
                generatedNames.computeIfAbsent("ru_ru", locale -> new java.util.LinkedHashMap<>()).put(key, definition.name());
                definition.translations().forEach((locale, text) ->
                        generatedNames.computeIfAbsent(locale, ignored -> new java.util.LinkedHashMap<>()).put(key, text));
                written++;
            }
            generatedNames.forEach((locale, entries) -> {
                try {
                    writeLangFile(langDir.resolve(locale + ".json"), entries);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            SandysDynamicClothing.LOGGER.info(
                    "[DynamicClothingSystem] Сгенерирован resource pack '{}' с иконками для {} предметов.",
                    PACK_DIR_NAME, written);
        } catch (IOException e) {
            SandysDynamicClothing.LOGGER.error(
                    "[DynamicClothingSystem] Не удалось сгенерировать resource pack с иконками одежды: {}",
                    e.getMessage(), e);
        }
    }

    /**
     * Регистрирует динамический pack как встроенный клиентский resource pack.
     * Событие вызывается до построения клиентского ResourceManager, поэтому
     * item-model JSON и иконки реально участвуют в загрузке моделей.
     */
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        writePack();

        Path packRoot = FMLPaths.GAMEDIR.get().resolve("resourcepacks").resolve(PACK_DIR_NAME);
        try {
            PackLocationInfo locationInfo = new PackLocationInfo(
                    "builtin/" + PACK_DIR_NAME,
                    Component.literal("Sandy's Dynamic Clothing (generated)"),
                    PackSource.BUILT_IN,
                    Optional.empty());

            PathPackResources.PathResourcesSupplier supplier =
                    new PathPackResources.PathResourcesSupplier(packRoot);

            Pack pack = Pack.readMetaAndCreate(
                    locationInfo,
                    supplier,
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, false));

            if (pack != null) {
                event.addRepositorySource(consumer -> consumer.accept(pack));
                SandysDynamicClothing.LOGGER.info(
                        "[DynamicClothingSystem] Зарегистрирован встроенный resource pack '{}'", PACK_DIR_NAME);
            } else {
                SandysDynamicClothing.LOGGER.error(
                        "[DynamicClothingSystem] Minecraft отклонил сгенерированный resource pack '{}'.", PACK_DIR_NAME);
            }
        } catch (RuntimeException e) {
            SandysDynamicClothing.LOGGER.error(
                    "[DynamicClothingSystem] Не удалось зарегистрировать resource pack '{}'.", PACK_DIR_NAME, e);
        }
    }

    private static void deleteGeneratedFiles(Path directory, String suffix) throws IOException {
        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }

    private static void writePackMcmeta(Path packRoot) throws IOException {
        String mcmeta = "{\n"
                + "  \"pack\": {\n"
                + "    \"pack_format\": " + PACK_FORMAT + ",\n"
                + "    \"description\": \"Dynamic Clothing System - сгенерированные иконки предметов одежды\"\n"
                + "  }\n"
                + "}\n";
        Files.writeString(packRoot.resolve("pack.mcmeta"), mcmeta, StandardCharsets.UTF_8);
    }

    private static void writeLangFile(Path file, java.util.Map<String, String> entries) throws IOException {
        StringBuilder json = new StringBuilder("{\n");
        int index = 0;
        for (var entry : entries.entrySet()) {
            json.append("  \"").append(escapeJson(entry.getKey())).append("\": \"")
                    .append(escapeJson(entry.getValue())).append("\"");
            if (index++ < entries.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("}\n");
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String itemModelJson(String id) {
        return "{\n"
                + "  \"parent\": \"item/generated\",\n"
                + "  \"textures\": {\n"
                + "    \"layer0\": \"" + SandysDynamicClothing.MODID + ":item/" + id + "\"\n"
                + "  }\n"
                + "}\n";
    }
}
