package dev.vconceited.gpuboost.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import dev.vconceited.gpuboost.client.GpuBoostClient;
import dev.vconceited.gpuboost.client.GpuBoostRuntime;
import dev.vconceited.gpuboost.client.Reflect;
import dev.vconceited.gpuboost.client.gpu.GlTimer;
import dev.vconceited.gpuboost.core.FrameStats;
import dev.vconceited.gpuboost.core.GpuBoostConfig;
import dev.vconceited.gpuboost.core.GpuTelemetry;
import dev.vconceited.gpuboost.core.WindowSample;

public final class HudOverlay implements HudElement {
	private static final int GRAPH_W = 120;
	private static final int GRAPH_H = 28;

	private final FrameGraph graph = new FrameGraph(GRAPH_W);
	private final Reflect.FieldReader hideGui = new Reflect.FieldReader("hideGui");
	private final List<String> lines = new ArrayList<>();
	private long nextRebuildNs;
	private int panelWidth = 100;
	private boolean failed;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (this.failed) {
			return;
		}

		GpuBoostRuntime rt = GpuBoostRuntime.get();

		if (rt == null) {
			return;
		}

		GpuBoostConfig cfg = rt.config();
		boolean benchActive = rt.bench().isActive();

		if (!cfg.hudEnabled && !benchActive) {
			return;
		}

		try {
			Minecraft mc = Minecraft.getInstance();

			if (Boolean.TRUE.equals(this.hideGui.read(mc.options))) {
				return;
			}

			Font font = mc.font;
			long now = System.nanoTime();

			if (now >= this.nextRebuildNs) {
				rebuild(rt, cfg, font, now);
				this.nextRebuildNs = now + (cfg.weakCpuMode ? 500_000_000L : 250_000_000L);
			}

			boolean showGraph = cfg.hudEnabled && cfg.hudGraph;
			int lineH = font.lineHeight + 1;
			int w = Math.max(this.panelWidth, showGraph ? GRAPH_W : 0) + 6;
			int h = this.lines.size() * lineH + 4 + (showGraph ? GRAPH_H + 3 : 0);
			boolean right = cfg.hudCorner == GpuBoostConfig.HudCorner.TOP_RIGHT || cfg.hudCorner == GpuBoostConfig.HudCorner.BOTTOM_RIGHT;
			boolean bottom = cfg.hudCorner == GpuBoostConfig.HudCorner.BOTTOM_LEFT || cfg.hudCorner == GpuBoostConfig.HudCorner.BOTTOM_RIGHT;
			int x = right ? graphics.guiWidth() - w - 2 : 2;
			int y = bottom ? graphics.guiHeight() - h - 2 : 2;

			graphics.fill(x, y, x + w, y + h, 0x80000000);
			int ty = y + 3;

			for (String line : this.lines) {
				graphics.text(font, line, x + 3, ty, 0xFFFFFFFF);
				ty += lineH;
			}

			if (showGraph) {
				this.graph.draw(graphics, font, rt.frames().ring(), x + 3, ty + 1, GRAPH_W, GRAPH_H, cfg.targetFps, false);
			}
		} catch (RuntimeException | LinkageError e) {
			this.failed = true;
			GpuBoostClient.LOGGER.error("[GpuBoost] HUD overlay disabled after an error", e);
		}
	}

	private void rebuild(GpuBoostRuntime rt, GpuBoostConfig cfg, Font font, long now) {
		this.lines.clear();

		if (rt.bench().isActive()) {
			this.lines.add(rt.bench().statusLine(now));
		}

		if (cfg.hudEnabled) {
			FrameStats.Summary recent = rt.frames().recentSummary(240);
			FrameStats.Summary rolling = rt.rolling();
			this.lines.add("FPS " + Fmt.num(recent.avgFps(), 0) + "  1% " + Fmt.num(rolling.onePercentLowFps(), 0)
					+ "  " + Fmt.num(recent.avgFrameMs(), 2) + " ms");
			this.lines.add(rt.verdict().bottleneck().label);

			WindowSample w = rt.lastWindow();
			GpuTelemetry t = rt.telemetry();
			StringBuilder gpuLine = new StringBuilder("GPU ");
			gpuLine.append(Fmt.pct(t.utilizationPct()));

			if (w != null && !Double.isNaN(w.gpuLatencyMs())) {
				gpuLine.append("  lat ").append(Fmt.num(w.gpuLatencyMs(), 1)).append(" ms");
			}

			double vram = !Double.isNaN(t.vramUsedMb()) ? t.vramUsedMb() : GlTimer.vramUsedMb();

			if (!Double.isNaN(vram)) {
				gpuLine.append("  ").append(Fmt.mb(vram));
			}

			this.lines.add(gpuLine.toString());

			if (w != null) {
				this.lines.add("CPU " + Fmt.num(w.renderThreadCpuMsPerFrame(), 1) + " ms/f  proc " + Fmt.pct(w.processCpuPct()));
			}
		}

		int max = 0;

		for (String l : this.lines) {
			max = Math.max(max, font.width(l));
		}

		this.panelWidth = max;
	}
}
