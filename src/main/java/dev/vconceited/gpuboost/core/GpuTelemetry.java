package dev.vconceited.gpuboost.core;

import java.util.Locale;

public record GpuTelemetry(
		double utilizationPct,
		double vramUsedMb,
		double vramTotalMb,
		double temperatureC,
		double coreClockMhz,
		double powerW,
		String source,
		long timestampMs
) {
	public static final GpuTelemetry NONE = new GpuTelemetry(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, "none", 0);

	public boolean isFresh(long nowMs, long maxAgeMs) {
		return this.timestampMs > 0 && nowMs - this.timestampMs <= maxAgeMs;
	}

	public static GpuTelemetry parseNvidiaSmi(String line, long nowMs) {
		if (line == null) {
			return null;
		}

		String[] parts = line.split(",");

		if (parts.length < 3) {
			return null;
		}

		double[] v = new double[6];

		for (int k = 0; k < 6; k++) {
			v[k] = k < parts.length ? parseNumber(parts[k]) : Double.NaN;
		}

		if (Double.isNaN(v[0]) && Double.isNaN(v[1])) {
			return null;
		}

		return new GpuTelemetry(v[0], v[1], v[2], v[3], v[4], v[5], "nvidia-smi", nowMs);
	}

	static double parseNumber(String raw) {
		String s = raw.trim().toLowerCase(Locale.ROOT);

		if (s.isEmpty() || s.startsWith("[") || s.equals("n/a")) {
			return Double.NaN;
		}

		int end = 0;

		while (end < s.length() && (Character.isDigit(s.charAt(end)) || s.charAt(end) == '.' || s.charAt(end) == '-')) {
			end++;
		}

		if (end == 0) {
			return Double.NaN;
		}

		try {
			return Double.parseDouble(s.substring(0, end));
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}
}
