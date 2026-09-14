package com.sandydev.dcs.clothing;

import java.util.List;

/**
 * Слоты Curios, которые мод регистрирует "из коробки".
 * <p>
 * Это не жёсткое ограничение - {@link ClothingSlotValidator} работает с любой строкой слота
 * из {@code item.json}, даже отсутствующей в этом списке. Константы здесь только для
 * читаемости кода; фактический список слотов живёт в
 * {@code data/dynamic_clothing_system/curios/slots/*.json} и
 * {@code data/dynamic_clothing_system/curios/entities/players.json} - при добавлении нового
 * слота их нужно править руками, этот класс сам ничего не генерирует.
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
