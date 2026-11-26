package dev.vconceited.gpuboost.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CapabilityReport {
	public enum Status { SUPPORTED, UNSUPPORTED, UNKNOWN }

	public record Capability(String name, Status status, String note) {
	}

	private CapabilityReport() {
	}

	public static List<Capability> build(String backendName, Set<String> extensions, Map<String, Object> features, boolean sodiumLoaded) {
		boolean vulkan = backendName != null && backendName.toLowerCase(Locale.ROOT).contains("vulkan");
		boolean gl = backendName != null && backendName.toLowerCase(Locale.ROOT).contains("opengl");
		Set<String> ext = extensions == null ? Set.of() : extensions;
		List<Capability> out = new ArrayList<>();

		String terrainUser = sodiumLoaded ? "used by Sodium's terrain renderer" : "used by vanilla where supported";

		out.add(new Capability("Indirect multi-draw",
				combine(feature(features, "multiDrawIndirect"), has(ext, "GL_ARB_multi_draw_indirect")),
				terrainUser));
		out.add(new Capability("Indirect draw count",
				any(ext, "GL_ARB_indirect_parameters", "VK_KHR_draw_indirect_count"),
				"lets the GPU decide how many draws to run; detected only"));
		out.add(new Capability("Multi-draw (direct)",
				combine(feature(features, "multiDrawDirectInterleaved"), any(ext, "VK_EXT_multi_draw")),
				terrainUser));
		out.add(new Capability("Persistent mapped buffers",
				combine(feature(features, "persistentMapping"), has(ext, "GL_ARB_buffer_storage")),
				"zero-copy CPU->GPU uploads; " + terrainUser));
		out.add(new Capability("Mesh shaders",
				any(ext, "GL_NV_mesh_shader", "VK_EXT_mesh_shader", "VK_NV_mesh_shader"),
				"detected only: no stable game/Sodium hook exists to replace terrain geometry with mesh shaders safely"));
		out.add(new Capability("Compute shaders",
				combine(feature(features, "computeShaders"), any(ext, "GL_ARB_compute_shader")),
				"detected only"));
		out.add(new Capability("NVIDIA command lists",
				vulkan ? Status.UNSUPPORTED : any(ext, "GL_NV_command_list"),
				"proprietary; intentionally not used"));
		out.add(new Capability("GPU timer queries",
				gl ? Status.SUPPORTED : Status.UNKNOWN,
				gl ? "used by GpuBoost for GPU latency / bottleneck detection"
						: "not accessible from mods on the Vulkan backend; GpuBoost uses driver telemetry instead"));
		out.add(new Capability("VRAM usage query",
				any(ext, "GL_NVX_gpu_memory_info", "GL_ATI_meminfo"),
				"used by GpuBoost on OpenGL; on Vulkan, nvidia-smi / amdgpu sysfs are used when present"));
		return out;
	}

	private static Status feature(Map<String, Object> features, String key) {
		if (features == null) {
			return Status.UNKNOWN;
		}

		Object v = features.get(key);

		if (v instanceof Boolean b) {
			return b ? Status.SUPPORTED : Status.UNSUPPORTED;
		}

		return Status.UNKNOWN;
	}

	private static Status has(Set<String> ext, String name) {
		if (ext.isEmpty()) {
			return Status.UNKNOWN;
		}

		return ext.contains(name) ? Status.SUPPORTED : Status.UNSUPPORTED;
	}

	private static Status any(Set<String> ext, String... names) {
		if (ext.isEmpty()) {
			return Status.UNKNOWN;
		}

		for (String n : names) {
			if (ext.contains(n)) {
				return Status.SUPPORTED;
			}
		}

		return Status.UNSUPPORTED;
	}

	private static Status combine(Status fromFeatures, Status fromExtensions) {
		if (fromFeatures == Status.SUPPORTED || fromExtensions == Status.SUPPORTED) {
			return Status.SUPPORTED;
		}

		if (fromFeatures == Status.UNSUPPORTED) {
			return Status.UNSUPPORTED;
		}

		return fromExtensions;
	}
}
