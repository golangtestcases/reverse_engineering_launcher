package com.mcskill.xray.mixin;

import com.mcskill.xray.RenderState;

import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Доп. механизм мгновенного применения: пока RenderState.pendingFrames() > 0,
 * каждый чанк, который рендерный цикл обрабатывает в этот кадр, помечается
 * "грязным" (WorldRenderer.e() = func_78914_f, ставит q=true) - этот же кадр
 * RenderBlocks перестроит блоки с новым State.xray/fullbright.
 */
@Mixin(WorldRenderer.class)
public abstract class MixinForceChunk {

    @Inject(method = "func_147892_a", at = @At("HEAD"))
    private void mcskillxray$forceChunk(EntityLivingBase living, CallbackInfo callback) {
        if (RenderState.pendingFrames() > 0) {
            if (RenderState.pendingFrames() == 8) {
                System.out.println("[mcskillxray] forcechunk window open");
            }
            ((WorldRenderer) (Object) this).func_78914_f();
        }
    }
}