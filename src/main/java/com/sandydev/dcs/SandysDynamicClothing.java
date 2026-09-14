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
 * Порядок вызовов в конструкторе важен - одежда должна быть найдена и поставлена
 * в очередь регистрации до того, как модовая шина доставит {@code RegisterEvent}.
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
        // Сканируем .minecraft/clothes/*.zip и заполняем ClothingRegistry определениями одежды.
        // Это должно случиться раньше, чем ClothingItems поставит предметы в очередь регистрации.
        ClothingLoader.loadAll(LOGGER);

        // По одному DeferredItem на каждое найденное определение. Сама регистрация в реестр
        // игры произойдёт позже - в момент, когда шина доставит RegisterEvent.
        ClothingItems.registerAll(LOGGER);
        ClothingItems.ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // Curios использует наш валидатор при загрузке данных слотов, поэтому регистрируем
        // его сразу, не дожидаясь commonSetup - иначе можно попасть на первый datapack reload
        // раньше, чем предикат вообще появится.
        ClothingSlotValidator.register();

        // Без AddPackFindersEvent сгенерированные item-model JSON никогда не попадут в ModelManager.
        modEventBus.addListener(com.sandydev.dcs.clothing.client.ClothingIconPackWriter::addPackFinders);

        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.addListener(com.sandydev.dcs.clothing.ClothingInteractionHandler::onRightClickItem);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // К этому моменту реестр предметов Minecraft уже заполнен, можно безопасно
        // связать id одежды с реальным Item.
        event.enqueueWork(() -> {
            ClothingItems.linkAll();
            LOGGER.info("[DynamicClothingSystem] commonSetup завершён, загружено предметов одежды: {}",
                    ClothingRegistry.get().size());
        });
    }
}
