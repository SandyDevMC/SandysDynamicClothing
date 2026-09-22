package com.sandydev.dcs.clothing;

/**
 * Версия формата пака одежды - указывается необязательным полем {@code format_version} в
 * необязательном {@code manifest.json} в корне архива (см. {@link ClothingPackManifest}).
 * <p>
 * Если у архива нет {@code manifest.json}, либо в нём нет поля {@code format_version} - версия
 * автоматически становится {@link #LATEST}. Явно указывать {@link #V1_0} имеет смысл только
 * для старых паков, сохранённых до перехода на формат {@code .cloth} - на случай, если в
 * будущем поведение версий разойдётся сильнее, чем сегодня.
 * <p>
 * Сегодня обе версии разбираются одним и тем же {@link ClothingArchiveParser} совершенно
 * одинаково - расхождения в схеме item.json между 1.0 и 1.1 пока не появилось. Версия несёт
 * чисто декларативный смысл (какой формат пак использует) и сохраняется в {@link
 * ClothingDefinition#packFormat()} для диагностики/аддонов - место, куда в будущем можно
 * добавить реальную версия-зависимую логику, не ломая паки, уже объявившие себя как 1.0.
 */
public enum ClothingPackFormat {
    /** Формат до перехода на {@code .cloth} - обычные {@code .zip}-архивы без манифеста. */
    V1_0("1.0"),
    /** Текущий формат: архив {@code .cloth}, необязательный {@code manifest.json}. */
    V1_1("1.1");

    /** Версия по умолчанию - используется, если манифеста нет или в нём не указана версия. */
    public static final ClothingPackFormat LATEST = V1_1;

    private final String id;

    ClothingPackFormat(String id) {
        this.id = id;
    }

    /** Строка версии как она пишется в {@code manifest.json}, например {@code "1.1"}. */
    public String id() {
        return id;
    }

    /**
     * @throws IllegalArgumentException если строка не соответствует ни одной известной версии.
     */
    public static ClothingPackFormat parse(String value) {
        String trimmed = value.trim();
        for (ClothingPackFormat format : values()) {
            if (format.id.equals(trimmed)) {
                return format;
            }
        }
        throw new IllegalArgumentException("неизвестная версия формата '" + trimmed
                + "'. Допустимые значения: " + knownIds());
    }

    private static String knownIds() {
        StringBuilder sb = new StringBuilder();
        for (ClothingPackFormat format : values()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(format.id);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return id;
    }
}
