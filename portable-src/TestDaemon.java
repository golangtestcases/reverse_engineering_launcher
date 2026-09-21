public class TestDaemon {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("McSkillBootstrap");
        java.lang.reflect.Method m = c.getDeclaredMethod("installToClients", java.io.File.class);
        m.setAccessible(true);
        m.invoke(null, new java.io.File(a[0]));
        System.out.println("installToClients done");
        m.invoke(null, new java.io.File(a[0]));
        System.out.println("second run done (should be no-op: same size/mtime)");
    }
}