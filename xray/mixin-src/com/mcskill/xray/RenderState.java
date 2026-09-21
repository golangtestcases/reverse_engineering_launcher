package com.mcskill.xray;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Мгновенное применение тумблера x-ray/fullbright.
 *
 * В 1.7.10 рендер каждого чанка кэшируется в GL-списки; пока чанк не
 * "грязный", старые списки рисуются дальше. Два механизма:
 *
 * 1) MixinForceChunk: в окне pendingFrames помечает каждый обрабатываемый
 *    рендерным циклом чанк грязным (q=true) через WorldRenderer.e().
 * 2) "Пинок дальности": меняем настройку RENDER_DISTANCE на +1 на пару
 *    кадров (и возвращаем) - Minecraft детектит смену дальности и
 *    перестраивает чанки целиком (проверено пользователем: слайдер
 *    дальности = мгновенная перерисовка).
 *
 * RENDER_DISTANCE getter/setter (SRG): func_74296_a (LGameSettings$Options;)F
 *                                     func_74304_a (LGameSettings$Options;F)V
 */
public final class RenderState {

    private static int pendingFrames = 0;

    private static boolean nudgeActive = false;
    private static int nudgeTimer = 0;
    private static float nudgeRestore = 0f;

    /** Запрос перерисовки после смены режима. Повторные клики продлевают пинок. */
    public static void requestReReRender() {
        pendingFrames = 8;
        if (!nudgeActive) {
            nudgeDistance();
        } else {
            // пинок уже идёт — продлеваем, чтобы перестроение успело завершиться
            nudgeTimer = 10;
        }
    }

    /** Доступно миксину MixinForceChunk. */
    public static int pendingFrames() {
        return pendingFrames;
    }

    /** Вызывается каждый клиентский тик (см. XrayMod). */
    public static void onClientTick() {
        if (pendingFrames > 0) {
            --pendingFrames;
        }
        if (nudgeActive) {
            --nudgeTimer;
            if (nudgeTimer <= 0) {
                nudgeActive = false;
                setRenderDistance(nudgeRestore);
                System.out.println("[mcskillxray] distance restored to " + nudgeRestore);
            }
        }
    }

    // ---------- пинок дальности ----------

    private static Class<?> optionsClass() {
        try {
            return Class.forName("net.minecraft.client.settings.GameSettings$Options");
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object gameSettings() {
        try {
            Minecraft mc = Minecraft.func_71410_x();
            if (mc == null) {
                return null;
            }
            Class<?> gsClass = Class.forName("net.minecraft.client.settings.GameSettings");
            for (Field field : Minecraft.class.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    Object value = field.get(mc);
                    if (value != null && gsClass.isInstance(value)) {
                        return value;
                    }
                } catch (Throwable ignored) {
                    // пропускаем
                }
            }
        } catch (Throwable t) {
            System.out.println("[mcskillxray] gameSettings find error: " + t);
        }
        return null;
    }

    private static void nudgeDistance() {
        try {
            Class<?> optClass = optionsClass();
            Object gs = gameSettings();
            if (optClass == null || gs == null) {
                return;
            }
            Object[] constants = optClass.getEnumConstants();
            if (constants == null || constants.length <= 5) {
                return;
            }
            Object renderDistance = constants[5]; // RENDER_DISTANCE (bbm.f)
            Method getter = gs.getClass().getMethod("func_74296_a", optClass);
            Method setter = gs.getClass().getMethod("func_74304_a", optClass, float.class);
            float cur = (Float) getter.invoke(gs, renderDistance);
            // пинаем В СВОБОДНУЮ сторону: если у потолка - уменьшаем, иначе увеличиваем
            float target = (cur >= 15.9f) ? (cur - 1.0f) : (cur + 1.0f);
            if (target == cur) {
                return;
            }
            nudgeRestore = cur;
            setter.invoke(gs, renderDistance, target);
            nudgeActive = true;
            nudgeTimer = 10; // ~0.7 сек после этого вернуть значение
            System.out.println("[mcskillxray] distance nudge " + cur + " -> " + target);
        } catch (Throwable t) {
            System.out.println("[mcskillxray] distance nudge error: " + t);
        }
    }

    private static void setRenderDistance(float value) {
        try {
            Class<?> optClass = optionsClass();
            Object gs = gameSettings();
            if (optClass == null || gs == null) {
                return;
            }
            Object[] constants = optClass.getEnumConstants();
            if (constants == null || constants.length <= 5) {
                return;
            }
            Object renderDistance = constants[5];
            Method setter = gs.getClass().getMethod("func_74304_a", optClass, float.class);
            setter.invoke(gs, renderDistance, value);
        } catch (Throwable t) {
            System.out.println("[mcskillxray] distance set error: " + t);
        }
    }
}