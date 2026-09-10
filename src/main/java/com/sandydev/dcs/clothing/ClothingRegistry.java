package com.sandydev.dcs.clothing;

import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Единая точка правды о загруженной одежде во время выполнения игры.
 * <p>
 * Заполняется в два этапа:
 * <ol>
 *     <li>{@link ClothingLoader} на этапе конструктора мода кладёт сюда все успешно
 *     разобранные {@link ClothingDefinition} (до срабатывания {@code RegisterEvent});</li>
 *     <li>{@link ClothingItems} во время регистрации {@code Item} связывает каждый id
 *     с созданным {@link Item} через {@link #linkItem(String, Item)}.</li>
 * </ol>
 * Сам объект - синглтон уровня мода (не датапак-регистр Minecraft), так как одежда
 * не является игровым датапак-контентом - это runtime-контент, читаемый из
 * {@code .minecraft/clothes/}, и одинаков в течение всего запуска клиента/сервера.
 */
public final class ClothingRegistry {

    private static final ClothingRegistry INSTANCE = new ClothingRegistry();

    private final Map<String, ClothingDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, Item> items = new ConcurrentHashMap<>();
    private boolean frozen = false;

    private ClothingRegistry() {
    }

    public static ClothingRegistry get() {
        return INSTANCE;
    }

    /** Вызывается только {@link ClothingLoader} во время сканирования архивов. */
    void register(ClothingDefinition definition) {
        if (frozen) {
            throw new IllegalStateException("ClothingRegistry уже заморожен, регистрация после старта запрещена");
        }
        definitions.put(definition.id(), definition);
    }

    /** Запрещает дальнейшую регистрацию определений - вызывается после окончания сканирования архивов. */
    void freeze() {
        frozen = true;
    }

    /** Вызывается {@link ClothingItems} сразу после создания Item для данного id. */
    public void linkItem(String id, Item item) {
        items.put(id, item);
    }

    public Optional<ClothingDefinition> getDefinition(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Optional<ClothingDefinition> getDefinitionForItem(Item item) {
        if (item instanceof ClothingItem clothingItem) {
            return Optional.of(clothingItem.getDefinition());
        }
        return Optional.empty();
    }

    public Optional<Item> getItem(String id) {
        return Optional.ofNullable(items.get(id));
    }

    public Collection<ClothingDefinition> getAllDefinitions() {
        return definitions.values();
    }

    public int size() {
        return definitions.size();
    }
}
