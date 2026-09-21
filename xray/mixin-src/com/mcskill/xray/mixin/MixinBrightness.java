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
 * Block.getShadeBrightness (рантайм: func_149677_c) возвращает свет 0-15:
 *   всегда 15 = весь мир освещён максимально.
 * Block.getAmbientShade (рантайм: func_149685_I) возвращает множитель граней
 *   (1.0 / 0.2): всегда 1.0 = без тёмных граней.
 * Работает, пока State.fullbright == true.
 */
@Mixin(Block.class)
public abstract class MixinBrightness {

    @Inject(method = "func_149677_c", at = @At("HEAD"), cancellable = true)
    private void mcskillxray$maxLight(IBlockAccess world, int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        if (State.fullbright) {
            cir.setReturnValue(Integer.valueOf(15));
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