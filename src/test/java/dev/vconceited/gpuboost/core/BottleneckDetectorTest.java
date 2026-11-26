package dev.vconceited.gpuboost.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BottleneckDetectorTest {
	private static final double NaN = Double.NaN;

	private static WindowSample window(double fps, double busyFrac, double gpuLatencyMs, double gpuUtil,
	                                   double systemCpu, double gcMs, double heap, int limit, boolean vsync) {
		int frames = (int) Math.round(fps);
		double frameMs = 1000.0 / fps;
		return new WindowSample(1000, frames, frameMs, Double.isNaN(busyFrac) ? NaN : busyFrac * 1000, gpuLatencyMs, NaN,
				gpuUtil, 30, systemCpu, gcMs, heap, limit, vsync, true);
	}

	@Test
	void gpuBoundFromTimerQueriesAndUtilization() {
		BottleneckDetector.Verdict v = BottleneckDetector.classify(window(55, 0.45, 14, 99, 40, 0, 0.5, 0, false));
		assertEquals(Bottleneck.GPU, v.bottleneck());
		assertTrue(v.confidence() > 0.8, "confidence " + v.confidence());
	}

	@Test
	void gpuInferredFromWaitingRenderThreadOnly() {
		BottleneckDetector.Verdict v = BottleneckDetector.classify(window(70, 0.40, NaN, NaN, 35, 0, 0.5, 0, false));
		assertEquals(Bottleneck.GPU, v.bottleneck());
		assertTrue(v.confidence() < 0.8);
	}

	@Test
	void cpuBoundRenderThread() {
		BottleneckDetector.Verdict v = BottleneckDetector.classify(window(48, 0.97, 0.5, 45, 50, 0, 0.5, 0, false));
		assertEquals(Bottleneck.CPU_MAIN_THREAD, v.bottleneck());
	}

	@Test
	void cpuSystemSaturated() {
		BottleneckDetector.Verdict v = BottleneckDetector.classify(window(40, 0.7, 0.4, 40, 98, 0, 0.5, 0, false));
		assertEquals(Bottleneck.CPU_SYSTEM, v.bottleneck());
	}

	@Test
	void frameCapDetected() {
		BottleneckDetector.Verdict v = BottleneckDetector.classify(window(119, 0.4, 2, 50, 30, 0, 0.5, 120, false));
		assertEquals(Bottleneck.FPS_CAP, v.bottleneck());

		BottleneckDetector.Verdict vs = BottleneckDetector.classify(window(60, 0.3, 1, 40, 30, 0, 0.5, 0, true));
		assertEquals(Bottleneck.FPS_CAP, vs.bottleneck());
	}

	@Test
	void vsyncWithSaturatedGpuIsStillGpu() {
		BottleneckDetector.Verdict v = BottleneckDetector.classify(window(41, 0.3, 20, 99, 30, 0, 0.5, 0, true));
		assertEquals(Bottleneck.GPU, v.bottleneck());
	}

	@Test
	void memoryPressure() {
		BottleneckDetector.Verdict v = BottleneckDetector.classify(window(60, 0.9, 1, 50, 50, 80, 0.95, 0, false));
		assertEquals(Bottleneck.MEMORY_GC, v.bottleneck());
	}

	@Test
	void idleWindowsAreIgnored() {
		WindowSample unfocused = new WindowSample(1000, 60, 16.6, 500, NaN, NaN, NaN, 10, 10, 0, 0.3, 0, false, false);
		assertEquals(Bottleneck.UNKNOWN, BottleneckDetector.classify(unfocused).bottleneck());

		BottleneckDetector d = new BottleneckDetector();
		d.update(unfocused);
		assertEquals(Bottleneck.UNKNOWN, d.current().bottleneck());
	}

	@Test
	void smoothingNeedsMajority() {
		BottleneckDetector d = new BottleneckDetector();
		WindowSample gpu = window(55, 0.45, 14, 99, 40, 0, 0.5, 0, false);
		WindowSample cpu = window(48, 0.97, 0.5, 45, 50, 0, 0.5, 0, false);

		d.update(gpu);
		d.update(gpu);
		d.update(gpu);

		assertEquals(Bottleneck.GPU, d.update(cpu).bottleneck());

		assertEquals(Bottleneck.GPU, d.update(cpu).bottleneck());

		assertEquals(Bottleneck.CPU_MAIN_THREAD, d.update(cpu).bottleneck());
	}
}
