package com.mcskill.xray;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjglx.opengl.GL11;
import org.lwjglx.input.Mouse;

/**
 * Меню мода: тёмная полупрозрачная карточка, плавные hover-анимации,
 * категории руд аккордеоном (dropdown), скролл колесом мыши, навигация
 * стрелками + Enter, кнопка "Применить".
 *
 * СТРАНИЦЫ
 *   MAIN : X-Ray / Fullbright + переход к рудам
 *   ORES : "Все руды" + категории (Vanilla/Металлы/Самоцветы/Прочее)
 *          аккордеоном; строка руды: зелёный полупрозрачный = вкл,
 *          красный полупрозрачный = выкл; внизу кнопка "Применить".
 *
 * ПРИМЕНЕНИЕ (важно): клики по рудам меняют ТОЛЬКО State.ores — мир не
 * перестраивается на каждый клик (меню мгновенно отзывчивое, нет фризов).
 * Перестроение чанков (RenderState.requestReReRender - пинок дальности +
 * forcechunk) вызывается один раз: по кнопке "Применить" или автоматически
 * при закрытии меню с незаписанными изменениями.
 *
 * SRG-имена (рантайм 1.7.10):
 *   GuiScreen.drawScreen   = func_73863_a (IIF)V
 *   GuiScreen.keyTyped     = func_73869_a (CI)V
 *   GuiScreen.mouseClicked = func_146273_a (IIIJ)V
 *   GuiScreen.doesGuiPause = func_73868_f ()Z
 *   GuiScreen.drawString   = func_146279_a (Ljava/lang/String;II)V
 */
public final class XrayMenu extends GuiScreen {

    // ---------- палитра (тёмная тема) ----------
    private static final float C_SHADE_A = 0.45f;                    // затемнение мира
    private static final float C_BG_R = 0.11f, C_BG_G = 0.12f, C_BG_B = 0.15f, C_BG_A = 0.96f;
    private static final float C_BORDER_R = 0.20f, C_BORDER_G = 0.22f, C_BORDER_B = 0.26f;
    private static final float C_ACCENT_R = 0.25f, C_ACCENT_G = 0.75f, C_ACCENT_B = 0.70f;   // teal
    private static final float C_ACCENT2_R = 0.35f, C_ACCENT2_G = 0.88f, C_ACCENT2_B = 0.82f; // teal light
    private static final float C_GREEN_R = 0.20f, C_GREEN_G = 0.68f, C_GREEN_B = 0.46f;       // руда вкл
    private static final float C_GREEN_D_R = 0.09f, C_GREEN_D_G = 0.30f, C_GREEN_D_B = 0.20f; // фон вкл
    private static final float C_RED_R = 0.92f, C_RED_G = 0.33f, C_RED_B = 0.30f;             // руда выкл
    private static final float C_RED_D_R = 0.38f, C_RED_D_G = 0.12f, C_RED_D_B = 0.12f;       // фон выкл
    private static final float C_OFF_R = 0.20f, C_OFF_G = 0.22f, C_OFF_B = 0.26f;
    private static final float C_NAV_R = 0.17f, C_NAV_G = 0.19f, C_NAV_B = 0.23f;
    private static final float C_NAV_HI_R = 0.24f, C_NAV_HI_G = 0.40f, C_NAV_HI_B = 0.45f;
    private static final float C_WARN_R = 0.90f, C_WARN_G = 0.72f, C_WARN_B = 0.24f;
    private static final float C_KNOB_R = 0.97f, C_KNOB_G = 0.97f, C_KNOB_B = 0.99f;

    // ---------- layout MAIN ----------
    private static final int CARD_X = 80;
    private static final int CARD_Y = 72;
    private static final int CARD_W = 380;
    private static final int CARD_H = 262;
    private static final int ROW_X = CARD_X + 20;
    private static final int ROW_W = CARD_W - 40;
    private static final int ROW_H = 38;
    private static final int ROW1_Y = CARD_Y + 46;
    private static final int ROW_STEP = 48;
    private static final int FOOT1_Y = CARD_Y + CARD_H - 38;
    private static final int FOOT2_Y = CARD_Y + CARD_H - 22;

    // ---------- layout ORES ----------
    private static final int O2_X = 60;
    private static final int O2_Y = 48;
    private static final int O2_W = 420;
    private static final int O2_H = 356;
    private static final int O2_ROW_X = O2_X + 16;
    private static final int O2_ROW_W = O2_W - 32;
    private static final int O2_HEAD_H = 26;   // строка-заголовок (категория / all)
    private static final int O2_ORE_H = 22;    // строка руды
    private static final int LIST_TOP = O2_Y + 44;
    private static final int SAVE_Y = O2_Y + O2_H - 44;
    private static final int SAVE_H = 32;
    private static final int FOOT_Y = O2_Y + O2_H - 16;

    // ---------- состояние ----------
    private static boolean orePage = false;
    private static final java.util.HashSet<String> expandedCats = new java.util.HashSet<String>();
    private static boolean allExpanded = false;
    private static float scroll = 0f;
    private static boolean dirty = false;
    private static long openTime = 0L;
    private static String focusId = null;

    // анимации (живут между кадрами)
    private static final java.util.HashMap<String, Float> hover = new java.util.HashMap<String, Float>();
    private static final java.util.HashMap<String, Float> knob = new java.util.HashMap<String, Float>();

    public XrayMenu() {
        openTime = System.currentTimeMillis();
        State.ensureOreList();
        dirty = false;
    }

    // ---------- draw ----------

    @Override
    public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
        super.func_73863_a(mouseX, mouseY, partialTicks);
        long now = System.currentTimeMillis();
        float open = smoothstep(Math.min(1f, (now - openTime) / 160f));
        int slide = (int) ((1f - open) * 14f);

        fillRect(0, 0, 3200, 3200, 0f, 0f, 0f, C_SHADE_A * open);

        if (orePage) {
            drawOrePage(mouseX, mouseY, open, slide);
        } else {
            drawMainPage(mouseX, mouseY, open, slide);
        }
        stepAnimations();
    }

    // ---------- MAIN ----------

    private void drawMainPage(int mx, int my, float open, int slide) {
        int y0 = CARD_Y + slide;
        card(CARD_X, y0, CARD_W, CARD_H, open);
        this.func_146279_a("McSkill X-Ray", CARD_X + 16, y0 + 16);

        row(ROW_X, y0 + 46, ROW_W, ROW_H, "m:xray", "X-Ray", State.xray, mx, my, true);
        row(ROW_X, y0 + 94, ROW_W, ROW_H, "m:fb", "Fullbright", State.fullbright, mx, my, true);
        row(ROW_X, y0 + 142, ROW_W, ROW_H, "m:ores", "Руды  >", false, mx, my, false);

        this.func_146279_a("1 - X-Ray   2 - Fullbright   X - закрыть", CARD_X + 16, FOOT1_Y);
        this.func_146279_a("Клик по строке - переключить", CARD_X + 16, FOOT2_Y);
    }

    // ---------- ORES ----------

    private void drawOrePage(int mx, int my, float open, int slide) {
        int y0 = O2_Y + slide;
        card(O2_X, y0, O2_W, O2_H, open);
        this.func_146279_a("Руды", O2_X + 16, y0 + 14);
        this.func_146279_a("ПКМ/Enter - вкл/выкл", O2_X + O2_W - 128, y0 + 14);
        this.func_146279_a(countStat(), O2_X + O2_W - 60, y0 + 30);

        int scrollMax = computeScrollMax();
        int dw = Mouse.getDWheel();
        if (dw != 0) {
            scroll -= Math.signum(dw) * 40f;
            if (scroll < 0f) {
                scroll = 0f;
            }
            if (scroll > scrollMax) {
                scroll = scrollMax;
            }
        }

        int y = LIST_TOP - (int) scroll;

        // "Все руды"
        drawOreHeader(y, "row:all", mx, my, "Все руды", State.allOresEnabled(), countOn(), countTotal());
        y += O2_HEAD_H;

        for (String cat : State.CATEGORIES) {
            String[] keys = State.oreKeysInCategory(cat);
            if (keys.length == 0) {
                continue;
            }
            boolean openCat = expandedCats.contains(cat);
            int[] cc = State.categoryCount(cat);
            drawOreHeader(y, "cat:" + cat, mx, my, (openCat ? "v " : "> ") + cat, openCat, cc[0], cc[1]);
            y += O2_HEAD_H;
            if (openCat) {
                for (String key : keys) {
                    drawOreRow(y, key, mx, my);
                    y += O2_ORE_H;
                }
            }
        }

        drawSaveButton(mx, my);

        this.func_146279_a("стрелки - навигация, B - назад, A - все категории", O2_X + 16, FOOT_Y);
    }

    private int computeScrollMax() {
        int contentH = O2_HEAD_H; // "Все руды"
        for (String cat : State.CATEGORIES) {
            String[] keys = State.oreKeysInCategory(cat);
            if (keys.length == 0) {
                continue;
            }
            contentH += O2_HEAD_H;
            if (expandedCats.contains(cat)) {
                contentH += keys.length * O2_ORE_H;
            }
        }
        int viewportH = SAVE_Y - 8 - LIST_TOP;
        return Math.max(0, contentH - viewportH);
    }

    private void drawOreHeader(int y, String id, int mx, int my, String label, boolean on, int onN, int totalN) {
        boolean hov = inRect(mx, my, O2_ROW_X, y, O2_ROW_W, O2_HEAD_H);
        float p = anim(hover, id, hov ? 1f : 0f);
        float r = lerp(C_NAV_R, C_NAV_HI_R, p);
        float g = lerp(C_NAV_G, C_NAV_HI_G, p);
        float b = lerp(C_NAV_B, C_NAV_HI_B, p);
        fillRect(O2_ROW_X, y, O2_ROW_W, O2_HEAD_H, r, g, b, 0.92f);
        fillRect(O2_ROW_X, y + O2_HEAD_H - 1, O2_ROW_W, 1, C_BORDER_R, C_BORDER_G, C_BORDER_B, 0.9f);
        if (id.equals(focusId)) {
            fillRect(O2_ROW_X, y, O2_ROW_W, 2, C_ACCENT2_R, C_ACCENT2_G, C_ACCENT2_B, 1f);
            fillRect(O2_ROW_X, y, 2, O2_HEAD_H, C_ACCENT2_R, C_ACCENT2_G, C_ACCENT2_B, 1f);
        }
        this.func_146279_a(label + "   " + onN + "/" + totalN, O2_ROW_X + 10, y + O2_HEAD_H / 2 - 4);
    }

    /** Строка руды: зелёный полупрозрачный = вкл, красный полупрозрачный = выкл. */
    private void drawOreRow(int y, String key, int mx, int my) {
        boolean on = State.isOreEnabled(key);
        String id = "ore:" + key;
        boolean hov = inRect(mx, my, O2_ROW_X, y, O2_ROW_W, O2_ORE_H);
        float p = anim(hover, id, hov ? 1f : 0f);
        float r = lerp(on ? C_GREEN_D_R : C_RED_D_R, on ? C_GREEN_R : C_RED_R, p);
        float g = lerp(on ? C_GREEN_D_G : C_RED_D_G, on ? C_GREEN_G : C_RED_G, p);
        float b = lerp(on ? C_GREEN_D_B : C_RED_D_B, on ? C_GREEN_B : C_RED_B, p);
        fillRect(O2_ROW_X, y, O2_ROW_W, O2_ORE_H, r, g, b, 0.55f);
        // индикатор слева
        float ir = on ? C_GREEN_R : C_RED_R;
        float ig = on ? C_GREEN_G : C_RED_G;
        float ib = on ? C_GREEN_B : C_RED_B;
        fillRect(O2_ROW_X, y, 4, O2_ORE_H, ir, ig, ib, on ? 0.9f : 0.95f);
        if (id.equals(focusId)) {
            fillRect(O2_ROW_X, y, O2_ROW_W, 2, C_ACCENT2_R, C_ACCENT2_G, C_ACCENT2_B, 1f);
            fillRect(O2_ROW_X + O2_ROW_W - 2, y, 2, O2_ORE_H, C_ACCENT2_R, C_ACCENT2_G, C_ACCENT2_B, 1f);
        }
        String mark = on ? "+" : "-";
        this.func_146279_a(mark + " " + State.friendlyOreNameRu(key), O2_ROW_X + 12, y + O2_ORE_H / 2 - 4);
    }

    private void drawSaveButton(int mx, int my) {
        int x = O2_X + 16;
        int y = SAVE_Y;
        int w = O2_W - 32;
        boolean hov = inRect(mx, my, x, y, w, SAVE_H);
        float p = anim(hover, "row:save", hov ? 1f : 0f);
        if (dirty) {
            float r = lerp(C_WARN_R * 0.55f, C_WARN_R, p);
            float g = lerp(C_WARN_G * 0.55f, C_WARN_G, p);
            float b = lerp(C_WARN_B * 0.55f, C_WARN_B, p);
            fillRect(x, y, w, SAVE_H, r, g, b, 0.94f);
            this.func_146279_a("Применить изменения  (чанки перестроятся)", x + 14, y + SAVE_H / 2 - 4);
        } else {
            float r = lerp(C_OFF_R, C_OFF_R + 0.12f, p);
            float g = lerp(C_OFF_G, C_OFF_G + 0.12f, p);
            float b = lerp(C_OFF_B, C_OFF_B + 0.12f, p);
            fillRect(x, y, w, SAVE_H, r, g, b, 0.94f);
            this.func_146279_a("Всё применено", x + 14, y + SAVE_H / 2 - 4);
        }
    }

    // ---------- primitives ----------

    private static void card(int x, int y, int w, int h, float open) {
        fillRect(x - 3, y - 3, w + 6, h + 6, 0f, 0f, 0f, 0.45f * open);
        fillRect(x, y, w, h, C_BG_R, C_BG_G, C_BG_B, C_BG_A * open);
        fillRect(x, y, w, 1, C_BORDER_R + 0.1f, C_BORDER_G + 0.1f, C_BORDER_B + 0.1f, open);
        fillRect(x, y + h - 1, w, 1, C_BORDER_R, C_BORDER_G, C_BORDER_B, open);
        fillRect(x, y, 1, h, C_BORDER_R, C_BORDER_G, C_BORDER_B, open);
        fillRect(x + w - 1, y, 1, h, C_BORDER_R, C_BORDER_G, C_BORDER_B, open);
        fillRect(x, y, w, 4, C_ACCENT2_R, C_ACCENT2_G, C_ACCENT2_B, open);
        fillRect(x + 12, y + 30, w - 24, 1, C_BORDER_R, C_BORDER_G, C_BORDER_B, 0.8f * open);
    }

    /** Универсальная строка: тумблер или кнопка. */
    private void row(int x, int y, int w, int h, String id, String label, boolean on, int mx, int my, boolean withSwitch) {
        boolean hov = inRect(mx, my, x, y, w, h);
        float p = anim(hover, id, hov ? 1f : 0f);
        float r = lerp(C_OFF_R, C_OFF_R + 0.12f, p);
        float g = lerp(C_OFF_G, C_OFF_G + 0.14f, p);
        float b = lerp(C_OFF_B, C_OFF_B + 0.16f, p);
        fillRect(x, y, w, h, r, g, b, 0.94f);
        fillRect(x, y + h - 1, w, 1, C_BORDER_R, C_BORDER_G, C_BORDER_B, 0.9f);
        if (id.equals(focusId)) {
            fillRect(x, y, w, 2, C_ACCENT2_R, C_ACCENT2_G, C_ACCENT2_B, 1f);
            fillRect(x, y, 3, h, C_ACCENT2_R, C_ACCENT2_G, C_ACCENT2_B, 1f);
        }
        this.func_146279_a(label, x + 12, y + h / 2 - 5);
        if (withSwitch) {
            switchKnob(x, y, w, h, on);
        } else {
            this.func_146279_a(">", x + w - 22, y + h / 2 - 5);
        }
    }

    /** Переключатель вправо: трек + ручка (ручка едет плавно). */
    private static void switchKnob(int x, int y, int w, int h, boolean on) {
        float target = on ? 1f : 0f;
        String id = "knob:" + x + ":" + y;
        Float cur = knob.get(id);
        float p = cur == null ? target : cur.floatValue();
        p += (target - p) * 0.32f;
        knob.put(id, p);
        int sw = 24;
        int kn = 12;
        int trackX = x + w - sw - 10;
        int trackY = y + (h - 8) / 2;
        float r = lerp(0.45f, C_ACCENT_R, p);
        float g = lerp(0.48f, C_ACCENT_G, p);
        float b = lerp(0.52f, C_ACCENT_B, p);
        fillRect(trackX, trackY, sw, 8, r, g, b, 1f);
        int off = (int) (p * (sw - kn));
        fillRect(trackX + off, trackY - 2, kn, 12, C_KNOB_R, C_KNOB_G, C_KNOB_B, 1f);
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

    // ---------- анимации ----------

    private static float anim(java.util.HashMap<String, Float> map, String id, float target) {
        Float cur = map.get(id);
        float v;
        if (cur == null) {
            v = target;
            map.put(id, Float.valueOf(v));
            return v;
        }
        v = cur.floatValue();
        v += (target - v) * 0.32f;
        if (Math.abs(target - v) < 0.004f) {
            v = target;
        }
        map.put(id, Float.valueOf(v));
        return v;
    }

    /** Чистка устаревших анимационных ключей. */
    private static void stepAnimations() {
        if (hover.size() > 600) {
            hover.clear();
        }
        if (knob.size() > 200) {
            knob.clear();
        }
    }

    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ---------- статистика ----------

    private static int countOn() {
        int n = 0;
        for (String k : State.ores.keySet()) {
            if (State.isOreEnabled(k)) {
                n++;
            }
        }
        return n;
    }

    private static int countTotal() {
        return State.ores.size();
    }

    private static String countStat() {
        return countOn() + "/" + countTotal() + " вкл";
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
        if (keyChar == 'a' || keyChar == 'A') { // A - все категории
            allExpanded = !allExpanded;
            expandedCats.clear();
            if (allExpanded) {
                for (String cat : State.CATEGORIES) {
                    if (State.categoryHasOres(cat)) {
                        expandedCats.add(cat);
                    }
                }
            }
            return;
        }
        if (keyChar == 0) {
            if (keyCode == 200) { // UP
                moveCursor(-1);
                return;
            }
            if (keyCode == 208) { // DOWN
                moveCursor(1);
                return;
            }
            if (keyCode == 28 || keyCode == 57) { // Enter / Space
                activateCursor();
                return;
            }
        }
        super.func_73869_a(keyChar, keyCode);
    }

    @Override
    protected void func_146273_a(int mouseX, int mouseY, int mouseButton, long param) {
        if (mouseButton == 0 || (mouseButton == 1 && orePage)) {
            if (orePage) {
                handleOreClick(mouseX, mouseY);
            } else {
                if (inRect(mouseX, mouseY, ROW_X, CARD_Y + 46, ROW_W, ROW_H)) {
                    toggleXray();
                } else if (inRect(mouseX, mouseY, ROW_X, CARD_Y + 94, ROW_W, ROW_H)) {
                    toggleFullbright();
                } else if (inRect(mouseX, mouseY, ROW_X, CARD_Y + 142, ROW_W, ROW_H)) {
                    openOres();
                }
            }
        }
        super.func_146273_a(mouseX, mouseY, mouseButton, param);
    }

    private void handleOreClick(int mx, int my) {
        int y = LIST_TOP - (int) scroll;

        if (inRect(mx, my, O2_ROW_X, y, O2_ROW_W, O2_HEAD_H)) {
            State.toggleAllOres();
            dirty = true;
            return;
        }
        y += O2_HEAD_H;

        for (String cat : State.CATEGORIES) {
            String[] keys = State.oreKeysInCategory(cat);
            if (keys.length == 0) {
                continue;
            }
            if (inRect(mx, my, O2_ROW_X, y, O2_ROW_W, O2_HEAD_H)) {
                if (expandedCats.contains(cat)) {
                    expandedCats.remove(cat);
                } else {
                    expandedCats.add(cat);
                }
                return;
            }
            y += O2_HEAD_H;
            if (expandedCats.contains(cat)) {
                for (String key : keys) {
                    if (inRect(mx, my, O2_ROW_X, y, O2_ROW_W, O2_ORE_H)) {
                        State.setOreEnabled(key, !State.isOreEnabled(key));
                        dirty = true;
                        return;
                    }
                    y += O2_ORE_H;
                }
            }
        }

        if (inRect(mx, my, O2_X + 16, SAVE_Y, O2_W - 32, SAVE_H)) {
            applyChanges();
        }
    }

    /** Перестроение чанков - один раз, по явной команде. */
    private static void applyChanges() {
        dirty = false;
        RenderState.requestReReRender();
    }

    // ---------- курсор (клавиатура) ----------

    /** Плоский список интерактивных id текущей страницы. */
    private static java.util.ArrayList<String> focusList() {
        java.util.ArrayList<String> list = new java.util.ArrayList<String>();
        if (!orePage) {
            list.add("m:xray");
            list.add("m:fb");
            list.add("m:ores");
            return list;
        }
        list.add("row:all");
        for (String cat : State.CATEGORIES) {
            String[] keys = State.oreKeysInCategory(cat);
            if (keys.length == 0) {
                continue;
            }
            list.add("cat:" + cat);
            if (expandedCats.contains(cat)) {
                for (String key : keys) {
                    list.add("ore:" + key);
                }
            }
        }
        return list;
    }

    private static void moveCursor(int delta) {
        java.util.ArrayList<String> list = focusList();
        if (list.isEmpty()) {
            return;
        }
        int idx = list.indexOf(focusId);
        if (idx < 0) {
            idx = 0;
        }
        idx += delta;
        if (idx < 0) {
            idx = list.size() - 1;
        }
        if (idx >= list.size()) {
            idx = 0;
        }
        focusId = list.get(idx);
    }

    private static void activateCursor() {
        String id = focusId;
        if (id == null) {
            return;
        }
        if (id.equals("m:xray")) {
            toggleXray();
            return;
        }
        if (id.equals("m:fb")) {
            toggleFullbright();
            return;
        }
        if (id.equals("m:ores")) {
            openOres();
            return;
        }
        if (id.equals("row:all")) {
            State.toggleAllOres();
            dirty = true;
            return;
        }
        if (id.startsWith("cat:")) {
            String cat = id.substring(4);
            if (expandedCats.contains(cat)) {
                expandedCats.remove(cat);
            } else {
                expandedCats.add(cat);
            }
            return;
        }
        if (id.startsWith("ore:")) {
            String key = id.substring(4);
            State.setOreEnabled(key, !State.isOreEnabled(key));
            dirty = true;
        }
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
        dirty = false;
    }

    private void close() {
        if (dirty) {
            applyChanges();
        }
        State.menuOpen = false;
        Minecraft.func_71410_x().func_147108_a(null);
    }

    @Override
    public boolean func_73868_f() {
        return false; // не ставить игру на паузу
    }
}