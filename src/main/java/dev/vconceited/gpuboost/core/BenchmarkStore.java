package dev.vconceited.gpuboost.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BenchmarkStore {
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

	private BenchmarkStore() {
	}

	public static String sanitizeLabel(String label) {
		if (label == null || label.isBlank()) {
			return "run";
		}

		String s = label.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
		return s.length() > 48 ? s.substring(0, 48) : s;
	}

	public static Path save(Path dir, String label, BenchmarkRecorder.Result result, LocalDateTime now) throws IOException {
		Files.createDirectories(dir);
		String base = STAMP.format(now) + "_" + sanitizeLabel(label);
		Path json = dir.resolve(base + ".json");
		Path csv = dir.resolve(base + ".frames.csv");

		GpuBoostConfig.writeAtomically(json, MiniJson.write(result.toJsonMap()));

		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("frame,frame_time_ms\n");
			float[] f = result.frameTimesMs();

			for (int k = 0; k < f.length; k++) {
				w.write(Integer.toString(k));
				w.write(',');
				w.write(String.format(Locale.ROOT, "%.4f", f[k]));
				w.write('\n');
			}
		}

		return json;
	}

	public static List<Path> listSummaries(Path dir) throws IOException {
		List<Path> out = new ArrayList<>();

		if (!Files.isDirectory(dir)) {
			return out;
		}

		try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
			for (Path p : ds) {
				out.add(p);
			}
		}

		out.sort(Comparator.comparing(p -> p.getFileName().toString()));
		return out;
	}

	public record Row(String name, String label, String mods, double avgFps, double low1, double low01, double p99Ms,
	                  double stdDevMs, int stutters, double cpu, double gpu, double heapMb, double vramMb, String bottleneck) {
	}

	@SuppressWarnings("unchecked")
	public static Row readRow(Path json) throws IOException {
		Map<String, Object> root = MiniJson.parseObject(Files.readString(json, StandardCharsets.UTF_8));
		Map<String, Object> meta = MiniJson.getObject(root, "metadata");
		Map<String, Object> s = MiniJson.getObject(root, "summary");

		if (meta == null || s == null) {
			throw new IOException("Not a GpuBoost benchmark file: " + json.getFileName());
		}

		String name = json.getFileName().toString().replaceFirst("\\.json$", "");
		return new Row(name,
				MiniJson.getString(meta, "label", "?"),
				MiniJson.getString(meta, "configuration", "?"),
				MiniJson.getDouble(s, "avgFps", Double.NaN),
				MiniJson.getDouble(s, "onePercentLowFps", Double.NaN),
				MiniJson.getDouble(s, "pointOnePercentLowFps", Double.NaN),
				MiniJson.getDouble(s, "p99FrameMs", Double.NaN),
				MiniJson.getDouble(s, "frameTimeStdDevMs", Double.NaN),
				MiniJson.getInt(s, "stutters", 0),
				MiniJson.getDouble(s, "avgProcessCpuPct", Double.NaN),
				MiniJson.getDouble(s, "avgGpuUtilPct", Double.NaN),
				MiniJson.getDouble(s, "peakHeapUsedMb", Double.NaN),
				MiniJson.getDouble(s, "peakVramUsedMb", Double.NaN),
				MiniJson.getString(s, "dominantBottleneck", "?"));
	}

	public static String compareTable(List<Row> rows) {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format(Locale.ROOT, "%-34s %-22s %8s %8s %8s %8s %7s %6s %6s %8s%n",
				"run", "configuration", "avg FPS", "1% low", "0.1%low", "p99 ms", "stutter", "CPU%", "GPU%", "vs #1"));

		double base = rows.isEmpty() ? Double.NaN : rows.get(0).avgFps();

		for (Row r : rows) {
			String rel = Double.isNaN(base) || base <= 0 || Double.isNaN(r.avgFps())
					? "n/a" : String.format(Locale.ROOT, "%+.1f%%", (r.avgFps() / base - 1) * 100);
			sb.append(String.format(Locale.ROOT, "%-34s %-22s %8s %8s %8s %8s %7d %6s %6s %8s%n",
					trim(r.name(), 34), trim(r.mods(), 22), fmt(r.avgFps(), 1), fmt(r.low1(), 1), fmt(r.low01(), 1),
					fmt(r.p99Ms(), 2), r.stutters(), fmt(r.cpu(), 0), fmt(r.gpu(), 0), rel));
		}

		return sb.toString();
	}

	private static String trim(String s, int n) {
		return s.length() <= n ? s : s.substring(0, n - 1) + "~";
	}

	private static String fmt(double v, int digits) {
		return Double.isNaN(v) ? "n/a" : String.format(Locale.ROOT, "%." + digits + "f", v);
	}
}
