package com.sandydev.dcs.clothing;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import com.sandydev.dcs.Config;
import com.sandydev.dcs.SandysDynamicClothing;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Что из одежды сейчас надето на существе - тонкая обёртка над Curios.
 * Общий код для сервера и клиента, поэтому клиентских классов (NativeImage,
 * Minecraft.getInstance() и т.п.) здесь быть не должно.
 */
public final class ClothingManager {

    private ClothingManager() {
    }

    /**
     * Все надетые в Curios-слоты предметы одежды данного существа, отсортированные по
     * возрастанию внутреннего priority (обычно полученного из ClothingLayer) - именно в этом
     * порядке их нужно накладывать на скин (нижний слой первым, верхний - последним).
     */
    public static List<ClothingDefinition> getEquippedClothingSortedByPriority(LivingEntity entity) {
        List<ClothingDefinition> result = new ArrayList<>();
        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
            for (ICurioStacksHandler stacksHandler : handler.getCurios().values()) {
                IDynamicStackHandler stacks = stacksHandler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    ClothingRegistry.get().getDefinitionForItem(stack.getItem()).ifPresentOrElse(
                            definition -> result.add(definition),
                            () -> {
                                if (Config.DEBUG_LOGGING.get()) {
                                    SandysDynamicClothing.LOGGER.debug(
                                            "[DCS][EQUIP] предмет в Curios-слоте не зарегистрирован как одежда: {}",
                                            BuiltInRegistries.ITEM.getKey(stack.getItem()));
                                }
                            });
                }
            }
        });
        result.sort((a, b) -> Integer.compare(a.priority(), b.priority()));
        return result;
    }

}
