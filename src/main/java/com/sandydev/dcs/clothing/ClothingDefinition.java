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
 * @param name          базовое отображаемое имя из {@code item.json} - показывается для всех локалей,
 *                      для которых нет перевода в {@code lang/<locale>.json}.
 * @param description   базовый список строк описания из {@code item.json} (каждая строка -
 *                      отдельная строка тултипа); так же служит запасным вариантом для локалей
 *                      без перевода описания.
 * @param slot          id Curios-слота, в который надевается предмет (см. {@link ClothingSlots}).
 * @param kind          формат предмета - {@link ClothingKind#SKIN} (по умолчанию) или
 *                      {@link ClothingKind#CAPE}. Определяет и требуемый размер
 *                      {@code textureBytes}, и то, как именно предмет рендерится на игроке.
 * @param armor         количество единиц ванильной брони, добавляемых через Curios.
 * @param toughness     ванильная armor toughness, добавляемая через Curios.
 * @param priority      внутренний порядок наложения одежды типа {@link ClothingKind#SKIN}. В
 *                      item.json вместо него обычно используется поле layer: under, shirt,
 *                      base, vest, jacket, coat или outer. Числовой priority - расширенный
 *                      режим для точной ручной настройки. Для {@link ClothingKind#CAPE}
 *                      не используется (всегда 0) - плащ не участвует в наложении на скин.
 * @param textureBytes  сырые байты {@code texture.png} с альфа-каналом: 64x64 в раскладке
 *                      скина для {@link ClothingKind#SKIN}, 64x32 в ванильной раскладке
 *                      cape/elytra для {@link ClothingKind#CAPE}.
 * @param iconBytes     сырые байты {@code icon.png}, используется как иконка предмета.
 * @param sourceArchive имя архива-источника (для логов и диагностики ошибок).
 * @param translations  необязательные переводы name/description по локалям (ключ - код локали в
 *                      нижнем регистре, например {@code ru_ru}). Источники: файлы
 *                      {@code lang/<locale>.json} рядом с {@code item.json} и устаревшее поле
 *                      {@code translations} внутри {@code item.json} (только name). Пустая карта -
 *                      нормальный случай: предмет полностью работает на базовых текстах.
 */
public record ClothingDefinition(
        String id,
        String name,
        List<String> description,
        String slot,
        ClothingKind kind,
        int armor,
        double toughness,
        int priority,
        byte[] textureBytes,
        byte[] iconBytes,
        String sourceArchive,
        java.util.Map<String, ClothingTranslation> translations
) {
}
