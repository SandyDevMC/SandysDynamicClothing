package com.sandydev.dcs.clothing;

import java.util.List;

/**
 * Перевод отображаемых текстов предмета на одну локаль (например {@code ru_ru}).
 * <p>
 * Берётся из файла {@code lang/<locale>.json}, лежащего рядом с {@code item.json} предмета.
 * Оба поля необязательны: если {@link #name()} равно {@code null}, а {@link #description()}
 * пуст, для этой локали в игре показываются базовые {@code name}/{@code description} из
 * {@code item.json} - поэтому предмет полностью работает и вовсе без файлов локализации.
 *
 * @param name        переведённое имя или {@code null}, если файл локали его не задаёт.
 * @param description переведённые строки описания (каждая строка - отдельная строка тултипа);
 *                    пустой список означает "описание не переведено, использовать базовое".
 */
public record ClothingTranslation(String name, List<String> description) {

    public ClothingTranslation {
        description = description == null ? List.of() : List.copyOf(description);
    }

    /** {@code true}, если перевод ничего не переопределяет. */
    public boolean isEmpty() {
        return name == null && description.isEmpty();
    }
}
