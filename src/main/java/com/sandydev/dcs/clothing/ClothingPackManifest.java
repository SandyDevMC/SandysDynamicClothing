package com.sandydev.dcs.clothing;

/**
 * Содержимое необязательного {@code manifest.json} в корне архива одежды.
 * <p>
 * Сам файл может отсутствовать - тогда используется {@link #DEFAULT} ({@link
 * ClothingPackFormat#LATEST}). Пример файла:
 * <pre>{@code
 * {
 *   "format_version": "1.1"
 * }
 * }</pre>
 *
 * @param formatVersion версия формата пака - см. {@link ClothingPackFormat}.
 */
public record ClothingPackManifest(ClothingPackFormat formatVersion) {

    /** Манифест "по умолчанию" - используется, когда в архиве нет manifest.json. */
    public static final ClothingPackManifest DEFAULT = new ClothingPackManifest(ClothingPackFormat.LATEST);
}
