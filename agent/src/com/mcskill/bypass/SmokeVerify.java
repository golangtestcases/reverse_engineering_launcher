package com.mcskill.bypass;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Триггерит верификацию invokeSuspend РЕАЛЬНЫМ исполнением.
 *  Инстанс создаётся через Unsafe.allocateInstance (без вызова ctor),
 *  поле j (getfield в prologue) = 0 -> tableswitch case 0 -> throwOnFailure(Object)
 *  => ожидается ClassCastException (метод исполнился, verify ПРОШЁЛ).
 *  VerifyError/ClassFormatError бросаются ДО исполнения — виден их класс. */
public final class SmokeVerify {
    public static void main(String[] args) throws Exception {
        String test = args.length > 0 ? args[0] : "e$d";
        Class<?> cls;
        String methodName;
        if ("e$d".equals(test)) {
            cls = Class.forName("net.mcsgroup.launcher.core.e.e$d");
            methodName = "invokeSuspend";
        } else if ("d".equals(test)) {
            cls = Class.forName("net.mcsgroup.launcher.core.i.d");
            methodName = "a";
        } else if ("b".equals(test)) {
            cls = Class.forName("net.mcsgroup.launcher.core.e.b");
            methodName = "a";
        } else {
            throw new IllegalArgumentException(test);
        }
        Method mtd = null;
        for (Method m : cls.getDeclaredMethods()) {
            if (!m.getName().equals(methodName)) {
                continue;
            }
            if ("b".equals(test)) {
                if (m.getParameterCount() == 1
                        && m.getParameterTypes()[0].getName().contains("kotlin.jvm.functions.Function")) {
                    mtd = m;
                    break;
                }
            } else if (m.getParameterCount() == 1) {
                mtd = m;
                break;
            }
        }
        if (mtd == null) {
            System.out.println("method not found");
            return;
        }
        mtd.setAccessible(true);
        Object inst = tryConstruct(cls);
        try {
            boolean isB = "b".equals(test);
            Object[] margs;
            if (isB) {
                margs = new Object[]{ null }; // scheduleKill(Function0) — тоже патчится
            } else {
                margs = new Object[]{ "e$d".equals(test) ? new Object() : null };
            }
            mtd.invoke(inst, margs);
            System.out.println("OK: returned normally");
        } catch (Throwable t) {
            Throwable c = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null
                    ? t.getCause() : t;
            System.out.println("result: " + c.getClass().getName() + (c.getMessage() != null ? ": " + c.getMessage() : ""));
        }
    }

    private static Object tryConstruct(Class<?> cls) throws Exception {
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            try {
                c.setAccessible(true);
                Object[] args = new Object[c.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    Class<?> p = c.getParameterTypes()[i];
                    if (p == boolean.class) args[i] = false;
                    else if (p == int.class) args[i] = 0;
                    else if (p == long.class) args[i] = 0L;
                    else if (p == double.class) args[i] = 0.0;
                    else if (!p.isPrimitive()) args[i] = null;
                }
                return c.newInstance(args);
            } catch (Throwable ignore) {
                // пробуем следующий
            }
        }
        // fallback: Unsafe.allocateInstance
        Class<?> unsafeCls = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field f = unsafeCls.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object unsafe = f.get(null);
        Method allocate = unsafeCls.getMethod("allocateInstance", Class.class);
        return allocate.invoke(unsafe, cls);
    }
}