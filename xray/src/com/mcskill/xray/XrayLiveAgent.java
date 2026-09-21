package com.mcskill.xray;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;

import javassist.ByteArrayClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;

/**
 * Live-агент: патчит УЖЕ загруженные RenderBlocks / Block через retransform.
 * Аргументы:
 *   "probe"          — только напечатать найденные имена классов/методов,
 *   <anything-else>  — патч + retransform.
 */
public class XrayLiveAgent {

    private static final String RB = "net.minecraft.client.renderer.RenderBlocks";
    private static final String BLK = "net.minecraft.block.Block";
    private static final String BLKS = "net.minecraft.init.Blocks";

    public static void agentmain(String args, Instrumentation inst) throws Exception {
        boolean probe = args != null && args.contains("probe");
        System.out.println("[XrayLive] attaching, probe=" + probe);

        ClassFileTransformer t = new ClassFileTransformer() {
            @Override
            public byte[] transform(Module m, ClassLoader loader, String internalName,
                                    Class<?> classBeingRedefined, ProtectionDomain pd, byte[] b) {
                if (b == null || internalName == null) {
                    return null;
                }
                String n = internalName.replace('/', '.');
                try {
                    if (RB.equals(n)) {
                        byte[] out = patchRenderBlocks(b, loader);
                        System.out.println("[XrayLive] patched RenderBlocks " + (out != null));
                        return out;
                    }
                    if (BLK.equals(n)) {
                        byte[] out = patchBlock(b, loader);
                        System.out.println("[XrayLive] patched Block " + (out != null));
                        return out;
                    }
                } catch (Throwable t2) {
                    System.out.println("[XrayLive] ERROR " + n + ": " + t2);
                    t2.printStackTrace(System.out);
                }
                return null;
            }
        };
        inst.addTransformer(t, true);

        System.out.println("[XrayLive] loaded:" + probeLoaded(inst));

        if (!probe) {
            retransform(inst, RB);
            retransform(inst, BLK);
            System.out.println("[XrayLive] retransform done");
        }
    }

    private static void retransform(Instrumentation inst, String name) throws Exception {
        Class<?> c = Class.forName(name, false, ClassLoader.getSystemClassLoader());
        System.out.println("[XrayLive] retransform " + name + " (" + c.getClassLoader() + ")");
        inst.retransformClasses(c);
    }

    private static String probeLoaded(Instrumentation inst) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> c : inst.getAllLoadedClasses()) {
            String n = c.getName();
            if (n.equals(RB) || n.equals(BLK) || n.equals(BLKS)) {
                sb.append(" ").append(n).append("(x").append(countMethods(c)).append(")");
            }
        }
        return sb.length() == 0 ? " NONE-OF-CLASSES-FOUND" : sb.toString();
    }

    private static int countMethods(Class<?> c) {
        return c.getDeclaredMethods().length;
    }

    // ---- патчи (MCP-имена; рантайм-деобф ФМЛа) ----

    private static byte[] patchRenderBlocks(byte[] data, ClassLoader loader) throws Exception {
        ClassPool pool = new ClassPool();
        pool.insertClassPath(new ByteArrayClassPath(RB, data));
        if (loader != null) {
            pool.insertClassPath(new LoaderClassPath(loader));
        }
        CtClass cc = pool.get(RB);
        System.out.println("[XrayLive] RenderBlocks methods: "
                + Arrays.toString(Arrays.stream(cc.getDeclaredMethods())
                        .map(m -> m.getName() + m.getMethodInfo().getDescriptor())
                        .filter(s -> s.contains("render") || s.contains("III"))
                        .toArray()));
        boolean hit = false;
        for (CtMethod m : cc.getDeclaredMethods()) {
            if (m.getName().equals("renderBlockByRenderType")
                    && m.getMethodInfo().getDescriptor().equals("(Lnet/minecraft/block/Block;III)Z")) {
                m.insertBefore("if ($1 != net.minecraft.init.Blocks.oreIron) return false;");
                hit = true;
            }
        }
        System.out.println("[XrayLive] renderBlockByRenderType patched: " + hit);
        byte[] out = cc.toBytecode();
        cc.detach();
        return out;
    }

    private static byte[] patchBlock(byte[] data, ClassLoader loader) throws Exception {
        ClassPool pool = new ClassPool();
        pool.insertClassPath(new ByteArrayClassPath(BLK, data));
        if (loader != null) {
            pool.insertClassPath(new LoaderClassPath(loader));
        }
        CtClass cc = pool.get(BLK);
        boolean hit = false;
        for (CtMethod m : cc.getDeclaredMethods()) {
            if (m.getName().equals("shouldSideBeRendered")
                    && m.getMethodInfo().getDescriptor().equals("(Lnet/minecraft/world/IBlockAccess;IIII)Z")) {
                m.insertBefore("return true;");
                hit = true;
            }
        }
        System.out.println("[XrayLive] shouldSideBeRendered patched: " + hit);
        byte[] out = cc.toBytecode();
        cc.detach();
        return out;
    }
}