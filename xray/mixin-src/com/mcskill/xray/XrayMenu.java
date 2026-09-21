package com.mcskill.xray;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjglx.opengl.GL11;

/**
 * Меню в стиле Material UI. Плоская тёмная карточка, teal-акценты, hover
 * (подсветка строки под мышью, т.к. drawScreen получает mouseX/mouseY),
 * ручной хит-тест по тем же координатам. Две страницы:
 *   MAIN : X-Ray / Fullbright + кнопка Ores
 *   ORES : All ores + список руд (6 на страницу, << >>), BACK
 * Применение мгновенное (RenderState.requestReReRender - пинок дальности
 * прорисовки, как проверено через слайдер RENDER_DISTANCE).
 *
 * SRG-имена (рантайм 1.7.10):
 *   GuiScreen.drawScreen   = func_73863_a (IIF)V
 *   GuiScreen.keyTyped     = func_73869_a (CI)V
 *   GuiScreen.mouseClicked = func_146273_a (IIIJ)V
 *   GuiScreen.doesGuiPause = func_73868_f ()Z
 *   GuiScreen.drawString   = func_146279_a (Ljava/lang/String;II)V
 */
public final class XrayMenu extends GuiScreen {

    // ---------- палитра ----------
    private static final float C_BG_R = 0.13f, C_BG_G = 0.14f, C_BG_B = 0.16f, C_BG_A = 0.95f;
    private static final float C_BORDER_R = 0.09f, C_BORDER_G = 0.10f, C_BORDER_B = 0.11f;
    private static final float C_ACCENT_R = 0.15f, C_ACCENT_G = 0.65f, C_ACCENT_B = 0.60f;   // teal
    private static final float C_ACCENT2_R = 0.30f, C_ACCENT2_G = 0.84f, C_ACCENT2_B = 0.77f; // teal light
    private static final float C_ON_R = 0.15f, C_ON_G = 0.65f, C_ON_B = 0.60f;
    private static final float C_ON_L_HOVER_R = 0.22f, C_ON_L_HOVER_G = 0.74f, C_ON_L_HOVER_B = 0.67f;
    private static final float C_OFF_R = 0.21f, C_OFF_G = 0.23f, C_OFF_B = 0.26f;
    private static final float C_OFF_HOVER_R = 0.27f, C_OFF_HOVER_G = 0.29f, C_OFF_HOVER_B = 0.32f;
    private static final float C_NAV_R = 0.30f, C_NAV_G = 0.33f, C_NAV_B = 0.40f;
    private static final float C_NAV_HOVER_R = 0.38f, C_NAV_HOVER_G = 0.42f, C_NAV_HOVER_B = 0.50f;
    private static final float C_KNOB_ON_R = 0.97f, C_KNOB_ON_G = 0.97f, C_KNOB_ON_B = 0.99f;
    private static final float C_KNOB_OFF_R = 0.66f, C_KNOB_OFF_G = 0.70f, C_KNOB_OFF_B = 0.74f;

    // ---------- layout MAIN ----------
    private static final int CARD_X = 90;
    private static final int CARD_Y = 80;
    private static final int CARD_W = 340;
    private static final int CARD_H = 250;
    private static final int TOG_X = CARD_X + 24;
    private static final int TOG_W = 292;
    private static final int TOG_H = 36;
    private static final int TOG1_Y = CARD_Y + 48;
    private static final int TOG2_Y = TOG1_Y + 52;
    private static final int TOG3_Y = TOG2_Y + 52;

    // ---------- layout ORES ----------
    private static final int O2_X = CARD_X + 20;
    private static final int O2_Y = 56;
    private static final int O2_W = 380;
    private static final int O2_H = 390;
    private static final int ROW_H = 30;
    private static final int ROW_STEP = 36;
    private static final int ROWS_PER_PAGE = 6;
    private static final int ALL_Y = O2_Y + 52;
    private static final int ROWS_Y = ALL_Y + 42;
    private static final int NAV_Y = O2_Y + 322;
    private static final int NAV_H = 30;
    private static final int NAV_BACK_W = 64;
    private static final int NAV_PREV_W = 56;
    private static final int NAV_NEXT_W = 56;
    private static final int NAV_FOOT_Y = NAV_Y + NAV_H + 12;

    // ---------- page state ----------
    private static boolean orePage = false;
    private static int orePageIndex = 0;

    public XrayMenu() {
    }

    // ---------- draw ----------

    @Override
    public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
        super.func_73863_a(mouseX, mouseY, partialTicks);
        if (orePage) {
            drawOrePage(mouseX, mouseY);
        } else {
            drawMainPage(mouseX, mouseY);
        }
    }

    private void drawMainPage(int mx, int my) {
        card(CARD_X, CARD_Y, CARD_W, CARD_H);
        this.func_146279_a("McSkill X-Ray", CARD_X + 16, CARD_Y + 18);

        hoverable(TOG_X, TOG1_Y, TOG_W, TOG_H, State.xray, mx, my,
                C_ON_R, C_ON_G, C_ON_B, C_ON_L_HOVER_R, C_ON_L_HOVER_G, C_ON_L_HOVER_B);
        this.func_146279_a("X-Ray", TOG_X + 12, TOG1_Y + TOG_H / 2 - 5);
        switchKnob(TOG_X, TOG1_Y, TOG_W, TOG_H, State.xray);

        hoverable(TOG_X, TOG2_Y, TOG_W, TOG_H, State.fullbright, mx, my,
                C_ON_R, C_ON_G, C_ON_B, C_ON_L_HOVER_R, C_ON_L_HOVER_G, C_ON_L_HOVER_B);
        this.func_146279_a("Fullbright", TOG_X + 12, TOG2_Y + TOG_H / 2 - 5);
        switchKnob(TOG_X, TOG2_Y, TOG_W, TOG_H, State.fullbright);

        // Ores - кнопка-переход
        hoverable(TOG_X, TOG3_Y, TOG_W, TOG_H, false, mx, my,
                C_NAV_R, C_NAV_G, C_NAV_B, C_NAV_HOVER_R, C_NAV_HOVER_G, C_NAV_HOVER_B);
        this.func_146279_a("Ores  >", TOG_X + 12, TOG3_Y + TOG_H / 2 - 5);

        this.func_146279_a("1/2 toggle     B ores     X close", CARD_X + 16, CARD_Y + CARD_H - 40);
        this.func_146279_a("X - menu", CARD_X + 16, CARD_Y + CARD_H - 22);
    }

    private void drawOrePage(int mx, int my) {
        card(O2_X, O2_Y, O2_W, O2_H);

        String[] keys = State.oreKeys();
        int pages = Math.max(1, (keys.length + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        if (orePageIndex >= pages) {
            orePageIndex = pages - 1;
        }
        if (orePageIndex < 0) {
            orePageIndex = 0;
        }

        this.func_146279_a("Ores (" + keys.length + ")", O2_X + 16, O2_Y + 18);

        // ALL - мастер-переключатель
        boolean allOn = State.allOresEnabled();
        hoverable(TOG_X, ALL_Y, TOG_W, ROW_H, allOn, mx, my,
                C_ON_R, C_ON_G, C_ON_B, C_ON_L_HOVER_R, C_ON_L_HOVER_G, C_ON_L_HOVER_B);
        this.func_146279_a("All ores", TOG_X + 12, ALL_Y + ROW_H / 2 - 5);
        switchKnob(TOG_X, ALL_Y, TOG_W, ROW_H, allOn);
        this.func_146279_a(allOn ? "ALL ON" : "ALL OFF", TOG_X + TOG_W - 96, ALL_Y + ROW_H / 2 - 5);

        // строки руд
        int start = orePageIndex * ROWS_PER_PAGE;
        int y = ROWS_Y;
        for (int i = start; i < start + ROWS_PER_PAGE && i < keys.length; i++) {
            String key = keys[i];
            boolean on = State.isOreEnabled(key);
            hoverable(TOG_X, y, TOG_W, ROW_H, on, mx, my,
                    C_ON_R, C_ON_G, C_ON_B, C_ON_L_HOVER_R, C_ON_L_HOVER_G, C_ON_L_HOVER_B);
            this.func_146279_a(State.friendlyOreName(key), TOG_X + 12, y + ROW_H / 2 - 5);
            switchKnob(TOG_X, y, TOG_W, ROW_H, on);
            y += ROW_STEP;
        }

        // навигация
        navButton(O2_X + 8, NAV_Y, NAV_BACK_W, NAV_H, "BACK",
                inRect(mx, my, O2_X + 8, NAV_Y, NAV_BACK_W, NAV_H));
        navButton(O2_X + O2_W - NAV_BACK_W - NAV_NEXT_W - NAV_PREV_W - 8 - 10, NAV_Y, NAV_PREV_W, NAV_H, "<<",
                inRect(mx, my, O2_X + O2_W - NAV_BACK_W - NAV_NEXT_W - NAV_PREV_W - 8 - 10, NAV_Y, NAV_PREV_W, NAV_H));
        navButton(O2_X + O2_W - NAV_BACK_W - NAV_NEXT_W, NAV_Y, NAV_NEXT_W, NAV_H, ">>",
                inRect(mx, my, O2_X + O2_W - NAV_BACK_W - NAV_NEXT_W, NAV_Y, NAV_NEXT_W, NAV_H));
        this.func_146279_a("P " + (orePageIndex + 1) + "/" + pages,
                O2_X + O2_W / 2 - 22, NAV_Y + NAV_H / 2 - 5);

        this.func_146279_a("click - toggle    ESC/X - close", O2_X + 16, NAV_FOOT_Y);
    }

    // ---------- primitives ----------

    private static void card(int x, int y, int w, int h) {
        // тень (слой потемнее, чуть сдвинут)
        fillRect(x - 2, y - 2, w + 4, h + 4, C_BORDER_R, C_BORDER_G, C_BORDER_B, 0.55f);
        // фон
        fillRect(x, y, w, h, C_BG_R, C_BG_G, C_BG_B, C_BG_A);
        // акцентная полоса
        fillRect(x, y, w, 5, C_ACCENT2_R, C_ACCENT2_G, C_ACCENT2_B, 1.0f);
    }

    /** Пилюля-строка с hover-состоянием. */
    private static void hoverable(int x, int y, int w, int h, boolean on, int mx, int my,
                                  float onR, float onG, float onB,
                                  float onHoverR, float onHoverG, float onHoverB) {
        boolean hover = inRect(mx, my, x, y, w, h);
        if (on) {
            if (hover) {
                fillRect(x, y, w, h, onHoverR, onHoverG, onHoverB, 0.94f);
            } else {
                fillRect(x, y, w, h, onR, onG, onB, 0.94f);
            }
        } else {
            if (hover) {
                fillRect(x, y, w, h, C_OFF_HOVER_R, C_OFF_HOVER_G, C_OFF_HOVER_B, 0.94f);
            } else {
                fillRect(x, y, w, h, C_OFF_R, C_OFF_G, C_OFF_B, 0.94f);
            }
        }
        // разделитель снизу
        fillRect(x, y + h - 1, w, 1, C_BORDER_R, C_BORDER_G, C_BORDER_B, 0.9f);
    }

    private static void navButton(int x, int y, int w, int h, String text, boolean hover) {
        if (hover) {
            fillRect(x, y, w, h, C_NAV_HOVER_R, C_NAV_HOVER_G, C_NAV_HOVER_B, 0.95f);
        } else {
            fillRect(x, y, w, h, C_NAV_R, C_NAV_G, C_NAV_B, 0.95f);
        }
    }

    /** Переключатель вправо: трек + ручка. */
    private static void switchKnob(int x, int y, int w, int h, boolean on) {
        int sw = 22;
        int kn = 14;
        int trackX = x + w - sw - 10;
        int trackY = y + (h - 10) / 2;
        if (on) {
            fillRect(trackX, trackY, sw, 10, C_ACCENT_R, C_ACCENT_G, C_ACCENT_B, 1.0f);
            fillRect(trackX + sw - kn, trackY - 2, kn, 14, C_KNOB_ON_R, C_KNOB_ON_G, C_KNOB_ON_B, 1.0f);
        } else {
            fillRect(trackX, trackY, sw, 10, 0.42f, 0.45f, 0.50f, 1.0f);
            fillRect(trackX, trackY - 2, kn, 14, C_KNOB_OFF_R, C_KNOB_OFF_G, C_KNOB_OFF_B, 1.0f);
        }
    }

    /** Плоский прямоугольник (квад). Вызывается только из drawScreen. */
    private static void fillRect(int x, int y, int w, int h, float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    // ---------- input ----------

    @Override
    public void func_73869_a(char keyChar, int keyCode) {
        if (keyChar == 27) { // ESC
            close();
            return;
        }
        if (keyChar == 'x' || keyChar == 'X' || keyChar == 1095 || keyChar == 1063) { // X / ч
            close();
            return;
        }
        if (keyChar == '1') {
            toggleXray();
            return;
        }
        if (keyChar == '2') {
            toggleFullbright();
            return;
        }
        if (keyChar == 'b' || keyChar == 'B' || keyChar == 1080 || keyChar == 1041) { // B / и
            if (orePage) {
                orePage = false;
            }
            return;
        }
        super.func_73869_a(keyChar, keyCode);
    }

    @Override
    protected void func_146273_a(int mouseX, int mouseY, int mouseButton, long param) {
        if (mouseButton == 0) {
            if (orePage) {
                handleOreClick(mouseX, mouseY);
            } else {
                if (inRect(mouseX, mouseY, TOG_X, TOG1_Y, TOG_W, TOG_H)) {
                    toggleXray();
                } else if (inRect(mouseX, mouseY, TOG_X, TOG2_Y, TOG_W, TOG_H)) {
                    toggleFullbright();
                } else if (inRect(mouseX, mouseY, TOG_X, TOG3_Y, TOG_W, TOG_H)) {
                    openOres();
                }
            }
        }
        super.func_146273_a(mouseX, mouseY, mouseButton, param);
    }

    private void handleOreClick(int mx, int my) {
        if (inRect(mx, my, TOG_X, ALL_Y, TOG_W, ROW_H)) {
            State.toggleAllOres();
            RenderState.requestReReRender();
            return;
        }
        String[] keys = State.oreKeys();
        int pages = Math.max(1, (keys.length + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        int start = orePageIndex * ROWS_PER_PAGE;
        int y = ROWS_Y;
        for (int i = start; i < start + ROWS_PER_PAGE && i < keys.length; i++) {
            if (inRect(mx, my, TOG_X, y, TOG_W, ROW_H)) {
                String key = keys[i];
                State.setOreEnabled(key, !State.isOreEnabled(key));
                RenderState.requestReReRender();
                return;
            }
            y += ROW_STEP;
        }
        // навигация
        int prevX = O2_X + O2_W - NAV_BACK_W - NAV_NEXT_W - NAV_PREV_W - 18;
        int nextX = O2_X + O2_W - NAV_BACK_W - NAV_NEXT_W;
        if (inRect(mx, my, O2_X + 8, NAV_Y, NAV_BACK_W, NAV_H)) {
            orePage = false;
        } else if (inRect(mx, my, prevX, NAV_Y, NAV_PREV_W, NAV_H)) {
            if (orePageIndex > 0) {
                orePageIndex--;
            }
        } else if (inRect(mx, my, nextX, NAV_Y, NAV_NEXT_W, NAV_H)) {
            if (orePageIndex + 1 < pages) {
                orePageIndex++;
            }
        }
    }

    @Override
    public boolean func_73868_f() {
        return false; // не ставить игру на паузу
    }

    // ---------- state ----------

    private static void toggleXray() {
        State.xray = !State.xray;
        RenderState.requestReReRender();
    }

    private static void toggleFullbright() {
        State.fullbright = !State.fullbright;
        RenderState.requestReReRender();
    }

    private static void openOres() {
        State.ensureOreList();
        orePage = true;
        orePageIndex = 0;
    }

    private void close() {
        State.menuOpen = false;
        Minecraft.func_71410_x().func_147108_a(null);
    }
}