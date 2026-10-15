package com.sandydev.dcs.compat;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Включает миксины Straw Statues, только если этот мод установлен. Лежит вне пакета миксинов,
 * иначе Mixin не даст загрузить сам плагин.
 */
public final class StrawStatuesMixinPlugin implements IMixinConfigPlugin {

    private Boolean strawStatuesPresent;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (strawStatuesPresent == null) {
            try {
                strawStatuesPresent = LoadingModList.get().getModFileById("strawstatues") != null;
            } catch (Throwable t) {
                strawStatuesPresent = false;
            }
        }
        return strawStatuesPresent;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
