package dev.vconceited.gpuboost.core;

public final class FrameTimeRing {
	private final float[] data;
	private int head;
	private int size;
	private long totalWritten;

	public FrameTimeRing(int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive");
		}

		this.data = new float[capacity];
	}

	public void add(float frameMs) {
		this.data[this.head] = frameMs;
		this.head = (this.head + 1) % this.data.length;

		if (this.size < this.data.length) {
			this.size++;
		}

		this.totalWritten++;
	}

	public int size() {
		return this.size;
	}

	public int capacity() {
		return this.data.length;
	}

	public long totalWritten() {
		return this.totalWritten;
	}

	public void clear() {
		this.head = 0;
		this.size = 0;
	}

	public float getRecent(int ageIndex) {
		if (ageIndex < 0 || ageIndex >= this.size) {
			throw new IndexOutOfBoundsException(ageIndex);
		}

		int idx = this.head - 1 - ageIndex;

		if (idx < 0) {
			idx += this.data.length;
		}

		return this.data[idx];
	}

	public int copyRecent(float[] dst, int count) {
		int n = Math.min(Math.min(count, this.size), dst.length);

		for (int k = 0; k < n; k++) {
			dst[k] = getRecent(n - 1 - k);
		}

		return n;
	}
}
