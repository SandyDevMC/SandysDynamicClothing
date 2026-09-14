package com.sandydev.dcs.clothing;

import java.util.List;

/**
 * Данные одного предмета одежды, полученные из {@code item.json} + {@code texture.png} + {@code icon.png}
 * внутри пользовательского архива в {@code .minecraft/clothes/}.
 * <p>
 * Это чистые данные без ссылки на Item/ItemStack - именно то, что описывает архив.
 * Регистрация в игровые реестры выполняется отдельно в {@link ClothingItems}, привязка
 * id -> Item хранится в {@link ClothingRegistry}.
 *
 * @param id            уникальный id предмета (используется как путь регистрации Item) -
 *                      строчные латинские буквы, цифры, {@code _}, {@code -}, {@code .}.
 * @param name          отображаемое имя, используется как fallback-перевод для всех локалей,
 *                      если лунг-файл не переопределяет ключ предмета.
 * @param description   список строк описания (каждая строка - отдельная строка тултипа).
 * @param slot          id Curios-слота, в который надевается предмет (см. {@link ClothingSlots}).
 * @param armor         количество единиц ванильной брони, добавляемых через Curios.
 * @param toughness     ванильная armor toughness, добавляемая через Curios.
 * @param priority      внутренний порядок наложения. В item.json вместо него обычно используется
 *                      поле layer: under, shirt, base, vest, jacket, coat или outer. Числовой
 *                      priority - расширенный режим для точной ручной настройки.
 * @param textureBytes  сырые байты {@code texture.png} (64x64, с альфа-каналом).
 * @param iconBytes     сырые байты {@code icon.png}, используется как иконка предмета.
 * @param sourceArchive имя архива-источника (для логов и диагностики ошибок).
 * @param translations  переопределения name по локалям, {@code {"ru_ru": "..."}}.
 */
public record ClothingDefinition(
        String id,
        String name,
        List<String> description,
        String slot,
        int armor,
        double toughness,
        int priority,
        byte[] textureBytes,
        byte[] iconBytes,
        String sourceArchive,
        java.util.Map<String, String> translations
) {
}
