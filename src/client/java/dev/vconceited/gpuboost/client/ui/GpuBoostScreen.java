package dev.vconceited.gpuboost.client.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.vconceited.gpuboost.client.GpuBoostClient;
import dev.vconceited.gpuboost.client.GpuBoostRuntime;
import dev.vconceited.gpuboost.client.compat.ModCompat;
import dev.vconceited.gpuboost.client.gpu.GlTimer;
import dev.vconceited.gpuboost.client.gpu.GpuInfo;
import dev.vconceited.gpuboost.client.optimize.EntityCulling;
import dev.vconceited.gpuboost.client.optimize.Recommendations;
import dev.vconceited.gpuboost.core.AdaptiveController;
import dev.vconceited.gpuboost.core.BottleneckDetector;
import dev.vconceited.gpuboost.core.CapabilityReport;
import dev.vconceited.gpuboost.core.FrameStats;
import dev.vconceited.gpuboost.core.GpuBoostConfig;
import dev.vconceited.gpuboost.core.GpuTelemetry;
import dev.vconceited.gpuboost.core.PerformanceProfile;
import dev.vconceited.gpuboost.core.WindowSample;

public final class GpuBoostScreen extends Screen {
	private static final int BUTTON_W = 150;
	private static final int BUTTON_H = 20;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFAAAAAA;
	private static final int ACCENT = 0xFF66CCFF;

	private final Screen parent;
	private final FrameGraph graph = new FrameGraph(600);
	private final List<String> lines = new ArrayList<>();
	private final List<Integer> colors = new ArrayList<>();
	private long nextRebuildNs;
	private String status = "";
	private Button profileButton;
	private Button hudButton;
	private Button benchButton;
	private boolean showCapabilities;

	public GpuBoostScreen(Screen parent) {
		super(Component.literal("GpuBoost"));
		this.parent = parent;
	}

	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	private GpuBoostRuntime rt() {
		return GpuBoostRuntime.get();
	}

	@Override
	protected void init() {
		GpuBoostRuntime rt = rt();
		int x = this.width - BUTTON_W - 8;
		int y = 24;
		int step = BUTTON_H + 3;

		this.profileButton = addRenderableWidget(Button.builder(profileLabel(), b -> {
					List<String> changes = rt.setProfile(rt.config().profile.next());
					b.setMessage(profileLabel());
					PerformanceProfile p = rt.config().profile;
					this.status = p.displayName + ": " + p.description + (changes.isEmpty() ? "" : "  Changed: " + String.join(", ", changes));
				})
				.pos(x, y).size(BUTTON_W, BUTTON_H)
				.tooltip(Tooltip.create(Component.literal("Click to cycle: MAX FPS, GPU POWER, WEAK CPU, BALANCED, CUSTOM")))
				.build());
		y += step;

		addRenderableWidget(Button.builder(Component.literal("Features..."), b -> this.minecraft.setScreen(new FeaturesScreen(this)))
				.pos(x, y).size(BUTTON_W, BUTTON_H).build());
		y += step;

		this.hudButton = addRenderableWidget(Button.builder(hudLabel(), b -> {
					rt.config().hudEnabled = !rt.config().hudEnabled;
					rt.saveConfig();
					b.setMessage(hudLabel());
				})
				.pos(x, y).size(BUTTON_W, BUTTON_H).build());
		y += step;

		this.benchButton = addRenderableWidget(Button.builder(benchLabel(), b -> startOrStopBenchmark(false))
				.pos(x, y).size(BUTTON_W, BUTTON_H)
				.tooltip(Tooltip.create(Component.literal("Repeatable benchmark with GpuBoost active. Stand still; the camera turns slowly.")))
				.build());
		y += step;

		addRenderableWidget(Button.builder(Component.literal("Baseline benchmark"), b -> startOrStopBenchmark(true))
				.pos(x, y).size(BUTTON_W, BUTTON_H)
				.tooltip(Tooltip.create(Component.literal("Same benchmark with every GpuBoost optimization off (measurement only),"
						+ " for a fair before/after comparison.")))
				.build());
		y += step;

		addRenderableWidget(Button.builder(Component.literal("GPU capabilities"), b -> {
					this.showCapabilities = !this.showCapabilities;
					this.nextRebuildNs = 0;
				})
				.pos(x, y).size(BUTTON_W, BUTTON_H).build());
		y += step;

		addRenderableWidget(Button.builder(Component.literal("Export config"), b -> {
					try {
						Path p = rt.exportConfig();
						this.status = "Exported to gpuboost/exports/" + p.getFileName();
					} catch (IOException e) {
						this.status = "Export failed: " + e.getMessage();
					}
				})
				.pos(x, y).size(BUTTON_W, BUTTON_H).build());
		y += step;

		addRenderableWidget(Button.builder(Component.literal("Import config"), b -> {
					try {
						Path p = rt.importConfig(null);
						this.status = "Imported " + p.getFileName();
						this.profileButton.setMessage(profileLabel());
						this.hudButton.setMessage(hudLabel());
					} catch (IOException e) {
						this.status = "Import failed: " + e.getMessage();
					}
				})
				.pos(x, y).size(BUTTON_W, BUTTON_H)
				.tooltip(Tooltip.create(Component.literal("Imports gpuboost/import.json, or the newest file in gpuboost/exports.")))
				.build());
		y += step;

		addRenderableWidget(Button.builder(Component.literal("Restore vanilla settings"), b -> {
					rt.vanilla().restoreTemporary();
					int n = rt.vanilla().restoreSnapshot();
					rt.config().applyVanillaTweaks = false;
					rt.onFeatureChanged();
					this.profileButton.setMessage(profileLabel());
					this.status = n > 0 ? "Restored " + n + " video settings GpuBoost had changed." : "No video settings were changed by GpuBoost.";
				})
				.pos(x, y).size(BUTTON_W, BUTTON_H).build());
		y += step;

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.pos(x, y).size(BUTTON_W, BUTTON_H).build());
	}

	private Component profileLabel() {
		return Component.literal("Profile: " + rt().config().profile.displayName);
	}

	private Component hudLabel() {
		return Component.literal("HUD overlay: " + Fmt.onOff(rt().config().hudEnabled));
	}

	private Component benchLabel() {
		return Component.literal(rt().bench().isActive() ? "Stop benchmark" : "Benchmark (" + rt().config().benchDurationSec + " s)");
	}

	private void startOrStopBenchmark(boolean passive) {
		GpuBoostRuntime rt = rt();

		if (rt.bench().isActive()) {
			rt.bench().cancel("stopped from the menu");
			this.benchButton.setMessage(benchLabel());
			return;
		}

		GpuBoostConfig cfg = rt.config();
		String error = rt.bench().start(cfg.benchDurationSec, cfg.benchMode, passive ? "baseline" : "gpuboost", passive);

		if (error != null) {
			this.status = error;
			return;
		}

		this.minecraft.setScreen(null);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		GpuBoostRuntime rt = rt();
		long now = System.nanoTime();

		if (now >= this.nextRebuildNs) {
			rebuild(rt);
			this.nextRebuildNs = now + 250_000_000L;
			this.benchButton.setMessage(benchLabel());
		}

		int textRight = this.width - BUTTON_W - 16;
		graphics.text(this.font, "GpuBoost " + ModCompat.version(GpuBoostClient.MOD_ID).orElse("") + "  by vConceited", 8, 8, ACCENT);

		int y = 24;
		int lineH = this.font.lineHeight + 2;
		int graphH = 60;
		int maxY = this.height - graphH - 28;

		for (int k = 0; k < this.lines.size() && y < maxY; k++) {
			graphics.text(this.font, clip(this.lines.get(k), textRight - 8), 8, y, this.colors.get(k));
			y += lineH;
		}

		int graphW = Math.max(60, Math.min(600, textRight - 8));
		int graphY = this.height - graphH - 22;
		this.graph.draw(graphics, this.font, rt.frames().ring(), 8, graphY, graphW, graphH, rt.config().targetFps, true);

		if (!this.status.isEmpty()) {
			graphics.text(this.font, clip(this.status, this.width - 16), 8, this.height - 14, 0xFFFFEE88);
		}
	}

	private String clip(String s, int maxWidth) {
		if (this.font.width(s) <= maxWidth) {
			return s;
		}

		String t = s;

		while (t.length() > 1 && this.font.width(t + "...") > maxWidth) {
			t = t.substring(0, t.length() - 1);
		}

		return t + "...";
	}

	private void add(String text, int color) {
		this.lines.add(text);
		this.colors.add(color);
	}

	private void rebuild(GpuBoostRuntime rt) {
		this.lines.clear();
		this.colors.clear();
		GpuBoostConfig cfg = rt.config();
		FrameStats.Summary recent = rt.frames().recentSummary(240);
		FrameStats.Summary rolling = rt.rolling();
		WindowSample w = rt.lastWindow();
		BottleneckDetector.Verdict v = rt.verdict();
		GpuInfo gpu = rt.gpu();
		GpuTelemetry tel = rt.telemetry();

		add("Profile " + cfg.profile.displayName + (cfg.weakCpuMode ? "  [Weak CPU Mode]" : "") + "  |  adaptive "
				+ (cfg.adaptiveEngine ? "pressure " + rt.adaptive().pressure() + "/" + AdaptiveController.MAX_PRESSURE : "off")
				+ "  |  target " + cfg.targetFps + " FPS", WHITE);
		add("FPS " + Fmt.num(recent.avgFps(), 0) + "   1% low " + Fmt.num(rolling.onePercentLowFps(), 0)
				+ "   0.1% low " + Fmt.num(rolling.pointOnePercentLowFps(), 0)
				+ "   frame " + Fmt.num(recent.avgFrameMs(), 2) + " ms (p99 " + Fmt.num(rolling.p99Ms(), 1) + ")", WHITE);
		add("Bottleneck: " + v.bottleneck().label + " (" + Fmt.pct(v.confidence() * 100) + " confidence)"
				+ (v.reason().isEmpty() ? "" : " - " + v.reason()), ACCENT);

		if (w != null) {
			add("CPU: render thread " + Fmt.num(w.renderThreadCpuMsPerFrame(), 2) + " ms/frame, "
					+ Fmt.pct(w.renderThreadBusyFraction() * 100) + " busy  |  process " + Fmt.pct(w.processCpuPct())
					+ "  |  system " + Fmt.pct(w.systemCpuPct()) + "  (" + rt.jvm().cores() + " threads)", WHITE);
		}

		add("GPU: " + gpu.deviceName() + "  |  " + gpu.vendor().displayName + "  |  backend " + gpu.backend()
				+ (gpu.driver().isBlank() ? "" : "  |  " + gpu.driver()), WHITE);

		StringBuilder g = new StringBuilder("GPU load ");
		g.append(Fmt.pct(tel.utilizationPct())).append(" (").append(rt.telemetryName()).append(")");

		if (w != null) {
			g.append("  |  queue latency ").append(Fmt.num(w.gpuLatencyMs(), 2)).append(" ms");
			g.append("  |  GPU frame <= ").append(Fmt.num(w.gpuBusyMs(), 2)).append(" ms");
		}

		double vramUsed = !Double.isNaN(tel.vramUsedMb()) ? tel.vramUsedMb() : GlTimer.vramUsedMb();
		double vramTotal = !Double.isNaN(tel.vramTotalMb()) ? tel.vramTotalMb() : GlTimer.vramTotalMb();

		if (!Double.isNaN(vramUsed) || !Double.isNaN(vramTotal)) {
			g.append("  |  VRAM ").append(Fmt.num(vramUsed, 0)).append(" / ").append(Fmt.mb(vramTotal));
		} else if (!Double.isNaN(GlTimer.vramFreeMb())) {
			g.append("  |  VRAM free ").append(Fmt.mb(GlTimer.vramFreeMb()));
		}

		add(g.toString(), WHITE);

		if (!gpu.isOpenGl() && !GlTimer.isHookActive()) {
			add("  (GPU timer queries need the OpenGL backend; on Vulkan the verdict uses CPU-side signals and driver telemetry)", GREY);
		}

		add("RAM: heap " + Fmt.num(rt.jvm().heapUsedMb(), 0) + " / " + Fmt.mb(rt.jvm().heapMaxMb())
				+ (w != null ? "  |  GC " + Fmt.num(w.gcMs() / Math.max(1, w.wallMs()) * 100, 1) + "% of time" : ""), WHITE);

		long[] ec = rt.lastEntityCounters();
		add("Entities: " + (EntityCulling.isBroken() ? "culling disabled after an error"
				: EntityCulling.isEnabled() ? "culling beyond " + Fmt.num(EntityCulling.effectiveDistance(), 0) + " blocks, "
				+ ec[1] + " of " + ec[0] + " render checks skipped last window" : "distance culling off")
				+ "  |  particles: " + particleText(rt), WHITE);
		add("Chunks: " + rt.chunks().loaded() + " loaded, " + Fmt.num(rt.chunks().loadsPerSecond(), 1) + "/s arriving"
				+ (Double.isNaN(rt.chunks().initialLoadSec()) ? "" : ", initial load " + Fmt.num(rt.chunks().initialLoadSec(), 1) + " s"), WHITE);

		if (ModCompat.sodium()) {
			String backend = this.minecraft.level != null ? ModCompat.sodiumDrawBackend() : "?";
			add("Sodium " + ModCompat.version("sodium").orElse("") + " renders terrain (draw backend: " + backend
					+ "). GpuBoost does not replace it.", GREY);
		} else {
			add("Sodium not installed: vanilla terrain renderer in use.", GREY);
		}

		if (ModCompat.isLoaded("iris")) {
			add("Iris " + ModCompat.version("iris").orElse("") + (ModCompat.irisShadersActive() ? ": shader pack active" : ": no shader pack"), GREY);
		}

		if (!rt.bench().lastResultLine().isEmpty()) {
			add("Last benchmark: " + rt.bench().lastResultLine(), 0xFFAAFFAA);
		}

		if (this.showCapabilities) {
			add("GPU capabilities:", ACCENT);

			for (CapabilityReport.Capability c : CapabilityReport.build(gpu.backend(), gpu.extensions(), gpu.features(), ModCompat.sodium())) {
				String mark = switch (c.status()) {
					case SUPPORTED -> "yes";
					case UNSUPPORTED -> "no";
					case UNKNOWN -> "?";
				};
				add("  " + c.name() + ": " + mark + " - " + c.note(), c.status() == CapabilityReport.Status.SUPPORTED ? WHITE : GREY);
			}
		} else {
			List<String> recs = Recommendations.build(this.minecraft, cfg, v, rolling, gpu, rt.recommendedRenderDistance(), rt.jvm().heapMaxMb());

			if (!recs.isEmpty()) {
				add("Recommendations:", ACCENT);

				for (String r : recs) {
					add("  - " + r, 0xFFDDDDDD);
				}
			}
		}
	}

	private static String particleText(GpuBoostRuntime rt) {
		try {
			String now = String.valueOf(net.minecraft.client.Minecraft.getInstance().options.particles().get());

			if (rt.vanilla().hasTemporary("particles")) {
				return now + " (adaptive, yours: " + rt.vanilla().originalValue("particles") + ")";
			}

			return now;
		} catch (RuntimeException | LinkageError e) {
			return "?";
		}
	}
}
