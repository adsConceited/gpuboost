package dev.vconceited.gpuboost.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class FrameStatsTest {
	@Test
	void constantFrameTimes() {
		float[] f = new float[200];
		Arrays.fill(f, 10f);
		FrameStats.Summary s = FrameStats.summarize(f, f.length, new float[f.length]);

		assertEquals(200, s.frames());
		assertEquals(100.0, s.avgFps(), 1e-9);
		assertEquals(100.0, s.onePercentLowFps(), 1e-9);
		assertEquals(10.0, s.medianMs(), 1e-9);
		assertEquals(0.0, s.stdDevMs(), 1e-9);
		assertEquals(0, s.stutters());
	}

	@Test
	void singleSpikeDominatesOnePercentLow() {
		float[] f = new float[100];
		Arrays.fill(f, 10f);
		f[37] = 100f;
		FrameStats.Summary s = FrameStats.summarize(f, f.length, new float[f.length]);

		assertEquals(100 * 1000.0 / 1090.0, s.avgFps(), 1e-9);

		assertEquals(10.0, s.onePercentLowFps(), 1e-9);
		assertEquals(100.0, s.maxMs(), 1e-9);
		assertEquals(1, s.stutters());
	}

	@Test
	void percentileInterpolates() {
		float[] sorted = {1, 2, 3, 4, 5};
		assertEquals(3.0, FrameStats.percentileSorted(sorted, 5, 50), 1e-9);
		assertEquals(4.6, FrameStats.percentileSorted(sorted, 5, 90), 1e-6);
		assertEquals(5.0, FrameStats.percentileSorted(sorted, 5, 100), 1e-9);
	}

	@Test
	void onePercentLowUsesWorstOnePercentAverage() {
		float[] f = new float[1000];
		Arrays.fill(f, 5f);

		for (int k = 0; k < 10; k++) {
			f[k * 97] = 20f + k * 2;
		}

		FrameStats.Summary s = FrameStats.summarize(f, f.length, new float[f.length]);
		assertEquals(1000.0 / 29.0, s.onePercentLowFps(), 1e-6);
	}

	@Test
	void emptyAndScratchValidation() {
		assertEquals(FrameStats.Summary.EMPTY, FrameStats.summarize(new float[0], 0, new float[0]));
		assertThrows(IllegalArgumentException.class, () -> FrameStats.summarize(new float[10], 10, new float[5]));
	}

	@Test
	void doesNotModifyInput() {
		float[] f = {9, 1, 5};
		FrameStats.summarize(f, 3, new float[3]);
		assertEquals(9f, f[0]);
		assertEquals(1f, f[1]);
		assertEquals(5f, f[2]);
	}
}
