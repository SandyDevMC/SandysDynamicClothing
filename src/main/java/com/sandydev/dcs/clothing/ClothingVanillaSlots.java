package com.sandydev.dcs.clothing;

import com.sandydev.dcs.SandysDynamicClothing;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;
import java.util.Map;

/**
 * Сопоставление слотов одежды ({@code slot} в {@code item.json}) с ванильными слотами брони.
 * <p>
 * Помимо собственного Curios-слота, предмет одежды можно надеть и в ванильный слот брони -
 * по этой таблице:
 * <pre>
 *   head, neck, face      -> шлем      ({@link EquipmentSlot#HEAD})
 *   body, jacket, back    -> нагрудник ({@link EquipmentSlot#CHEST})
 *   legs                  -> поножи    ({@link EquipmentSlot#LEGS})
 *   feet                  -> ботинки   ({@link EquipmentSlot#FEET})
 *   hands, accessory      -> только свои Curios-слоты, ванильного аналога нет
 * </pre>
 * Любой другой (нестандартный) slot из {@code item.json} тоже остаётся только Curios-слотом.
 * <p>
 * Таблица общая для сервера и клиента, поэтому клиентских классов здесь быть не должно.
 */
public final class ClothingVanillaSlots {

    /** Ванильные слоты брони, из которых мод читает надетую одежду (для рендера и т.п.). */
    public static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private static final Map<String, EquipmentSlot> MAPPING = Map.of(
            ClothingSlots.HEAD, EquipmentSlot.HEAD,
            ClothingSlots.NECK, EquipmentSlot.HEAD,
            ClothingSlots.FACE, EquipmentSlot.HEAD,
            ClothingSlots.BODY, EquipmentSlot.CHEST,
            ClothingSlots.JACKET, EquipmentSlot.CHEST,
            ClothingSlots.BACK, EquipmentSlot.CHEST,
            ClothingSlots.LEGS, EquipmentSlot.LEGS,
            ClothingSlots.FEET, EquipmentSlot.FEET
    );

    private ClothingVanillaSlots() {
    }

    /**
     * @param clothingSlot значение {@code slot} из {@code item.json}
     * @return ванильный слот брони, в который тоже можно надеть такой предмет, или {@code null},
     * если ванильного аналога нет ({@code hands}, {@code accessory}, нестандартные слоты)
     */
    public static EquipmentSlot forClothingSlot(String clothingSlot) {
        return clothingSlot == null ? null : MAPPING.get(clothingSlot);
    }

    /**
     * Группа слотов для ванильных атрибутов предмета (броня/твёрдость, пока предмет лежит
     * в ванильном слоте), или {@code null}, если ванильного слота нет.
     */
    public static EquipmentSlotGroup groupForClothingSlot(String clothingSlot) {
        EquipmentSlot slot = forClothingSlot(clothingSlot);
        if (slot == null) {
            return null;
        }
        return switch (slot) {
            case HEAD -> EquipmentSlotGroup.HEAD;
            case CHEST -> EquipmentSlotGroup.CHEST;
            case LEGS -> EquipmentSlotGroup.LEGS;
            case FEET -> EquipmentSlotGroup.FEET;
            default -> null;
        };
    }

    /**
     * Ванильные атрибуты предмета (броня и твёрдость брони), действующие, пока одежда лежит
     * в ванильном слоте брони. В Curios-слоте они не работают - там броню выдаёт
     * {@link ClothingItem#getAttributeModifiers}, так что двойного учёта нет.
     * <p>
     * id модификаторов намеренно отличаются от Curios-шных ({@code clothing_armor/<id>}), чтобы
     * два экземпляра одного предмета - один в ванильном слоте, другой в Curios - не конфликтовали.
     *
     * @return атрибуты для {@code DataComponents.ATTRIBUTE_MODIFIERS}, или {@code null}, если
     * у предмета нет ванильного слота либо нечего добавлять (броня и твёрдость равны нулю)
     */
    public static ItemAttributeModifiers buildWornModifiers(ClothingDefinition definition) {
        EquipmentSlotGroup group = groupForClothingSlot(definition.slot());
        if (group == null || (definition.armor() <= 0 && definition.toughness() <= 0.0)) {
            return null;
        }

        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        if (definition.armor() > 0) {
            builder.add(Attributes.ARMOR, new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath(SandysDynamicClothing.MODID,
                            "clothing_armor_worn/" + definition.id()),
                    definition.armor(),
                    AttributeModifier.Operation.ADD_VALUE), group);
        }
        if (definition.toughness() > 0.0) {
            builder.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath(SandysDynamicClothing.MODID,
                            "clothing_toughness_worn/" + definition.id()),
                    definition.toughness(),
                    AttributeModifier.Operation.ADD_VALUE), group);
        }
        return builder.build();
    }
}
