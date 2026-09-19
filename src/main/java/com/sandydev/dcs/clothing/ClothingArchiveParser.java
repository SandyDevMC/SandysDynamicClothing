package com.sandydev.dcs.clothing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Разбирает один zip-архив одежды в список {@link ClothingDefinition}.
 * <p>
 * Архив может хранить {@code item.json}/{@code texture.png}/{@code icon.png} как в корне,
 * так и в произвольной вложенной папке (например {@code clothes/item.json}). Внутри одного
 * архива поддерживается несколько предметов - либо несколько файлов {@code item.json} в
 * разных папках, либо один {@code item.json}, корень которого - JSON-массив объектов.
 * <p>
 * Текстура и иконка резолвятся относительно папки конкретного {@code item.json}, если сам
 * объект не переопределяет имя файла полями {@code texture}/{@code icon} - это удобно,
 * когда в одном архиве лежит несколько предметов с разными текстурами.
 * <p>
 * Необязательные переводы лежат в подпапке {@code lang/} рядом с {@code item.json}
 * ({@code lang/en_us.json}, {@code lang/ru_ru.json}, ...) - см. {@link #readLangFiles}.
 * Проблема в файле перевода никогда не отменяет загрузку предмета: он просто остаётся на
 * базовых {@code name}/{@code description} из {@code item.json}, а причина попадает в
 * {@link ParsedEntry#warnings()}.
 * <p>
 * Ошибка в одном предмете ({@link ClothingLoadException}) не мешает разобрать остальные
 * предметы того же архива - она просто логируется на уровне {@link ClothingLoader}.
 */
public final class ClothingArchiveParser {

    /** Разрешённые символы id одежды - подмножество правил ResourceLocation-пути. */
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_.-]+$");

    private static final int REQUIRED_TEXTURE_SIZE = 64;

    /** Подпапка с необязательными переводами рядом с item.json. */
    private static final String LANG_FOLDER = "lang";

    /** Имя файла локали - как в ресурспаках Minecraft: строчные латинские буквы, цифры и '_'. */
    private static final Pattern LOCALE_PATTERN = Pattern.compile("^[a-z0-9_]+$");

    /** Ванильный размер cape/elytra-полотна - см. {@link ClothingKind#CAPE}. */
    private static final int REQUIRED_CAPE_WIDTH = 64;
    private static final int REQUIRED_CAPE_HEIGHT = 32;

    private ClothingArchiveParser() {
    }

    /**
     * Результат разбора одного {@code item.json} - либо готовое определение, либо ошибка.
     * Список содержит один элемент на каждый обнаруженный в JSON объект предмета.
     *
     * @param warnings некритичные замечания (например, битый файл {@code lang/ru_ru.json}).
     *                 Предмет при этом загружен, просто без соответствующего перевода.
     */
    public record ParsedEntry(ClothingDefinition definition, ClothingLoadException error, List<String> warnings) {

        public ParsedEntry {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        static ParsedEntry ok(ClothingDefinition definition, List<String> warnings) {
            return new ParsedEntry(definition, null, warnings);
        }

        static ParsedEntry failed(ClothingLoadException error) {
            return new ParsedEntry(null, error, List.of());
        }

        public boolean isOk() {
            return error == null;
        }
    }

    /**
     * Разбирает архив целиком. Никогда не бросает исключение наружу - неразбираемый архив
     * даёт пустой список плюс запись в логе на стороне вызывающего кода ({@link ClothingLoader}).
     */
    public static List<ParsedEntry> parse(Path archivePath) {
        List<ParsedEntry> results = new ArrayList<>();
        String archiveName = archivePath.getFileName().toString();

        try (ZipFile zip = new ZipFile(archivePath.toFile())) {
            List<ZipEntry> itemJsonEntries = zip.stream()
                    .filter(e -> !e.isDirectory())
                    .filter(e -> baseName(e.getName()).equalsIgnoreCase("item.json"))
                    .map(e -> (ZipEntry) e)
                    .toList();

            if (itemJsonEntries.isEmpty()) {
                results.add(ParsedEntry.failed(new ClothingLoadException(
                        ClothingLoadException.Reason.MISSING_ITEM_JSON,
                        "Архив '" + archiveName + "' не содержит ни одного item.json")));
                return results;
            }

            for (ZipEntry itemJsonEntry : itemJsonEntries) {
                String folder = folderOf(itemJsonEntry.getName());
                results.addAll(parseItemJson(zip, itemJsonEntry, folder, archiveName));
            }
        } catch (IOException e) {
            results.add(ParsedEntry.failed(new ClothingLoadException(
                    ClothingLoadException.Reason.IO_ERROR,
                    "Не удалось открыть архив '" + archiveName + "': " + e.getMessage(), e)));
        }
        return results;
    }

    private static List<ParsedEntry> parseItemJson(ZipFile zip, ZipEntry itemJsonEntry, String folder,
                                                     String archiveName) {
        List<ParsedEntry> out = new ArrayList<>();
        JsonElement root;
        try {
            byte[] raw = readEntry(zip, itemJsonEntry);
            root = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8));
        } catch (IOException e) {
            out.add(ParsedEntry.failed(new ClothingLoadException(
                    ClothingLoadException.Reason.IO_ERROR,
                    "Не удалось прочитать " + itemJsonEntry.getName() + " в '" + archiveName + "': " + e.getMessage(), e)));
            return out;
        } catch (JsonParseException e) {
            out.add(ParsedEntry.failed(new ClothingLoadException(
                    ClothingLoadException.Reason.INVALID_JSON,
                    "Некорректный JSON в " + itemJsonEntry.getName() + " ('" + archiveName + "'): " + e.getMessage(), e)));
            return out;
        }

        List<JsonObject> objects = new ArrayList<>();
        if (root.isJsonArray()) {
            JsonArray array = root.getAsJsonArray();
            for (JsonElement el : array) {
                if (el.isJsonObject()) {
                    objects.add(el.getAsJsonObject());
                } else {
                    out.add(ParsedEntry.failed(new ClothingLoadException(
                            ClothingLoadException.Reason.INVALID_JSON,
                            "Элемент массива в " + itemJsonEntry.getName() + " ('" + archiveName + "') не является объектом")));
                }
            }
        } else if (root.isJsonObject()) {
            objects.add(root.getAsJsonObject());
        } else {
            out.add(ParsedEntry.failed(new ClothingLoadException(
                    ClothingLoadException.Reason.INVALID_JSON,
                    "Корень " + itemJsonEntry.getName() + " ('" + archiveName + "') должен быть объектом или массивом объектов")));
            return out;
        }

        LangFiles langFiles = objects.isEmpty() ? LangFiles.EMPTY : readLangFiles(zip, folder, objects.size());
        boolean singleItem = objects.size() == 1;
        for (JsonObject obj : objects) {
            out.add(parseSingleDefinition(zip, obj, folder, archiveName, langFiles, singleItem));
        }
        return out;
    }

    private static ParsedEntry parseSingleDefinition(ZipFile zip, JsonObject obj, String folder, String archiveName,
                                                       LangFiles langFiles, boolean singleItem) {
        try {
            String id = requireString(obj, "id");
            if (!ID_PATTERN.matcher(id).matches()) {
                throw new ClothingLoadException(ClothingLoadException.Reason.INVALID_ID,
                        "Некорректный id '" + id + "' в архиве '" + archiveName + "' - разрешены строчные "
                                + "латинские буквы, цифры, '_', '-', '.'");
            }

            String name = requireString(obj, "name");
            List<String> description = readDescription(obj);
            String slot = requireString(obj, "slot");
            if (slot.isBlank()) {
                throw new ClothingLoadException(ClothingLoadException.Reason.UNKNOWN_SLOT_FORMAT,
                        "Пустой slot у предмета '" + id + "' в архиве '" + archiveName + "'");
            }

            ClothingKind kind;
            try {
                kind = ClothingKind.parse(readOptionalString(obj, "type", "skin", id));
            } catch (IllegalArgumentException e) {
                throw new ClothingLoadException(ClothingLoadException.Reason.INVALID_JSON,
                        "Поле 'type' у предмета '" + id + "': " + e.getMessage(), e);
            }

            int armor = readInt(obj, "armor", 0);
            if (armor < 0) {
                throw new ClothingLoadException(ClothingLoadException.Reason.INVALID_JSON,
                        "Поле 'armor' у предмета '" + id + "' не может быть отрицательным");
            }
            double toughness = readDouble(obj, "toughness", 0.0);
            if (toughness < 0.0) {
                throw new ClothingLoadException(ClothingLoadException.Reason.INVALID_JSON,
                        "Поле 'toughness' у предмета '" + id + "' не может быть отрицательным");
            }

            int priority;
            if (kind == ClothingKind.CAPE) {
                // Плащ не участвует в композиции скина - порядок наложения ему не нужен,
                // поле layer/priority у него просто игнорируется, даже если указано в item.json.
                priority = 0;
            } else if (obj.has("priority")) {
                // Обратная совместимость: точное числовое значение по-прежнему можно использовать.
                priority = readInt(obj, "priority", 20);
            } else {
                String layer = readOptionalString(obj, "layer", "base", id);
                try {
                    priority = ClothingLayer.parse(layer).priority();
                } catch (IllegalArgumentException e) {
                    throw new ClothingLoadException(ClothingLoadException.Reason.INVALID_JSON,
                            "Поле 'layer' у предмета '" + id + "': " + e.getMessage(), e);
                }
            }

            List<String> warnings = new ArrayList<>(langFiles.warnings());
            Map<String, ClothingTranslation> translations =
                    buildTranslations(obj, id, langFiles, singleItem, warnings);

            String textureFile = readOptionalString(obj, "texture", "texture.png", id);
            String iconFile = readOptionalString(obj, "icon", "icon.png", id);

            byte[] textureBytes = readSibling(zip, folder, textureFile, id, archiveName,
                    ClothingLoadException.Reason.MISSING_TEXTURE);
            if (kind == ClothingKind.CAPE) {
                validateCapeTextureSize(textureBytes, id, archiveName);
            } else {
                validateTextureSize(textureBytes, id, archiveName);
            }

            byte[] iconBytes = readSibling(zip, folder, iconFile, id, archiveName,
                    ClothingLoadException.Reason.MISSING_ICON);
            validateIsPng(iconBytes, id, archiveName);

            return ParsedEntry.ok(new ClothingDefinition(
                    id, name, description, slot, kind, armor, toughness, priority, textureBytes, iconBytes,
                    archiveName, translations), warnings);
        } catch (ClothingLoadException e) {
            return ParsedEntry.failed(e);
        } catch (RuntimeException e) {
            return ParsedEntry.failed(new ClothingLoadException(
                    ClothingLoadException.Reason.INVALID_JSON,
                    "Некорректное поле у предмета в архиве '" + archiveName + "': " + e.getMessage(), e));
        }
    }

    /**
     * Содержимое подпапки {@code lang/} одного {@code item.json}.
     *
     * @param byLocale код локали (нижний регистр, без {@code .json}) -&gt; корневой JSON-объект файла
     * @param warnings проблемы, из-за которых какой-то файл перевода пришлось пропустить
     */
    private record LangFiles(Map<String, JsonObject> byLocale, List<String> warnings) {
        static final LangFiles EMPTY = new LangFiles(Map.of(), List.of());
    }

    /**
     * Читает {@code <папка item.json>/lang/*.json}. Подпапки внутри {@code lang/} не
     * просматриваются. Всё в этой папке необязательно: отсутствие папки - нормальный случай,
     * а нечитаемый файл только даёт предупреждение и пропускается, но не ломает предмет.
     */
    private static LangFiles readLangFiles(ZipFile zip, String folder, int itemCount) {
        String prefix = (folder.isEmpty() ? "" : folder + "/") + LANG_FOLDER + "/";
        List<ZipEntry> candidates = zip.stream()
                .filter(e -> !e.isDirectory())
                .filter(e -> e.getName().length() > prefix.length()
                        && e.getName().regionMatches(true, 0, prefix, 0, prefix.length()))
                .filter(e -> e.getName().indexOf('/', prefix.length()) < 0)
                .filter(e -> e.getName().toLowerCase(Locale.ROOT).endsWith(".json"))
                .map(e -> (ZipEntry) e)
                .sorted(java.util.Comparator.comparing(ZipEntry::getName))
                .toList();
        if (candidates.isEmpty()) {
            return LangFiles.EMPTY;
        }

        Map<String, JsonObject> byLocale = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (ZipEntry entry : candidates) {
            String fileName = entry.getName().substring(prefix.length());
            String locale = fileName.substring(0, fileName.length() - ".json".length()).toLowerCase(Locale.ROOT);
            if (!LOCALE_PATTERN.matcher(locale).matches()) {
                warnings.add("файл перевода '" + entry.getName() + "' пропущен: имя должно быть кодом локали "
                        + "вида en_us / ru_ru (латиница, цифры и '_')");
                continue;
            }
            try {
                JsonElement root = JsonParser.parseString(
                        new String(readEntry(zip, entry), StandardCharsets.UTF_8));
                if (!root.isJsonObject()) {
                    warnings.add("файл перевода '" + entry.getName() + "' пропущен: корень должен быть объектом "
                            + "вида {\"name\": \"...\", \"description\": [\"...\"]}");
                    continue;
                }
                JsonObject object = root.getAsJsonObject();
                if (itemCount > 1 && (object.has("name") || object.has("description"))) {
                    warnings.add("файл перевода '" + entry.getName() + "': item.json содержит несколько предметов, "
                            + "поэтому 'name'/'description' на верхнем уровне ни к кому не относятся - "
                            + "используйте разделы по id: {\"<id>\": {\"name\": \"...\"}}");
                }
                byLocale.put(locale, object);
            } catch (IOException | JsonParseException e) {
                warnings.add("файл перевода '" + entry.getName() + "' пропущен: " + firstLine(e.getMessage()));
            }
        }
        return new LangFiles(byLocale, warnings);
    }

    /**
     * Собирает переводы предмета. Приоритет по полям, от низшего к высшему:
     * базовые {@code name}/{@code description} из item.json (здесь не участвуют - они запасной
     * вариант на стороне {@link ClothingLang}) &lt; устаревшее {@code translations} в item.json (только name)
     * &lt; {@code lang/<locale>.json}.
     */
    private static Map<String, ClothingTranslation> buildTranslations(JsonObject obj, String id, LangFiles langFiles,
                                                                       boolean singleItem, List<String> warnings)
            throws ClothingLoadException {
        Map<String, ClothingTranslation> result = new LinkedHashMap<>();

        readTranslations(obj, id).forEach((locale, text) ->
                result.put(locale.toLowerCase(Locale.ROOT), new ClothingTranslation(text, List.of())));

        for (Map.Entry<String, JsonObject> file : langFiles.byLocale().entrySet()) {
            String locale = file.getKey();
            JsonObject section = pickLangSection(file.getValue(), id, singleItem);
            if (section == null) {
                if (singleItem && !file.getValue().entrySet().isEmpty()) {
                    warnings.add("lang/" + locale + ".json у предмета '" + id + "' не содержит ни 'name', "
                            + "ни 'description' - файл проигнорирован");
                }
                continue;
            }
            try {
                ClothingTranslation fromFile = readTranslationSection(section);
                if (fromFile.isEmpty()) {
                    continue;
                }
                ClothingTranslation legacy = result.get(locale);
                String name = fromFile.name() != null ? fromFile.name() : legacy != null ? legacy.name() : null;
                result.put(locale, new ClothingTranslation(name, fromFile.description()));
            } catch (ClothingLoadException e) {
                warnings.add("lang/" + locale + ".json у предмета '" + id + "': " + e.getMessage());
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    /**
     * Выбирает из файла перевода раздел, относящийся к предмету. Поддерживаются две формы:
     * <ul>
     *   <li>плоская {@code {"name": ..., "description": ...}} - когда {@code item.json} описывает
     *       ровно один предмет (обычный случай "одна папка - один предмет");</li>
     *   <li>по id {@code {"<id>": {"name": ..., ...}}} - нужна, если один {@code item.json}
     *       содержит массив из нескольких предметов и файл перевода у них общий.</li>
     * </ul>
     */
    private static JsonObject pickLangSection(JsonObject root, String id, boolean singleItem) {
        JsonElement byId = root.get(id);
        if (byId != null && byId.isJsonObject()) {
            return byId.getAsJsonObject();
        }
        if (singleItem && (root.has("name") || root.has("description"))) {
            return root;
        }
        return null;
    }

    /** Читает {@code name}/{@code description} из раздела файла перевода; оба поля необязательны. */
    private static ClothingTranslation readTranslationSection(JsonObject section) throws ClothingLoadException {
        String name = null;
        if (section.has("name") && !section.get("name").isJsonNull()) {
            JsonElement el = section.get("name");
            if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
                throw new ClothingLoadException(ClothingLoadException.Reason.INVALID_JSON,
                        "поле 'name' должно быть строкой");
            }
            String trimmed = el.getAsString().trim();
            if (!trimmed.isEmpty()) {
                name = trimmed;
            }
        }
        return new ClothingTranslation(name, readDescription(section));
    }

    private static java.util.Map<String, String> readTranslations(JsonObject obj, String id) throws ClothingLoadException {
        if (!obj.has("translations") || obj.get("translations").isJsonNull()) {
            return java.util.Map.of();
        }
        JsonElement el = obj.get("translations");
        if (!el.isJsonObject()) {
            throw new ClothingLoadException(ClothingLoadException.Reason.INVALID_JSON,
                    "Поле 'translations' у предмета '" + id + "' должно быть объектом вида {\"ru_ru\":\"...\"}");
        }
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        for (var entry : el.getAsJsonObject().entrySet()) {
            String locale = entry.getKey().trim();
            JsonElement value = entry.getValue();
            if (locale.isEmpty() || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new ClothingLoadException(ClothingLoadException.Reason.INVALID_JSON,
                        "Каждый перевод в 'translations' у предмета '" + id + "' должен быть строкой");
            }
            String text = value.getAsString().trim();
            if (!text.isEmpty()) {
                result.put(locale, text);
            }
        }
        return java.util.Map.copyOf(result);
    }

    private static String requireString(JsonObject obj, String field) throws ClothingLoadException {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new ClothingLoadException(ClothingLoadException.Reason.MISSING_FIELD,
                    "Отсутствует обязательное поле '" + field + "'");
        }
        try {
            JsonElement element = obj.get(field);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException();
            }
            String value = element.getAsString().trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (RuntimeException e) {
            throw new ClothingLoadException(ClothingLoadException.Reason.MISSING_FIELD,
                    "Поле '" + field + "' должно быть непустой строкой", e);
        }
    }

    private static List<String> readDescription(JsonObject obj) throws ClothingLoadException {
        if (!obj.has("description") || obj.get("description").isJsonNull()) {
            return List.of();
        }
        JsonElement el = obj.get("description");
        try {
            if (el.isJsonArray()) {
                List<String> lines = new ArrayList<>();
                for (JsonElement line : el.getAsJsonArray()) {
                    if (!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString()) {
                        throw new IllegalArgumentException("каждый элемент должен быть строкой");
                    }
                    lines.add(line.getAsString());
                }
                return List.copyOf(lines);
            }
            if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("значение должно быть строкой или массивом строк");
            }
            return List.of(el.getAsString());
        } catch (RuntimeException e) {
            throw new ClothingLoadException(ClothingLoadException.Reason.INVALID_JSON,
                    "Поле 'description' должно быть строкой или массивом строк", e);
        }
    }

    private static double readDouble(JsonObject obj, String field, double defaultValue) throws ClothingLoadException {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            return defaultValue;
        }
        JsonElement element = obj.get(field);
        try {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException();
            }
            return element.getAsDouble();
        } catch (RuntimeException e) {
            throw new ClothingLoadException(ClothingLoadException.Reason.INVALID_JSON,
                    "Поле '" + field + "' должно быть числом", e);
        }
    }

    private static int readInt(JsonObject obj, String field, int defaultValue) throws ClothingLoadException {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            return defaultValue;
        }
        JsonElement element = obj.get(field);
        try {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException();
            }
            return element.getAsInt();
        } catch (RuntimeException e) {
            throw new ClothingLoadException(ClothingLoadException.Reason.INVALID_JSON,
                    "Поле '" + field + "' должно быть целым числом", e);
        }
    }

    private static String readOptionalString(JsonObject obj, String field, String defaultValue, String id)
            throws ClothingLoadException {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            return defaultValue;
        }
        JsonElement element = obj.get(field);
        try {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException();
            }
            String value = element.getAsString().trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (RuntimeException e) {
            throw new ClothingLoadException(ClothingLoadException.Reason.INVALID_JSON,
                    "Поле '" + field + "' у предмета '" + id + "' должно быть непустой строкой", e);
        }
    }

    private static byte[] readSibling(ZipFile zip, String folder, String fileName, String id, String archiveName,
                                       ClothingLoadException.Reason missingReason) throws ClothingLoadException {
        String fullPath = folder.isEmpty() ? fileName : folder + "/" + fileName;
        ZipEntry entry = zip.getEntry(fullPath);
        if (entry == null) {
            // Дополнительно пробуем найти файл с этим именем где угодно в архиве -
            // упрощает жизнь авторам, кладущим texture.png/icon.png не строго рядом с item.json.
            entry = zip.stream()
                    .filter(e -> !e.isDirectory())
                    .filter(e -> baseName(e.getName()).equalsIgnoreCase(fileName))
                    .findFirst().orElse(null);
        }
        if (entry == null) {
            throw new ClothingLoadException(missingReason,
                    "Не найден файл '" + fileName + "' для предмета '" + id + "' в архиве '" + archiveName + "'");
        }
        try {
            return readEntry(zip, entry);
        } catch (IOException e) {
            throw new ClothingLoadException(ClothingLoadException.Reason.IO_ERROR,
                    "Не удалось прочитать '" + fileName + "' для предмета '" + id + "' в архиве '" + archiveName
                            + "': " + e.getMessage(), e);
        }
    }

    private static void validateTextureSize(byte[] pngBytes, String id, String archiveName)
            throws ClothingLoadException {
        BufferedImage image = readPng(pngBytes, id, archiveName, ClothingLoadException.Reason.INVALID_TEXTURE_SIZE);
        try {
            if (image.getWidth() != REQUIRED_TEXTURE_SIZE || image.getHeight() != REQUIRED_TEXTURE_SIZE) {
                throw new ClothingLoadException(ClothingLoadException.Reason.INVALID_TEXTURE_SIZE,
                        "texture.png предмета '" + id + "' в архиве '" + archiveName + "' имеет размер "
                                + image.getWidth() + "x" + image.getHeight() + ", а должен быть "
                                + REQUIRED_TEXTURE_SIZE + "x" + REQUIRED_TEXTURE_SIZE);
            }
        } finally {
            image.flush();
        }
    }

    /** Как {@link #validateTextureSize}, но для плаща - требует ванильную раскладку cape/elytra 64x32. */
    private static void validateCapeTextureSize(byte[] pngBytes, String id, String archiveName)
            throws ClothingLoadException {
        BufferedImage image = readPng(pngBytes, id, archiveName, ClothingLoadException.Reason.INVALID_TEXTURE_SIZE);
        try {
            if (image.getWidth() != REQUIRED_CAPE_WIDTH || image.getHeight() != REQUIRED_CAPE_HEIGHT) {
                throw new ClothingLoadException(ClothingLoadException.Reason.INVALID_TEXTURE_SIZE,
                        "texture.png плаща '" + id + "' в архиве '" + archiveName + "' имеет размер "
                                + image.getWidth() + "x" + image.getHeight() + ", а должен быть "
                                + REQUIRED_CAPE_WIDTH + "x" + REQUIRED_CAPE_HEIGHT
                                + " (ванильная раскладка cape/elytra)");
            }
        } finally {
            image.flush();
        }
    }

    private static void validateIsPng(byte[] pngBytes, String id, String archiveName) throws ClothingLoadException {
        readPng(pngBytes, id, archiveName, ClothingLoadException.Reason.MISSING_ICON);
    }

    private static BufferedImage readPng(byte[] pngBytes, String id, String archiveName,
                                          ClothingLoadException.Reason reasonOnFailure) throws ClothingLoadException {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (image == null) {
                throw new IOException("не является валидным PNG");
            }
            return image;
        } catch (IOException e) {
            throw new ClothingLoadException(reasonOnFailure,
                    "Некорректный PNG у предмета '" + id + "' в архиве '" + archiveName + "': " + e.getMessage(), e);
        }
    }

    private static byte[] readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.max(64, (int) entry.getSize()));
            in.transferTo(buffer);
            return buffer.toByteArray();
        }
    }

    /** Первая строка сообщения исключения - у Gson в конец дописана ссылка на Troubleshooting. */
    private static String firstLine(String message) {
        if (message == null) {
            return "неизвестная ошибка";
        }
        int idx = message.indexOf('\n');
        return idx < 0 ? message : message.substring(0, idx);
    }

    private static String baseName(String zipEntryName) {
        int idx = zipEntryName.lastIndexOf('/');
        return idx < 0 ? zipEntryName : zipEntryName.substring(idx + 1);
    }

    private static String folderOf(String zipEntryName) {
        int idx = zipEntryName.lastIndexOf('/');
        return idx < 0 ? "" : zipEntryName.substring(0, idx);
    }
}
