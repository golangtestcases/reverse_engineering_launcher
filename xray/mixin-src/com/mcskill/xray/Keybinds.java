package com.mcskill.xray;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

/**
 * Клавиша X через ВАНИЛЬНЫЙ KeyBinding (ClientRegistry.registerKeyBinding) -
 * состояние клавиши остаётся только на клиенте и НИКАКИЕ пакеты на сервер
 * не уходят (в отличие от gtnhlib SyncedKeybind, который синхронизирует
 * клавишу с сервером и кикался на Thermos-серверах: "fatal error ... connection
 * terminated").
 *
 * KeyBinding (рантайм MCP/деобф):
 *   ctor          = (Ljava/lang/String;ILjava/lang/String;)V
 *   isPressed     = func_151468_f ()Z   - потребляет одно нажатие (edge)
 */
public final class Keybinds {

    private static KeyBinding binding = null;
    private static long lastFire = 0L;

    private Keybinds() {
    }

    /** Вызывается один раз с первого клиентского тика (см. XrayMod). */
    public static synchronized void initOnce() {
        if (binding != null) {
            return;
        }
        try {
            binding = new KeyBinding("mcskill_xray_menu", 45, "McSkill X-Ray");
            // рефлексия: сигнатура ClientRegistry в forge.jar имеет notch-имя (Lbal;),
            // которое javac не резолвит; в рантайме после деобфа это
            // net.minecraft.client.settings.KeyBinding
            Class<?> clz = Class.forName("cpw.mods.fml.client.registry.ClientRegistry");
            clz.getMethod("registerKeyBinding", KeyBinding.class).invoke(null, binding);
        } catch (Throwable t) {
            binding = null; // не удалось зарегистрировать - меню просто не откроется
        }
    }

    /** Вызывается каждый клиентский тик. */
    public static void poll() {
        KeyBinding kb = binding;
        if (kb == null || !kb.func_151468_f()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastFire < 300L) {
            return;
        }
        lastFire = now;

        Minecraft mc = Minecraft.func_71410_x();
        if (mc == null) {
            return;
        }
        if (State.menuOpen) {
            State.menuOpen = false;
            mc.func_147108_a(null);
        } else {
            State.menuOpen = true;
            mc.func_147108_a(new XrayMenu());
        }
    }
}