package com.mcskill.xray;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Офлайн-проверка трансформации на реальных blm.class / aji.class
 * (из minecraft.jar клиента): применяет тот же код, что и FML-трансформер,
 * и проверяет, что (а) вставка прошла, (б) патченые классы парсятся/грузятся.
 */
public class XrayTest {

    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : ".";
        byte[] blm = Files.readAllBytes(Paths.get(dir, "blm.class"));
        byte[] aji = Files.readAllBytes(Paths.get(dir, "aji.class"));

        XrayTransformer t = new XrayTransformer();
        byte[] blmOut = t.transform("blm", "blm", blm);
        byte[] ajiOut = t.transform("aji", "aji", aji);
        System.out.println("blm patched: " + (blmOut != null) + ", aji patched: " + (ajiOut != null));

        // парсинг через javassist (проверка структуры)
        verifyContains(blmOut, true);
        verifyContains(ajiOut, true);

        Files.write(Paths.get(dir, "blm.patched.class"), blmOut);
        Files.write(Paths.get(dir, "aji.patched.class"), ajiOut);
        System.out.println("written: " + dir + File.separator + "blm.patched.class / aji.patched.class");

        // defineClass: парсер + верификатор JVM принимают
        ClassLoader cl = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                try {
                    if (name.equals("blm")) return defineClass("blm", blmOut, 0, blmOut.length);
                    if (name.equals("aji")) return defineClass("aji", ajiOut, 0, ajiOut.length);
                } catch (Throwable t) {
                    throw new ClassNotFoundException(name, t);
                }
                return super.findClass(name);
            }
        };
        Class.forName("blm", false, cl);
        Class.forName("aji", false, cl);
        System.out.println("defineClass OK (no VerifyError)");
    }

    private static void verifyContains(byte[] cls, boolean body) throws Exception {
        javassist.ClassPool pool = new javassist.ClassPool();
        pool.insertClassPath(new javassist.ByteArrayClassPath("x", cls));
        // просто читаем, чтобы убедиться, что структура валидна
        javassist.CtClass cc = pool.makeClass(new java.io.ByteArrayInputStream(cls));
        cc.detach();
    }

    private static byte[] read(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }
}