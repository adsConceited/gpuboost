package dev.vconceited.gpuboost.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdaptiveControllerTest {
	private static final BottleneckDetector.Verdict GPU = new BottleneckDetector.Verdict(Bottleneck.GPU, 0.9, "");
	private static final BottleneckDetector.Verdict CPU = new BottleneckDetector.Verdict(Bottleneck.CPU_MAIN_THREAD, 0.9, "");
	private static final BottleneckDetector.Verdict CAP = new BottleneckDetector.Verdict(Bottleneck.FPS_CAP, 0.9, "");
	private static final int COOLDOWN = 5000;

	@Test
	void escalatesOnlyAfterThreeBadWindows() {
		AdaptiveController c = new AdaptiveController();
		long t = 100_000;

		assertFalse(c.update(GPU, 40, 30, 60, t, COOLDOWN));
		assertFalse(c.update(GPU, 40, 30, 60, t += 1000, COOLDOWN));
		assertTrue(c.update(GPU, 40, 30, 60, t += 1000, COOLDOWN));
		assertEquals(1, c.pressure());
		assertEquals(0.8, c.entityDistanceMultiplier(false), 1e-9);
	}

	@Test
	void respectsCooldownBetweenChanges() {
		AdaptiveController c = new AdaptiveController();
		long t = 100_000;

		for (int k = 0; k < 3; k++) {
			c.update(CPU, 30, 20, 60, t += 1000, COOLDOWN);
		}

		assertEquals(1, c.pressure());

		for (int k = 0; k < 3; k++) {
			c.update(CPU, 30, 20, 60, t += 1000, COOLDOWN);
		}

		assertEquals(1, c.pressure());

		t += 5000;

		for (int k = 0; k < 3; k++) {
			c.update(CPU, 30, 20, 60, t += 1000, COOLDOWN);
		}

		assertEquals(2, c.pressure());
	}

	@Test
	void relaxesSlowlyAndNeverBelowZero() {
		AdaptiveController c = new AdaptiveController();
		long t = 100_000;

		for (int k = 0; k < 3; k++) {
			c.update(GPU, 40, 30, 60, t += 1000, COOLDOWN);
		}

		assertEquals(1, c.pressure());
		t += 20_000;

		for (int k = 0; k < 9; k++) {
			c.update(GPU, 90, 70, 60, t += 1000, COOLDOWN);
		}

		assertEquals(1, c.pressure(), "9 good windows are not enough");
		c.update(GPU, 90, 70, 60, t += 1000, COOLDOWN);
		assertEquals(0, c.pressure());

		for (int k = 0; k < 30; k++) {
			c.update(GPU, 90, 70, 60, t += 1000, COOLDOWN);
		}

		assertEquals(0, c.pressure());
	}

	@Test
	void deadBandResetsStreaks() {
		AdaptiveController c = new AdaptiveController();
		long t = 100_000;
		c.update(GPU, 40, 30, 60, t += 1000, COOLDOWN);
		c.update(GPU, 40, 30, 60, t += 1000, COOLDOWN);

		c.update(GPU, 62, 45, 60, t += 1000, COOLDOWN);
		c.update(GPU, 40, 30, 60, t += 1000, COOLDOWN);
		assertEquals(0, c.pressure());
	}

	@Test
	void fpsCapCountsAsHeadroom() {
		AdaptiveController c = new AdaptiveController();
		long t = 100_000;

		for (int k = 0; k < 3; k++) {
			c.update(CPU, 30, 20, 60, t += 1000, COOLDOWN);
		}

		t += 20_000;

		for (int k = 0; k < 10; k++) {
			c.update(CAP, 60, 50, 144, t += 1000, COOLDOWN);
		}

		assertEquals(0, c.pressure());
	}

	@Test
	void particleStepsAndWeakCpuTables() {
		AdaptiveController c = new AdaptiveController();
		long t = 100_000;
		assertEquals(0, c.particleReductionSteps(true));

		for (int level = 1; level <= 3; level++) {
			for (int k = 0; k < 3; k++) {
				c.update(CPU, 20, 10, 60, t += 1000, 0);
			}

			assertEquals(level, c.pressure());
		}

		assertEquals(2, c.particleReductionSteps(false));
		assertEquals(0.45, c.entityDistanceMultiplier(true), 1e-9);

		for (int k = 0; k < 10; k++) {
			c.update(CPU, 20, 10, 60, t += 1000, 0);
		}

		assertEquals(AdaptiveController.MAX_PRESSURE, c.pressure());
	}

	@Test
	void renderDistanceRecommendation() {
		assertEquals(11, AdaptiveController.recommendRenderDistance(12, GPU, 50, 35, 60, 6, 32));
		assertEquals(10, AdaptiveController.recommendRenderDistance(12, CPU, 30, 20, 60, 6, 32));
		assertEquals(13, AdaptiveController.recommendRenderDistance(12, CAP, 144, 120, 60, 6, 32));
		assertEquals(12, AdaptiveController.recommendRenderDistance(12, GPU, 62, 50, 60, 6, 32));

		assertEquals(6, AdaptiveController.recommendRenderDistance(7, CPU, 20, 10, 60, 6, 32));

		assertEquals(40, AdaptiveController.recommendRenderDistance(40, CAP, 200, 150, 60, 6, 32));
		assertEquals(4, AdaptiveController.recommendRenderDistance(4, CPU, 20, 10, 60, 6, 32));
	}
}
