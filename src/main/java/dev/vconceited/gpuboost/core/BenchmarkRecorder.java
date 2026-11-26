package dev.vconceited.gpuboost.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BenchmarkRecorder {
	public enum State { IDLE, WARMUP, RECORDING, DONE }

	public record Sample(double tSec, double fps, double processCpuPct, double systemCpuPct, double renderThreadBusyPct,
	                     double gpuUtilPct, double gpuLatencyMs, double heapUsedMb, double vramUsedMb, String bottleneck) {
	}

	public record Result(Map<String, Object> metadata, FrameStats.Summary summary, float[] frameTimesMs, List<Sample> samples) {
		public Map<String, Object> toJsonMap() {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("format", "gpuboost-benchmark-v1");
			m.put("metadata", this.metadata);

			Map<String, Object> s = new LinkedHashMap<>();
			s.put("frames", this.summary.frames());
			s.put("durationSec", round(this.summary.totalMs() / 1000.0, 3));
			s.put("avgFps", round(this.summary.avgFps(), 2));
			s.put("onePercentLowFps", round(this.summary.onePercentLowFps(), 2));
			s.put("pointOnePercentLowFps", round(this.summary.pointOnePercentLowFps(), 2));
			s.put("avgFrameMs", round(this.summary.avgFrameMs(), 3));
			s.put("medianFrameMs", round(this.summary.medianMs(), 3));
			s.put("p95FrameMs", round(this.summary.p95Ms(), 3));
			s.put("p99FrameMs", round(this.summary.p99Ms(), 3));
			s.put("maxFrameMs", round(this.summary.maxMs(), 3));
			s.put("frameTimeStdDevMs", round(this.summary.stdDevMs(), 3));
			s.put("stutters", this.summary.stutters());
			s.put("avgProcessCpuPct", round(mean(this.samples, Sample::processCpuPct), 1));
			s.put("avgSystemCpuPct", round(mean(this.samples, Sample::systemCpuPct), 1));
			s.put("avgRenderThreadBusyPct", round(mean(this.samples, Sample::renderThreadBusyPct), 1));
			s.put("avgGpuUtilPct", round(mean(this.samples, Sample::gpuUtilPct), 1));
			s.put("avgGpuLatencyMs", round(mean(this.samples, Sample::gpuLatencyMs), 3));
			s.put("peakHeapUsedMb", round(max(this.samples, Sample::heapUsedMb), 0));
			s.put("peakVramUsedMb", round(max(this.samples, Sample::vramUsedMb), 0));
			s.put("dominantBottleneck", dominantBottleneck(this.samples));
			m.put("summary", s);

			List<Object> rows = new ArrayList<>();

			for (Sample x : this.samples) {
				Map<String, Object> r = new LinkedHashMap<>();
				r.put("t", round(x.tSec(), 2));
				r.put("fps", round(x.fps(), 1));
				r.put("cpuProcess", round(x.processCpuPct(), 1));
				r.put("cpuSystem", round(x.systemCpuPct(), 1));
				r.put("renderThreadBusy", round(x.renderThreadBusyPct(), 1));
				r.put("gpuUtil", round(x.gpuUtilPct(), 1));
				r.put("gpuLatencyMs", round(x.gpuLatencyMs(), 3));
				r.put("heapMb", round(x.heapUsedMb(), 0));
				r.put("vramMb", round(x.vramUsedMb(), 0));
				r.put("bottleneck", x.bottleneck());
				rows.add(r);
			}

			m.put("samples", rows);
			return m;
		}
	}

	private State state = State.IDLE;
	private long phaseStartNs;
	private long warmupNs;
	private long durationNs;
	private Map<String, Object> metadata = new LinkedHashMap<>();
	private float[] frames = new float[4096];
	private int frameCount;
	private final List<Sample> samples = new ArrayList<>();

	public State state() {
		return this.state;
	}

	public boolean isActive() {
		return this.state == State.WARMUP || this.state == State.RECORDING;
	}

	public void start(long nowNs, int warmupSec, int durationSec, Map<String, Object> metadata) {
		this.state = warmupSec > 0 ? State.WARMUP : State.RECORDING;
		this.phaseStartNs = nowNs;
		this.warmupNs = warmupSec * 1_000_000_000L;
		this.durationNs = durationSec * 1_000_000_000L;
		this.metadata = new LinkedHashMap<>(metadata);
		this.frameCount = 0;
		this.samples.clear();
	}

	public void cancel() {
		this.state = State.IDLE;
		this.frameCount = 0;
		this.samples.clear();
	}

	public double recordingSeconds(long nowNs) {
		return this.state == State.RECORDING ? (nowNs - this.phaseStartNs) / 1e9 : 0;
	}

	public double progress(long nowNs) {
		return switch (this.state) {
			case WARMUP -> 0;
			case RECORDING -> Math.min(1.0, (nowNs - this.phaseStartNs) / (double) this.durationNs);
			case DONE -> 1;
			case IDLE -> 0;
		};
	}

	public State onFrame(long nowNs, float frameMs) {
		switch (this.state) {
			case WARMUP -> {
				if (nowNs - this.phaseStartNs >= this.warmupNs) {
					this.state = State.RECORDING;
					this.phaseStartNs = nowNs;
				}
			}
			case RECORDING -> {
				if (this.frameCount == this.frames.length) {
					this.frames = Arrays.copyOf(this.frames, this.frames.length * 2);
				}

				this.frames[this.frameCount++] = frameMs;

				if (nowNs - this.phaseStartNs >= this.durationNs) {
					this.state = State.DONE;
				}
			}
			default -> {
			}
		}

		return this.state;
	}

	public void addSample(Sample sample) {
		if (this.state == State.RECORDING) {
			this.samples.add(sample);
		}
	}

	public Result finish() {
		float[] recorded = Arrays.copyOf(this.frames, this.frameCount);
		float[] scratch = new float[Math.max(1, recorded.length)];
		FrameStats.Summary summary = FrameStats.summarize(recorded, recorded.length, scratch);
		Result result = new Result(new LinkedHashMap<>(this.metadata), summary, recorded, new ArrayList<>(this.samples));
		this.state = State.IDLE;
		this.frameCount = 0;
		this.samples.clear();
		return result;
	}

	static double round(double v, int digits) {
		if (Double.isNaN(v) || Double.isInfinite(v)) {
			return Double.NaN;
		}

		double f = Math.pow(10, digits);
		return Math.round(v * f) / f;
	}

	private static double mean(List<Sample> list, java.util.function.ToDoubleFunction<Sample> fn) {
		double sum = 0;
		int n = 0;

		for (Sample s : list) {
			double v = fn.applyAsDouble(s);

			if (!Double.isNaN(v)) {
				sum += v;
				n++;
			}
		}

		return n == 0 ? Double.NaN : sum / n;
	}

	private static double max(List<Sample> list, java.util.function.ToDoubleFunction<Sample> fn) {
		double best = Double.NaN;

		for (Sample s : list) {
			double v = fn.applyAsDouble(s);

			if (!Double.isNaN(v) && (Double.isNaN(best) || v > best)) {
				best = v;
			}
		}

		return best;
	}

	private static String dominantBottleneck(List<Sample> list) {
		Map<String, Integer> counts = new LinkedHashMap<>();

		for (Sample s : list) {
			if (s.bottleneck() != null) {
				counts.merge(s.bottleneck(), 1, Integer::sum);
			}
		}

		return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("UNKNOWN");
	}
}
