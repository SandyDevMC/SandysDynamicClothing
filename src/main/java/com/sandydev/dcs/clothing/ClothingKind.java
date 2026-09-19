package com.sandydev.dcs.clothing;

import java.util.Locale;

/**
 * Формат предмета одежды - определяет, как его текстура применяется к рендеру игрока.
 * Указывается полем {@code type} в {@code item.json} (по умолчанию {@code skin}, так что
 * все существующие паки остаются рабочими без изменений).
 * <p>
 * {@link #SKIN} - "обычная" одежда: 64x64 текстура в раскладке скина игрока, накладывается
 * на скин попиксельно через {@link com.sandydev.dcs.clothing.client.ClothingTextureComposer},
 * с учётом второго (overlay) слоя скина - см. этот класс. Порядок наложения на скин
 * определяется полем {@code layer}/{@code priority}.
 * <p>
 * {@link #CAPE} - плащ: собственная 64x32 текстура в стандартной ванильной раскладке
 * cape/elytra-полотна, никак не связанная со скином. Композиция пикселей не нужна - вся
 * текстура целиком подменяет cape-текстуру игрока (см. {@code AbstractClientPlayerMixin} и
 * {@link com.sandydev.dcs.clothing.client.ClothingCapeTextureCache}), а рисует её уже штатный
 * ванильный {@code CapeLayer}. Поле {@code layer}/{@code priority} у плаща игнорируется - он
 * не участвует в наложении на скин и не сортируется относительно другой одежды.
 */
public enum ClothingKind {
    SKIN,
    CAPE;

    public static ClothingKind parse(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "skin", "clothing", "одежда", "скин" -> SKIN;
            case "cape", "плащ", "накидка" -> CAPE;
            default -> throw new IllegalArgumentException(
                    "Неизвестный type '" + value + "'. Допустимые значения: skin, cape");
        };
    }
}
