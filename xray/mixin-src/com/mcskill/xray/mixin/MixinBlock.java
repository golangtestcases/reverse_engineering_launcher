package com.mcskill.xray.mixin;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

import com.mcskill.xray.State;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block.shouldSideBeRendered (рантайм: func_149646_a).
 * При включённом x-ray все 6 граней блоков рисуются всегда => замурованные
 * в камне грани руды тоже отрисовываются (руда видна изнутри толщи камня).
 */
@Mixin(Block.class)
public abstract class MixinBlock {

    @Inject(method = "func_149646_a", at = @At("HEAD"), cancellable = true)
    private void mcskillxray$allSides(IBlockAccess world, int x, int y, int z, int side, CallbackInfoReturnable<Boolean> cir) {
        if (State.xray) {
            cir.setReturnValue(Boolean.TRUE);
            cir.cancel();
        }
    }
}