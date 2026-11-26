package dev.vconceited.gpuboost.client.gpu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import dev.vconceited.gpuboost.client.GpuBoostClient;
import dev.vconceited.gpuboost.core.GpuTelemetry;

public final class NvidiaSmiMonitor implements TelemetrySource {
	private static final String QUERY = "--query-gpu=utilization.gpu,memory.used,memory.total,temperature.gpu,clocks.gr,power.draw";

	private final String deviceName;
	private volatile GpuTelemetry latest = GpuTelemetry.NONE;
	private volatile boolean alive = true;
	private volatile boolean stopping;
	private volatile Process process;
	private Thread shutdownHook;
	private final Thread reader;

	private NvidiaSmiMonitor(String deviceName) {
		this.deviceName = deviceName == null ? "" : deviceName;
		this.reader = new Thread(this::run, "GpuBoost-nvidia-smi");
		this.reader.setDaemon(true);
		this.reader.setPriority(Thread.MIN_PRIORITY);
	}

	public static NvidiaSmiMonitor start(String deviceName) {
		NvidiaSmiMonitor m = new NvidiaSmiMonitor(deviceName);
		m.reader.start();
		return m;
	}

	@Override
	public String name() {
		return "nvidia-smi";
	}

	@Override
	public GpuTelemetry latest() {
		return this.latest;
	}

	@Override
	public boolean isAlive() {
		return this.alive;
	}

	@Override
	public void stop() {
		this.stopping = true;
		Process p = this.process;

		if (p != null) {
			p.destroy();
		}

		removeShutdownHook();
	}

	private void run() {
		try {
			String exe = findExecutable();

			if (exe == null) {
				GpuBoostClient.LOGGER.info("[GpuBoost] nvidia-smi not found; GPU utilization will not be shown.");
				return;
			}

			String index = selectGpuIndex(exe);

			if (this.stopping) {
				return;
			}

			List<String> cmd = new ArrayList<>(List.of(exe, QUERY, "--format=csv,noheader,nounits", "-lms", "1000"));

			if (index != null) {
				cmd.add("-i");
				cmd.add(index);
			}

			Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
			this.process = p;
			installShutdownHook(p);

			try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String line;

				while (!this.stopping && (line = br.readLine()) != null) {
					GpuTelemetry t = GpuTelemetry.parseNvidiaSmi(line, System.currentTimeMillis());

					if (t != null) {
						this.latest = t;
					}
				}
			}
		} catch (IOException e) {
			if (!this.stopping) {
				GpuBoostClient.LOGGER.info("[GpuBoost] nvidia-smi telemetry unavailable: {}", e.getMessage());
			}
		} finally {
			this.alive = false;
			Process p = this.process;

			if (p != null) {
				p.destroy();
			}

			removeShutdownHook();
		}
	}

	private String selectGpuIndex(String exe) {
		try {
			Process p = new ProcessBuilder(exe, "--query-gpu=index,name", "--format=csv,noheader").redirectErrorStream(true).start();
			List<String> lines = new ArrayList<>();

			try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String line;

				while ((line = br.readLine()) != null && lines.size() < 16) {
					lines.add(line);
				}
			}

			if (!p.waitFor(5, TimeUnit.SECONDS)) {
				p.destroyForcibly();
			}

			if (lines.size() <= 1) {
				return null;
			}

			String want = this.deviceName.toLowerCase(Locale.ROOT);

			for (String l : lines) {
				int comma = l.indexOf(',');

				if (comma > 0) {
					String idx = l.substring(0, comma).trim();
					String name = l.substring(comma + 1).trim().toLowerCase(Locale.ROOT);

					if (!name.isEmpty() && (want.contains(name) || name.contains(want))) {
						return idx;
					}
				}
			}
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		return null;
	}

	private static String findExecutable() {
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		List<String> candidates = new ArrayList<>();

		if (windows) {
			String sysRoot = System.getenv().getOrDefault("SystemRoot", "C:\\Windows");
			candidates.add(sysRoot + "\\System32\\nvidia-smi.exe");
			String pf = System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files");
			candidates.add(pf + "\\NVIDIA Corporation\\NVSMI\\nvidia-smi.exe");
		} else {
			candidates.add("/usr/bin/nvidia-smi");
			candidates.add("/usr/local/bin/nvidia-smi");
			candidates.add("/opt/bin/nvidia-smi");
		}

		for (String c : candidates) {
			if (Files.isRegularFile(Path.of(c))) {
				return c;
			}
		}

		String path = System.getenv("PATH");

		if (path != null) {
			for (String dir : path.split(java.io.File.pathSeparator)) {
				Path p = Path.of(dir, windows ? "nvidia-smi.exe" : "nvidia-smi");

				if (Files.isRegularFile(p)) {
					return p.toString();
				}
			}
		}

		return null;
	}

	private synchronized void installShutdownHook(Process p) {
		try {
			this.shutdownHook = new Thread(p::destroy, "GpuBoost-nvidia-smi-cleanup");
			Runtime.getRuntime().addShutdownHook(this.shutdownHook);
		} catch (IllegalStateException e) {
			p.destroy();
		}
	}

	private synchronized void removeShutdownHook() {
		if (this.shutdownHook != null) {
			try {
				Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
			} catch (IllegalStateException ignored) {
			}

			this.shutdownHook = null;
		}
	}
}
