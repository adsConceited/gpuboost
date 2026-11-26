package dev.vconceited.gpuboost.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class BottleneckDetector {
	public static final int HISTORY = 5;

	public record Verdict(Bottleneck bottleneck, double confidence, String reason) {
		public static final Verdict UNKNOWN = new Verdict(Bottleneck.UNKNOWN, 0, "collecting data");
	}

	private final Deque<Verdict> history = new ArrayDeque<>(HISTORY + 1);
	private Verdict current = Verdict.UNKNOWN;

	public Verdict current() {
		return this.current;
	}

	public void reset() {
		this.history.clear();
		this.current = Verdict.UNKNOWN;
	}

	public Verdict update(WindowSample sample) {
		Verdict raw = classify(sample);

		if (raw.bottleneck() == Bottleneck.UNKNOWN) {
			return this.current;
		}

		this.history.addLast(raw);

		while (this.history.size() > HISTORY) {
			this.history.removeFirst();
		}

		this.current = smooth();
		return this.current;
	}

	private Verdict smooth() {
		Map<Bottleneck, Integer> counts = new EnumMap<>(Bottleneck.class);
		Map<Bottleneck, Double> confSum = new EnumMap<>(Bottleneck.class);

		for (Verdict v : this.history) {
			counts.merge(v.bottleneck(), 1, Integer::sum);
			confSum.merge(v.bottleneck(), v.confidence(), Double::sum);
		}

		Verdict latest = this.history.peekLast();
		Bottleneck best = latest.bottleneck();
		int bestCount = counts.get(best);

		for (Map.Entry<Bottleneck, Integer> e : counts.entrySet()) {
			if (e.getValue() > bestCount) {
				best = e.getKey();
				bestCount = e.getValue();
			}
		}

		double agreement = bestCount / (double) this.history.size();
		double conf = confSum.get(best) / bestCount * agreement;

		String reason = latest.bottleneck() == best ? latest.reason() : findLatestReason(best);
		return new Verdict(best, conf, reason);
	}

	private String findLatestReason(Bottleneck b) {
		Verdict found = null;

		for (Verdict v : this.history) {
			if (v.bottleneck() == b) {
				found = v;
			}
		}

		return found != null ? found.reason() : "";
	}

	public static Verdict classify(WindowSample s) {
		if (!s.focused() || s.frames() < 3 || s.wallMs() <= 0) {
			return Verdict.UNKNOWN;
		}

		double fps = s.fps();
		double busy = s.renderThreadBusyFraction();
		double gcFrac = s.gcMs() / s.wallMs();
		boolean systemSaturated = !Double.isNaN(s.systemCpuPct()) && s.systemCpuPct() >= 90;

		if (gcFrac >= 0.05 || (s.heapUsedFraction() >= 0.92 && gcFrac >= 0.02)) {
			return new Verdict(Bottleneck.MEMORY_GC, Math.min(1.0, 0.5 + gcFrac * 5),
					String.format(Locale.ROOT, "%.1f%% of time in GC, heap %.0f%% full", gcFrac * 100, s.heapUsedFraction() * 100));
		}

		boolean nearLimit = s.fpsLimit() > 0 && fps >= s.fpsLimit() * 0.93;

		if (nearLimit || s.vsync()) {
			boolean cpuSaturated = !Double.isNaN(busy) && busy >= 0.92;
			boolean gpuSaturated = !Double.isNaN(s.gpuUtilPct()) && s.gpuUtilPct() >= 97;

			if (nearLimit || (!cpuSaturated && !gpuSaturated)) {
				String why = nearLimit
						? String.format(Locale.ROOT, "%.0f FPS at a %d FPS limit", fps, s.fpsLimit())
						: "VSync is enabled";
				return new Verdict(Bottleneck.FPS_CAP, 0.9, why);
			}
		}

		int gpuVotes = 0;
		int cpuVotes = 0;
		StringBuilder why = new StringBuilder();

		if (!Double.isNaN(s.gpuLatencyMs()) && s.avgFrameMs() > 0) {
			double latencyFrac = s.gpuLatencyMs() / s.avgFrameMs();

			if (latencyFrac >= 0.5) {
				gpuVotes += 2;
				append(why, String.format(Locale.ROOT, "GPU finishes %.1f ms after submit", s.gpuLatencyMs()));
			} else if (latencyFrac <= 0.2) {
				cpuVotes += 1;
				append(why, String.format(Locale.ROOT, "GPU idle-waits (latency %.1f ms)", s.gpuLatencyMs()));
			}
		}

		if (!Double.isNaN(s.gpuUtilPct())) {
			if (s.gpuUtilPct() >= 92) {
				gpuVotes += 2;
				append(why, String.format(Locale.ROOT, "GPU %.0f%% busy", s.gpuUtilPct()));
			} else if (s.gpuUtilPct() <= 60) {
				cpuVotes += 1;
				append(why, String.format(Locale.ROOT, "GPU only %.0f%% busy", s.gpuUtilPct()));
			}
		}

		if (!Double.isNaN(busy)) {
			if (busy >= 0.85) {
				cpuVotes += 2;
				append(why, String.format(Locale.ROOT, "render thread %.0f%% busy", busy * 100));
			} else if (busy <= 0.55) {
				gpuVotes += 1;
				append(why, String.format(Locale.ROOT, "render thread waits %.0f%% of the time", (1 - busy) * 100));
			}
		}

		if (systemSaturated) {
			append(why, String.format(Locale.ROOT, "system CPU %.0f%%", s.systemCpuPct()));
		}

		int total = gpuVotes + cpuVotes;

		if (gpuVotes > cpuVotes) {
			double conf = 0.4 + 0.6 * (gpuVotes - cpuVotes) / (double) Math.max(total, 3);
			return new Verdict(Bottleneck.GPU, Math.min(1.0, conf), why.toString());
		}

		if (cpuVotes > gpuVotes) {
			double conf = 0.4 + 0.6 * (cpuVotes - gpuVotes) / (double) Math.max(total, 3);
			Bottleneck b = systemSaturated && (Double.isNaN(busy) || busy < 0.85) ? Bottleneck.CPU_SYSTEM : Bottleneck.CPU_MAIN_THREAD;
			return new Verdict(b, Math.min(1.0, conf), why.toString());
		}

		if (systemSaturated) {
			return new Verdict(Bottleneck.CPU_SYSTEM, 0.5, why.toString());
		}

		return new Verdict(Bottleneck.BALANCED, total == 0 ? 0.3 : 0.5, why.isEmpty() ? "no saturated component" : why.toString());
	}

	private static void append(StringBuilder sb, String part) {
		if (!sb.isEmpty()) {
			sb.append(", ");
		}

		sb.append(part);
	}
}
