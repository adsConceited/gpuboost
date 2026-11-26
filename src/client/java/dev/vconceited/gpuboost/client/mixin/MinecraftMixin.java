package dev.vconceited.gpuboost.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.vconceited.gpuboost.client.GpuBoostRuntime;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void gpuboost$onFrameStart(boolean advanceGameTime, CallbackInfo ci) {
		GpuBoostRuntime.onRenderFrameStart();
	}
}
