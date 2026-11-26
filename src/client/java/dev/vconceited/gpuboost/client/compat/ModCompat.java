package dev.vconceited.gpuboost.client.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import dev.vconceited.gpuboost.client.GpuBoostClient;
import dev.vconceited.gpuboost.client.Reflect;

public final class ModCompat {
	public record KnownMod(String id, String name, String role) {
	}

	public static final List<KnownMod> KNOWN = List.of(
			new KnownMod("sodium", "Sodium", "terrain renderer: chunk meshing, section culling, multi-draw/indirect drawing"),
			new KnownMod("iris", "Iris", "shader packs; GPU cost of shaders is included in GpuBoost's measurements"),
			new KnownMod("lithium", "Lithium", "game logic / server tick optimizations (no overlap)"),
			new KnownMod("immediatelyfast", "ImmediatelyFast", "batches immediate-mode rendering such as HUD and text (no overlap)"),
			new KnownMod("entityculling", "Entity Culling", "occlusion-based entity culling; GpuBoost's distance culling stacks with it"),
			new KnownMod("moreculling", "More Culling", "block-face culling (no overlap)"),
			new KnownMod("ferritecore", "FerriteCore", "memory usage reduction (no overlap)"),
			new KnownMod("c2me", "C2ME", "multi-threaded chunk generation/IO (no overlap)"),
			new KnownMod("modernfix", "ModernFix", "load time / memory fixes (no overlap)"));

	private static String cachedSodiumBackend;

	private ModCompat() {
	}

	public static boolean isLoaded(String id) {
		return FabricLoader.getInstance().isModLoaded(id);
	}

	public static Optional<String> version(String id) {
		return FabricLoader.getInstance().getModContainer(id).map(ModContainer::getMetadata).map(m -> m.getVersion().getFriendlyString());
	}

	public static boolean sodium() {
		return isLoaded("sodium");
	}

	public static List<String> installedSummary() {
		List<String> out = new ArrayList<>();

		for (KnownMod m : KNOWN) {
			version(m.id()).ifPresent(v -> out.add(m.name() + " " + v));
		}

		return out;
	}

	public static String sodiumDrawBackend() {
		if (cachedSodiumBackend != null || !sodium()) {
			return cachedSodiumBackend;
		}

		Object backend = Reflect.readStaticField("net.caffeinemc.mods.sodium.client.gpu.device.backend.DrawBackend", "BACKEND");

		if (backend != null) {
			Object name = Reflect.call(backend, "getName");
			cachedSodiumBackend = name != null ? name.toString() : backend.toString();
			GpuBoostClient.LOGGER.info("[GpuBoost] Sodium draw backend: {}", cachedSodiumBackend);
		} else {
			cachedSodiumBackend = "unknown";
		}

		return cachedSodiumBackend;
	}

	public static boolean irisShadersActive() {
		if (!isLoaded("iris")) {
			return false;
		}

		Object api = Reflect.callStatic("net.irisshaders.iris.api.v0.IrisApi", "getInstance");
		return Boolean.TRUE.equals(Reflect.call(api, "isShaderPackInUse"));
	}
}
