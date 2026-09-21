package com.mcskill.xray.mixin;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

import com.mcskill.xray.State;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fullbright (1.7.10).
 * Block.getMixedBrightnessForBlock (рантайм: func_149677_c) возвращает
 * УПАКОВАННУЮ яркость: (sky << 20) | (block << 4), где sky и block - по 0..15.
 * Возврат (15 << 20) | (15 << 4) = максимум и неба, и блочного света => световая
 * текстура (lightmap) всегда берётся из самого яркого пикселя.
 * ВАЖНО: нельзя возвращать голое 15 - это (0<<20)|(0<<4) = полная темнота.
 * Block.getAmbientShade (рантайм: func_149685_I) возвращает множитель граней
 *   (1.0 / 0.2): всегда 1.0 = без тёмных граней.
 * Работает, пока State.fullbright == true.
 */
@Mixin(Block.class)
public abstract class MixinBrightness {

    private static final int FULL_BRIGHT = (15 << 20) | (15 << 4);

    @Inject(method = "func_149677_c", at = @At("HEAD"), cancellable = true)
    private void mcskillxray$maxLight(IBlockAccess world, int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        if (State.fullbright) {
            cir.setReturnValue(Integer.valueOf(FULL_BRIGHT));
            cir.cancel();
        }
    }

    @Inject(method = "func_149685_I", at = @At("HEAD"), cancellable = true)
    private void mcskillxray$maxAmbient(CallbackInfoReturnable<Float> cir) {
        if (State.fullbright) {
            cir.setReturnValue(Float.valueOf(1.0f));
            cir.cancel();
        }
    }
}