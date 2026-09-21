package com.mcskill.xray;

/**
 * Глобальное состояние мода. Миксины читают эти флаги каждый кадр
 * (volatile - без гонок между тик-потоком и render-потоком).
 *
 * Выбор руд: State.ores - имя руды (OreDictionary "oreGold"/имя класса) ->
 * включена ли. Неизвестные руды добавляются автоматически (по умолчанию
 * включены) при первой встрече в рендере. Карта потоко-безопасна
 * (ConcurrentHashMap), дефолт для отсутствующих ключей = включено.
 *
 * Категории: для меню руды группируются (Vanilla / Металлы / Самоцветы /
 * Прочее) - аккордеон в XrayMenu. Неизвестные руды попадают в "Прочее".
 */
public final class State {

    public static volatile boolean xray = true;
    public static volatile boolean fullbright = true;
    public static volatile boolean menuOpen = false;

    public static final java.util.concurrent.ConcurrentHashMap<String, Boolean> ores =
            new java.util.concurrent.ConcurrentHashMap<String, Boolean>();

    private static final java.util.concurrent.atomic.AtomicBoolean seeded =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    // ---------- категории руд ----------
    public static final String CAT_VANILLA = "Vanilla";
    public static final String CAT_METALS = "Металлы";
    public static final String CAT_GEMS = "Самоцветы";
    public static final String CAT_OTHER = "Прочее";
    public static final String[] CATEGORIES = {CAT_VANILLA, CAT_METALS, CAT_GEMS, CAT_OTHER};

    private static final String[] VANILLA_ORES = {
            "oreCoal", "oreIron", "oreGold", "oreDiamond", "oreEmerald",
            "oreLapis", "oreRedstone", "oreQuartz"
    };
    private static final String[] METAL_ORES = {
            "oreCopper", "oreTin", "oreLead", "oreNickel", "oreSilver", "oreZinc",
            "orePlatinum", "oreAluminum", "oreAluminium", "oreTitanium", "oreMithril",
            "oreUranium", "oreCobalt", "oreMagnesium", "oreTungsten", "oreIridium",
            "oreFrozenIron", "oreMetallic"
    };
    private static final String[] GEM_ORES = {
            "oreGemRuby", "oreGemSapphire", "oreGemGreenSapphire", "oreBlackDiamond"
    };

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

    // Русские названия для меню (ключ -> отображаемое имя).
    private static final java.util.Map<String, String> RU_NAMES = buildRuNames();

    private State() {
    }

    private static java.util.Map<String, String> buildRuNames() {
        java.util.Map<String, String> m = new java.util.HashMap<String, String>();
        m.put("oreIron", "Железо");
        m.put("oreGold", "Золото");
        m.put("oreCoal", "Уголь");
        m.put("oreDiamond", "Алмаз");
        m.put("oreEmerald", "Изумруд");
        m.put("oreLapis", "Лазурит");
        m.put("oreRedstone", "Редстоун");
        m.put("oreQuartz", "Кварц");
        m.put("oreCopper", "Медь");
        m.put("oreTin", "Олово");
        m.put("oreLead", "Свинец");
        m.put("oreNickel", "Никель");
        m.put("oreSilver", "Серебро");
        m.put("oreZinc", "Цинк");
        m.put("orePlatinum", "Платина");
        m.put("oreAluminum", "Алюминий");
        m.put("oreAluminium", "Алюминий");
        m.put("oreTitanium", "Титан");
        m.put("oreMithril", "Мифрил");
        m.put("oreUranium", "Уран");
        m.put("oreCobalt", "Кобальт");
        m.put("oreMagnesium", "Магний");
        m.put("oreSulfur", "Сера");
        m.put("oreTungsten", "Вольфрам");
        m.put("oreIlmenite", "Ильменит");
        m.put("oreDesh", "Деш");
        m.put("oreSilicon", "Кремний");
        m.put("oreDolomite", "Доломит");
        m.put("oreBlackDiamond", "Чёрный алмаз");
        m.put("oreFrozenIron", "Ледяное железо");
        m.put("oreGemRuby", "Рубин");
        m.put("oreGemSapphire", "Сапфир");
        m.put("oreGemGreenSapphire", "Зелёный сапфир");
        m.put("oreIridium", "Иридий");
        m.put("oreMetallic", "Металл");
        m.put("BlockAncientDebris", "Древние обломки");
        return m;
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

    /** Категория руды по её ключу. Неизвестное -> "Прочее". */
    public static String oreCategory(String ore) {
        if (ore == null) {
            return CAT_OTHER;
        }
        for (String v : VANILLA_ORES) {
            if (v.equals(ore)) {
                return CAT_VANILLA;
            }
        }
        for (String v : METAL_ORES) {
            if (v.equals(ore)) {
                return CAT_METALS;
            }
        }
        for (String v : GEM_ORES) {
            if (v.equals(ore)) {
                return CAT_GEMS;
            }
        }
        return CAT_OTHER;
    }

    /** Ключи руд конкретной категории, отсортированные. */
    public static String[] oreKeysInCategory(String category) {
        java.util.ArrayList<String> keys = new java.util.ArrayList<String>(ores.keySet());
        java.util.Collections.sort(keys);
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        for (String k : keys) {
            if (oreCategory(k).equals(category)) {
                out.add(k);
            }
        }
        return out.toArray(new String[out.size()]);
    }

    /** Сколько руд включено/всего в категории. */
    public static int[] categoryCount(String category) {
        int on = 0;
        int total = 0;
        for (String k : ores.keySet()) {
            if (oreCategory(k).equals(category)) {
                total++;
                if (isOreEnabled(k)) {
                    on++;
                }
            }
        }
        return new int[]{on, total};
    }

    /** Есть ли хотя бы одна руда в категории (чтобы не рисовать пустые секции). */
    public static boolean categoryHasOres(String category) {
        for (String k : ores.keySet()) {
            if (oreCategory(k).equals(category)) {
                return true;
            }
        }
        return false;
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

    /** Русское название руды (для меню). Фолбэк на friendlyOreName. */
    public static String friendlyOreNameRu(String ore) {
        if (ore == null) {
            return "?";
        }
        String ru = RU_NAMES.get(ore);
        if (ru != null) {
            return ru;
        }
        return friendlyOreName(ore);
    }
}