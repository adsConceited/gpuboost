package dev.vconceited.gpuboost.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BenchmarkTest {
	private static final long MS = 1_000_000L;

	private static BenchmarkRecorder.Result simulate(String label, String configuration, float frameMs, int warmupSec, int durationSec) {
		BenchmarkRecorder r = new BenchmarkRecorder();
		long t = 0;
		r.start(t, warmupSec, durationSec, Map.of("label", label, "configuration", configuration));
		double lastSample = 0;

		while (true) {
			t += (long) (frameMs * MS);
			BenchmarkRecorder.State s = r.onFrame(t, frameMs);
			double sec = r.recordingSeconds(t);

			if (s == BenchmarkRecorder.State.RECORDING && sec - lastSample >= 1) {
				lastSample = sec;
				r.addSample(new BenchmarkRecorder.Sample(sec, 1000 / frameMs, 40, 55, 70, 90, 3, 2048, 3000, "GPU"));
			}

			if (s == BenchmarkRecorder.State.DONE) {
				break;
			}
		}

		return r.finish();
	}

	@Test
	void warmupIsExcludedAndDurationRespected() {
		BenchmarkRecorder.Result res = simulate("a", "sodium", 10f, 2, 5);

		assertEquals(500, res.summary().frames(), 1);
		assertEquals(100.0, res.summary().avgFps(), 1e-6);
		assertTrue(res.samples().size() >= 4);
	}

	@Test
	void jsonSummaryContainsMeasuredValuesOnly() {
		BenchmarkRecorder.Result res = simulate("b", "vanilla", 20f, 0, 3);
		Map<String, Object> json = res.toJsonMap();
		Map<String, Object> s = MiniJson.getObject(json, "summary");
		assertEquals(50.0, ((Number) s.get("avgFps")).doubleValue(), 1e-6);
		assertEquals("GPU", s.get("dominantBottleneck"));
		assertEquals(90.0, ((Number) s.get("avgGpuUtilPct")).doubleValue(), 1e-9);
		assertEquals("gpuboost-benchmark-v1", json.get("format"));
	}

	@Test
	void saveReadAndCompare() throws Exception {
		Path dir = Files.createTempDirectory("gpuboost-bench");
		LocalDateTime now = LocalDateTime.of(2026, 11, 26, 10, 0, 0);
		Path a = BenchmarkStore.save(dir, "baseline run", simulate("baseline", "vanilla", 20f, 0, 3), now);
		Path b = BenchmarkStore.save(dir, "with/gpuboost", simulate("gb", "sodium+gpuboost", 10f, 0, 3), now.plusMinutes(1));

		assertTrue(a.getFileName().toString().startsWith("20261126-100000_baseline_run"));
		assertTrue(b.getFileName().toString().contains("with_gpuboost"));
		assertTrue(Files.exists(dir.resolve(a.getFileName().toString().replace(".json", ".frames.csv"))));

		List<String> csv = Files.readAllLines(dir.resolve(a.getFileName().toString().replace(".json", ".frames.csv")), StandardCharsets.UTF_8);
		assertEquals("frame,frame_time_ms", csv.get(0));
		assertEquals("0,20.0000", csv.get(1));

		List<Path> files = BenchmarkStore.listSummaries(dir);
		assertEquals(2, files.size());

		BenchmarkStore.Row ra = BenchmarkStore.readRow(files.get(0));
		BenchmarkStore.Row rb = BenchmarkStore.readRow(files.get(1));
		assertEquals(50.0, ra.avgFps(), 1e-6);
		assertEquals(100.0, rb.avgFps(), 1e-6);

		String table = BenchmarkStore.compareTable(List.of(ra, rb));
		assertTrue(table.contains("+100.0%"), table);
		assertTrue(table.contains("sodium+gpuboost"), table);
	}

	@Test
	void labelsAreSanitized() {
		assertEquals("run", BenchmarkStore.sanitizeLabel("  "));
		assertEquals("a_b_c", BenchmarkStore.sanitizeLabel("a b/c"));
		assertEquals(48, BenchmarkStore.sanitizeLabel("x".repeat(100)).length());
	}
}
