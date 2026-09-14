package com.sandydev.dcs.clothing;

import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Единая точка правды о загруженной одежде: что нашли на диске и какому Item это в итоге
 * соответствует после регистрации.
 * <p>
 * Заполняется в два прохода: сначала {@link ClothingLoader} кладёт сюда разобранные
 * {@link ClothingDefinition} (до RegisterEvent), затем {@link ClothingItems} связывает
 * каждый id с уже созданным {@link Item} через {@link #linkItem(String, Item)}.
 * <p>
 * Это синглтон уровня мода, а не датапак-регистр Minecraft - одежда читается из
 * {@code .minecraft/clothes/} и не зависит от датапаков/миров.
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
