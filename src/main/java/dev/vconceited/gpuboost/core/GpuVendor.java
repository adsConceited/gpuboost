package dev.vconceited.gpuboost.core;

import java.util.Locale;

public enum GpuVendor {
	NVIDIA("NVIDIA"),
	AMD("AMD"),
	INTEL("Intel"),
	APPLE("Apple"),
	SOFTWARE("Software renderer"),
	OTHER("Other"),
	UNKNOWN("Unknown");

	public final String displayName;

	GpuVendor(String displayName) {
		this.displayName = displayName;
	}

	public static GpuVendor detect(String vendor, String renderer) {
		String v = vendor == null ? "" : vendor.toLowerCase(Locale.ROOT);
		String r = renderer == null ? "" : renderer.toLowerCase(Locale.ROOT);
		String all = v + " " + r;

		if (all.contains("llvmpipe") || all.contains("softpipe") || all.contains("swiftshader") || all.contains("microsoft basic render")) {
			return SOFTWARE;
		}

		if (all.contains("nvidia") || all.contains("geforce") || all.contains("quadro") || all.contains("rtx ") || v.contains("10de")) {
			return NVIDIA;
		}

		if (all.contains("amd") || all.contains("radeon") || all.contains("ati technologies") || v.contains("1002")) {
			return AMD;
		}

		if (all.contains("intel") || v.contains("8086")) {
			return INTEL;
		}

		if (all.contains("apple")) {
			return APPLE;
		}

		if (v.isBlank() && r.isBlank()) {
			return UNKNOWN;
		}

		return OTHER;
	}
}
