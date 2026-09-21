import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Обёртка портативного лаунчера McSkill.
 *
 * 1) Запускает настоящий лаунчер (Launcher.jar) во ВЛОЖЕННОЙ JRE со всеми
 *    нужными флагами, вычисляя пути относительно собственного расположения:
 *
 *      <appdir>/Launcher.jar           - лаунчер
 *      <appdir>/launcher-agent.jar     - патч-агент (premain)
 *      <appdir>/skiko/                 - нативный рантайм skiko (QtWebEngine)
 *      <appdir>/xray-mcskill.jar       - клиентский мод (x-ray/fullbright)
 *
 * 2) Фоновый демон: пока лаунчер жив, раз в 5 секунд проверяет клиенты
 *    %USERPROFILE%\McSkill\clients\*\mods и кладёт xray-mcskill.jar, если его
 *    там нет или он устарел. Лаунчер пересоздаёт mods при CDN-синхронизации
 *    и удаляет «неожиданные» джарники - демон восстанавливает мод.
 *
 * Эквивалент команды:
 *   java -javaagent:<appdir>/launcher-agent.jar -XX:+EnableDynamicAgentLoading
 *        -Dskiko.library.path=<appdir>/skiko -jar <appdir>/Launcher.jar
 *
 * Процесс-родитель ждёт завершения лаунчера (одно окно в таскбаре).
 */
public class McSkillBootstrap {

    public static void main(String[] args) throws Exception {
        File codeSource = new File(
                McSkillBootstrap.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI());
        final Path appDir = codeSource.toPath().toAbsolutePath().getParent().normalize();

        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path javaExe = javaHome.resolve("bin").resolve("java.exe");
        Path agent = appDir.resolve("launcher-agent.jar");
        Path skiko = appDir.resolve("skiko");
        Path launcher = appDir.resolve("Launcher.jar");
        Path xray = appDir.resolve("xray-mcskill.jar");

        if (!javaExe.toFile().isFile() || !agent.toFile().isFile()
                || !launcher.toFile().isFile() || !skiko.toFile().isDirectory()) {
            System.err.println("[bootstrap] missing: " + javaExe + " | " + agent
                    + " | " + launcher + " | " + skiko);
            System.exit(2);
        }

        List<String> cmd = new ArrayList<String>();
        cmd.add(javaExe.toString());
        cmd.add("-javaagent:" + agent);
        cmd.add("-XX:+EnableDynamicAgentLoading");
        cmd.add("-Dskiko.library.path=" + skiko);
        cmd.add("-jar");
        cmd.add(launcher.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(appDir.toFile());
        pb.inheritIO();
        final Process process = pb.start();

        // Демон автокопирования xray-мода в клиенты.
        if (xray.toFile().isFile()) {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    while (process.isAlive()) {
                        installToClients(xray.toFile());
                        try {
                            Thread.sleep(5000L);
                        } catch (InterruptedException ignored) {
                            return;
                        }
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }

        int code = process.waitFor();
        System.exit(code);
    }

    private static void installToClients(File xray) {
        try {
            String home = System.getProperty("user.home");
            if (home == null) return;
            File root = Paths.get(home, "McSkill", "clients").toFile();
            if (!root.isDirectory()) return;
            File[] clients = root.listFiles();
            if (clients == null) return;
            for (File client : clients) {
                if (!client.isDirectory()) continue;
                File mods = new File(client, "mods");
                if (!mods.isDirectory()) {
                    // сам клиент ещё не скачан лаунчером - дождёмся
                    continue;
                }
                File dest = new File(mods, "xray-mcskill.jar");
                if (dest.exists() && dest.isFile()
                        && dest.length() == xray.length()
                        && dest.lastModified() >= xray.lastModified()) {
                    continue;
                }
                try {
                    Path tmp = Paths.get(dest.getAbsolutePath() + ".tmp");
                    Files.copy(Paths.get(xray.getAbsolutePath()), tmp,
                            StandardCopyOption.REPLACE_EXISTING);
                    Files.move(tmp, Paths.get(dest.getAbsolutePath()),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}