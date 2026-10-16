package com.sandydev.dcs;

import com.sandydev.dcs.clothing.ClothingComponents;
import com.sandydev.dcs.clothing.client.ClothingIconPackWriter;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = SandysDynamicClothing.MODID, dist = Dist.CLIENT)
public class SandysDynamicClothingClient {
    public SandysDynamicClothingClient(ModContainer container) {
        // Client-only: register the generated resource pack before client resources are loaded.
        container.getEventBus().addListener(ClothingIconPackWriter::addPackFinders);
        container.getEventBus().addListener(SandysDynamicClothingClient::onClientSetup);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /**
     * Свойство предмета {@code dynamic_clothing_system:style} для оверрайдов моделей: отдаёт номер
     * выбранного стиля, по нему сгенерированная модель предмета (см. {@link ClothingIconPackWriter})
     * выбирает иконку стиля. Свойство общее для всех предметов; на предметах без стилей и без
     * оверрайдов в модели оно просто не используется.
     */
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.registerGeneric(
                ResourceLocation.fromNamespaceAndPath(SandysDynamicClothing.MODID, "style"),
                (stack, level, entity, seed) -> ClothingComponents.getStyle(stack)));
    }

}
