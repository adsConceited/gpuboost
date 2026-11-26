package dev.vconceited.gpuboost.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import dev.vconceited.gpuboost.client.bench.BenchmarkController;
import dev.vconceited.gpuboost.client.compat.ModCompat;
import dev.vconceited.gpuboost.client.gpu.AmdSysfsMonitor;
import dev.vconceited.gpuboost.client.gpu.DeviceInspector;
import dev.vconceited.gpuboost.client.gpu.GlTimer;
import dev.vconceited.gpuboost.client.gpu.GpuInfo;
import dev.vconceited.gpuboost.client.gpu.GpuProbeSink;
import dev.vconceited.gpuboost.client.gpu.NvidiaSmiMonitor;
import dev.vconceited.gpuboost.client.gpu.TelemetrySource;
import dev.vconceited.gpuboost.client.monitor.ChunkMetrics;
import dev.vconceited.gpuboost.client.monitor.FrameMonitor;
import dev.vconceited.gpuboost.client.monitor.JvmMetrics;
import dev.vconceited.gpuboost.client.optimize.EntityCulling;
import dev.vconceited.gpuboost.client.optimize.VanillaOptionManager;
import dev.vconceited.gpuboost.core.AdaptiveController;
import dev.vconceited.gpuboost.core.BenchmarkRecorder;
import dev.vconceited.gpuboost.core.BottleneckDetector;
import dev.vconceited.gpuboost.core.FrameStats;
import dev.vconceited.gpuboost.core.GpuBoostConfig;
import dev.vconceited.gpuboost.core.GpuTelemetry;
import dev.vconceited.gpuboost.core.GpuVendor;
import dev.vconceited.gpuboost.core.MiniJson;
import dev.vconceited.gpuboost.core.PerformanceProfile;
import dev.vconceited.gpuboost.core.WindowSample;

public final class GpuBoostRuntime implements FrameMonitor.Callback {
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
	private static final long RENDER_DISTANCE_COOLDOWN_MS = 30_000;
	private static GpuBoostRuntime instance;

	private final Path configFile;
	private final Path dataDir;
	private final GpuBoostConfig config;
	private final JvmMetrics jvm = new JvmMetrics();
	private final FrameMonitor frames;
	private final BottleneckDetector detector = new BottleneckDetector();
	private final AdaptiveController adaptive = new AdaptiveController();
	private final ChunkMetrics chunks = new ChunkMetrics();
	private final VanillaOptionManager vanilla;
	private final BenchmarkController bench;

	private volatile GpuInfo gpu = GpuInfo.UNKNOWN;
	private TelemetrySource telemetry;
	private int telemetryStarts;
	private boolean started;
	private boolean passive;
	private Screen pendingScreen;

	private WindowSample lastWindow;
	private FrameStats.Summary rolling = FrameStats.Summary.EMPTY;
	private int recommendedRenderDistance = -1;
	private long[] lastEntityCounters = {0, 0};
	private int rdStableWindows;
	private int lastRdRecommendation = -1;
	private long lastRdChangeMs;
	private long hookCheckDeadlineNs;
	private boolean warnedMissingFrameHook;
	private long lastFrameHookNs;

	private GpuBoostRuntime(Path configFile, Path dataDir) {
		this.configFile = configFile;
		this.dataDir = dataDir;
		this.config = GpuBoostConfig.load(configFile);
		this.frames = new FrameMonitor(this.jvm, this);
		this.frames.setIntervalMs(this.config.evaluationIntervalMs);
		this.vanilla = new VanillaOptionManager(this.config, this::saveConfig);
		this.bench = new BenchmarkController(this);
		GpuProbeSink.setListener(this::onOpenGlProbe);
	}

	public static GpuBoostRuntime init(Path configFile, Path dataDir) {
		instance = new GpuBoostRuntime(configFile, dataDir);
		return instance;
	}

	public static GpuBoostRuntime get() {
		return instance;
	}

	public GpuBoostConfig config() {
		return this.config;
	}

	public FrameMonitor frames() {
		return this.frames;
	}

	public BottleneckDetector.Verdict verdict() {
		return this.detector.current();
	}

	public AdaptiveController adaptive() {
		return this.adaptive;
	}

	public ChunkMetrics chunks() {
		return this.chunks;
	}

	public JvmMetrics jvm() {
		return this.jvm;
	}

	public VanillaOptionManager vanilla() {
		return this.vanilla;
	}

	public BenchmarkController bench() {
		return this.bench;
	}

	public GpuInfo gpu() {
		return this.gpu;
	}

	public WindowSample lastWindow() {
		return this.lastWindow;
	}

	public FrameStats.Summary rolling() {
		return this.rolling;
	}

	public int recommendedRenderDistance() {
		return this.recommendedRenderDistance;
	}

	public long[] lastEntityCounters() {
		return this.lastEntityCounters;
	}

	public boolean isPassive() {
		return this.passive;
	}

	public Path benchmarkDir() {
		return this.dataDir.resolve("benchmarks");
	}

	public Path exportDir() {
		return this.dataDir.resolve("exports");
	}

	public Path dataDir() {
		return this.dataDir;
	}

	public String telemetryName() {
		TelemetrySource t = this.telemetry;
		return t != null && t.isAlive() ? t.name() : "none";
	}

	public GpuTelemetry telemetry() {
		TelemetrySource t = this.telemetry;

		if (t == null) {
			return GpuTelemetry.NONE;
		}

		GpuTelemetry v = t.latest();
		return v.isFresh(System.currentTimeMillis(), 3000) ? v : GpuTelemetry.NONE;
	}

	public void openScreenNextTick(Screen screen) {
		this.pendingScreen = screen;
	}

	public void onClientStarted(Minecraft mc) {
		this.started = true;
		GpuInfo fromDevice = DeviceInspector.inspect();
		GpuInfo current = this.gpu;

		this.gpu = current == GpuInfo.UNKNOWN ? fromDevice
				: (fromDevice == GpuInfo.UNKNOWN ? current
				: fromDevice.withOpenGl(current.vendorName(), current.deviceName(), current.glVersion(), current.extensions()));

		this.vanilla.recoverAfterCrash();
		applyRuntimeSettings();
		this.hookCheckDeadlineNs = System.nanoTime() + 15_000_000_000L;
		GpuBoostClient.LOGGER.info("[GpuBoost] Ready. Profile {}, installed optimization mods: {}", this.config.profile.displayName,
				ModCompat.installedSummary());
	}

	public void onClientStopping(Minecraft mc) {
		this.bench.cancel("game closing");
		this.vanilla.restoreTemporary();
		stopTelemetry();
		saveConfig();
	}

	public void onDisconnect() {
		this.bench.cancel("disconnected");
		this.adaptive.reset();
		this.detector.reset();
		this.vanilla.restoreTemporary();
		applyCulling();
	}

	public void onClientTick(Minecraft mc) {
		if (this.pendingScreen != null) {
			Screen s = this.pendingScreen;
			this.pendingScreen = null;
			mc.gui.setScreen(s);
		}

		long now = System.nanoTime();

		if (this.started && !this.warnedMissingFrameHook && now > this.hookCheckDeadlineNs && now - this.lastFrameHookNs > 5_000_000_000L) {
			this.warnedMissingFrameHook = true;
			GpuBoostClient.LOGGER.warn("[GpuBoost] The frame hook (Minecraft.renderFrame) is not firing; monitoring and adaptive features are inactive."
					+ " This usually means a Minecraft/mod version mismatch.");
		}
	}

	private void onOpenGlProbe(String vendor, String renderer, String version, Set<String> ext) {
		GpuInfo g = this.gpu;
		this.gpu = g.withOpenGl(vendor, renderer, version, ext);
	}

	public static void onRenderFrameStart() {
		GpuBoostRuntime rt = instance;

		if (rt != null) {
			long now = System.nanoTime();
			rt.lastFrameHookNs = now;
			rt.frames.onFrameStart(now);
		}
	}

	@Override
	public void onFrame(long nowNs, float frameMs) {
		this.bench.onFrame(nowNs, frameMs);
	}

	@Override
	public void onWindow(FrameMonitor.Window w) {
		Minecraft mc = Minecraft.getInstance();
		long nowMs = System.currentTimeMillis();

		TelemetrySource t = this.telemetry;

		if (t != null) {
			t.poll();
		}

		GpuTelemetry tel = telemetry();
		double[] gl = GlTimer.consumeWindowAverages();
		int fpsLimit = 0;
		boolean vsync = false;

		try {
			int limit = mc.options.framerateLimit().get();
			fpsLimit = limit >= 260 ? 0 : limit;
			vsync = mc.options.enableVsync().get();
		} catch (RuntimeException | LinkageError ignored) {
		}

		boolean focused = mc.level != null && Reflect.call(mc, "isWindowActive") != Boolean.FALSE;
		double heapFrac = this.jvm.heapUsedMb() / Math.max(1, this.jvm.heapMaxMb());

		WindowSample s = new WindowSample(w.wallMs(), w.frames(), w.frames() > 0 ? w.wallMs() / w.frames() : 0,
				w.renderThreadCpuMs(), gl[0], gl[1], tel.utilizationPct(), this.jvm.processCpuPct(), this.jvm.systemCpuPct(),
				w.gcMs(), heapFrac, fpsLimit, vsync, focused);

		this.lastWindow = s;
		this.rolling = this.frames.rollingSummary(FrameMonitor.ROLLING_WINDOWS);

		FrameStats.Summary shortTerm = this.frames.rollingSummary(3);
		this.lastEntityCounters = EntityCulling.consumeCounters();
		this.chunks.onWindow(w.wallMs() / 1000.0, w.endNs());

		BottleneckDetector.Verdict verdict = this.detector.update(s);

		if (this.config.adaptiveEngine && !this.passive && focused) {
			boolean changed = this.adaptive.update(verdict, s.fps(), shortTerm.onePercentLowFps(), this.config.targetFps, nowMs,
					this.config.adaptCooldownSec * 1000);

			if (changed) {
				GpuBoostClient.LOGGER.debug("[GpuBoost] Adaptive pressure -> {} ({})", this.adaptive.pressure(), verdict.bottleneck());
				applyCulling();

				if (this.config.adaptiveParticles) {
					this.vanilla.applyParticleReduction(this.adaptive.particleReductionSteps(this.config.weakCpuMode));
				}
			}
		}

		updateRenderDistance(mc, verdict, s, nowMs);

		if (this.bench.isActive()) {
			double vram = !Double.isNaN(tel.vramUsedMb()) ? tel.vramUsedMb() : GlTimer.vramUsedMb();
			this.bench.addSample(new BenchmarkRecorder.Sample(this.bench.recordingSeconds(w.endNs()), s.fps(), s.processCpuPct(),
					s.systemCpuPct(), s.renderThreadBusyFraction() * 100, s.gpuUtilPct(), s.gpuLatencyMs(), this.jvm.heapUsedMb(), vram,
					verdict.bottleneck().name()));
		}
	}

	private void updateRenderDistance(Minecraft mc, BottleneckDetector.Verdict verdict, WindowSample s, long nowMs) {
		int current;

		try {
			current = mc.options.renderDistance().get();
		} catch (RuntimeException | LinkageError e) {
			return;
		}

		int rec = AdaptiveController.recommendRenderDistance(current, verdict, s.fps(), this.rolling.onePercentLowFps(),
				this.config.targetFps, this.config.minRenderDistance, this.config.maxRenderDistance);
		this.recommendedRenderDistance = rec;

		if (!this.config.autoRenderDistance || this.passive || this.bench.isActive() || !s.focused()) {
			this.rdStableWindows = 0;
			return;
		}

		this.rdStableWindows = rec == this.lastRdRecommendation ? this.rdStableWindows + 1 : 0;
		this.lastRdRecommendation = rec;

		if (rec != current && this.rdStableWindows >= 3 && nowMs - this.lastRdChangeMs >= RENDER_DISTANCE_COOLDOWN_MS) {
			int step = Math.max(-2, Math.min(2, rec - current));
			this.vanilla.setTemporaryRenderDistance(current + step);
			this.lastRdChangeMs = nowMs;
			this.rdStableWindows = 0;
			GpuBoostClient.LOGGER.info("[GpuBoost] Auto render distance {} -> {}", current, current + step);
		}
	}

	public void setPassive(boolean value) {
		if (this.passive == value) {
			return;
		}

		this.passive = value;

		if (value) {
			this.adaptive.reset();
			this.vanilla.restoreTemporary();
		}

		applyCulling();
	}

	private void applyCulling() {
		EntityCulling.reconfigure(this.config, this.adaptive.entityDistanceMultiplier(this.config.weakCpuMode), this.passive);
	}

	public void applyRuntimeSettings() {
		this.config.validate();
		this.frames.setIntervalMs(this.config.evaluationIntervalMs);
		GlTimer.setEnabled(this.config.gpuTimerQueries);

		if (!this.config.adaptiveEngine) {
			this.adaptive.reset();
			this.vanilla.restoreTemporary();
		}

		if (!this.config.adaptiveParticles) {
			this.vanilla.applyParticleReduction(0);
		}

		if (!this.config.autoRenderDistance && this.vanilla.hasTemporary(VanillaOptionManager.RENDER_DISTANCE)) {
			this.vanilla.restoreTemporary();
		}

		applyCulling();
		restartTelemetry();
	}

	public List<String> setProfile(PerformanceProfile profile) {
		profile.applyTo(this.config);
		this.adaptive.reset();
		List<String> changes = reapplyTweaks();
		applyRuntimeSettings();
		saveConfig();
		return changes;
	}

	public void onFeatureChanged() {
		this.config.profile = PerformanceProfile.CUSTOM;
		applyRuntimeSettings();
		saveConfig();
	}

	public void setVanillaTweaks(boolean on) {
		this.config.applyVanillaTweaks = on;
		reapplyTweaks();
	}

	public List<String> reapplyTweaks() {
		this.vanilla.restoreTemporary();
		int restored = this.vanilla.hasSnapshot() ? this.vanilla.restoreSnapshot() : 0;

		if (!this.config.applyVanillaTweaks) {
			return restored > 0 ? List.of("restored " + restored + " video settings") : List.of();
		}

		PerformanceProfile set = this.config.profile == PerformanceProfile.MAX_FPS || this.config.profile == PerformanceProfile.WEAK_CPU
				? this.config.profile : PerformanceProfile.CUSTOM;
		return this.vanilla.applyProfileTweaks(set, this.config.weakCpuMode);
	}

	public void saveConfig() {
		try {
			this.config.save(this.configFile);
		} catch (IOException e) {
			GpuBoostClient.LOGGER.error("[GpuBoost] Could not save config", e);
		}
	}

	public Path exportConfig() throws IOException {
		Path out = exportDir().resolve("gpuboost-config-" + STAMP.format(LocalDateTime.now()) + ".json");
		GpuBoostConfig.writeAtomically(out, MiniJson.write(this.config.toJsonMap(false)));
		return out;
	}

	public Path importConfig(Path file) throws IOException {
		Path source = file;

		if (source == null) {
			Path dropIn = this.dataDir.resolve("import.json");
			source = Files.isRegularFile(dropIn) ? dropIn : newestExport();
		}

		if (source == null || !Files.isRegularFile(source)) {
			throw new IOException("Nothing to import. Put a file at gpuboost/import.json or export a config first.");
		}

		GpuBoostConfig imported;

		try {
			imported = GpuBoostConfig.fromJsonMap(MiniJson.parseObject(Files.readString(source, StandardCharsets.UTF_8)), false);
		} catch (RuntimeException e) {
			throw new IOException("Invalid GpuBoost config: " + e.getMessage(), e);
		}

		this.config.copySettingsFrom(imported);
		this.adaptive.reset();
		reapplyTweaks();
		applyRuntimeSettings();
		saveConfig();
		return source;
	}

	private Path newestExport() throws IOException {
		Path dir = exportDir();

		if (!Files.isDirectory(dir)) {
			return null;
		}

		Path best = null;

		try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "gpuboost-config-*.json")) {
			for (Path p : ds) {
				if (best == null || p.getFileName().toString().compareTo(best.getFileName().toString()) > 0) {
					best = p;
				}
			}
		}

		return best;
	}

	private void restartTelemetry() {
		boolean want = this.config.driverTelemetry && !this.config.weakCpuMode && this.started;
		TelemetrySource t = this.telemetry;

		if (!want) {
			stopTelemetry();
			return;
		}

		if (t != null && t.isAlive()) {
			return;
		}

		if (this.telemetryStarts >= 2) {
			return;
		}

		this.telemetryStarts++;
		GpuInfo g = this.gpu;

		if (g.vendor() == GpuVendor.NVIDIA) {
			this.telemetry = NvidiaSmiMonitor.start(g.deviceName());
		} else if (g.vendor() == GpuVendor.AMD) {
			this.telemetry = AmdSysfsMonitor.tryCreate();
		} else {
			this.telemetry = null;
		}
	}

	private void stopTelemetry() {
		TelemetrySource t = this.telemetry;
		this.telemetry = null;
		this.telemetryStarts = 0;

		if (t != null) {
			t.stop();
		}
	}
}
