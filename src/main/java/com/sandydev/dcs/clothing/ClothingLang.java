package com.sandydev.dcs.clothing;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Ключи локализации предметов одежды и сборка записей для сгенерированного resource pack.
 * <p>
 * Схема такая: базовые {@code name}/{@code description} из {@code item.json} кладутся в
 * {@code en_us} - Minecraft всегда грузит {@code en_us} первым слоем, а выбранный язык
 * накладывает поверх, так что базовый текст автоматически становится запасным вариантом для
 * любой локали. Переводы из {@code lang/<locale>.json} предмета переопределяют его только в
 * своей локали и только по тем полям, которые в них реально заданы.
 * <p>
 * Описание хранится ОДНОЙ записью, строки склеены через {@code \n}. Если бы каждая строка
 * лежала под своим ключом ({@code ...description.0}, {@code ...description.1}), перевод с
 * меньшим числом строк "просвечивал" бы оставшимися строками базового текста.
 * <p>
 * Класс намеренно не зависит от Minecraft, чтобы логику можно было проверять обычным
 * юнит-тестом.
 */
public final class ClothingLang {

    /** Локаль, в которую кладётся базовый текст (Minecraft всегда грузит её первой). */
    public static final String BASE_LOCALE = "en_us";

    private ClothingLang() {
    }

    public static String nameKey(String modId, String id) {
        return "item." + modId + "." + id;
    }

    public static String descriptionKey(String modId, String id) {
        return nameKey(modId, id) + ".description";
    }

    /** Склеивает строки описания в одну запись lang-файла. */
    public static String joinDescription(java.util.List<String> lines) {
        return String.join("\n", lines);
    }

    /** Обратная операция для {@link #joinDescription}; хвостовые пустые строки сохраняются. */
    public static java.util.List<String> splitDescription(String text) {
        return java.util.List.of(text.split("\n", -1));
    }

    /**
     * Строит содержимое lang-файлов: локаль -&gt; (ключ -&gt; значение).
     * Локали идут в порядке появления, {@link #BASE_LOCALE} всегда присутствует, если есть
     * хотя бы один предмет.
     */
    public static Map<String, Map<String, String>> buildEntries(String modId,
                                                                Collection<ClothingDefinition> definitions) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (ClothingDefinition definition : definitions) {
            String nameKey = nameKey(modId, definition.id());
            String descriptionKey = descriptionKey(modId, definition.id());

            Map<String, String> base = result.computeIfAbsent(BASE_LOCALE, l -> new LinkedHashMap<>());
            base.put(nameKey, definition.name());
            if (!definition.description().isEmpty()) {
                base.put(descriptionKey, joinDescription(definition.description()));
            }

            for (Map.Entry<String, ClothingTranslation> entry : definition.translations().entrySet()) {
                ClothingTranslation translation = entry.getValue();
                if (translation.isEmpty()) {
                    continue;
                }
                Map<String, String> entries = result.computeIfAbsent(
                        entry.getKey().toLowerCase(Locale.ROOT), l -> new LinkedHashMap<>());
                if (translation.name() != null) {
                    entries.put(nameKey, translation.name());
                }
                if (!translation.description().isEmpty()) {
                    entries.put(descriptionKey, joinDescription(translation.description()));
                }
            }
        }
        return result;
    }
}
