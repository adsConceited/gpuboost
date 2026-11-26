package dev.vconceited.gpuboost.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.vconceited.gpuboost.client.gpu.GlTimer;

@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlSurface")
public abstract class GlSurfaceMixin {
	@Inject(method = "present", at = @At("HEAD"))
	private static void gpuboost$beforePresent(CallbackInfo ci) {
		GlTimer.beforePresent();
	}

	@Inject(method = "present", at = @At("RETURN"))
	private static void gpuboost$afterPresent(CallbackInfo ci) {
		GlTimer.afterPresent();
	}
}
