package dev.vconceited.gpuboost.client.bench;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import dev.vconceited.gpuboost.client.GpuBoostClient;
import dev.vconceited.gpuboost.client.GpuBoostRuntime;
import dev.vconceited.gpuboost.client.Reflect;
import dev.vconceited.gpuboost.client.compat.ModCompat;
import dev.vconceited.gpuboost.client.gpu.GpuInfo;
import dev.vconceited.gpuboost.core.BenchmarkRecorder;
import dev.vconceited.gpuboost.core.BenchmarkStore;
import dev.vconceited.gpuboost.core.GpuBoostConfig;

public final class BenchmarkController {
	private final GpuBoostRuntime runtime;
	private final BenchmarkRecorder recorder = new BenchmarkRecorder();
	private String label = "";
	private boolean passive;
	private boolean spin;
	private double spinDps;
	private float startYaw;
	private float startPitch;
	private long startNs;
	private volatile String lastResultLine = "";

	public BenchmarkController(GpuBoostRuntime runtime) {
		this.runtime = runtime;
	}

	public boolean isActive() {
		return this.recorder.isActive();
	}

	public boolean isPassive() {
		return this.passive && isActive();
	}

	public String lastResultLine() {
		return this.lastResultLine;
	}

	public String statusLine(long nowNs) {
		return switch (this.recorder.state()) {
			case WARMUP -> "Benchmark '" + this.label + "': warming up...";
			case RECORDING -> String.format(Locale.ROOT, "Benchmark '%s'%s: %.0f%%", this.label, this.passive ? " (passive)" : "",
					this.recorder.progress(nowNs) * 100);
			default -> "";
		};
	}

	public String start(int durationSec, String mode, String rawLabel, boolean passiveRun) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;

		if (isActive()) {
			return "A benchmark is already running.";
		}

		if (player == null || mc.level == null) {
			return "Join a world first (use the same world, position and settings for every run).";
		}

		GpuBoostConfig cfg = this.runtime.config();
		this.label = BenchmarkStore.sanitizeLabel(rawLabel == null || rawLabel.isBlank() ? (passiveRun ? "baseline" : "gpuboost") : rawLabel);
		this.passive = passiveRun;
		this.spin = !"static".equals(mode);
		this.spinDps = cfg.spinDegreesPerSecond;
		this.startYaw = player.getYRot();
		this.startPitch = player.getXRot();
		this.startNs = System.nanoTime();

		this.runtime.setPassive(passiveRun);
		this.recorder.start(this.startNs, cfg.benchWarmupSec, durationSec, buildMetadata(mc, cfg, durationSec));
		GpuBoostClient.LOGGER.info("[GpuBoost] Benchmark '{}' started ({} s, mode {}, passive {})", this.label, durationSec, mode, passiveRun);
		return null;
	}

	public void cancel(String reason) {
		if (!isActive()) {
			return;
		}

		this.recorder.cancel();
		restoreCamera();
		this.runtime.setPassive(false);
		message("GpuBoost benchmark cancelled: " + reason);
	}

	public void onFrame(long nowNs, float frameMs) {
		if (!isActive()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;

		if (player == null) {
			cancel("left the world");
			return;
		}

		if (this.spin) {
			double sec = (nowNs - this.startNs) / 1e9;
			player.setYRot((float) (this.startYaw + this.spinDps * sec) % 360f);
			player.setXRot(this.startPitch);
		}

		if (this.recorder.onFrame(nowNs, frameMs) == BenchmarkRecorder.State.DONE) {
			complete();
		}
	}

	public void addSample(BenchmarkRecorder.Sample sample) {
		this.recorder.addSample(sample);
	}

	public double recordingSeconds(long nowNs) {
		return this.recorder.recordingSeconds(nowNs);
	}

	private void complete() {
		BenchmarkRecorder.Result result = this.recorder.finish();
		restoreCamera();
		this.runtime.setPassive(false);

		String summary = String.format(Locale.ROOT, "%s: avg %.1f FPS | 1%% low %.1f | 0.1%% low %.1f | p99 %.2f ms | %d stutters",
				this.label, result.summary().avgFps(), result.summary().onePercentLowFps(), result.summary().pointOnePercentLowFps(),
				result.summary().p99Ms(), result.summary().stutters());
		this.lastResultLine = summary;
		message("GpuBoost benchmark finished - " + summary);

		Path dir = this.runtime.benchmarkDir();
		String fileLabel = this.label;
		Consumer<String> report = this::message;

		Thread writer = new Thread(() -> {
			try {
				Path json = BenchmarkStore.save(dir, fileLabel, result, LocalDateTime.now());
				Minecraft.getInstance().execute(() -> report.accept("Saved " + json.getFileName() + " (+ .frames.csv) in gpuboost/benchmarks"));
			} catch (IOException e) {
				GpuBoostClient.LOGGER.error("[GpuBoost] Could not save benchmark", e);
				Minecraft.getInstance().execute(() -> report.accept("Could not save benchmark: " + e.getMessage()));
			}
		}, "GpuBoost-benchmark-writer");
		writer.setDaemon(true);
		writer.start();
	}

	private void restoreCamera() {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player != null && this.spin) {
			player.setYRot(this.startYaw);
			player.setXRot(this.startPitch);
		}
	}

	private void message(String text) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player != null) {
			player.sendSystemMessage(Component.literal(text));
		}

		GpuBoostClient.LOGGER.info("[GpuBoost] {}", text);
	}

	private Map<String, Object> buildMetadata(Minecraft mc, GpuBoostConfig cfg, int durationSec) {
		Map<String, Object> m = new LinkedHashMap<>();
		GpuInfo gpu = this.runtime.gpu();
		boolean sodium = ModCompat.sodium();
		boolean shaders = ModCompat.irisShadersActive();
		String configuration = (sodium ? "sodium" : "vanilla") + (this.passive ? "" : "+gpuboost") + (shaders ? "+shaders" : "");

		m.put("label", this.label);
		m.put("configuration", configuration);
		m.put("passive", this.passive);
		m.put("mode", this.spin ? "spin" : "static");
		m.put("spinDegreesPerSecond", this.spin ? this.spinDps : 0);
		m.put("durationSec", durationSec);
		m.put("warmupSec", cfg.benchWarmupSec);
		m.put("startedAt", LocalDateTime.now().toString());
		m.put("minecraft", ModCompat.version("minecraft").orElse("?"));
		m.put("gpuboost", ModCompat.version(GpuBoostClient.MOD_ID).orElse("?"));
		m.put("sodium", ModCompat.version("sodium").orElse("not installed"));
		m.put("iris", ModCompat.version("iris").orElse("not installed"));
		m.put("shaderPackActive", shaders);
		m.put("otherMods", String.join(", ", ModCompat.installedSummary()));
		m.put("gpuboostProfile", cfg.profile.name());
		m.put("java", System.getProperty("java.version"));
		m.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
		m.put("cpuThreads", Runtime.getRuntime().availableProcessors());
		m.put("maxHeapMb", Math.round(Runtime.getRuntime().maxMemory() / (1024.0 * 1024.0)));
		m.put("gpu", gpu.deviceName());
		m.put("gpuVendor", gpu.vendor().displayName);
		m.put("graphicsBackend", gpu.backend());
		m.put("driver", gpu.driver());
		m.put("sodiumDrawBackend", sodium ? ModCompat.sodiumDrawBackend() : "n/a");

		try {
			var o = mc.options;
			m.put("renderDistance", o.renderDistance().get());
			m.put("simulationDistance", o.simulationDistance().get());
			m.put("framerateLimit", o.framerateLimit().get());
			m.put("vsync", o.enableVsync().get());
			m.put("particles", String.valueOf(o.particles().get()));
			m.put("clouds", String.valueOf(o.cloudStatus().get()));
			m.put("entityShadows", o.entityShadows().get());
			m.put("biomeBlendRadius", o.biomeBlendRadius().get());
			m.put("improvedTransparency", o.improvedTransparency().get());
		} catch (RuntimeException | LinkageError e) {
			m.put("optionsError", e.toString());
		}

		Object window = Reflect.call(mc, "getWindow");
		m.put("windowWidth", Reflect.intCall(window, "getWidth", -1));
		m.put("windowHeight", Reflect.intCall(window, "getHeight", -1));

		LocalPlayer p = mc.player;

		if (p != null) {
			m.put("position", String.format(Locale.ROOT, "%.1f %.1f %.1f", p.getX(), p.getY(), p.getZ()));
			m.put("yaw", p.getYRot());
			m.put("pitch", p.getXRot());
		}

		m.put("entityCulling", cfg.entityCulling && !this.passive);
		m.put("entityCullDistance", cfg.entityCullDistance);
		m.put("adaptiveEngine", cfg.adaptiveEngine && !this.passive);
		return m;
	}
}
