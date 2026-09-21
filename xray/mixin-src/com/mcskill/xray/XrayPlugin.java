package com.mcskill.xray;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coremod-плагин MOD: регистрирует миксины через GTNHMixins (IEarlyMixinLoader).
 * НЕ регистрирует НИ ОДНОГО ASM-трансформера (пустой список) - это обходит
 * фатальный баг RFB "Class bytes are null" при регистрации трансформеров.
 */
@IFMLLoadingPlugin.Name("McSkillXray")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({"com.mcskill.xray."})
public final class XrayPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // no-op
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public String getMixinConfig() {
        return "mixins.mcskillxray.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return Arrays.asList("MixinRenderBlocks", "MixinBlock", "MixinBrightness", "MixinForceChunk");
    }
}