package dev.vconceited.gpuboost.client.optimize;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import dev.vconceited.gpuboost.client.compat.ModCompat;
import dev.vconceited.gpuboost.client.gpu.GpuInfo;
import dev.vconceited.gpuboost.core.Bottleneck;
import dev.vconceited.gpuboost.core.BottleneckDetector;
import dev.vconceited.gpuboost.core.FrameStats;
import dev.vconceited.gpuboost.core.GpuBoostConfig;
import dev.vconceited.gpuboost.core.GpuVendor;

public final class Recommendations {
	private Recommendations() {
	}

	public static List<String> build(Minecraft mc, GpuBoostConfig cfg, BottleneckDetector.Verdict verdict, FrameStats.Summary rolling,
	                                 GpuInfo gpu, int recommendedRenderDistance, double heapMaxMb) {
		List<String> out = new ArrayList<>();
		Bottleneck b = verdict.bottleneck();

		if (!ModCompat.sodium()) {
			out.add("Install Sodium: it replaces the terrain renderer and is the largest single FPS gain. GpuBoost works on top of it.");
		}

		try {
			int rd = mc.options.renderDistance().get();

			if (recommendedRenderDistance > 0 && recommendedRenderDistance != rd) {
				out.add("Render distance " + rd + " -> " + recommendedRenderDistance + " recommended for your " + cfg.targetFps + " FPS target"
						+ (cfg.autoRenderDistance ? " (auto mode will apply it)." : "."));
			}

			int sim = mc.options.simulationDistance().get();

			if (mc.isLocalServer() && (b == Bottleneck.CPU_SYSTEM || b == Bottleneck.CPU_MAIN_THREAD || cfg.weakCpuMode) && sim > 8) {
				out.add("Singleplayer on a busy CPU: simulation distance " + sim + " -> 6-8 frees CPU time for rendering"
						+ " (changes how far away farms and mobs tick, so GpuBoost will not do this for you).");
			}
		} catch (RuntimeException | LinkageError ignored) {
		}

		if (b == Bottleneck.MEMORY_GC) {
			out.add(heapMaxMb < 3500
					? "Frequent GC: give Minecraft more memory in the launcher (4-6 GB is usually enough; more is not faster)."
					: "Frequent GC with a large heap: lower render distance, or check for a mod that leaks memory.");
		}

		if (b == Bottleneck.GPU && rolling.avgFps() < cfg.targetFps) {
			if (ModCompat.irisShadersActive()) {
				out.add("GPU-bound with a shader pack: shader settings (shadows, volumetrics, resolution scale) dominate GPU time.");
			} else {
				out.add("GPU-bound: lower resolution scale/fullscreen resolution, disable Improved Transparency, or use the MAX FPS profile.");
			}
		}

		if (gpu.vendor() == GpuVendor.INTEL && gpu.isOpenGl()) {
			out.add("Intel GPU on OpenGL: try the Vulkan backend in Video Settings; Intel's Vulkan drivers are often faster.");
		}

		if (gpu.vendor() == GpuVendor.SOFTWARE) {
			out.add("A software renderer is in use (" + gpu.deviceName() + "). Install your GPU driver; nothing else will help as much.");
		}

		if (cfg.weakCpuMode && cfg.driverTelemetry) {
			out.add("Weak CPU Mode: you can turn off driver telemetry to save a background process.");
		}

		return out;
	}
}
