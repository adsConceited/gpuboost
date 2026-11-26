package dev.vconceited.gpuboost.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FrameTimeRingTest {
	@Test
	void wrapsAroundKeepingNewest() {
		FrameTimeRing r = new FrameTimeRing(4);

		for (int k = 1; k <= 6; k++) {
			r.add(k);
		}

		assertEquals(4, r.size());
		assertEquals(6, r.totalWritten());
		assertEquals(6f, r.getRecent(0));
		assertEquals(3f, r.getRecent(3));

		float[] dst = new float[10];
		int n = r.copyRecent(dst, 10);
		assertEquals(4, n);
		assertEquals(3f, dst[0]);
		assertEquals(6f, dst[3]);

		n = r.copyRecent(dst, 2);
		assertEquals(2, n);
		assertEquals(5f, dst[0]);
		assertEquals(6f, dst[1]);
	}

	@Test
	void boundsAreChecked() {
		FrameTimeRing r = new FrameTimeRing(2);
		r.add(1);
		assertThrows(IndexOutOfBoundsException.class, () -> r.getRecent(1));
		assertThrows(IllegalArgumentException.class, () -> new FrameTimeRing(0));
		r.clear();
		assertEquals(0, r.size());
	}
}
