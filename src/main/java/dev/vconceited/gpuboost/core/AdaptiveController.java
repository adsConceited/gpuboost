package dev.vconceited.gpuboost.core;

public final class AdaptiveController {
	public static final int MAX_PRESSURE = 3;
	public static final int ESCALATE_AFTER = 3;
	public static final int RELAX_AFTER = 10;

	private static final double[] ENTITY_MULT_NORMAL = {1.0, 0.8, 0.65, 0.5};
	private static final double[] ENTITY_MULT_WEAK_CPU = {1.0, 0.75, 0.6, 0.45};

	private int pressure;
	private int badStreak;
	private int goodStreak;
	private long lastChangeMs = Long.MIN_VALUE / 2;

	public int pressure() {
		return this.pressure;
	}

	public void reset() {
		this.pressure = 0;
		this.badStreak = 0;
		this.goodStreak = 0;
		this.lastChangeMs = Long.MIN_VALUE / 2;
	}

	public boolean update(BottleneckDetector.Verdict verdict, double avgFps, double onePercentLow, int targetFps, long nowMs, int cooldownMs) {
		Bottleneck b = verdict.bottleneck();

		if (b == Bottleneck.UNKNOWN) {
			return false;
		}

		boolean capped = b == Bottleneck.FPS_CAP;
		boolean bad = !capped && (avgFps < targetFps * 0.92 || onePercentLow < targetFps * 0.5);
		boolean good = capped || (avgFps > targetFps * 1.20 && onePercentLow > targetFps * 0.75);

		if (bad) {
			this.badStreak++;
			this.goodStreak = 0;
		} else if (good) {
			this.goodStreak++;
			this.badStreak = 0;
		} else {
			this.badStreak = 0;
			this.goodStreak = 0;
		}

		long sinceChange = nowMs - this.lastChangeMs;

		if (this.badStreak >= ESCALATE_AFTER && this.pressure < MAX_PRESSURE && sinceChange >= cooldownMs) {
			this.pressure++;
			this.badStreak = 0;
			this.lastChangeMs = nowMs;
			return true;
		}

		if (this.goodStreak >= RELAX_AFTER && this.pressure > 0 && sinceChange >= 2L * cooldownMs) {
			this.pressure--;
			this.goodStreak = 0;
			this.lastChangeMs = nowMs;
			return true;
		}

		return false;
	}

	public double entityDistanceMultiplier(boolean weakCpu) {
		return (weakCpu ? ENTITY_MULT_WEAK_CPU : ENTITY_MULT_NORMAL)[this.pressure];
	}

	public int particleReductionSteps(boolean weakCpu) {
		if (this.pressure >= 3) {
			return 2;
		}

		if (this.pressure >= (weakCpu ? 1 : 2)) {
			return 1;
		}

		return 0;
	}

	public static int recommendRenderDistance(int current, BottleneckDetector.Verdict verdict, double avgFps, double onePercentLow,
	                                          int targetFps, int min, int max) {
		Bottleneck b = verdict.bottleneck();
		int rec = current;

		boolean heavy = b == Bottleneck.CPU_MAIN_THREAD || b == Bottleneck.CPU_SYSTEM || b == Bottleneck.GPU || b == Bottleneck.MEMORY_GC;

		if (heavy && avgFps < targetFps * 0.9) {
			rec = current - (avgFps < targetFps * 0.6 ? 2 : 1);
		} else if ((b == Bottleneck.FPS_CAP || b == Bottleneck.BALANCED || b == Bottleneck.GPU || b == Bottleneck.CPU_MAIN_THREAD)
				&& avgFps >= targetFps * 1.3 && onePercentLow >= targetFps * 0.9) {
			rec = current + 1;
		}

		if (rec < current) {
			return current <= min ? current : Math.max(rec, min);
		}

		if (rec > current) {
			return current >= max ? current : Math.min(rec, max);
		}

		return current;
	}
}
