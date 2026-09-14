package com.sandydev.dcs.clothing;

import com.sandydev.dcs.SandysDynamicClothing;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Проверка совместимости ItemStack и Curios-слота для одежды.
 * <p>
 * По умолчанию Curios сверяет предмет со слотом через vanilla item tags ({@code curios:<slot>}),
 * но одежда регистрируется динамически из архивов, и генерировать datapack-теги на лету
 * неудобно. Вместо этого регистрируем один кастомный validator-предикат и ссылаемся на него
 * из {@code "validators"} каждого файла {@code data/dynamic_clothing_system/curios/slots/*.json}.
 * Предикат просто сверяет {@link ClothingDefinition#slot()} предмета с id слота, в который
 * его пытаются положить - никакие теги для этого не нужны.
 */
public final class ClothingSlotValidator {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SandysDynamicClothing.MODID, "clothing_slot_match");

    private ClothingSlotValidator() {
    }

    /**
     * Регистрируется прямо в конструкторе мода, до первого server/datapack reload - Curios
     * читает validators из data/curios/slots уже на этом этапе, и наш предикат должен
     * к этому моменту существовать.
     */
    public static void register() {
        CuriosApi.registerCurioPredicate(ID, ClothingSlotValidator::matches);
    }

    private static boolean matches(top.theillusivec4.curios.api.SlotResult slotResult) {
        ItemStack stack = slotResult.stack();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String targetSlot = slotResult.slotContext().identifier();
        return ClothingRegistry.get().getDefinitionForItem(stack.getItem())
                .map(def -> def.slot().equals(targetSlot))
                .orElse(false);
    }
}
