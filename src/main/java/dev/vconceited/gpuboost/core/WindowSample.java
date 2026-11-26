package dev.vconceited.gpuboost.core;

public record WindowSample(
		double wallMs,
		int frames,
		double avgFrameMs,
		double renderThreadCpuMs,
		double gpuLatencyMs,
		double gpuBusyMs,
		double gpuUtilPct,
		double processCpuPct,
		double systemCpuPct,
		double gcMs,
		double heapUsedFraction,
		int fpsLimit,
		boolean vsync,
		boolean focused
) {
	public double fps() {
		return this.wallMs > 0 ? this.frames * 1000.0 / this.wallMs : 0;
	}

	public double renderThreadBusyFraction() {
		if (Double.isNaN(this.renderThreadCpuMs) || this.wallMs <= 0) {
			return Double.NaN;
		}

		return Math.min(1.0, Math.max(0.0, this.renderThreadCpuMs / this.wallMs));
	}

	public double renderThreadCpuMsPerFrame() {
		if (Double.isNaN(this.renderThreadCpuMs) || this.frames <= 0) {
			return Double.NaN;
		}

		return this.renderThreadCpuMs / this.frames;
	}
}
