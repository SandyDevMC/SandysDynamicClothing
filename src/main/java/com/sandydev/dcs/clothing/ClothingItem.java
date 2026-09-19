package com.sandydev.dcs.clothing;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.item.TooltipFlag;
import com.sandydev.dcs.SandysDynamicClothing;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * Обычный {@link Item}, представляющий один предмет одежды.
 * <p>
 * Не является ArmorItem: экипировка происходит исключительно через Curios.
 * Защита реализуется стандартными ванильными атрибутами {@code ARMOR} и {@code ARMOR_TOUGHNESS},
 * которые Curios применяет к носителю надетого предмета.
 * <p>
 * Совместимость со слотом дополнительно проверяется через {@link #canEquip} в самом предмете,
 * а datapack-валидатор остаётся резервным механизмом на стороне Curios.
 */
public class ClothingItem extends Item implements ICurioItem {

    private final ClothingDefinition definition;

    public ClothingItem(ClothingDefinition definition, Properties properties) {
        super(properties);
        this.definition = definition;
    }

    public ClothingDefinition getDefinition() {
        return definition;
    }

    @Override
    public Component getName(ItemStack stack) {
        // Имя берётся из сгенерированного lang-файла (базовое - в en_us, переводы - в своих локалях).
        // Fallback на базовое имя из item.json нужен на случай, если ресурспак с языками не подключился.
        return Component.translatableWithFallback(
                ClothingLang.nameKey(SandysDynamicClothing.MODID, definition.id()), definition.name());
    }

    /**
     * Строки описания для текущего языка клиента. Если для языка (или хотя бы для en_us) нет
     * сгенерированной записи - например, на сервере, где клиентский ресурспак не загружен, -
     * возвращается базовое описание из item.json.
     */
    private List<String> localizedDescription() {
        Language language = Language.getInstance();
        String key = ClothingLang.descriptionKey(SandysDynamicClothing.MODID, definition.id());
        if (language.has(key)) {
            return ClothingLang.splitDescription(language.getOrDefault(key));
        }
        return definition.description();
    }

    /**
     * Curios дополнительно спрашивает у самого предмета, подходит ли он под слот - это
     * подстраховка на случай, если Curios пересобрал свой кэш типов слотов раньше, чем
     * успел дойти до нашего datapack-валидатора.
     */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return slotContext != null && definition.slot().equals(slotContext.identifier());
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = LinkedHashMultimap.create();

        if (definition.armor() > 0) {
            modifiers.put(Attributes.ARMOR, new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath(SandysDynamicClothing.MODID,
                            "clothing_armor/" + definition.id()),
                    definition.armor(),
                    AttributeModifier.Operation.ADD_VALUE));
        }

        if (definition.toughness() > 0.0) {
            modifiers.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath(SandysDynamicClothing.MODID,
                            "clothing_toughness/" + definition.id()),
                    definition.toughness(),
                    AttributeModifier.Operation.ADD_VALUE));
        }

        return modifiers;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                 TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        for (String line : localizedDescription()) {
            tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        MutableComponent slotLine = Component.translatable("tooltip.dynamic_clothing_system.slot",
                Component.translatable("curios.identifier." + definition.slot()))
                .withStyle(ChatFormatting.DARK_GRAY);
        tooltip.add(slotLine);
        if (definition.armor() > 0) {
            tooltip.add(Component.translatable("tooltip.dynamic_clothing_system.armor", definition.armor())
                    .withStyle(ChatFormatting.BLUE));
        }
        if (definition.toughness() > 0.0) {
            tooltip.add(Component.translatable("tooltip.dynamic_clothing_system.armor_toughness", definition.toughness())
                    .withStyle(ChatFormatting.BLUE));
        }
    }
}
