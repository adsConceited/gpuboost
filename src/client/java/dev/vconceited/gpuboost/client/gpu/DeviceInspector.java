package dev.vconceited.gpuboost.client.gpu;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import dev.vconceited.gpuboost.client.GpuBoostClient;
import dev.vconceited.gpuboost.client.Reflect;
import dev.vconceited.gpuboost.core.GpuVendor;

public final class DeviceInspector {
	private DeviceInspector() {
	}

	public static GpuInfo inspect() {
		Object device = Reflect.callStatic("com.mojang.blaze3d.systems.RenderSystem", "getDevice");

		if (device == null) {
			GpuBoostClient.LOGGER.warn("[GpuBoost] Could not access the game's GPU device; GPU details will come from OpenGL only.");
			return GpuInfo.UNKNOWN;
		}

		Object info = Reflect.call(device, "getDeviceInfo");

		if (info == null) {
			GpuBoostClient.LOGGER.warn("[GpuBoost] GPU device has no readable device info.");
			return GpuInfo.UNKNOWN;
		}

		Map<String, Object> d = Reflect.describe(info, 0);
		String backend = str(d.get("backendName"), "unknown");
		String name = str(d.get("name"), "unknown");
		String vendor = str(d.get("vendorName"), "unknown");
		String driver = str(d.get("driverInfo"), "");
		String type = str(d.get("type"), "");
		Map<String, Object> features = map(d.get("features"));
		Map<String, Object> limits = map(d.get("limits"));
		Set<String> ext = new LinkedHashSet<>();

		if (d.get("underlyingExtensions") instanceof Set<?> s) {
			for (Object o : s) {
				ext.add(String.valueOf(o));
			}
		}

		GpuInfo result = new GpuInfo(backend, name, vendor, driver, type, GpuVendor.detect(vendor, name), "", features, limits, ext);
		GpuBoostClient.LOGGER.info("[GpuBoost] Graphics backend: {} | device: {} | vendor: {} | driver: {} | {} extensions",
				backend, name, vendor, driver, ext.size());
		return result;
	}

	private static String str(Object o, String fallback) {
		return o == null ? fallback : String.valueOf(o);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object o) {
		return o instanceof Map ? new LinkedHashMap<>((Map<String, Object>) o) : Map.of();
	}
}
