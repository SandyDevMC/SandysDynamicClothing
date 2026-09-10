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
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

}
