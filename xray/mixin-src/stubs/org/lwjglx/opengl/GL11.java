package org.lwjglx.opengl;

/**
 * Стаб для компиляции. Рантайм: настоящий org.lwjglx.opengl.GL11 из lwjgl3ify
 * (совместимый с LWJGL2 API). Используем только квады для 2D-фигур GUI.
 */
public class GL11 {

    public static final int GL_QUADS = 7;

    public static void glColor4f(float r, float g, float b, float a) {
    }

    public static void glBegin(int mode) {
    }

    public static void glVertex2f(float x, float y) {
    }

    public static void glEnd() {
    }
}