package com.mcskill.e2e;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * E2E-хост: имитирует запущенный лаунчер. Класс net.mcsgroup.launcher.core.i.d
 * загружен ДО attach (retransform) и имеет метод a(ClientProfile) -> false.
 * После attach ожидаем true.
 */
public final class HostStub {
    public static void main(String[] args) throws Exception {
        String pidFile = args.length > 0 ? args[0] : "host.pid";
        Files.writeString(Path.of(pidFile), String.valueOf(ProcessHandle.current().pid()));
        net.mcsgroup.launcher.proto.ClientProfile p = new net.mcsgroup.launcher.proto.ClientProfile();
        net.mcsgroup.launcher.core.i.d verifier = new net.mcsgroup.launcher.core.i.d();
        // класс уже загружен до attach -> пойдёт retransform
        for (int i = 0; i < 120; i++) {
            System.out.println("verify -> " + verifier.a(p));
            System.out.flush();
            Thread.sleep(1000);
        }
    }
}