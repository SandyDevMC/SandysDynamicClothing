package com.sandydev.dcs;

import com.sandydev.dcs.clothing.ClothingItems;
import com.sandydev.dcs.clothing.ClothingLoader;
import com.sandydev.dcs.clothing.ClothingRegistry;
import com.sandydev.dcs.clothing.ClothingSlotValidator;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Точка входа мода.
 * <p>
 * Порядок вызовов в конструкторе критичен (см. комментарии внутри) - это ровно
 * "discovery/loading-этап перед регистрацией", описанный в архитектуре {@link ClothingItems}.
 */
@Mod(SandysDynamicClothing.MODID)
public class SandysDynamicClothing {
    public static final String MODID = "dynamic_clothing_system";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CLOTHING_TAB =
            CREATIVE_MODE_TABS.register("clothing_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dynamic_clothing_system"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ClothingRegistry.get().getAllDefinitions().stream().findFirst()
                            .flatMap(def -> ClothingRegistry.get().getItem(def.id()))
                            .map(Item::getDefaultInstance)
                            .orElse(Items.LEATHER_CHESTPLATE.getDefaultInstance()))
                    .displayItems((parameters, output) -> ClothingRegistry.get().getAllDefinitions()
                            .forEach(def -> ClothingRegistry.get().getItem(def.id()).ifPresent(output::accept)))
                    .build());

    public SandysDynamicClothing(IEventBus modEventBus, ModContainer modContainer) {
        // 1) Discovery/loading-этап: синхронно сканируем .minecraft/clothes/*.zip и заполняем
        //    ClothingRegistry определениями (без создания Item). Должно случиться максимально
        //    рано - до того, как ClothingItems поставит предметы на регистрацию.
        ClothingLoader.loadAll(LOGGER);

        // 2) Ставим по одному Item на каждое найденное определение в очередь DeferredRegister -
        //    это ещё НЕ регистрация в реестр игры, а просто заполнение очереди на неё.
        ClothingItems.registerAll(LOGGER);

        // 3) Подписываем DeferredRegister'ы на модовую шину - фактическая регистрация в реестр
        //    Minecraft произойдёт позже, когда шина доставит RegisterEvent.
        ClothingItems.ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // Регистрируем валидатор заранее: Curios использует validators при загрузке данных слотов,
        // поэтому предикат должен быть зарегистрирован до первого server/datapack reload.
        ClothingSlotValidator.register();

        // Динамический клиентский resource pack должен быть добавлен через AddPackFindersEvent,
        // иначе сгенерированные item-model JSON никогда не попадут в ModelManager.
        modEventBus.addListener(com.sandydev.dcs.clothing.client.ClothingIconPackWriter::addPackFinders);

        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.addListener(com.sandydev.dcs.clothing.ClothingInteractionHandler::onRightClickItem);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Реестр предметов Minecraft к этому моменту гарантированно заполнен - можно безопасно
        // связать id одежды -> реальный Item.
        event.enqueueWork(() -> {
            ClothingItems.linkAll();
            LOGGER.info("[DynamicClothingSystem] commonSetup завершён, загружено предметов одежды: {}",
                    ClothingRegistry.get().size());
        });
    }
}
