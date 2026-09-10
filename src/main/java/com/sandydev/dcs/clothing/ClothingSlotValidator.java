package com.sandydev.dcs.clothing;

import com.sandydev.dcs.SandysDynamicClothing;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Проверка совместимости ItemStack <-> Curios slot для одежды.
 * <p>
 * Curios по умолчанию проверяет совместимость через vanilla item tags
 * ({@code curios:<slot>}), но одежда регистрируется динамически из архивов, и генерировать
 * datapack-теги на лету неудобно и хрупко. Вместо этого регистрируем один кастомный
 * validator-предикат (см. {@code CuriosApi#registerPredicate}) и ссылаемся на него из поля
 * {@code "validators"} каждого файла {@code data/dynamic_clothing_system/curios/slots/*.json}.
 * <p>
 * Предикат просто сверяет {@link ClothingDefinition#slot()} предмета с идентификатором слота,
 * в который его пытаются положить - никаких item tags не требуется вообще.
 */
public final class ClothingSlotValidator {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SandysDynamicClothing.MODID, "clothing_slot_match");

    private ClothingSlotValidator() {
    }

    /** Вызывать один раз, до старта загрузки мира (например, из commonSetup). */
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
