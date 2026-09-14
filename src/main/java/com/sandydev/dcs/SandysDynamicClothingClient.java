package com.sandydev.dcs;

import com.sandydev.dcs.clothing.client.ClothingIconPackWriter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = SandysDynamicClothing.MODID, dist = Dist.CLIENT)
public class SandysDynamicClothingClient {
    public SandysDynamicClothingClient(ModContainer container) {
        // Client-only: register the generated resource pack before client resources are loaded.
        container.getEventBus().addListener(ClothingIconPackWriter::addPackFinders);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

}
