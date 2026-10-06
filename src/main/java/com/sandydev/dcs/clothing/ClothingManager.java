package com.sandydev.dcs.clothing;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import com.sandydev.dcs.Config;
import com.sandydev.dcs.SandysDynamicClothing;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Что из одежды сейчас надето на существе - тонкая обёртка над Curios и ванильными слотами брони.
 * Общий код для сервера и клиента, поэтому клиентских классов (NativeImage,
 * Minecraft.getInstance() и т.п.) здесь быть не должно.
 */
public final class ClothingManager {

    private ClothingManager() {
    }

    /**
     * Все надетые в Curios-слоты предметы одежды типа {@link ClothingKind#SKIN} данного
     * существа, отсортированные по возрастанию внутреннего priority (обычно полученного из
     * ClothingLayer) - именно в этом порядке их нужно накладывать на скин (нижний слой первым,
     * верхний - последним). Плащи ({@link ClothingKind#CAPE}) сюда не попадают - см.
     * {@link #getEquippedCape}.
     */
    public static List<ClothingDefinition> getEquippedClothingSortedByPriority(LivingEntity entity) {
        List<ClothingDefinition> result = new ArrayList<>();
        for (ClothingDefinition definition : getAllEquippedDefinitions(entity)) {
            if (definition.kind() == ClothingKind.SKIN) {
                result.add(definition);
            }
        }
        result.sort((a, b) -> Integer.compare(a.priority(), b.priority()));
        return result;
    }

    /**
     * Надетый плащ ({@link ClothingKind#CAPE}) данного существа, если есть.
     * <p>
     * В отличие от {@link #getEquippedClothingSortedByPriority}, плащ не участвует в композиции
     * скина: его текстура целиком подменяет cape-текстуру игрока (см. {@code
     * AbstractClientPlayerMixin} и {@code ClothingCapeTextureCache}), а рисует её уже штатный
     * ванильный {@code CapeLayer}. Обычно плащ надевается в стандартный "back"-слот Curios (см.
     * {@link ClothingSlots#BACK}), но метод не привязан к конкретному слоту - как и остальная
     * одежда в этом моде, он просто ищет предмет нужного {@link ClothingKind} среди всего
     * надетого в Curios. Если плащей надето несколько сразу (в разных слотах), берётся первый
     * найденный.
     */
    public static Optional<ClothingDefinition> getEquippedCape(LivingEntity entity) {
        for (ClothingDefinition definition : getAllEquippedDefinitions(entity)) {
            if (definition.kind() == ClothingKind.CAPE) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }

    /**
     * Все распознанные определения одежды, надетые на существо - и в Curios-слоты, и в ванильные
     * слоты брони (см. {@link ClothingVanillaSlots}) - без сортировки и без фильтрации по типу.
     * <p>
     * Один и тот же предмет, надетый сразу в двух местах (например, в Curios-слоте и в ванильном),
     * возвращается один раз: второй экземпляр не добавил бы к внешнему виду ничего нового.
     */
    private static List<ClothingDefinition> getAllEquippedDefinitions(LivingEntity entity) {
        Map<String, ClothingDefinition> result = new LinkedHashMap<>();

        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
            for (ICurioStacksHandler stacksHandler : handler.getCurios().values()) {
                IDynamicStackHandler stacks = stacksHandler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    ClothingRegistry.get().getDefinitionForItem(stack.getItem()).ifPresentOrElse(
                            definition -> result.putIfAbsent(definition.id(), definition),
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

        // Ванильные слоты брони. Берём предмет только если он лежит именно в "своём" слоте по
        // таблице сопоставления: одежду, оказавшуюся в чужой ячейке (например, через команду
        // /item replace), не рисуем, чтобы правило "руки и аксессуары - только свои слоты" не обходилось.
        for (EquipmentSlot slot : ClothingVanillaSlots.ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ClothingRegistry.get().getDefinitionForItem(stack.getItem()).ifPresent(definition -> {
                if (ClothingVanillaSlots.forClothingSlot(definition.slot()) == slot) {
                    result.putIfAbsent(definition.id(), definition);
                }
            });
        }

        return new ArrayList<>(result.values());
    }

}
