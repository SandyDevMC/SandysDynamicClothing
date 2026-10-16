package com.sandydev.dcs.clothing;

import com.sandydev.dcs.SandysDynamicClothing;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Компоненты данных предметов одежды.
 * <p>
 * Выбранный стиль хранится прямо в {@link ItemStack}, а не где-то рядом: так он сам ездит вместе с
 * предметом - сохраняется в мире, переживает перенос между инвентарями и, главное, уже входит
 * в то, что ванильная сеть и Curios отправляют другим клиентам вместе с надетым предметом. Отдельных
 * пакетов для синхронизации стиля не нужно.
 */
public final class ClothingComponents {

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, SandysDynamicClothing.MODID);

    /**
     * Номер выбранного стиля. Отсутствие компонента означает стиль 0 (основная текстура предмета),
     * поэтому у ни разу не переключавшегося предмета в стаке нет лишних данных и он равен
     * свежесозданному.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STYLE =
            COMPONENTS.registerComponentType("style", builder -> builder
                    .persistent(ExtraCodecs.NON_NEGATIVE_INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    private ClothingComponents() {
    }

    /** Сырой номер стиля из стака, 0 если не задан. Может указывать на несуществующий стиль - см. {@link ClothingDefinition#resolveStyle}. */
    public static int getStyle(ItemStack stack) {
        return stack.getOrDefault(STYLE.get(), 0);
    }

    /** Записывает стиль в стак; для стиля 0 компонент убирается, а не хранится нулём. */
    public static void setStyle(ItemStack stack, int style) {
        if (style <= 0) {
            stack.remove(STYLE.get());
        } else {
            stack.set(STYLE.get(), style);
        }
    }
}
