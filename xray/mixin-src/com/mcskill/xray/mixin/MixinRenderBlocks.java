package com.mcskill.xray.mixin;

import com.mcskill.xray.State;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RenderBlocks.renderBlockByRenderType (рантайм: func_147805_b).
 * При включённом x-ray пропускаем все блоки, кроме руд (BlockOre) и древних
 * обломков (etfuturum) => камень/земля прозрачные, руды видны сквозь блоки.
 *
 * FullBright (сильный): хелперы отрисовки граней (func_147792_a /
 * func_147768_a / func_147806_b / func_147798_e) пишут цвет каждого квада из
 * 12 полей-множителей (notch aq..aB, SRG field_...). Ванильные базы
 * 0.5/0.6/0.8 + усреднение соседних I() дают максимум ~0.8 яркости — поэтому
 * "fullbright" выглядит слабо. Здесь при включённом fullbright принудительно
 * ставим все 12 множителей в 1.0 => текстура рисуется в полной яркости
 * (максимум возможного в 1.7.10).
 */
@Mixin(RenderBlocks.class)
public abstract class MixinRenderBlocks {

    // SRG-имена 12 полей-множителей (notch: aq ar as at au av aw ax ay az aA aB)
    private static final String[] SHADE_FIELDS = new String[]{
        "field_147872_ap", "field_147852_aq", "field_147850_ar", "field_147848_as",
        "field_147846_at", "field_147860_au", "field_147858_av", "field_147856_aw",
        "field_147854_ax", "field_147841_ay", "field_147839_az", "field_147833_aA"
    };
    private static volatile java.lang.reflect.Field[] shadeFields;

    @Inject(method = "func_147792_a", at = @At("HEAD"))
    private void mcskillxray$brightA(CallbackInfo callback) {
        mcskillxray$paint(this);
    }

    @Inject(method = "func_147768_a", at = @At("HEAD"))
    private void mcskillxray$brightA2(CallbackInfo callback) {
        mcskillxray$paint(this);
    }

    @Inject(method = "func_147806_b", at = @At("HEAD"))
    private void mcskillxray$brightB(CallbackInfo callback) {
        mcskillxray$paint(this);
    }

    @Inject(method = "func_147798_e", at = @At("HEAD"))
    private void mcskillxray$brightE(CallbackInfo callback) {
        mcskillxray$paint(this);
    }

    private static void mcskillxray$paint(Object self) {
        if (!State.fullbright || self == null) return;
        java.lang.reflect.Field[] arr = shadeFields;
        if (arr == null) {
            synchronized (MixinRenderBlocks.class) {
                arr = shadeFields;
                if (arr == null) {
                    java.lang.reflect.Field[] tmp = new java.lang.reflect.Field[SHADE_FIELDS.length];
                    for (int i = 0; i < SHADE_FIELDS.length; i++) {
                        try {
                            java.lang.reflect.Field f =
                                    net.minecraft.client.renderer.RenderBlocks.class
                                            .getDeclaredField(SHADE_FIELDS[i]);
                            f.setAccessible(true);
                            tmp[i] = f;
                        } catch (Exception ignored) {
                            tmp[i] = null;
                        }
                    }
                    arr = tmp;
                    shadeFields = arr;
                }
            }
        }
        for (int i = 0; i < arr.length; i++) {
            java.lang.reflect.Field f = arr[i];
            if (f == null) continue;
            try {
                f.setFloat(self, 1.0f);
            } catch (Exception ignored) {
            }
        }
    }

    @Inject(method = "func_147805_b", at = @At("HEAD"), cancellable = true)
    private void mcskillxray$skipNonOre(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (State.xray && !mcskillxray$shouldRender(block)) {
            cir.setReturnValue(Boolean.FALSE);
            cir.cancel();
        }
    }

    /** Рисовать ли блок под x-ray: это руда И она не выключена пользователем. */
    private static boolean mcskillxray$shouldRender(Block block) {
        boolean isOre;
        if (block instanceof net.minecraft.block.BlockOre) {
            isOre = true;
        } else {
            String cn = block.getClass().getName();
            if (cn.equals("ganymedes01.etfuturum.blocks.BlockAncientDebris")) {
                isOre = true;
            } else {
                int dot = cn.lastIndexOf('.');
                String simple = dot < 0 ? cn : cn.substring(dot + 1);
                isOre = simple.toLowerCase(java.util.Locale.ROOT).indexOf("ore") >= 0;
            }
        }
        if (!isOre) {
            return false;
        }
        return State.isOreEnabled(mcskillxray$oreName(block));
    }

    /** Имя руды: OreDictionary ("oreGold") либо имя класса (BlockAncientDebris). */
    private static String mcskillxray$oreName(Block block) {
        try {
            Object item = new net.minecraft.item.ItemStack(block, 1, 0);
            Class<?> od = net.minecraftforge.oredict.OreDictionary.class;
            java.lang.reflect.Method idM = od.getMethod("getOreID", item.getClass());
            Object idObj = idM.invoke(null, item);
            int id = ((Number) idObj).intValue();
            if (id >= 0) {
                java.lang.reflect.Method nmM = od.getMethod("getOreName", Integer.TYPE);
                Object name = nmM.invoke(null, Integer.valueOf(id));
                if (name instanceof String && ((String) name).length() > 0) {
                    return (String) name;
                }
            }
        } catch (Throwable ignored) {
        }
        String cn = block.getClass().getName();
        int dot = cn.lastIndexOf('.');
        return dot < 0 ? cn : cn.substring(dot + 1);
    }
}