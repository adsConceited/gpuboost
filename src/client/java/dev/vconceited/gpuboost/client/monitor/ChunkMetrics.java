package dev.vconceited.gpuboost.client.monitor;

public final class ChunkMetrics {
	private static final long SETTLE_NS = 2_000_000_000L;

	private int loaded;
	private long loadsTotal;
	private long windowLoads;
	private double loadsPerSecond;
	private long joinNs;
	private long lastLoadNs;
	private double initialLoadSec = Double.NaN;
	private boolean measuringInitial;

	public void onJoin() {
		this.loaded = 0;
		this.joinNs = System.nanoTime();
		this.lastLoadNs = this.joinNs;
		this.initialLoadSec = Double.NaN;
		this.measuringInitial = true;
	}

	public void onChunkLoad() {
		this.loaded++;
		this.loadsTotal++;
		this.windowLoads++;
		this.lastLoadNs = System.nanoTime();
	}

	public void onChunkUnload() {
		if (this.loaded > 0) {
			this.loaded--;
		}
	}

	public void onWindow(double windowSec, long nowNs) {
		this.loadsPerSecond = windowSec > 0 ? this.windowLoads / windowSec : 0;
		this.windowLoads = 0;

		if (this.measuringInitial && this.loaded > 0 && nowNs - this.lastLoadNs >= SETTLE_NS) {
			this.initialLoadSec = (this.lastLoadNs - this.joinNs) / 1e9;
			this.measuringInitial = false;
		}
	}

	public int loaded() {
		return this.loaded;
	}

	public long loadsTotal() {
		return this.loadsTotal;
	}

	public double loadsPerSecond() {
		return this.loadsPerSecond;
	}

	public double initialLoadSec() {
		return this.initialLoadSec;
	}
}
