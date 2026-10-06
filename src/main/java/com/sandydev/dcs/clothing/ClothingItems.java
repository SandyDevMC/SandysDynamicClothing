package com.sandydev.dcs.clothing;

import com.sandydev.dcs.SandysDynamicClothing;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Превращает то, что нашёл {@link ClothingLoader}, в настоящие зарегистрированные {@link Item}.
 * <p>
 * Список предметов заранее не известен на этапе компиляции - именно для такого случая
 * и существует {@link DeferredRegister}. Единственное жёсткое требование - все вызовы
 * {@code register(name, supplier)} должны произойти до того, как модовая шина доставит
 * {@code RegisterEvent}. Поэтому {@link ClothingLoader#loadAll} и {@link #registerAll(Logger)}
 * вызываются синхронно из конструктора мода, до {@code ITEMS.register(modEventBus)}.
 */
public final class ClothingItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(SandysDynamicClothing.MODID);

    private static final Map<String, DeferredItem<ClothingItem>> HOLDERS = new LinkedHashMap<>();

    private ClothingItems() {
    }

    /**
     * Создаёт по одному {@link ClothingItem} на каждое определение из {@link ClothingRegistry}.
     * Вызывать после {@link ClothingLoader#loadAll} и до того, как {@code ITEMS} получит
     * {@code RegisterEvent}.
     */
    public static void registerAll(Logger logger) {
        for (ClothingDefinition definition : ClothingRegistry.get().getAllDefinitions()) {
            DeferredItem<ClothingItem> holder = ITEMS.register(definition.id(),
                    () -> new ClothingItem(definition, createProperties(definition)));
            HOLDERS.put(definition.id(), holder);
        }
        logger.info("[DynamicClothingSystem] Поставлено на регистрацию {} предметов одежды", HOLDERS.size());
    }

    private static Item.Properties createProperties(ClothingDefinition definition) {
        Item.Properties properties = new Item.Properties().stacksTo(1);
        // Броня для случая, когда одежда лежит в ванильном слоте (а не в Curios).
        ItemAttributeModifiers worn = ClothingVanillaSlots.buildWornModifiers(definition);
        if (worn != null) {
            properties.component(DataComponents.ATTRIBUTE_MODIFIERS, worn);
        }
        return properties;
    }

    /**
     * Связывает id с реальным {@link Item} в {@link ClothingRegistry}. Вызывать только когда
     * реестр предметов уже гарантированно заполнен (например, из {@code FMLCommonSetupEvent}) -
     * до этого момента {@link DeferredItem#get()} бросит исключение.
     */
    public static void linkAll() {
        HOLDERS.forEach((id, holder) -> ClothingRegistry.get().linkItem(id, holder.get()));
    }
}
