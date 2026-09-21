package org.lwjglx.input;

/**
 * Стаб для компиляции. Рантайм: настоящий org.lwjglx.input.Mouse из
 * lwjgl3ify (совместимый с LWJGL2 API). Используем только колесо:
 * getDWheel() возвращает накопленную дельту колеса с прошлого кадра
 * (положительная - вверх).
 */
public class Mouse {

    public static int getDWheel() {
        return 0;
    }
}