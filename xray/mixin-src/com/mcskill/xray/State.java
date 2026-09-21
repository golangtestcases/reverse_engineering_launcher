package com.mcskill.xray;

/**
 * Глобальное состояние мода. Миксины читают эти флаги каждый кадр
 * (volatile - без гонок между тик-потоком и render-потоком).
 *
 * Выбор руд: State.ores - имя руды (OreDictionary "oreGold"/имя класса) ->
 * включена ли. Неизвестные руды добавляются автоматически (по умолчанию
 * включены) при первой встрече в рендере. Карта потоко-безопасна
 * (ConcurrentHashMap), дефолт для отсутствующих ключей = включено.
 */
public final class State {

    public static volatile boolean xray = true;
    public static volatile boolean fullbright = true;
    public static volatile boolean menuOpen = false;

    public static final java.util.concurrent.ConcurrentHashMap<String, Boolean> ores =
            new java.util.concurrent.ConcurrentHashMap<String, Boolean>();

    private static final java.util.concurrent.atomic.AtomicBoolean seeded =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    // Известные руды (ванилла + основные моды клиента; список пополняется сам).
    private static final String[] KNOWN_ORES = new String[]{
            "oreIron", "oreGold", "oreCoal", "oreDiamond", "oreEmerald",
            "oreLapis", "oreRedstone", "oreQuartz",
            "oreCopper", "oreTin", "oreLead", "oreNickel", "oreSilver", "oreZinc",
            "orePlatinum", "oreAluminum", "oreAluminium", "oreTitanium", "oreMithril",
            "oreUranium", "oreCobalt", "oreMagnesium", "oreSulfur", "oreTungsten",
            "oreIlmenite", "oreDesh", "oreSilicon", "oreDolomite",
            "oreBlackDiamond", "oreFrozenIron", "oreGemRuby", "oreGemSapphire",
            "oreGemGreenSapphire", "oreIridium", "oreLead", "oreMetallic",
            "BlockAncientDebris"
    };

    private State() {
    }

    /** Засеять известные руды (вызывается при первом открытии меню ORES). */
    public static void ensureOreList() {
        if (seeded.get()) {
            return;
        }
        synchronized (State.class) {
            if (seeded.get()) {
                return;
            }
            for (String n : KNOWN_ORES) {
                ores.putIfAbsent(n, Boolean.TRUE);
            }
            seeded.set(true);
        }
    }

    /**
     * Включена ли руда. Неизвестное имя добавляется в список как включённое.
     * Вызывается из render-потока.
     */
    public static boolean isOreEnabled(String ore) {
        if (ore == null) {
            return true;
        }
        Boolean v = ores.get(ore);
        if (v == null) {
            Boolean prev = ores.putIfAbsent(ore, Boolean.TRUE);
            return true;
        }
        return v.booleanValue();
    }

    /** Вкл/выкл руды. */
    public static void setOreEnabled(String ore, boolean enabled) {
        ores.put(ore, Boolean.valueOf(enabled));
    }

    /** Все ли руды включены (для кнопки All ores). */
    public static boolean allOresEnabled() {
        if (ores.isEmpty()) {
            return true;
        }
        for (Boolean v : ores.values()) {
            if (v == null || !v.booleanValue()) {
                return false;
            }
        }
        return true;
    }

    /** Переключить все руды разом. */
    public static void toggleAllOres() {
        boolean target = !allOresEnabled();
        java.util.ArrayList<String> keys = new java.util.ArrayList<String>(ores.keySet());
        for (String k : keys) {
            ores.put(k, Boolean.valueOf(target));
        }
    }

    /** Ключи карты руд (для меню), отсортированные. */
    public static String[] oreKeys() {
        java.util.ArrayList<String> keys = new java.util.ArrayList<String>(ores.keySet());
        java.util.Collections.sort(keys);
        return keys.toArray(new String[keys.size()]);
    }

    /** Человеко-читаемое имя: oreGold -> Gold, BlockAncientDebris -> same. */
    public static String friendlyOreName(String ore) {
        if (ore == null) {
            return "?";
        }
        String s = ore;
        if (s.length() > 3 && s.startsWith("ore")) {
            s = s.substring(3);
        }
        if (s.length() == 0) {
            return ore;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}