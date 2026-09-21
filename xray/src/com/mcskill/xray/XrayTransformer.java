package com.mcskill.xray;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javassist.ByteArrayClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import net.minecraft.launchwrapper.IClassTransformer;

/**
 * X-Ray for Minecraft 1.7.10.
 *
 * FML runtime deobf: vanilla classes are loaded under MCP names,
 * methods/fields under SRG names (func / field). The transformer
 * order is not guaranteed, so each patch accepts ALL namespace forms
 * (notch / MCP / SRG): we patch whatever form arrives, and insert
 * references in the same form (remaining transformers, including
 * DeobfuscationTransformer, rename as needed).
 *
 * Patch 1 - RenderBlocks.renderBlockByRenderType(Block,int,int,int)Z:
 *   if (block != Blocks.oreIron) return false;
 *   => iron ore is drawn, all other blocks become transparent.
 *
 * Patch 2 - Block.shouldSideBeRendered(IBlockAccess,int,int,int,int)Z:
 *   return true;
 *   => all 6 faces of ores are drawn, including ones embedded in stone.
 */
public class XrayTransformer implements IClassTransformer {

    // нотч-имена (внутри minecraft.jar)
    private static final String RB_NOTCH = "blm";
    private static final String BLK_NOTCH = "aji";
    private static final String BLOCKS_NOTCH = "ajn";
    // MCP-имена (как грузит ФМЛ-деобф)
    private static final String RB_MCP = "net/minecraft/client/renderer/RenderBlocks";
    private static final String BLK_MCP = "net/minecraft/block/Block";
    private static final String BLOCKS_MCP = "net.minecraft.init.Blocks";
    private static final String IB_MCP = "net.minecraft.world.IBlockAccess";

    private static volatile boolean patchedRenderBlocks = false;
    private static volatile boolean patchedBlock = false;

    @Override
    public byte[] transform(String name, String transformedName, byte[] classData) {
        if (classData == null || classData.length == 0) {
            return null;
        }
        try {
            boolean rb = name.endsWith(RB_NOTCH) || name.endsWith(RB_MCP)
                    || transformedName != null && (transformedName.endsWith(RB_NOTCH) || transformedName.endsWith(RB_MCP));
            boolean blk = name.endsWith(BLK_NOTCH) || name.endsWith(BLK_MCP)
                    || transformedName != null && (transformedName.endsWith(BLK_NOTCH) || transformedName.endsWith(BLK_MCP));
            if (rb) {
                byte[] out = patchRenderBlocks(classData, name);
                if (out != null) {
                    patchedRenderBlocks = true;
                    System.out.println("[McSkillXray] patched RenderBlocks (wanted-as " + name + "): non-ore blocks => transparent");
                }
                return out == null ? classData : out;
            }
            if (blk) {
                byte[] out = patchBlock(classData, name);
                if (out != null) {
                    patchedBlock = true;
                    System.out.println("[McSkillXray] patched Block (wanted-as " + name + "): shouldSideBeRendered => true");
                }
                return out == null ? classData : out;
            }
        } catch (Throwable t) {
            System.out.println("[McSkillXray] ERROR transforming " + name + ": " + t);
            t.printStackTrace(System.out);
        }
        return null;
    }

    private byte[] patchRenderBlocks(byte[] data, String served) throws Exception {
        ClassPool pool = newPool(data, served);
        CtClass cc = pool.get(served);
        CtMethod m = null;
        boolean notch = false;
        for (CtMethod mm : cc.getDeclaredMethods()) {
            String d = mm.getMethodInfo().getDescriptor();
            String n = mm.getName();
            if (isRenderByTypeName(n, d)) {
                m = mm;
                notch = n.equals("b");
                break;
            }
        }
        if (m == null) {
            System.out.println("[McSkillXray] renderBlockByRenderType not found in " + served
                    + "; seen=" + firstFewMethods(cc));
            cc.detach();
            return null;
        }
        // ссылку на руду вставляем в той же namespace-форме, что и метод:
        // notch -> Blocks.ajn.p, остальное -> MCP Blocks.oreIron (деобф переименует)
        String oreRef = notch ? "ajn.p" : "net.minecraft.init.Blocks.oreIron";
        m.insertBefore("if ($1 != " + oreRef + ") return false;");
        String desc = m.getMethodInfo().getDescriptor();
        byte[] out = cc.toBytecode();
        cc.detach();
        System.out.println("[McSkillXray] method " + m.getName() + desc
                + " patched (oreRef=" + oreRef + ")");
        return out;
    }

    private boolean isRenderByTypeName(String n, String d) {
        // все формы: notch b / SRG func_147805_b / MCP renderBlockByRenderType
        if (n.equals("b") && d.equals("(Laji;III)Z")) return true;
        if (n.equals("b") && d.equals("(Lnet/minecraft/block/Block;III)Z")) return true;
        if (n.equals("func_147805_b") && d.equals("(Lnet/minecraft/block/Block;III)Z")) return true;
        if (n.equals("renderBlockByRenderType") && d.equals("(Lnet/minecraft/block/Block;III)Z")) return true;
        if (n.equals("renderBlockByRenderType") && d.equals("(Laji;III)Z")) return true;
        return false;
    }

    private byte[] patchBlock(byte[] data, String served) throws Exception {
        ClassPool pool = newPool(data, served);
        CtClass cc = pool.get(served);
        String found = null;
        for (CtMethod m : cc.getDeclaredMethods()) {
            String d = m.getMethodInfo().getDescriptor();
            String n = m.getName();
            if (isShouldSideName(n, d)) {
                found = n + d;
                break;
            }
        }
        if (found == null) {
            System.out.println("[McSkillXray] shouldSideBeRendered not found in " + served
                    + "; seen=" + firstFewMethods(cc));
            cc.detach();
            return null;
        }
        for (CtMethod m : cc.getDeclaredMethods()) {
            String d = m.getMethodInfo().getDescriptor();
            String n = m.getName();
            if (isShouldSideName(n, d)) {
                m.insertBefore("return true;");
                break;
            }
        }
        byte[] out = cc.toBytecode();
        cc.detach();
        System.out.println("[McSkillXray] method " + found + " patched");
        return out;
    }

    private boolean isShouldSideName(String n, String d) {
        if (n.equals("a") && d.equals("(Lahl;IIII)Z")) return true;
        if (n.equals("a") && d.equals("(Lnet/minecraft/world/IBlockAccess;IIII)Z")) return true;
        if (n.equals("func_149646_a") && d.equals("(Lnet/minecraft/world/IBlockAccess;IIII)Z")) return true;
        if (n.equals("shouldSideBeRendered") && d.equals("(Lnet/minecraft/world/IBlockAccess;IIII)Z")) return true;
        return false;
    }

    private ClassPool newPool(byte[] self, String selfName) throws Exception {
        ClassPool pool = new ClassPool();
        pool.insertClassPath(new ByteArrayClassPath(selfName, self));
        ClassLoader cl = XrayTransformer.class.getClassLoader();
        if (cl != null) {
            pool.insertClassPath(new LoaderClassPath(cl));
            seed(pool, cl, BLOCKS_MCP);
            seed(pool, cl, BLK_MCP);
            seed(pool, cl, IB_MCP);
        }
        return pool;
    }

    private static void seed(ClassPool pool, ClassLoader cl, String name) throws Exception {
        try {
            pool.get(name);
            return;
        } catch (javassist.NotFoundException ignored) {
        }
        InputStream in = cl.getResourceAsStream(name.replace('.', '/') + ".class");
        if (in != null) {
            try {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                pool.insertClassPath(new ByteArrayClassPath(name, bos.toByteArray()));
            } finally {
                in.close();
            }
        }
    }

    private static String firstFewMethods(CtClass cc) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (CtMethod m : cc.getDeclaredMethods()) {
            if (i++ < 6) {
                sb.append(m.getName()).append(m.getMethodInfo().getDescriptor()).append(' ');
            }
        }
        return sb.append("...}").toString();
    }

    public static boolean isPatchedRenderBlocks() {
        return patchedRenderBlocks;
    }

    public static boolean isPatchedBlock() {
        return patchedBlock;
    }
}