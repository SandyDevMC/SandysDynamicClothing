package com.sandydev.dcs.clothing.client;

import com.sandydev.dcs.SandysDynamicClothing;
import com.sandydev.dcs.clothing.ClothingDefinition;
import com.sandydev.dcs.clothing.ClothingLang;
import com.sandydev.dcs.clothing.ClothingRegistry;
import com.sandydev.dcs.clothing.ClothingStyle;
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
 * <p>
 * Иконки стилей (см. {@link ClothingDefinition#styles()}) кладутся в подпапки {@code styles/<id>/<номер>}
 * рядом с основной иконкой, а модель предмета получает {@code overrides} по свойству
 * {@code dynamic_clothing_system:style} (его отдаёт клиент, см. {@code SandysDynamicClothingClient}).
 * Оверрайды пишутся только у предметов, у которых хотя бы у одного стиля есть своя иконка, - у
 * остальных модель остаётся ровно такой же, как раньше.
 */
public final class ClothingIconPackWriter {

    public static final String PACK_DIR_NAME = "dynamic_clothing_system_dynamic";
    private static final int PACK_FORMAT = 34; // соответствует 1.21-1.21.1

    /** Подпапка для иконок и моделей стилей внутри {@code textures/item} и {@code models/item}. */
    private static final String STYLES_DIR = "styles";

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
            deleteRecursively(texturesDir.resolve(STYLES_DIR));
            deleteRecursively(modelsDir.resolve(STYLES_DIR));

            int written = 0;
            for (ClothingDefinition definition : ClothingRegistry.get().getAllDefinitions()) {
                Files.write(texturesDir.resolve(definition.id() + ".png"), definition.iconBytes());
                Files.writeString(modelsDir.resolve(definition.id() + ".json"), itemModelJson(definition),
                        StandardCharsets.UTF_8);
                writeStyleIcons(definition, texturesDir, modelsDir);
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

    /** Пишет иконки и модели стилей предмета, у которых есть своя иконка (стиль без иконки модели не требует). */
    private static void writeStyleIcons(ClothingDefinition definition, Path texturesDir, Path modelsDir)
            throws IOException {
        if (!definition.hasStyleIcons()) {
            return;
        }
        Path styleTextures = texturesDir.resolve(STYLES_DIR).resolve(definition.id());
        Path styleModels = modelsDir.resolve(STYLES_DIR).resolve(definition.id());
        Files.createDirectories(styleTextures);
        Files.createDirectories(styleModels);
        for (ClothingStyle style : definition.styles().values()) {
            if (!style.hasOwnIcon()) {
                continue;
            }
            Files.write(styleTextures.resolve(style.number() + ".png"), style.iconBytes());
            Files.writeString(styleModels.resolve(style.number() + ".json"),
                    modelJson(styleTextureRef(definition.id(), style.number()), ""), StandardCharsets.UTF_8);
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var walk = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) walk.sorted(java.util.Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String baseTextureRef(String id) {
        return SandysDynamicClothing.MODID + ":item/" + id;
    }

    private static String styleTextureRef(String id, int number) {
        return SandysDynamicClothing.MODID + ":item/" + STYLES_DIR + "/" + id + "/" + number;
    }

    private static String itemModelJson(ClothingDefinition definition) {
        return modelJson(baseTextureRef(definition.id()), overridesJson(definition));
    }

    /**
     * {@code overrides} модели предмета: для каждого номера стиля - модель его иконки (или основная
     * модель, если у стиля своей иконки нет). Предикат {@code style >= N} срабатывает по принципу
     * «последний подходящий побеждает», поэтому номера идут по возрастанию, а явно перечислены все
     * стили, включая без иконки, - иначе стиль без иконки подхватил бы иконку предыдущего номера.
     * Стиль 0 оверрайда не требует: ни один предикат не срабатывает, остаётся основная модель.
     */
    private static String overridesJson(ClothingDefinition definition) {
        if (!definition.hasStyleIcons()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (ClothingStyle style : definition.styles().values()) {
            String model = style.hasOwnIcon()
                    ? styleTextureRef(definition.id(), style.number())
                    : baseTextureRef(definition.id());
            if (!out.isEmpty()) {
                out.append(",\n");
            }
            out.append("    { \"predicate\": { \"").append(SandysDynamicClothing.MODID).append(":style\": ")
                    .append(style.number()).append(" }, \"model\": \"").append(model).append("\" }");
        }
        return out.toString();
    }

    private static String modelJson(String textureRef, String overrides) {
        return "{\n"
                + "  \"parent\": \"item/generated\",\n"
                + "  \"textures\": {\n"
                + "    \"layer0\": \"" + textureRef + "\"\n"
                + "  },\n"
                // Одежда в ванильном слоте шлема: CustomHeadLayer рисует на голове любой не-ArmorItem
                // как плоскую иконку. Одежда рисуется скином, поэтому иконку на голове скрываем.
                + "  \"display\": {\n"
                + "    \"head\": { \"scale\": [0.0001, 0.0001, 0.0001] }\n"
                + "  }"
                + (overrides.isEmpty() ? "\n" : ",\n  \"overrides\": [\n" + overrides + "\n  ]\n")
                + "}\n";
    }
}
