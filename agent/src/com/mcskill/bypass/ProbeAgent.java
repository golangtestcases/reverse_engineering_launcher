package com.mcskill.bypass;

import java.lang.instrument.Instrumentation;

/**
 * Диагностический агент: печатает, какие целевые классы уже загружены в JVM,
 * и (если передан аргумент) принудительно грузит указанный класс через
 * Class.forName — это триггерит transformer основного агента в load-time.
 */
public final class ProbeAgent {

    private static final String[] TARGETS = {
            "net.mcsgroup.launcher.core.i.d",
            "net.mcsgroup.launcher.core.e.b",
            "net.mcsgroup.launcher.core.e.e$d",
    };

    public static void agentmain(String args, Instrumentation inst) {
        String force = args == null ? "" : args.trim();
        try {
            System.out.println("[ProbeAgent] targets loaded before: " + status(inst));
            System.out.flush();
            if (!force.isEmpty()) {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl == null) {
                    cl = ClassLoader.getSystemClassLoader();
                }
                try {
                    Class.forName(force, false, cl);
                    System.out.println("[ProbeAgent] forced load requested: " + force);
                } catch (Throwable t) {
                    System.out.println("[ProbeAgent] forced load of " + force + " FAILED: " + t);
                }
                System.out.flush();
                System.out.println("[ProbeAgent] targets loaded after:  " + status(inst));
                System.out.flush();
            }
        } catch (Throwable t) {
            System.out.println("[ProbeAgent] error: " + t);
            System.out.flush();
        }
    }

    private static String status(Instrumentation inst) {
        StringBuilder sb = new StringBuilder();
        for (String n : TARGETS) {
            sb.append(n).append('=').append(isLoaded(inst, n)).append(' ');
        }
        return sb.toString().trim();
    }

    private static boolean isLoaded(Instrumentation inst, String name) {
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c != null && name.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }
}