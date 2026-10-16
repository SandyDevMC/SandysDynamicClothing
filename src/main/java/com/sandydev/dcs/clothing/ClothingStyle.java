package com.sandydev.dcs.clothing;

/**
 * Один дополнительный вариант внешнего вида предмета одежды (стиль 1, 2, ...).
 * <p>
 * Стиль 0 - это сам предмет: его текстура и иконка лежат прямо в {@link ClothingDefinition}
 * ({@code texture.png}/{@code icon.png} или файлы, заданные полями {@code texture}/{@code icon}
 * в {@code item.json}). Здесь хранятся только стили, начиная с первого.
 *
 * @param number       номер стиля, {@code 1..}{@link ClothingDefinition#MAX_STYLE_NUMBER}. Это ровно та
 *                     цифра, что стоит в имени файла ({@code texture3.png} - стиль 3), поэтому
 *                     пропуски в нумерации допустимы и номера не сдвигаются.
 * @param textureBytes сырые байты PNG текстуры стиля, тот же размер и раскладка, что и у основной
 *                     текстуры предмета (64x64 для скина, 64x32 для плаща).
 * @param iconBytes    сырые байты PNG иконки стиля либо {@code null}, если у стиля нет своей иконки
 *                     (тогда в инвентаре остаётся иконка предмета по умолчанию).
 */
public record ClothingStyle(int number, byte[] textureBytes, byte[] iconBytes) {

    public boolean hasOwnIcon() {
        return iconBytes != null;
    }
}
