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
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Точка входа мода.
 * <p>
 * Сканирование {@code clothes/} и постановка предметов в очередь DeferredRegister происходит не
 * прямо в конструкторе, а в обработчике {@link #onConstructMod}, подписанном на {@code
 * FMLConstructModEvent} - это единственный момент, который одновременно (а) наступает уже
 * после того, как отработали конструкторы ВСЕХ модов, и (б) наступает раньше, чем шина
 * доставит {@code RegisterEvent}. Первое важно для хуков аддонов (см. {@code
 * com.sandydev.dcs.api.event} и ADDON_API.md) - если сканировать и слать события прямо из
 * своего конструктора, при некоторых порядках загрузки модов слушатель аддона просто не успел
 * бы подписаться на {@code NeoForge.EVENT_BUS}. Второе - потому что {@link ClothingItems}
 * читает {@link ClothingRegistry} для постановки предметов в очередь регистрации, а это должно
 * произойти строго до RegisterEvent.
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
        // По одному DeferredItem на каждое найденное определение (само наполнение реестра
        // произойдёт позже, в onConstructMod) - сама подписка DeferredRegister на шину не
        // зависит от того, есть ли уже в нём записи, поэтому её можно сделать прямо сейчас.
        ClothingItems.ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // Curios использует наш валидатор при загрузке данных слотов, поэтому регистрируем
        // его сразу, не дожидаясь commonSetup - иначе можно попасть на первый datapack reload
        // раньше, чем предикат вообще появится.
        ClothingSlotValidator.register();

        modEventBus.addListener(this::onConstructMod);
        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.addListener(com.sandydev.dcs.clothing.ClothingInteractionHandler::onRightClickItem);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void onConstructMod(FMLConstructModEvent event) {
        // Сканируем .minecraft/clothes/ (*.cloth, *.zip) и заполняем ClothingRegistry
        // определениями одежды - см. класс-javadoc про то, почему именно здесь, а не в
        // конструкторе. Это должно случиться раньше, чем ClothingItems поставит предметы
        // в очередь регистрации.
        ClothingLoader.loadAll(LOGGER);
        ClothingItems.registerAll(LOGGER);
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
