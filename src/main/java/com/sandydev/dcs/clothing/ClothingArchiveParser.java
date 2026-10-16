package com.sandydev.dcs.clothing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Разбирает один архив одежды ({@code .cloth}, либо устаревший {@code .zip}) в список
 * {@link ClothingDefinition}.
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
 * Необязательный {@code manifest.json} в корне архива объявляет версию формата пака - см.
 * {@link #readManifest} и {@link ClothingPackFormat}.
 * <p>
 * Необязательные дополнительные стили (варианты текстуры) подхватываются по имени файла - к имени
 * основной текстуры дописывается номер ({@code texture1.png}, {@code texture2.png}, ...) - либо
 * перечисляются явно полем {@code styles} в {@code item.json}, см. {@link #readStyles}.
 * Стили - это дополнение: проблема в файле стиля никогда не отменяет загрузку предмета.
 * <p>
 * Необязательные переводы лежат в подпапке {@code lang/} рядом с {@code item.json}
 * ({@code lang/en_us.json}, {@code lang/ru_ru.json}, ...) - см. {@link #readLangFiles}.
 * Проблема в файле перевода никогда не отменяет загрузку предмета: он просто остаётся на
 * базовых {@code name}/{@code description} из {@code item.json}, а причина попадает в
 * {@link ParsedEntry#warnings()}.
 * <p>
 * Ошибка в одном предмете ({@link ClothingLoadException}) не мешает разобрать остальные
 * предметы того же архива - она просто логируется на уровне {@link ClothingLoader}.
 * <p>
 * Ничего из архива никогда не исполняется и не пишется на диск, а имена записей и размеры
 * проверяются на входе - см. {@link ClothingArchiveSecurity}.
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

    /** Необязательный манифест архива - только в корне, см. {@link #readManifest}. */
    private static final String MANIFEST_ENTRY_NAME = "manifest.json";

    private ClothingArchiveParser() {
    }

    /**
     * Результат разбора одного {@code item.json} - либо готовое определение, либо ошибка.
     * Список содержит один элемент на каждый обнаруженный в JSON объект предмета.
     *
     * @param warnings некритичные замечания (например, битый файл {@code lang/ru_ru.json} или
     *                 неизвестная версия в {@code manifest.json}). Предмет при этом загружен,
     *                 просто с запасным вариантом вместо того, что не удалось разобрать.
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

        try {
            if (!ClothingArchiveSecurity.isArchiveFileSizeSafe(archivePath)) {
                results.add(ParsedEntry.failed(new ClothingLoadException(
                        ClothingLoadException.Reason.ARCHIVE_TOO_LARGE,
                        "Архив '" + archiveName + "' превышает предельный размер файла "
                                + ClothingArchiveSecurity.MAX_ARCHIVE_FILE_SIZE + " байт")));
                return results;
            }
        } catch (IOException e) {
            results.add(ParsedEntry.failed(new ClothingLoadException(
                    ClothingLoadException.Reason.IO_ERROR,
                    "Не удалось прочитать размер файла архива '" + archiveName + "': " + e.getMessage(), e)));
            return results;
        }

        try (ZipFile zip = new ZipFile(archivePath.toFile())) {
            if (!ClothingArchiveSecurity.isEntryCountSafe(zip)) {
                results.add(ParsedEntry.failed(new ClothingLoadException(
                        ClothingLoadException.Reason.TOO_MANY_ENTRIES,
                        "Архив '" + archiveName + "' содержит больше " + ClothingArchiveSecurity.MAX_ENTRY_COUNT
                                + " записей")));
                return results;
            }

            ClothingArchiveSecurity.ReadBudget budget =
                    new ClothingArchiveSecurity.ReadBudget(ClothingArchiveSecurity.MAX_TOTAL_UNCOMPRESSED_SIZE);

            ManifestResult manifestResult = readManifest(zip, archiveName, budget);

            List<ZipEntry> itemJsonEntries = zip.stream()
                    .filter(e -> !e.isDirectory())
                    .filter(e -> ClothingArchiveSecurity.isEntryNameSafe(e.getName()))
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
                results.addAll(parseItemJson(zip, itemJsonEntry, folder, archiveName,
                        manifestResult.manifest(), manifestResult.warnings(), budget));
            }
        } catch (IOException e) {
            results.add(ParsedEntry.failed(new ClothingLoadException(
                    ClothingLoadException.Reason.IO_ERROR,
                    "Не удалось открыть архив '" + archiveName + "': " + e.getMessage(), e)));
        }
        return results;
    }

    /** {@code manifest.json} архива вместе с некритичными замечаниями к его разбору. */
    private record ManifestResult(ClothingPackManifest manifest, List<String> warnings) {
    }

    /**
     * Читает необязательный {@code manifest.json} из корня архива (в отличие от
     * {@code item.json}, манифест один на весь архив и ищется только в корне, а не во
     * вложенных папках). Отсутствие файла - обычный случай, даёт {@link ClothingPackManifest#DEFAULT}
     * без предупреждений. Любая проблема с содержимым (некорректный JSON, неизвестная версия)
     * тоже не считается ошибкой уровня архива - она просто откатывается на {@link
     * ClothingPackFormat#LATEST} с предупреждением, которое прикладывается к каждому предмету
     * этого архива.
     */
    private static ManifestResult readManifest(ZipFile zip, String archiveName,
                                                ClothingArchiveSecurity.ReadBudget budget) {
        ZipEntry entry = zip.stream()
                .filter(e -> !e.isDirectory())
                .filter(e -> ClothingArchiveSecurity.isEntryNameSafe(e.getName()))
                .filter(e -> e.getName().equalsIgnoreCase(MANIFEST_ENTRY_NAME))
                .map(e -> (ZipEntry) e)
                .findFirst().orElse(null);
        if (entry == null) {
            return new ManifestResult(ClothingPackManifest.DEFAULT, List.of());
        }

        byte[] raw;
        try {
            raw = ClothingArchiveSecurity.readBounded(zip, entry, budget, archiveName);
        } catch (IOException e) {
            return new ManifestResult(ClothingPackManifest.DEFAULT,
                    List.of("manifest.json не удалось прочитать (" + e.getMessage()
                            + ") - используется последняя версия формата пака"));
        }

        JsonElement root;
        try {
            root = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8));
        } catch (JsonParseException e) {
            return new ManifestResult(ClothingPackManifest.DEFAULT,
                    List.of("manifest.json содержит некорректный JSON (" + firstLine(e.getMessage())
                            + ") - используется последняя версия формата пака"));
        }
        if (!root.isJsonObject()) {
            return new ManifestResult(ClothingPackManifest.DEFAULT,
                    List.of("manifest.json должен быть объектом - используется последняя версия формата пака"));
        }

        JsonObject obj = root.getAsJsonObject();
        if (!obj.has("format_version") || obj.get("format_version").isJsonNull()) {
            return new ManifestResult(ClothingPackManifest.DEFAULT, List.of());
        }
        JsonElement versionEl = obj.get("format_version");
        if (!versionEl.isJsonPrimitive() || !versionEl.getAsJsonPrimitive().isString()) {
            return new ManifestResult(ClothingPackManifest.DEFAULT,
                    List.of("поле 'format_version' в manifest.json должно быть строкой - используется "
                            + "последняя версия формата пака"));
        }
        try {
            ClothingPackFormat format = ClothingPackFormat.parse(versionEl.getAsString());
            return new ManifestResult(new ClothingPackManifest(format), List.of());
        } catch (IllegalArgumentException e) {
            return new ManifestResult(ClothingPackManifest.DEFAULT,
                    List.of("manifest.json: " + e.getMessage() + " - используется последняя версия формата пака"));
        }
    }

    private static List<ParsedEntry> parseItemJson(ZipFile zip, ZipEntry itemJsonEntry, String folder,
                                                     String archiveName, ClothingPackManifest manifest,
                                                     List<String> manifestWarnings,
                                                     ClothingArchiveSecurity.ReadBudget budget) {
        List<ParsedEntry> out = new ArrayList<>();
        JsonElement root;
        try {
            byte[] raw = ClothingArchiveSecurity.readBounded(zip, itemJsonEntry, budget, archiveName);
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

        LangFiles langFiles = objects.isEmpty() ? LangFiles.EMPTY : readLangFiles(zip, folder, objects.size(), budget, archiveName);
        boolean singleItem = objects.size() == 1;
        Set<String> reservedFiles = collectReservedFileNames(objects, folder);
        for (JsonObject obj : objects) {
            out.add(parseSingleDefinition(zip, obj, folder, archiveName, langFiles, singleItem, manifest,
                    manifestWarnings, reservedFiles, budget));
        }
        return out;
    }

    private static ParsedEntry parseSingleDefinition(ZipFile zip, JsonObject obj, String folder, String archiveName,
                                                       LangFiles langFiles, boolean singleItem,
                                                       ClothingPackManifest manifest, List<String> manifestWarnings,
                                                       Set<String> reservedFiles,
                                                       ClothingArchiveSecurity.ReadBudget budget) {
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

            List<String> warnings = new ArrayList<>(manifestWarnings);
            warnings.addAll(langFiles.warnings());
            Map<String, ClothingTranslation> translations =
                    buildTranslations(obj, id, langFiles, singleItem, warnings);

            String textureFile = readOptionalString(obj, "texture", "texture.png", id);
            String iconFile = readOptionalString(obj, "icon", "icon.png", id);
            requireSafeRelativeName(textureFile, "texture", id, archiveName);
            requireSafeRelativeName(iconFile, "icon", id, archiveName);

            Found textureFound = readSibling(zip, folder, textureFile, id, archiveName,
                    ClothingLoadException.Reason.MISSING_TEXTURE, budget);
            byte[] textureBytes = textureFound.bytes();
            if (kind == ClothingKind.CAPE) {
                validateCapeTextureSize(textureBytes, id, archiveName);
            } else {
                validateTextureSize(textureBytes, id, archiveName);
            }

            Found iconFound = readSibling(zip, folder, iconFile, id, archiveName,
                    ClothingLoadException.Reason.MISSING_ICON, budget);
            byte[] iconBytes = iconFound.bytes();
            validateIsPng(iconBytes, id, archiveName);

            Map<Integer, ClothingStyle> styles = readStyles(zip, obj, id, archiveName, kind, folder,
                    textureFile, textureFound.entryName(), iconFile, iconFound.entryName(),
                    reservedFiles, budget, warnings);

            return ParsedEntry.ok(new ClothingDefinition(
                    id, name, description, slot, kind, armor, toughness, priority, textureBytes, iconBytes,
                    archiveName, manifest.formatVersion(), translations, styles), warnings);
        } catch (ClothingLoadException e) {
            return ParsedEntry.failed(e);
        } catch (RuntimeException e) {
            return ParsedEntry.failed(new ClothingLoadException(
                    ClothingLoadException.Reason.INVALID_JSON,
                    "Некорректное поле у предмета в архиве '" + archiveName + "': " + e.getMessage(), e));
        }
    }

    /**
     * Поля вроде {@code texture}/{@code icon} в item.json должны быть простыми относительными
     * именами файлов, а не путём, выходящим за пределы архива - см. {@link
     * ClothingArchiveSecurity#isEntryNameSafe}. Это не защита от чего-то реально
     * эксплуатируемого (запись всё равно ищется только внутри самого zip, наружу он выйти не
     * может), а гигиена входных данных: заведомо мусорное значение отклоняется сразу, а не
     * молча даёт "файл не найден" где-то глубже.
     */
    private static void requireSafeRelativeName(String fileName, String field, String id, String archiveName)
            throws ClothingLoadException {
        if (!ClothingArchiveSecurity.isEntryNameSafe(fileName)) {
            throw new ClothingLoadException(ClothingLoadException.Reason.UNSAFE_ENTRY_NAME,
                    "Поле '" + field + "' у предмета '" + id + "' в архиве '" + archiveName
                            + "' должно быть простым относительным именем файла, а не '" + fileName + "'");
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
    private static LangFiles readLangFiles(ZipFile zip, String folder, int itemCount,
                                            ClothingArchiveSecurity.ReadBudget budget, String archiveName) {
        String prefix = (folder.isEmpty() ? "" : folder + "/") + LANG_FOLDER + "/";
        List<ZipEntry> candidates = zip.stream()
                .filter(e -> !e.isDirectory())
                .filter(e -> ClothingArchiveSecurity.isEntryNameSafe(e.getName()))
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
                        new String(ClothingArchiveSecurity.readBounded(zip, entry, budget, archiveName), StandardCharsets.UTF_8));
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

    /** Прочитанный файл вместе с полным именем записи, под которым он найден в архиве. */
    private record Found(String entryName, byte[] bytes) {
    }

    private static Found readSibling(ZipFile zip, String folder, String fileName, String id, String archiveName,
                                       ClothingLoadException.Reason missingReason,
                                       ClothingArchiveSecurity.ReadBudget budget) throws ClothingLoadException {
        String fullPath = folder.isEmpty() ? fileName : folder + "/" + fileName;
        ZipEntry entry = zip.getEntry(fullPath);
        if (entry == null || !ClothingArchiveSecurity.isEntryNameSafe(entry.getName())) {
            // Дополнительно пробуем найти файл с этим именем где угодно в архиве -
            // упрощает жизнь авторам, кладущим texture.png/icon.png не строго рядом с item.json.
            entry = zip.stream()
                    .filter(e -> !e.isDirectory())
                    .filter(e -> ClothingArchiveSecurity.isEntryNameSafe(e.getName()))
                    .filter(e -> baseName(e.getName()).equalsIgnoreCase(fileName))
                    .findFirst().orElse(null);
        }
        if (entry == null) {
            throw new ClothingLoadException(missingReason,
                    "Не найден файл '" + fileName + "' для предмета '" + id + "' в архиве '" + archiveName + "'");
        }
        try {
            return new Found(entry.getName(),
                    ClothingArchiveSecurity.readBounded(zip, entry, budget, archiveName));
        } catch (IOException e) {
            throw new ClothingLoadException(ClothingLoadException.Reason.IO_ERROR,
                    "Не удалось прочитать '" + fileName + "' для предмета '" + id + "' в архиве '" + archiveName
                            + "': " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Стили (варианты текстуры)
    // ---------------------------------------------------------------------------------------

    /**
     * Полные (с папкой, в нижнем регистре) имена файлов, которые {@code item.json} прямо отдаёт под
     * текстуры/иконки/стили - своих предметов или соседних предметов той же папки. Автопоиск стилей
     * ({@code texture1.png}) их не трогает: иначе в папке с несколькими предметами, где один
     * использует {@code a.png}, а другой {@code a2.png}, второй предмет по ошибке стал бы
     * «стилем 2» первого.
     */
    private static Set<String> collectReservedFileNames(List<JsonObject> objects, String folder) {
        Set<String> reserved = new HashSet<>();
        for (JsonObject o : objects) {
            reserved.add(reservedPath(folder, stringOrNull(o, "texture"), "texture.png"));
            reserved.add(reservedPath(folder, stringOrNull(o, "icon"), "icon.png"));
            JsonElement styles = o.get("styles");
            if (styles != null && styles.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : styles.getAsJsonObject().entrySet()) {
                    JsonElement v = e.getValue();
                    if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                        reserved.add(reservedPath(folder, v.getAsString(), null));
                    } else if (v.isJsonObject()) {
                        reserved.add(reservedPath(folder, stringOrNull(v.getAsJsonObject(), "texture"), null));
                        reserved.add(reservedPath(folder, stringOrNull(v.getAsJsonObject(), "icon"), null));
                    }
                }
            }
        }
        reserved.remove(null);
        return reserved;
    }

    private static String reservedPath(String folder, String name, String defaultName) {
        String chosen = name != null && !name.isBlank() ? name.trim() : defaultName;
        if (chosen == null) {
            return null;
        }
        return (folder.isEmpty() ? chosen : folder + "/" + chosen).toLowerCase(Locale.ROOT);
    }

    private static String stringOrNull(JsonObject obj, String field) {
        JsonElement el = obj.get(field);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() ? el.getAsString() : null;
    }

    /**
     * Дополнительные стили предмета (стиль 0 - сам предмет, его тут нет). Два способа, не смешиваются:
     * <ol>
     *   <li><b>Явный</b> - поле {@code styles} в {@code item.json}: объект, где ключ - номер стиля, а
     *       значение - имя файла текстуры либо {@code {"texture": "...", "icon": "..."}}. Имена
     *       произвольные, ищутся так же, как {@code texture}/{@code icon}. Если поле есть, автопоиск
     *       для этого предмета не выполняется.</li>
     *   <li><b>По имени</b> (по умолчанию) - к имени основной текстуры дописывается номер: у
     *       {@code texture.png} стили - {@code texture1.png}, {@code texture2.png}; у предмета с
     *       {@code "texture": "officer.png"} - {@code officer1.png}, {@code officer2.png}. Так же и для
     *       иконки: {@code icon1.png} (или {@code <имя иконки>1.png}) - своя иконка стиля 1. Файлы
     *       ищутся в той же папке, где лежит основная текстура.</li>
     * </ol>
     * Любая проблема с файлом стиля (битый PNG, не тот размер, нечитаемый файл) только пропускает
     * этот стиль и даёт предупреждение - предмет с остальными стилями загружается.
     */
    private static Map<Integer, ClothingStyle> readStyles(ZipFile zip, JsonObject obj, String id,
                                                            String archiveName, ClothingKind kind, String folder,
                                                            String textureFile, String textureEntryName,
                                                            String iconFile, String iconEntryName,
                                                            Set<String> reservedFiles,
                                                            ClothingArchiveSecurity.ReadBudget budget,
                                                            List<String> warnings) {
        Map<Integer, ClothingStyle> result = new TreeMap<>();
        JsonElement explicit = obj.get("styles");
        if (explicit != null && !explicit.isJsonNull()) {
            readExplicitStyles(zip, explicit, id, archiveName, kind, folder, budget, warnings, result);
        } else {
            readNamedStyles(zip, id, archiveName, kind, textureFile, textureEntryName, iconFile, iconEntryName,
                    reservedFiles, budget, warnings, result);
        }
        return result;
    }

    private static void readExplicitStyles(ZipFile zip, JsonElement explicit, String id, String archiveName,
                                             ClothingKind kind, String folder,
                                             ClothingArchiveSecurity.ReadBudget budget, List<String> warnings,
                                             Map<Integer, ClothingStyle> result) {
        if (!explicit.isJsonObject()) {
            warnings.add("предмет '" + id + "': поле 'styles' должно быть объектом вида "
                    + "{\"1\": \"red.png\"} - стили проигнорированы");
            return;
        }
        for (Map.Entry<String, JsonElement> entry : explicit.getAsJsonObject().entrySet()) {
            int number = parseStyleNumber(entry.getKey());
            if (number < 1) {
                warnings.add("предмет '" + id + "': ключ стиля '" + entry.getKey() + "' пропущен - нужен номер от 1 до "
                        + ClothingDefinition.MAX_STYLE_NUMBER + " (стиль 0 - это основная текстура предмета)");
                continue;
            }
            JsonElement value = entry.getValue();
            String textureName;
            String iconName = null;
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                textureName = value.getAsString().trim();
            } else if (value.isJsonObject()) {
                textureName = stringOrNull(value.getAsJsonObject(), "texture");
                iconName = stringOrNull(value.getAsJsonObject(), "icon");
            } else {
                warnings.add("предмет '" + id + "': стиль " + number
                        + " пропущен - значение должно быть именем файла или объектом {\"texture\": \"...\"}");
                continue;
            }
            if (textureName == null || textureName.isBlank()) {
                warnings.add("предмет '" + id + "': стиль " + number + " пропущен - не указана текстура");
                continue;
            }
            try {
                requireSafeRelativeName(textureName.trim(), "styles." + number + ".texture", id, archiveName);
                Found texture = readSibling(zip, folder, textureName.trim(), id, archiveName,
                        ClothingLoadException.Reason.MISSING_TEXTURE, budget);
                validateStyleTexture(kind, texture.bytes(), id, archiveName);

                byte[] icon = null;
                if (iconName != null && !iconName.isBlank()) {
                    try {
                        requireSafeRelativeName(iconName.trim(), "styles." + number + ".icon", id, archiveName);
                        Found iconFound = readSibling(zip, folder, iconName.trim(), id, archiveName,
                                ClothingLoadException.Reason.MISSING_ICON, budget);
                        validateIsPng(iconFound.bytes(), id, archiveName);
                        icon = iconFound.bytes();
                    } catch (ClothingLoadException iconError) {
                        warnings.add("предмет '" + id + "': иконка стиля " + number + " пропущена ("
                                + iconError.getMessage() + ") - останется иконка по умолчанию");
                    }
                }
                result.put(number, new ClothingStyle(number, texture.bytes(), icon));
            } catch (ClothingLoadException e) {
                warnings.add("предмет '" + id + "': стиль " + number + " (файл '" + textureName.trim()
                        + "') пропущен: " + e.getMessage());
            }
        }
    }

    private static void readNamedStyles(ZipFile zip, String id, String archiveName, ClothingKind kind,
                                          String textureFile, String textureEntryName,
                                          String iconFile, String iconEntryName, Set<String> reservedFiles,
                                          ClothingArchiveSecurity.ReadBudget budget, List<String> warnings,
                                          Map<Integer, ClothingStyle> result) {
        Map<Integer, ZipEntry> textureEntries = findNumberedEntries(zip, textureFile, textureEntryName, reservedFiles);
        for (Map.Entry<Integer, ZipEntry> found : textureEntries.entrySet()) {
            int number = found.getKey();
            ZipEntry entry = found.getValue();
            if (number < 1) {
                warnings.add("предмет '" + id + "': файл '" + entry.getName() + "' пропущен - стиль 0 это основная "
                        + "текстура предмета, нумерация стилей начинается с 1");
                continue;
            }
            if (result.containsKey(number)) {
                warnings.add("предмет '" + id + "': файл '" + entry.getName() + "' пропущен - стиль " + number
                        + " уже задан другим файлом");
                continue;
            }
            try {
                byte[] bytes = ClothingArchiveSecurity.readBounded(zip, entry, budget, archiveName);
                validateStyleTexture(kind, bytes, id, archiveName);
                result.put(number, new ClothingStyle(number, bytes, null));
            } catch (IOException e) {
                warnings.add("предмет '" + id + "': стиль " + number + " ('" + entry.getName()
                        + "') пропущен: не удалось прочитать файл (" + e.getMessage() + ")");
            } catch (ClothingLoadException e) {
                warnings.add("предмет '" + id + "': стиль " + number + " (файл '" + entry.getName()
                        + "') пропущен: " + e.getMessage());
            }
        }
        if (result.isEmpty()) {
            return;
        }

        // Иконки стилей - только для тех номеров, у которых есть текстура.
        for (Map.Entry<Integer, ZipEntry> found : findNumberedEntries(zip, iconFile, iconEntryName, reservedFiles).entrySet()) {
            ClothingStyle style = result.get(found.getKey());
            if (style == null || style.hasOwnIcon()) {
                continue;
            }
            try {
                byte[] bytes = ClothingArchiveSecurity.readBounded(zip, found.getValue(), budget, archiveName);
                validateIsPng(bytes, id, archiveName);
                result.put(style.number(), new ClothingStyle(style.number(), style.textureBytes(), bytes));
            } catch (IOException | ClothingLoadException e) {
                warnings.add("предмет '" + id + "': иконка стиля " + style.number() + " ('"
                        + found.getValue().getName() + "') пропущена (" + e.getMessage()
                        + ") - останется иконка по умолчанию");
            }
        }
    }

    /**
     * Файлы вида {@code <имя><число><расширение>} рядом с базовым файлом: для {@code texture.png} -
     * {@code texture1.png}, {@code texture12.png}. Число - одна или две цифры. Результат отсортирован по числу.
     * Файлы из {@code reservedFiles} (явно названные в item.json) не считаются стилями.
     */
    private static Map<Integer, ZipEntry> findNumberedEntries(ZipFile zip, String baseFileName,
                                                                String baseEntryName, Set<String> reservedFiles) {
        String fileName = baseName(baseFileName);
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        Pattern pattern = Pattern.compile("^" + Pattern.quote(stem) + "(\\d{1,2})" + Pattern.quote(extension) + "$",
                Pattern.CASE_INSENSITIVE);
        String directory = folderOf(baseEntryName);

        Map<Integer, ZipEntry> byNumber = new TreeMap<>();
        zip.stream()
                .filter(e -> !e.isDirectory())
                .filter(e -> ClothingArchiveSecurity.isEntryNameSafe(e.getName()))
                .filter(e -> folderOf(e.getName()).equalsIgnoreCase(directory))
                .filter(e -> !reservedFiles.contains(e.getName().toLowerCase(Locale.ROOT)))
                .sorted(java.util.Comparator.comparing(ZipEntry::getName))
                .forEach(e -> {
                    Matcher matcher = pattern.matcher(baseName(e.getName()));
                    if (matcher.matches()) {
                        // putIfAbsent: texture1.png и texture01.png - один и тот же номер, берём первый по имени.
                        byNumber.putIfAbsent(Integer.parseInt(matcher.group(1)), (ZipEntry) e);
                    }
                });
        return byNumber;
    }

    private static int parseStyleNumber(String key) {
        String trimmed = key.trim();
        if (!trimmed.matches("\\d{1,2}")) {
            return -1;
        }
        int number = Integer.parseInt(trimmed);
        return number >= 1 && number <= ClothingDefinition.MAX_STYLE_NUMBER ? number : -1;
    }

    private static void validateStyleTexture(ClothingKind kind, byte[] pngBytes, String id, String archiveName)
            throws ClothingLoadException {
        if (kind == ClothingKind.CAPE) {
            validateCapeTextureSize(pngBytes, id, archiveName);
        } else {
            validateTextureSize(pngBytes, id, archiveName);
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
