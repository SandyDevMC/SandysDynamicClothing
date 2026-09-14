package com.sandydev.dcs.clothing;

import com.sandydev.dcs.Config;
import com.sandydev.dcs.SandysDynamicClothing;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

/**
 * Надевание одежды обычным ПКМ по миру, без открытия инвентаря Curios.
 * Предмет уходит в свой слот, а то, что там уже лежало, возвращается игроку в инвентарь
 * (или падает под ноги, если инвентарь полон).
 */
public final class ClothingInteractionHandler {

    private ClothingInteractionHandler() {
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (held.isEmpty() || !(held.getItem() instanceof ClothingItem clothingItem)) {
            return;
        }

        ClothingDefinition definition = clothingItem.getDefinition();
        CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                handler.getStacksHandler(definition.slot()).ifPresent(stacksHandler -> {
                    var stacks = stacksHandler.getStacks();
                    if (stacks.getSlots() <= 0) {
                        debug("[DCS][EQUIP] ПКМ: слот '{}' существует, но не содержит ячеек", definition.slot());
                        return;
                    }

                    SlotContext context = new SlotContext(definition.slot(), player, 0, false,
                            stacksHandler.getRenders().size() > 0 && stacksHandler.getRenders().get(0));
                    if (!CuriosApi.isStackValid(context, held)) {
                        debug("[DCS][EQUIP] ПКМ: предмет '{}' отклонён Curios для слота '{}'",
                                definition.id(), definition.slot());
                        return;
                    }

                    ItemStack replacement = held.copyWithCount(1);
                    ItemStack old = stacks.getStackInSlot(0).copy();

                    held.shrink(1);
                    stacks.setStackInSlot(0, replacement);

                    if (!old.isEmpty()) {
                        if (!player.addItem(old)) {
                            player.drop(old, false);
                        }
                    }

                    debug("[DCS][EQUIP] ПКМ: '{}' надет в слот '{}', заменён='{}'",
                            definition.id(), definition.slot(), old.isEmpty() ? "нет" : old.getHoverName().getString());
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                }));
    }

    private static void debug(String message, Object... args) {
        if (Config.DEBUG_LOGGING.get()) {
            SandysDynamicClothing.LOGGER.debug(message, args);
        }
    }
}
