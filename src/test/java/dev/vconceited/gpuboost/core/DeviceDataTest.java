package dev.vconceited.gpuboost.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DeviceDataTest {
	@Test
	void vendorDetection() {
		assertEquals(GpuVendor.NVIDIA, GpuVendor.detect("NVIDIA Corporation", "NVIDIA GeForce RTX 4070/PCIe/SSE2"));
		assertEquals(GpuVendor.NVIDIA, GpuVendor.detect("0x10de", "Some device"));
		assertEquals(GpuVendor.AMD, GpuVendor.detect("ATI Technologies Inc.", "AMD Radeon RX 7800 XT"));
		assertEquals(GpuVendor.INTEL, GpuVendor.detect("Intel", "Mesa Intel(R) UHD Graphics 620 (KBL GT2)"));
		assertEquals(GpuVendor.SOFTWARE, GpuVendor.detect("Mesa", "llvmpipe (LLVM 17.0.6, 256 bits)"));
		assertEquals(GpuVendor.UNKNOWN, GpuVendor.detect(null, null));
		assertEquals(GpuVendor.OTHER, GpuVendor.detect("Qualcomm", "Adreno 740"));
	}

	@Test
	void nvidiaSmiParsing() {
		GpuTelemetry t = GpuTelemetry.parseNvidiaSmi("97, 6123, 12282, 68, 2610, 187.43", 1000);
		assertEquals(97, t.utilizationPct(), 0);
		assertEquals(6123, t.vramUsedMb(), 0);
		assertEquals(12282, t.vramTotalMb(), 0);
		assertEquals(187.43, t.powerW(), 1e-9);
		assertTrue(t.isFresh(2500, 2000));

		GpuTelemetry laptop = GpuTelemetry.parseNvidiaSmi("12, 512, 4096, [N/A], 1200, [Not Supported]", 1000);
		assertTrue(Double.isNaN(laptop.temperatureC()));
		assertTrue(Double.isNaN(laptop.powerW()));
		assertEquals(12, laptop.utilizationPct(), 0);

		GpuTelemetry withUnits = GpuTelemetry.parseNvidiaSmi("50 %, 1000 MiB, 8000 MiB", 1);
		assertEquals(50, withUnits.utilizationPct(), 0);
		assertEquals(8000, withUnits.vramTotalMb(), 0);

		assertNull(GpuTelemetry.parseNvidiaSmi("utilization.gpu [%]", 1));
		assertNull(GpuTelemetry.parseNvidiaSmi("", 1));
		assertNull(GpuTelemetry.parseNvidiaSmi(null, 1));
	}

	@Test
	void capabilityReportPrefersGameFeatureFlags() {
		List<CapabilityReport.Capability> caps = CapabilityReport.build("Vulkan",
				Set.of("VK_EXT_mesh_shader", "VK_KHR_draw_indirect_count"),
				Map.of("multiDrawIndirect", true, "persistentMapping", false), true);

		assertEquals(CapabilityReport.Status.SUPPORTED, find(caps, "Indirect multi-draw").status());
		assertEquals(CapabilityReport.Status.SUPPORTED, find(caps, "Mesh shaders").status());
		assertEquals(CapabilityReport.Status.UNSUPPORTED, find(caps, "Persistent mapped buffers").status());
		assertEquals(CapabilityReport.Status.UNKNOWN, find(caps, "GPU timer queries").status());
		assertTrue(find(caps, "Indirect multi-draw").note().contains("Sodium"));
	}

	@Test
	void capabilityReportOnOpenGl() {
		List<CapabilityReport.Capability> caps = CapabilityReport.build("OpenGL",
				Set.of("GL_ARB_multi_draw_indirect", "GL_NVX_gpu_memory_info"), null, false);
		assertEquals(CapabilityReport.Status.SUPPORTED, find(caps, "Indirect multi-draw").status());
		assertEquals(CapabilityReport.Status.SUPPORTED, find(caps, "VRAM usage query").status());
		assertEquals(CapabilityReport.Status.SUPPORTED, find(caps, "GPU timer queries").status());
		assertEquals(CapabilityReport.Status.UNSUPPORTED, find(caps, "Mesh shaders").status());
	}

	private static CapabilityReport.Capability find(List<CapabilityReport.Capability> caps, String name) {
		return caps.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
	}
}
