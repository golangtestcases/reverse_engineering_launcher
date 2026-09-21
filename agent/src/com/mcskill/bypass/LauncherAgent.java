package com.mcskill.bypass;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;

/**
 * Attach-агент для McSkill Launcher (net.mcsgroup.launcher).
 *
 * Патчи (см. {@link #PATCHES}):
 *   1. net.mcsgroup.launcher.core.i.d  (SignatureVerifier) — a(ClientProfile) всегда true;
 *   2. net.mcsgroup.launcher.core.e.b  (IntegrityWatcher)  — register() и scheduleKill() пустые;
 *   3. net.mcsgroup.launcher.core.e.e$d (корутина LaunchViewModel) — условие abort
 *      (декомпилированная строка 836 "pre-launch FAILED — aborting launch") не срабатывает.
 *
 * Тело патча выбирается по типу возвращаемого значения из JVM-дескриптора метода
 * (m.getMethodInfo().getDescriptor()):
 *     V (void)                -> "return;"
 *     Z (boolean)             -> "return true;"
 *     I, J, S, B, C           -> "return 0;"
 *     всё остальное (ссылки)  -> "return null;"
 *
 * Загрузка: JDK-механизм attach (VirtualMachine.loadAgent) -> agentmain(String, Instrumentation).
 * Возможен также запуск через -javaagent на старте JVM (тогда вызывается premain).
 */
public final class LauncherAgent {

    /** Одна запись конфига: класс :: метод :: префикс JVM-дескриптора (для перегрузок). */
    static final class Patch {
        final String className;        // бинарное имя, с '$' для inner-классов
        final String methodName;
        final String descriptorPrefix; // null => особый случай (правка байткода guard'а)
        Patch(String className, String methodName, String descriptorPrefix) {
            this.className = className;
            this.methodName = methodName;
            this.descriptorPrefix = descriptorPrefix;
        }
    }

    /**
     * Конфигурация патчей.
     * Дескрипторы получены из реальных классов Launcher.jar (см. decompiled):
     *   - d.a:     (Lnet/mcsgroup/launcher/proto/ClientProfile;)Z
     *   - b.a:     (Ljava/nio/file/Path;Ljava/util/List;Ljava/util/List;Ljava/util/Set;)V  (register)
     *   - b.a:     (Lkotlin/jvm/functions/Function0;)V                                   (scheduleKill)
     *   - e$d:     invokeSuspend — guard abort-ветки (строка 836) правится на уровне байткода.
     */
    private static final Patch[] PATCHES = {
        new Patch("net.mcsgroup.launcher.core.i.d", "a",
                "(Lnet/mcsgroup/launcher/proto/ClientProfile;)"),
        new Patch("net.mcsgroup.launcher.core.e.b", "a",
                "(Ljava/nio/file/Path;Ljava/util/List;Ljava/util/List;Ljava/util/Set;)"),
        new Patch("net.mcsgroup.launcher.core.e.b", "a",
                "(Lkotlin/jvm/functions/Function0;)"),
        new Patch("net.mcsgroup.launcher.core.e.e$d", "invokeSuspend", null),
        // sync-delete: k.f.a(Path,List<String>,Continuation)Object — удаление "лишних"
        // локальных файлов клиента (зачистка mods/ и пр.). Пустое тело => никогда.
        new Patch("net.mcsgroup.launcher.core.k.f", "a",
                "(Ljava/nio/file/Path;Ljava/util/List;Lkotlin/coroutines/Continuation;)"),
    };

    private static final Map<String, List<Patch>> BY_CLASS = buildIndex();

    /** Retransform-флаги: даём JVM право перетрансформировать уже загруженные классы. */
    public static void agentmain(String args, Instrumentation inst) {
        log("attaching (agentmain): instrumenting launcher...");
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(Module module, ClassLoader loader, String className,
                                    Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                                    byte[] classfileBuffer) {
                if (className == null) {
                    return null;
                }
                // ВАЖНО: при retransformation JVM передаёт имя класса в виде
                // "net/mcsgroup/launcher/core/i/d" (со слешами), а getLoadedClasses
                // возвращает "net.mcsgroup.launcher.core.i.d" (с точками).
                String binName = className.replace('/', '.');
                List<Patch> patches = BY_CLASS.get(binName);
                if (patches == null) {
                    return null;
                }
                try {
                    return apply(patches, binName, classfileBuffer);
                } catch (Throwable t) {
                    log("PATCH FAILED " + className + ": " + t);
                    return null; // не валим загрузку/ретрасформацию класса
                }
            }
        }, true);

        retransformLoadedClasses(inst);
        log("done. targets=" + BY_CLASS.keySet());
    }

    /** Для -javaagent на старте JVM (альтернативный способ, если attach недоступен). */
    public static void premain(String args, Instrumentation inst) {
        agentmain(args, inst);
    }

    // ====================================================================
    //  Повторная трансформация уже загруженных классов
    // ====================================================================

    private static void retransformLoadedClasses(Instrumentation inst) {
        List<Class<?>> targets = new ArrayList<>();
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c == null) {
                continue;
            }
            String n = c.getName();
            if (n != null && BY_CLASS.containsKey(n)) {
                targets.add(c);
            }
        }
        if (targets.isEmpty()) {
            log("no target classes loaded yet; load-time transforms will apply them");
            return;
        }
        try {
            inst.retransformClasses(targets.toArray(new Class<?>[0]));
            log("retransformed " + targets.size() + " loaded class(es)");
        } catch (UnmodifiableClassException e) {
            log("retransform rejected some classes: " + e.getMessage());
        }
    }

    // ====================================================================
    //  Применение патчей через Javassist
    // ====================================================================

    /** Тело формируется напрямую в байткоде; применяет Javassist-трансформацию. */
    public static byte[] apply(List<Patch> patches, String className, byte[] classfileBuffer) throws Exception {
        ClassPool pool = new ClassPool();
        CtClass cc = pool.makeClass(new ByteArrayInputStream(classfileBuffer));
        if (cc.isFrozen()) {
            cc.defrost();
        }
        StringBuilder changes = new StringBuilder();
        for (Patch p : patches) {
            if (p.descriptorPrefix == null) {
                // e$d: правам 15 байт guard'а НАПРЯМУЮ в исходном classfile-буфере.
                // javassist на toBytecode() выкидывает Kotlin-атрибут StackMapTable,
                // и тогда JVM падает при первом же исполнении метода:
                //   VerifyError: Expecting a stackmap frame at branch target 2585
                // (invokeSuspend — suspend-lambda, верифицируется по фреймам таблицы).
                byte[] raw = rawNeutralizeAbortGuard(classfileBuffer);
                if (raw != null) {
                    log("patched " + className + ": " + lastRawChange);
                    return raw;
                }
                neutralizeAbortGuard(cc, changes); // fallback: другой layout
                continue;
            }
            int hit = 0;
            for (CtMethod m : cc.getDeclaredMethods()) {
                if (!p.methodName.equals(m.getName())) {
                    continue;
                }
                MethodInfo mi = m.getMethodInfo();
                String descriptor = mi.getDescriptor();
                if (descriptor == null || !descriptor.startsWith(p.descriptorPrefix)) {
                    continue;
                }
                // Тело собираем вручную из байткода: так не нужен javassist-компилятор,
                // который требует типы параметров в ClassPool (ClientProfile и пр.).
                byte[] body = simpleBodyBytes(jvmReturnType(descriptor));
                int maxStack = simpleBodyMaxStack(body);
                // Слоты locals: параметры + слот "this" у instance-методов (JVM: this=local 0).
                int argSlots = localSlotsForDescriptor(descriptor);
                boolean isStatic = (mi.getAccessFlags() & javassist.bytecode.AccessFlag.STATIC) != 0;
                JvmBody.write(m, body, maxStack, argSlots + (isStatic ? 0 : 1));
                changes.append(m.getName()).append(' ').append(descriptor)
                       .append(" -> ").append(describeBody(body)).append(" | ");
                hit++;
            }
            if (hit == 0) {
                changes.append("!!no-method ").append(p.methodName).append(" ").append(p.descriptorPrefix).append(" | ");
            }
        }
        if (changes.length() == 0) {
            cc.detach();
            return null;
        }
        byte[] out = cc.toBytecode();
        cc.detach();
        log("patched " + className + ": " + changes);
        return out;
    }

    // ====================================================================
    //  Правило тела метода по JVM-дескриптору (см. javadoc класса)
    // ====================================================================

    /** Генерирует байткод тривиального тела: return; / return true; / return 0; / return null; */
    private static byte[] simpleBodyBytes(String ret) {
        if ("V".equals(ret)) {
            return new byte[]{(byte) 0xB1};            // arreturn
        }
        if ("Z".equals(ret) || "I".equals(ret) || "J".equals(ret)
                || "S".equals(ret) || "B".equals(ret) || "C".equals(ret)) {
            return new byte[]{(byte) 0x04, (byte) 0xAC}; // iconst_1; ireturn
        }
        return new byte[]{(byte) 0x01, (byte) 0xB0};    // aconst_null; areturn
    }

    /** Максимальная глубина стека для тел из simpleBodyBytes. */
    private static int simpleBodyMaxStack(byte[] body) {
        int depth = 0;
        int max = 0;
        for (byte b : body) {
            int op = b & 0xFF;
            if (op == 0x01 || (op >= 0x03 && op <= 0x08)) { // aconst_null, iconst_m1..iconst_5
                depth++;
            } else if (op == 0xAC || op == 0xB0 || op == 0xB1) { // ireturn/areturn/arreturn
                depth = 0;
            }
            max = Math.max(max, depth);
        }
        return max;
    }

    /** Человекочитаемое описание базйткода для лога. */
    private static String describeBody(byte[] body) {
        StringBuilder sb = new StringBuilder();
        for (byte b : body) {
            int op = b & 0xFF;
            if (op == 0xB1) {
                sb.append("return; ");
            } else if (op == 0x04) {
                sb.append("1 ");
            } else if (op == 0x03) {
                sb.append("0 ");
            } else if (op == 0x01) {
                sb.append("null ");
            } else if (op == 0xAC) {
                sb.append("iret; ");
            } else if (op == 0xB0) {
                sb.append("aret; ");
            } else {
                sb.append("0x").append(Integer.toHexString(op)).append(' ');
            }
        }
        return sb.toString().trim();
    }

    /** Число слотов локальных переменных, занимаемых параметрами (long/double = 2 слота). */
    private static int localSlotsForDescriptor(String descriptor) {
        int open = descriptor.indexOf('(');
        int close = descriptor.lastIndexOf(')');
        if (open < 0 || close < 0) {
            return 0;
        }
        String params = descriptor.substring(open + 1, close);
        int slots = 0;
        for (int i = 0; i < params.length(); ) {
            char c = params.charAt(i);
            if (c == 'L') {
                slots++;
                i = params.indexOf(';', i) + 1;
                if (i == 0) {
                    break;
                }
            } else if (c == '[') {
                slots++;
                int j = i;
                while (j < params.length() && params.charAt(j) == '[') {
                    j++;
                }
                if (j < params.length() && params.charAt(j) == 'L') {
                    j = params.indexOf(';', j);
                }
                i = (j < 0) ? params.length() : j + 1;
            } else if (c == 'J' || c == 'D') { // long / double
                slots += 2;
                i++;
            } else {
                slots++;
                i++;
            }
        }
        return slots;
    }

    /** Записывает новое тело (Code attribute) в метод. */
    private static final class JvmBody {
        static void write(CtMethod m, byte[] code, int maxStack, int maxLocals) {
            MethodInfo mi = m.getMethodInfo();
            CodeAttribute ca = mi.getCodeAttribute();
            if (ca == null) {
                throw new IllegalStateException("no CodeAttribute on " + m.getName());
            }
            // Старый CodeAttribute.set(byte[]) в javassist не поддерживается,
            // поэтому собираем новый CodeAttribute с телом нужной длины.
            javassist.bytecode.ExceptionTable et = ca.getExceptionTable();
            CodeAttribute nca = new CodeAttribute(mi.getConstPool(), maxStack, maxLocals, code, et);
            mi.setCodeAttribute(nca);
        }
    }

    /**
     * Тип возврата из дескриптора вида "(...)Z", "(...)V", "(...)Lnet/...;" и т.п.
     * У примитивов это одна буква (V,Z,I,J,S,B,C,D,F), у ссылок — "L...;" (всегда объект).
     */
    private static String jvmReturnType(String descriptor) {
        int close = descriptor.lastIndexOf(')');
        if (close < 0) {
            return "";
        }
        String rest = descriptor.substring(close + 1);
        if (rest.isEmpty()) {
            return "";
        }
        char c = rest.charAt(0);
        if ("VZIJSDCB".indexOf(c) >= 0) {
            return String.valueOf(c);
        }
        return rest; // "L...;" или "[..." — в любом случае не примитив -> return null
    }

    // ====================================================================
    //  Спец-патч: нейтрализация abort-условия в LaunchViewModel (строка 836)
    // ====================================================================

    /**
     * 15 байт guard-блока условия abort «pre-launch FAILED» в ТЕКУЩЕЙ версии
     * Launcher.jar (pc 1324..1338 метода invokeSuspend):
     *   iload 10; ifne T1; iload 12; ifeq T1; iload 14; ifeq T2
     * где T1 = 1339 (abort-блок), T2 = 1660 (продолжение запуска).
     * Смещения относительные (pc + u2), поэтому байты стабильны для этой сборки.
     */
    private static final byte[] GUARD_BYTES = new byte[]{
            (byte) 0x15, 0x0A, (byte) 0x9A, 0x00, 0x0D,
            (byte) 0x15, 0x0C, (byte) 0x99, 0x00, 0x08,
            (byte) 0x15, 0x0E, (byte) 0x99, 0x01, 0x44};

    /** Последнее сообщение raw-патча (для лога apply-а). */
    private static String lastRawChange = "";

    /**
     * Правка abort-guard'а НАПРЯМУЮ в байтах classfile, без перекодирования:
     * те же 15 байт заменяются на `iconst_0; ifeq T2; nop x11`, поэтому
     * ветвление ВСЕГДА уходит в T2 (продолжение запуска), abort-блок недостижим.
     *
     * Все остальные байты classfile — включая Kotlin-атрибут StackMapTable
     * (73 фрейма suspend-lambda) — остаются НЕТРОНУТЫМИ: длины не меняются,
     * смещения фреймов валидны, стек-эффекты в точке входа T2 совпадают
     * с исходными (одно значение int до ifeq, пустой стек после).
     *
     * @return изменённый buffer или null, если guard не найден/форма не сошлась.
     */
    private static byte[] rawNeutralizeAbortGuard(byte[] buf) {
        int s = indexOf(buf, GUARD_BYTES);
        if (s < 0) {
            return null;
        }
        // сразу за guard'ом должен начинаться abort-блок: aload_0 getfield
        if (s + 17 > buf.length || (buf[s + 15] & 0xFF) != 0x2A || (buf[s + 16] & 0xFF) != 0xB4) {
            return null;
        }
        // T2 из последнего ifeq: target = pc(ifeq @ s+12) + u2
        int t2 = (s + 12) + (((buf[s + 13] & 0xFF) << 8) | (buf[s + 14] & 0xFF));
        int branchPc = s + 1; // сюда ставим ifeq
        int off = t2 - branchPc;
        if (off < 0 || off > 0xFFFF) {
            return null;
        }
        byte[] out = buf.clone();
        out[s] = 0x03;                    // iconst_0
        out[s + 1] = (byte) 0x99;         // ifeq (срабатывает: 0 == 0)
        out[s + 2] = (byte) (off >>> 8);  // target: pc + u2
        out[s + 3] = (byte) off;
        for (int i = s + 4; i < s + 15; i++) {
            out[i] = 0x00;                // nop
        }
        lastRawChange = "abort-guard(line 836) neutralized @buf+" + s
                + " (ifeq target " + t2 + ") -> always branch, StackMapTable preserved";
        return out;
    }

    /** Поиск первой позиции подпоследовательности hayneedle в haystack. */
    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /**
     * Ищет в методе invokeSuspend класса e$d guard-блок условия abort:
     *
     *   iload 10; ifne T1; iload 12; ifeq T1; iload 14; ifeq T2;  <ABORT-блок: "pre-launch FAILED ...">
     *
     * (pc 1324..1338 в текущей версии Launcher.jar) и заменяет 15 байт на
     *
     *   iconst_0; ifeq T2; nop x11
     *
     * Теперь ветвление ВСЕГДА уходит в T2 — продолжение запуска, abort-блок недостижим.
     * Стековая карта не меняется: во всех точках входа в T2 и в fall-through ровно один
     * int (результат сравнения), поэтому байткод проходит верификацию JVM.
     */
    private static boolean neutralizeAbortGuard(CtClass cc, StringBuilder changes) throws Exception {
        CtMethod m = null;
        for (CtMethod mm : cc.getDeclaredMethods()) {
            if ("invokeSuspend".equals(mm.getName())) {
                m = mm;
                break;
            }
        }
        if (m == null) {
            changes.append("!!no invokeSuspend | ");
            return false;
        }
        MethodInfo mi = m.getMethodInfo();
        CodeAttribute ca = mi.getCodeAttribute();
        byte[] code = ca.getCode();

        // 1) константа маркера abort-блока
        ConstPool cp = mi.getConstPool();
        final String marker = "pre-launch FAILED";
        int cpi = -1;
        for (int i = 1; i < cp.getSize(); i++) {
            try {
                String s = cp.getStringInfo(i);
                if (s != null && s.contains(marker)) {
                    cpi = i;
                    break;
                }
            } catch (Exception ignore) {
                // не строковая константа
            }
        }
        if (cpi < 0) {
            changes.append("!!abort marker not found | ");
            return false;
        }

        // 2) pc инструкции ldc <cpi>.
        // В текущей сборке лаунчера (Java 21, обфускация yGuard) ldc закодирован
        // как 0x12 + u1 index (2 байта); в стандартном класcфайле — 0x12 + u2 (3 байта).
        // Поддерживаем обе формы.
        int ldcPc = findLdc(code, cpi);
        if (ldcPc < 0) {
            changes.append("!!ldc not found | ");
            return false;
        }

        // 3) начало abort-блока: aload_0 (0x2A) getfield (0xB4) invokestatic (0xB8) ldc
        int aPc = ldcPc - 7;
        if (aPc < 0 || (code[aPc] & 0xFF) != 0x2A || (code[aPc + 1] & 0xFF) != 0xB4) {
            changes.append("!!abort block shape mismatch | ");
            return false;
        }

        // 4) последний условный переход guard'а: за 3 байта до блока (ifeq=0x99 / ifne=0x9A)
        int g = aPc - 3;
        int op = code[g] & 0xFF;
        if (op != 0x99 && op != 0x9A) {
            changes.append("!!no guard branch | ");
            return false;
        }
        int t2 = g + (((code[g + 1] & 0xFF) << 8) | (code[g + 2] & 0xFF));

        // 5) контроль формы 15-байтового guard'а текущей версии лаунчера
        int s = aPc - 15;
        if (s < 0
                || (code[s] & 0xFF) != 0x15        // iload 10
                || (code[s + 2] & 0xFF) != 0x9A    // ifne
                || (code[s + 5] & 0xFF) != 0x15    // iload 12
                || (code[s + 7] & 0xFF) != 0x99    // ifeq
                || (code[s + 10] & 0xFF) != 0x15   // iload 14
                || (code[s + 12] & 0xFF) != 0x99) { // ifeq
            changes.append("!!guard shape mismatch, skipping | ");
            return false;
        }

        // 6) перезапись ровно 15 байт: iconst_0(0x03) ifeq(0x99) <u2 offset> nop*11
        //    target = pc + offset, ifeq срабатывает при 0==0 -> ВСЕГДА переход в T2
        int branchPc = s + 1;
        int offset = t2 - branchPc;
        code[s] = 0x03;
        code[s + 1] = (byte) 0x99;
        code[s + 2] = (byte) (offset >>> 8);
        code[s + 3] = (byte) offset;
        for (int i = s + 4; i < aPc; i++) {
            code[i] = 0x00; // nop
        }
        // CodeAttribute.set(byte[]) в javassist не поддерживается, поэтому
        // переустанавливаем полностью новый CodeAttribute с патченными байтами.
        javassist.bytecode.ExceptionTable et = ca.getExceptionTable();
        CodeAttribute nca = new CodeAttribute(mi.getConstPool(), ca.getMaxStack(), ca.getMaxLocals(), code, et);
        mi.setCodeAttribute(nca);
        changes.append("abort-guard(line 836) neutralized @pc ").append(s)
               .append(" -> always branch to ").append(t2).append(" | ");
        return true;
    }

    /**
     * Поиск pc инструкции ldc (0x12), загружающей константу cpi.
     * Учитываются двухбайтовая (u1 index) и стандартная (u2 index) формы.
     */
    private static int findLdc(byte[] code, int cpi) {
        int lo = cpi & 0xFF;
        int hi = (cpi >>> 8) & 0xFF;
        for (int i = 0; i < code.length; i++) {
            if ((code[i] & 0xFF) != 0x12) {
                continue;
            }
            if ((code[i + 1] & 0xFF) == lo && hi == 0) {
                return i; // 2-байтовая форма: 0x12 + u1
            }
            if (i + 2 < code.length && (code[i + 1] & 0xFF) == hi && (code[i + 2] & 0xFF) == lo) {
                return i; // стандартная форма: 0x12 + u2
            }
        }
        return -1;
    }

    // ====================================================================

    /** Список патчей для класса (используется тестами). */
    public static List<Patch> targets(String className) {
        List<Patch> l = BY_CLASS.get(className);
        return l != null ? l : java.util.Collections.emptyList();
    }

    private static Map<String, List<Patch>> buildIndex() {
        Map<String, List<Patch>> m = new LinkedHashMap<>();
        for (Patch p : PATCHES) {
            m.computeIfAbsent(p.className, k -> new ArrayList<>()).add(p);
        }
        return m;
    }

    private static void log(String msg) {
        System.out.println("[LauncherAgent] " + msg);
        System.out.flush();
    }
}