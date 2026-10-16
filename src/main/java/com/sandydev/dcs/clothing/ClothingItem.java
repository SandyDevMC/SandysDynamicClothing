package com.sandydev.dcs.clothing;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
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
 * Не является ArmorItem. Надевается в свой Curios-слот из {@code item.json}, а также - по
 * таблице {@link ClothingVanillaSlots} - в соответствующий ванильный слот брони
 * (см. {@link #getEquipmentSlot}).
 * <p>
 * Защита реализуется стандартными ванильными атрибутами {@code ARMOR} и {@code ARMOR_TOUGHNESS}:
 * в Curios-слоте их применяет Curios (см. {@link #getAttributeModifiers}), в ванильном слоте -
 * компонент {@code ATTRIBUTE_MODIFIERS}, который выставляет {@link ClothingItems}.
 * <p>
 * Совместимость со слотом дополнительно проверяется через {@link #canEquip} в самом предмете,
 * а datapack-валидатор остаётся резервным механизмом на стороне Curios.
 * <p>
 * Если у предмета есть дополнительные стили ({@link ClothingDefinition#hasStyles()}), их можно
 * переключать правым кликом по предмету в любом инвентаре - см. {@link #overrideOtherStackedOnMe}.
 * Выбранный стиль лежит в компоненте {@link ClothingComponents#STYLE}.
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

    /**
     * Ванильный слот брони, в который можно надеть этот предмет, или {@code null}, если такого
     * нет (тогда NeoForge откатывается на штатную логику, и предмет в слоты брони не ложится).
     * <p>
     * Этот хук читают и инвентарь игрока (можно положить предмет в ячейку брони, shift+клик
     * отправляет его туда же), и остальной ванильный код, спрашивающий
     * {@code LivingEntity#getEquipmentSlotForItem}.
     */
    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return ClothingVanillaSlots.forClothingSlot(definition.slot());
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

    /**
     * Правый клик по предмету в инвентаре переключает его стиль на следующий по кругу
     * (0 -> 1 -> ... -> последний -> 0).
     * <p>
     * Хук вызывается ванильным {@code AbstractContainerMenu#doClick} для предмета, лежащего в ячейке,
     * когда по нему кликнули (ПКМ) с пустым курсором - в обычном инвентаре, в окне Curios, в сундуках и
     * т.д. Он выполняется и на клиенте (для мгновенного отклика), и на сервере (авторитетно), а
     * результат уходит остальным клиентам тем же путём, что и любое изменение предмета, - отдельных
     * пакетов не нужно. Если курсор не пуст, клик обрабатывается как обычно (взять/поменять местами).
     * <p>
     * Возвращает {@code true} только когда стиль реально переключён, чтобы ванильный ПКМ
     * ("взять половину стака") не сработал следом и не унёс предмет на курсор.
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action,
                                             Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !other.isEmpty() || !definition.hasStyles()) {
            return false;
        }
        if (!slot.allowModification(player)) {
            return false;
        }

        int next = definition.nextStyle(definition.resolveStyle(ClothingComponents.getStyle(stack)));
        // Записываем копию через slot.set, а не меняем стак на месте: так ячейка (в том числе Curios-слот)
        // узнаёт об изменении и разошлёт его наблюдателям.
        ItemStack updated = stack.copy();
        ClothingComponents.setStyle(updated, next);
        slot.set(updated);

        if (player.level().isClientSide()) {
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.6F, 1.1F, false);
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                 TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        for (String line : localizedDescription()) {
            tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        if (definition.hasStyles()) {
            int style = definition.resolveStyle(ClothingComponents.getStyle(stack));
            tooltip.add(Component.translatable("tooltip." + SandysDynamicClothing.MODID + ".style",
                    style, definition.maxStyle()).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip." + SandysDynamicClothing.MODID + ".style_hint")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        // Слот, броня и твёрдость брони не дублируются вручную: Curios сам добавляет
        // стандартный блок информации для экипировки и её атрибутов. Наш предмет отвечает
        // здесь только за собственное текстовое описание.
    }
}
