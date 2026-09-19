package com.sandydev.dcs.clothing.client;

import com.sandydev.dcs.SandysDynamicClothing;
import com.sandydev.dcs.clothing.ClothingDefinition;
import com.sandydev.dcs.clothing.ClothingLang;
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
 * Пак пишется на диск и подключается через штатный {@code PathPackResources}, а не через
 * свою реализацию {@code PackResources} "в памяти" - точная сигнатура её методов в 1.21.1
 * не гарантирована без доступа к jar-файлу самого Minecraft, и ошибка в ней сломала бы
 * компиляцию всего мода, а не только иконок. Loose-файлы дают тот же результат (пак
 * подключается автоматически через {@code AddPackFindersEvent}, без действий игрока),
 * но без этого риска.
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
            for (ClothingDefinition definition : ClothingRegistry.get().getAllDefinitions()) {
                Files.write(texturesDir.resolve(definition.id() + ".png"), definition.iconBytes());
                Files.writeString(modelsDir.resolve(definition.id() + ".json"), itemModelJson(definition.id()),
                        StandardCharsets.UTF_8);
                written++;
            }
            // Имена и описания: базовый текст из item.json уходит в en_us (запасной слой для всех
            // языков), переводы из lang/<locale>.json - в файлы своих локалей. Подробности - в ClothingLang.
            java.util.Map<String, java.util.Map<String, String>> generatedLang = ClothingLang.buildEntries(
                    SandysDynamicClothing.MODID, ClothingRegistry.get().getAllDefinitions());
            generatedLang.forEach((locale, entries) -> {
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
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
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
