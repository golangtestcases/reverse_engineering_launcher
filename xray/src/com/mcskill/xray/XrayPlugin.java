package com.mcskill.xray;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * FML CoreMod for 1.7.10 client Galaxy.
 *
 * Registered via manifest attribute "FMLCorePlugin" in META-INF/MANIFEST.MF
 * of our JAR inside mods/. The transformer applies on class load, before
 * any vanilla class gets executed.
 */
@IFMLLoadingPlugin.Name("McSkillXray")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({"com.mcskill.xray."})
@IFMLLoadingPlugin.SortingIndex(100)
public class XrayPlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        // класс, реализующий net.minecraft.launchwrapper.IClassTransformer
        return new String[]{"com.mcskill.xray.XrayTransformer"};
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
        // не требуется
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}