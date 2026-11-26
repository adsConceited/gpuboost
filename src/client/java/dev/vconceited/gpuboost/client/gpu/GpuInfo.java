package dev.vconceited.gpuboost.client.gpu;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.vconceited.gpuboost.core.GpuVendor;

public record GpuInfo(String backend, String deviceName, String vendorName, String driver, String deviceType, GpuVendor vendor,
                      String glVersion, Map<String, Object> features, Map<String, Object> limits, Set<String> extensions) {
	public static final GpuInfo UNKNOWN = new GpuInfo("unknown", "unknown", "unknown", "", "", GpuVendor.UNKNOWN, "", Map.of(), Map.of(), Set.of());

	public boolean isOpenGl() {
		return this.backend.toLowerCase(Locale.ROOT).contains("opengl");
	}

	public boolean isVulkan() {
		return this.backend.toLowerCase(Locale.ROOT).contains("vulkan");
	}

	public GpuInfo withOpenGl(String glVendor, String glRenderer, String version, Set<String> glExtensions) {
		String name = isBlankOrUnknown(this.deviceName) ? glRenderer : this.deviceName;
		String vendorStr = isBlankOrUnknown(this.vendorName) ? glVendor : this.vendorName;
		String backendName = isBlankOrUnknown(this.backend) ? "OpenGL" : this.backend;
		Set<String> ext = glExtensions.isEmpty() ? this.extensions : glExtensions;
		return new GpuInfo(backendName, name, vendorStr, this.driver.isBlank() ? version : this.driver, this.deviceType,
				GpuVendor.detect(vendorStr, name + " " + glRenderer), version, this.features, this.limits, ext);
	}

	private static boolean isBlankOrUnknown(String s) {
		return s == null || s.isBlank() || s.equals("unknown");
	}
}
