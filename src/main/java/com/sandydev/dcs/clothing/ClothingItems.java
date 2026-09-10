package com.sandydev.dcs.clothing;

import com.sandydev.dcs.SandysDynamicClothing;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Превращает всё, что нашёл {@link ClothingLoader}, в настоящие зарегистрированные {@link Item}.
 * <p>
 * Почему это безопасно, хотя предметы заранее не описаны Java-классами (см. требование ТЗ
 * "не использовать unsafe hacks"): {@link DeferredRegister} и так проектировался для случая,
 * когда список регистрируемых объектов не известен на этапе компиляции. Единственное жёсткое
 * условие - вызовы {@code register(name, supplier)} должны произойти ДО того, как модовая шина
 * доставит {@code RegisterEvent} (это происходит один раз, сразу после того как отработают
 * конструкторы всех модов). Поэтому архитектура мода делает discovery/loading-этап
 * ({@link ClothingLoader#loadAll}) синхронным и максимально ранним - прямо в конструкторе мода,
 * до вызова {@link #registerAll(Logger)} - а сам {@link #registerAll(Logger)} тоже вызывается
 * из конструктора мода, до {@code ITEMS.register(modEventBus)}. Никакой рефлексии по "заморожен-
 * ным" реестрам, никакого форс-инжекта - обычный, штатный порядок работы DeferredRegister.
 */
public final class ClothingItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(SandysDynamicClothing.MODID);

    private static final Map<String, DeferredItem<ClothingItem>> HOLDERS = new LinkedHashMap<>();

    private ClothingItems() {
    }

    /**
     * Создаёт по одному {@link ClothingItem} на каждое определение, накопленное в
     * {@link ClothingRegistry}. Должен быть вызван после {@link ClothingLoader#loadAll}
     * и до того как {@code ITEMS.register(modEventBus)} получит {@code RegisterEvent}.
     */
    public static void registerAll(Logger logger) {
        for (ClothingDefinition definition : ClothingRegistry.get().getAllDefinitions()) {
            DeferredItem<ClothingItem> holder = ITEMS.register(definition.id(),
                    () -> new ClothingItem(definition, new Item.Properties().stacksTo(1)));
            HOLDERS.put(definition.id(), holder);
        }
        logger.info("[DynamicClothingSystem] Поставлено на регистрацию {} предметов одежды", HOLDERS.size());
    }

    /**
     * Связывает id -> реальный {@link Item} в {@link ClothingRegistry}. Вызывать только после
     * того, как реестр предметов гарантированно заполнен (например, из {@code FMLCommonSetupEvent}) -
     * до этого момента {@link DeferredItem#get()} может бросить исключение.
     */
    public static void linkAll() {
        HOLDERS.forEach((id, holder) -> ClothingRegistry.get().linkItem(id, holder.get()));
    }
}
