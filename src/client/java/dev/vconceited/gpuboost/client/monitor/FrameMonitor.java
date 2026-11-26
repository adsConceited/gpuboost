package dev.vconceited.gpuboost.client.monitor;

import dev.vconceited.gpuboost.core.FrameStats;
import dev.vconceited.gpuboost.core.FrameTimeRing;

public final class FrameMonitor {
	public static final int RING_CAPACITY = 16384;
	public static final int ROLLING_WINDOWS = 10;

	public record Window(long endNs, double wallMs, int frames, double renderThreadCpuMs, double gcMs) {
	}

	public interface Callback {
		void onFrame(long nowNs, float frameMs);

		void onWindow(Window window);
	}

	private final FrameTimeRing ring = new FrameTimeRing(RING_CAPACITY);
	private final float[] copy = new float[RING_CAPACITY];
	private final float[] sorted = new float[RING_CAPACITY];
	private final int[] windowFrameHistory = new int[ROLLING_WINDOWS];
	private int windowHistoryIdx;
	private final JvmMetrics jvm;
	private final Callback callback;

	private long lastFrameNs;
	private long windowStartNs;
	private int windowFrames;
	private long windowCpuStartNs;
	private long windowGcStartMs;
	private long intervalNs = 1_000_000_000L;
	private float lastFrameMs;

	public FrameMonitor(JvmMetrics jvm, Callback callback) {
		this.jvm = jvm;
		this.callback = callback;
	}

	public void setIntervalMs(int ms) {
		this.intervalNs = ms * 1_000_000L;
	}

	public FrameTimeRing ring() {
		return this.ring;
	}

	public float lastFrameMs() {
		return this.lastFrameMs;
	}

	public void onFrameStart(long now) {
		if (this.lastFrameNs != 0) {
			float ms = (now - this.lastFrameNs) / 1_000_000f;

			if (ms > 0 && ms < 5_000f) {
				this.ring.add(ms);
				this.lastFrameMs = ms;
				this.windowFrames++;
				this.callback.onFrame(now, ms);
			}
		}

		this.lastFrameNs = now;

		if (this.windowStartNs == 0) {
			startWindow(now);
		} else if (now - this.windowStartNs >= this.intervalNs) {
			long cpu = this.jvm.currentThreadCpuNs();
			long gc = this.jvm.gcPauseMsTotal();
			double wallMs = (now - this.windowStartNs) / 1e6;
			double cpuMs = cpu >= 0 && this.windowCpuStartNs >= 0 ? (cpu - this.windowCpuStartNs) / 1e6 : Double.NaN;
			Window w = new Window(now, wallMs, this.windowFrames, cpuMs, gc - this.windowGcStartMs);

			this.windowFrameHistory[this.windowHistoryIdx] = this.windowFrames;
			this.windowHistoryIdx = (this.windowHistoryIdx + 1) % ROLLING_WINDOWS;

			startWindow(now);
			this.callback.onWindow(w);
		}
	}

	private void startWindow(long now) {
		this.windowStartNs = now;
		this.windowFrames = 0;
		this.windowCpuStartNs = this.jvm.currentThreadCpuNs();
		this.windowGcStartMs = this.jvm.gcPauseMsTotal();
	}

	public FrameStats.Summary rollingSummary(int windows) {
		int frames = 0;
		int count = Math.max(1, Math.min(windows, ROLLING_WINDOWS));

		for (int k = 1; k <= count; k++) {
			int idx = Math.floorMod(this.windowHistoryIdx - k, ROLLING_WINDOWS);
			frames += this.windowFrameHistory[idx];
		}

		int n = this.ring.copyRecent(this.copy, Math.max(frames, 1));
		return FrameStats.summarize(this.copy, n, this.sorted);
	}

	public FrameStats.Summary recentSummary(int count) {
		int n = this.ring.copyRecent(this.copy, count);
		return FrameStats.summarize(this.copy, n, this.sorted);
	}
}
