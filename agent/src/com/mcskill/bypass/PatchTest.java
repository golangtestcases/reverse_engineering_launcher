package com.mcskill.bypass;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Офлайн-проверка агента БЕЗ attach: читает настоящие классы из Launcher.jar,
 * прогоняет через ту же логику патчинга (LauncherAgent.apply) и грузит результат
 * через ClassLoader.defineClass — JVM при этом выполняет полную верификацию байткода.
 *
 * Сборка:  javac -cp <lib>\javassist-3.33.0-GA.jar -d test-out src\com\mcskill\bypass\PatchTest.java
 * Запуск:  java -cp <lib>\javassist-3.33.0-GA.jar;test-out;<путь>\Launcher.jar com.mcskill.bypass.PatchTest \
 *              <dir c содержимым i\d.class, e\b.class, e\e$d.class>
 *   (java-зависимости (kotlin и т.п.) берутся из Launcher.jar, поэтому его кладём в -cp)
 */
public final class PatchTest {

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : ".");
        Path out = root.resolve("patched");
        Files.createDirectories(out);

        test("net.mcsgroup.launcher.core.i.d", root.resolve("i/d.class"), out);
        test("net.mcsgroup.launcher.core.e.b", root.resolve("e/b.class"), out);
        test("net.mcsgroup.launcher.core.e.e$d", root.resolve("e/e$d.class"), out);
    }

    private static void test(String binName, Path in, Path outDir) throws Exception {
        byte[] original = Files.readAllBytes(in);
        byte[] patched = LauncherAgent.apply(LauncherAgent.targets(binName), binName, original);
        if (patched == null) {
            System.out.println("FAIL(transform null): " + binName);
            return;
        }
        String fileName = binName.replace('$', '_') + ".class";
        Path pOut = outDir.resolve(fileName);
        Files.write(pOut, patched);

        // defineClass -> JVM парсит и ВЕРИФИЦИРУЕТ патченный байткод
        ClassLoader cl = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals(binName)) {
                    return defineClass(binName, patched, 0, patched.length);
                }
                return super.findClass(name);
            }
        };
        try {
            Class<?> c = cl.loadClass(binName);
            System.out.println("OK   " + binName + "  (" + pOut.getFileName() + ", " + patched.length + " bytes) -> " + c);
        } catch (Throwable t) {
            System.out.println("VERIFY-FAIL " + binName + ": " + t);
            t.printStackTrace(System.out);
        }
    }
}