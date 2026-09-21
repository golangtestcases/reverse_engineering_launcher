package com.mcskill.xray;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Контейнер мода (обычный @Mod, без Instance-поля - FML не injects final fields).
 * Рендер модифицируют миксины (com.mcskill.xray.mixin.*), регистрируемые
 * XrayPlugin через IEarlyMixinLoader (GTNHMixins).
 * С первого клиентского тика инициализируется и опрашивается клавиша X
 * (меню тумблеров) - локальный KeyBinding, без пакетов на сервер.
 */
@Mod(modid = XrayMod.MODID, name = "McSkill X-Ray", version = XrayMod.VERSION)
public final class XrayMod {

    public static final String MODID = "mcskillxray";
    public static final String VERSION = "1.0.0";

    public XrayMod() {
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Keybinds.initOnce();
        Keybinds.poll();
        RenderState.onClientTick();
    }
}