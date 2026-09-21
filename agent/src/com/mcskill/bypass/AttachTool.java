package com.mcskill.bypass;

import java.io.File;

import com.sun.tools.attach.VirtualMachine;

/**
 * Подключается к уже запущенному JVM лаунчера и загружает агент.
 *
 * Сборка:  javac --add-modules jdk.attach -d out .\AttachTool.java
 * Запуск:  java --add-modules jdk.attach -cp out com.mcskill.bypass.AttachTool <pid> <путь до agent.jar> [agentArgs]
 *
 * Примечание для JDK 21+: динамическая загрузка агентов разрешена, только если
 * целевой JVM был запущен с -XX:+EnableDynamicAgentLoading (иначе attach упадёт
 * в loadAgent). Лаунчер в debugMode attach НЕ запрещает (DisableAttachMechanism
 * не выставляется), но сам флаг динамической загрузки ему всё равно нужен.
 */
public final class AttachTool {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: AttachTool <pid> <agent.jar> [agentArgs]");
            System.err.println("  pid      — PID JVM лаунчера (можно найти через jps)");
            System.err.println("  agent.jar— абсолютный путь до собранного агента");
            System.exit(2);
        }
        String pid = args[0];
        String agent = new File(args[1]).getAbsolutePath();

        VirtualMachine vm = VirtualMachine.attach(pid);
        try {
            try {
                if (args.length > 2) {
                    vm.loadAgent(agent, args[2]);
                } else {
                    vm.loadAgent(agent);
                }
            } catch (Exception e) {
                System.err.println("loadAgent failed: " + e);
                System.err.println("Проверяем: целевой JVM должен быть запущен с "
                        + "-XX:+EnableDynamicAgentLoading (см. README/settings).");
                throw e;
            }
            System.out.println("agent loaded into pid " + pid + " (" + agent + ")");
        } finally {
            vm.detach();
            System.out.println("detached");
        }
    }
}