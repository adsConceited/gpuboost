package dev.vconceited.gpuboost.core;

import java.util.Arrays;

public final class FrameStats {
	public static final double STUTTER_FACTOR = 2.0;
	public static final double STUTTER_MIN_MS = 8.0;

	private FrameStats() {
	}

	public record Summary(
			int frames,
			double totalMs,
			double avgFps,
			double onePercentLowFps,
			double pointOnePercentLowFps,
			double avgFrameMs,
			double medianMs,
			double p95Ms,
			double p99Ms,
			double maxMs,
			double stdDevMs,
			int stutters
	) {
		public static final Summary EMPTY = new Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	public static Summary summarize(float[] frameMs, int n, float[] scratch) {
		if (n <= 0) {
			return Summary.EMPTY;
		}

		if (scratch.length < n) {
			throw new IllegalArgumentException("scratch too small");
		}

		double total = 0;

		for (int k = 0; k < n; k++) {
			total += frameMs[k];
		}

		double mean = total / n;
		double var = 0;

		for (int k = 0; k < n; k++) {
			double d = frameMs[k] - mean;
			var += d * d;
		}

		double std = n > 1 ? Math.sqrt(var / (n - 1)) : 0;

		System.arraycopy(frameMs, 0, scratch, 0, n);
		Arrays.sort(scratch, 0, n);

		double median = percentileSorted(scratch, n, 50);
		double p95 = percentileSorted(scratch, n, 95);
		double p99 = percentileSorted(scratch, n, 99);
		double max = scratch[n - 1];

		double low1 = lowFps(scratch, n, 0.01);
		double low01 = lowFps(scratch, n, 0.001);

		double stutterThreshold = Math.max(median * STUTTER_FACTOR, STUTTER_MIN_MS);
		int stutters = 0;

		for (int k = n - 1; k >= 0 && scratch[k] > stutterThreshold; k--) {
			stutters++;
		}

		double avgFps = total > 0 ? n * 1000.0 / total : 0;

		return new Summary(n, total, avgFps, low1, low01, mean, median, p95, p99, max, std, stutters);
	}

	public static double percentileSorted(float[] sorted, int n, double pct) {
		if (n == 0) {
			return 0;
		}

		if (n == 1) {
			return sorted[0];
		}

		double rank = (pct / 100.0) * (n - 1);
		int lo = (int) Math.floor(rank);
		int hi = Math.min(lo + 1, n - 1);
		double frac = rank - lo;
		return sorted[lo] + (sorted[hi] - sorted[lo]) * frac;
	}

	public static double lowFps(float[] sorted, int n, double fraction) {
		if (n == 0) {
			return 0;
		}

		int count = Math.max(1, (int) Math.ceil(n * fraction));
		double sum = 0;

		for (int k = n - count; k < n; k++) {
			sum += sorted[k];
		}

		double meanMs = sum / count;
		return meanMs > 0 ? 1000.0 / meanMs : 0;
	}
}
