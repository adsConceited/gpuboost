package dev.vconceited.gpuboost.client.gpu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import dev.vconceited.gpuboost.core.GpuTelemetry;

public final class AmdSysfsMonitor implements TelemetrySource {
	private final Path deviceDir;
	private GpuTelemetry latest = GpuTelemetry.NONE;
	private boolean alive = true;

	private AmdSysfsMonitor(Path deviceDir) {
		this.deviceDir = deviceDir;
	}

	public static AmdSysfsMonitor tryCreate() {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
			return null;
		}

		Path drm = Path.of("/sys/class/drm");

		if (!Files.isDirectory(drm)) {
			return null;
		}

		try (DirectoryStream<Path> ds = Files.newDirectoryStream(drm, "card[0-9]*")) {
			for (Path card : ds) {
				Path dev = card.resolve("device");
				Path vendor = dev.resolve("vendor");

				if (Files.isRegularFile(dev.resolve("gpu_busy_percent")) && Files.isRegularFile(vendor)
						&& Files.readString(vendor, StandardCharsets.US_ASCII).trim().equalsIgnoreCase("0x1002")) {
					return new AmdSysfsMonitor(dev);
				}
			}
		} catch (IOException | RuntimeException e) {
			return null;
		}

		return null;
	}

	@Override
	public String name() {
		return "amdgpu sysfs";
	}

	@Override
	public GpuTelemetry latest() {
		return this.latest;
	}

	@Override
	public void poll() {
		if (!this.alive) {
			return;
		}

		try {
			double busy = readNumber("gpu_busy_percent");
			double used = readNumber("mem_info_vram_used") / (1024.0 * 1024.0);
			double total = readNumber("mem_info_vram_total") / (1024.0 * 1024.0);
			this.latest = new GpuTelemetry(busy, used, total, Double.NaN, Double.NaN, Double.NaN, name(), System.currentTimeMillis());
		} catch (IOException | RuntimeException e) {
			this.alive = false;
		}
	}

	private double readNumber(String file) throws IOException {
		Path p = this.deviceDir.resolve(file);

		if (!Files.isRegularFile(p)) {
			return Double.NaN;
		}

		return Double.parseDouble(Files.readString(p, StandardCharsets.US_ASCII).trim());
	}

	@Override
	public boolean isAlive() {
		return this.alive;
	}

	@Override
	public void stop() {
		this.alive = false;
	}
}
