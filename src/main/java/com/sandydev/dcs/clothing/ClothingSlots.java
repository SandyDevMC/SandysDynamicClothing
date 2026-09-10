package com.sandydev.dcs.clothing;

import java.util.List;

/**
 * Набор Curios-слотов "из коробки" для одежды.
 * <p>
 * Это НЕ жёстко закодированный список в смысле ограничения - {@link ClothingSlotValidator}
 * работает с произвольной строкой слота из {@code item.json}. Этот класс лишь:
 * <ul>
 *     <li>служит источником правды при генерации datapack-файлов слотов
 *     (см. {@code data/dynamic_clothing_system/curios/slots/*.json} и
 *     {@code data/dynamic_clothing_system/curios/entities/players.json});</li>
 *     <li>даёт человекочитаемые константы для остального кода мода.</li>
 * </ul>
 * Чтобы добавить новый слот одежды:
 * <ol>
 *     <li>добавить {@code data/dynamic_clothing_system/curios/slots/<slot>.json};</li>
 *     <li>добавить его id в список слотов, назначенных игроку в
 *     {@code data/dynamic_clothing_system/curios/entities/players.json};</li>
 *     <li>(опционально) добавить константу сюда для читаемости.</li>
 * </ol>
 * Изменять Java-код системы валидации/композиции при этом не требуется.
 */
public final class ClothingSlots {

    public static final String HEAD = "head";
    public static final String FACE = "face";
    public static final String NECK = "neck";
    public static final String TORSO = "torso";
    public static final String JACKET = "jacket";
    public static final String LEGS = "legs";
    public static final String FEET = "feet";
    public static final String HANDS = "hands";
    public static final String ACCESSORY = "accessory";

    /** Слоты, регистрируемые модом по умолчанию, в порядке отображения в GUI Curios. */
    public static final List<String> DEFAULT_SLOTS = List.of(
            HEAD, FACE, NECK, TORSO, JACKET, LEGS, FEET, HANDS, ACCESSORY
    );

    private ClothingSlots() {
    }
}
