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
 * @param packFormat    версия формата пака, объявленная в {@code manifest.json} архива-источника
 *                      (или {@link ClothingPackFormat#LATEST}, если манифеста нет) - см.
 *                      {@link ClothingPackManifest}.
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
        ClothingPackFormat packFormat,
        java.util.Map<String, ClothingTranslation> translations,
        java.util.Map<Integer, ClothingStyle> styles
) {

    /** Наибольший допустимый номер стиля (в имени файла - не больше двух цифр). */
    public static final int MAX_STYLE_NUMBER = 99;

    public ClothingDefinition {
        // Отсортированная неизменяемая копия: порядок переключения стилей - по возрастанию номера.
        styles = styles == null || styles.isEmpty()
                ? java.util.Map.of()
                : java.util.Collections.unmodifiableSortedMap(new java.util.TreeMap<>(styles));
    }

    /**
     * Прежняя сигнатура без стилей - оставлена, чтобы аддоны, собирающие определения в коде
     * (см. ADDON_API.md), продолжали компилироваться без изменений.
     */
    public ClothingDefinition(String id, String name, List<String> description, String slot, ClothingKind kind,
                              int armor, double toughness, int priority, byte[] textureBytes, byte[] iconBytes,
                              String sourceArchive, ClothingPackFormat packFormat,
                              java.util.Map<String, ClothingTranslation> translations) {
        this(id, name, description, slot, kind, armor, toughness, priority, textureBytes, iconBytes,
                sourceArchive, packFormat, translations, java.util.Map.of());
    }

    /** Есть ли у предмета стили помимо стиля 0 - то есть есть ли что переключать. */
    public boolean hasStyles() {
        return !styles.isEmpty();
    }

    /** Наибольший номер стиля предмета; 0, если стилей нет. */
    public int maxStyle() {
        return styles.isEmpty() ? 0 : java.util.Collections.max(styles.keySet());
    }

    /**
     * Приводит номер стиля из стака к существующему: если такого стиля у предмета нет (пак на этом
     * клиенте старее, стиль удалили, значение выставили командой) - возвращается 0, то есть
     * основной вид, а не ошибка.
     */
    public int resolveStyle(int requested) {
        return requested > 0 && styles.containsKey(requested) ? requested : 0;
    }

    /** Текстура указанного стиля; для 0 и несуществующих номеров - основная текстура предмета. */
    public byte[] textureBytesForStyle(int style) {
        ClothingStyle entry = style > 0 ? styles.get(style) : null;
        return entry != null ? entry.textureBytes() : textureBytes;
    }

    /** Иконка указанного стиля; если у стиля нет своей иконки - иконка предмета по умолчанию. */
    public byte[] iconBytesForStyle(int style) {
        ClothingStyle entry = style > 0 ? styles.get(style) : null;
        return entry != null && entry.hasOwnIcon() ? entry.iconBytes() : iconBytes;
    }

    /** Следующий стиль по кругу: 0 -> наименьший номер -> ... -> наибольший -> снова 0. */
    public int nextStyle(int current) {
        for (int number : styles.keySet()) {
            if (number > current) {
                return number;
            }
        }
        return 0;
    }

    /** Есть ли хотя бы у одного стиля собственная иконка (нужно ли генерировать модели-оверрайды). */
    public boolean hasStyleIcons() {
        return styles.values().stream().anyMatch(ClothingStyle::hasOwnIcon);
    }
}
