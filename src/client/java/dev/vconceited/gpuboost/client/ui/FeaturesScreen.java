package dev.vconceited.gpuboost.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.vconceited.gpuboost.client.GpuBoostRuntime;
import dev.vconceited.gpuboost.core.GpuBoostConfig;

public final class FeaturesScreen extends Screen {
	private static final int BUTTON_W = 200;
	private static final int BUTTON_H = 20;
	private static final int[] TARGET_FPS = {30, 45, 60, 75, 90, 120, 144, 165, 240};
	private static final int[] CULL_DISTANCES = {32, 48, 64, 96, 128, 192, 256, 384};
	private static final int[] BENCH_SECONDS = {30, 60, 120, 300};

	private final Screen parent;
	private final List<Runnable> refreshers = new ArrayList<>();

	public FeaturesScreen(Screen parent) {
		super(Component.literal("GpuBoost features"));
		this.parent = parent;
	}

	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private record Entry(Supplier<String> label, Runnable action, String tooltip) {
	}

	@Override
	protected void init() {
		this.refreshers.clear();
		GpuBoostRuntime rt = GpuBoostRuntime.get();
		GpuBoostConfig c = rt.config();
		List<Entry> entries = new ArrayList<>();

		entries.add(toggle("Adaptive engine", () -> c.adaptiveEngine, v -> c.adaptiveEngine = v, true,
				"Measures every second and sheds workload (entity distance, particles) only while FPS is below target."));
		entries.add(new Entry(() -> "Target FPS: " + c.targetFps, () -> {
			c.targetFps = cycle(TARGET_FPS, c.targetFps);
			rt.onFeatureChanged();
		}, "The adaptive engine and recommendations aim for this frame rate."));
		entries.add(toggle("Entity distance culling", () -> c.entityCulling, v -> c.entityCulling = v, true,
				"Skips rendering entities beyond the distance below. Rendering only: AI, ticking and collisions are unchanged."));
		entries.add(new Entry(() -> "Entity distance: " + c.entityCullDistance + " blocks", () -> {
			c.entityCullDistance = cycle(CULL_DISTANCES, c.entityCullDistance);
			rt.onFeatureChanged();
		}, "Base distance; the adaptive engine may shrink it to 45% under pressure (never below 16 blocks)."));
		entries.add(toggle("Always render players", () -> c.alwaysRenderPlayers, v -> c.alwaysRenderPlayers = v, true,
				"Other players are never culled."));
		entries.add(toggle("Always render named", () -> c.alwaysRenderNamed, v -> c.alwaysRenderNamed = v, true,
				"Entities with a name tag are never culled."));
		entries.add(toggle("Always render glowing", () -> c.alwaysRenderGlowing, v -> c.alwaysRenderGlowing = v, true,
				"Glowing entities (spectral arrows, teams) are never culled."));
		entries.add(toggle("Adaptive particles", () -> c.adaptiveParticles, v -> c.adaptiveParticles = v, true,
				"Temporarily lowers the vanilla Particles setting under pressure and restores your choice afterwards."));
		entries.add(new Entry(() -> "Profile video tweaks: " + Fmt.onOff(c.applyVanillaTweaks), () -> {
			rt.setVanillaTweaks(!c.applyVanillaTweaks);
			rt.onFeatureChanged();
		}, "Applies the profile's video settings (shadows, biome blend, clouds...). Turning it off restores your values."));
		entries.add(toggle("Auto render distance", () -> c.autoRenderDistance, v -> c.autoRenderDistance = v, true,
				"Off by default. Moves render distance by at most 2 chunks, at most every 30 s, within "
						+ c.minRenderDistance + "-" + c.maxRenderDistance + "; your value is restored on exit."));
		entries.add(new Entry(() -> "Weak CPU Mode: " + Fmt.onOff(c.weakCpuMode), () -> {
			c.weakCpuMode = !c.weakCpuMode;
			rt.onFeatureChanged();

			if (c.applyVanillaTweaks) {
				rt.reapplyTweaks();
			}
		}, "More aggressive entity budget, slower monitoring, no background telemetry process, cheaper meshing tweaks."));
		entries.add(toggle("GPU timer queries", () -> c.gpuTimerQueries, v -> c.gpuTimerQueries = v, true,
				"OpenGL only: non-blocking timestamp queries to measure GPU latency for bottleneck detection."));
		entries.add(toggle("Driver telemetry", () -> c.driverTelemetry, v -> c.driverTelemetry = v, true,
				"GPU load/VRAM from nvidia-smi (NVIDIA, Windows/Linux) or amdgpu sysfs (AMD, Linux)."));
		entries.add(toggle("HUD graph", () -> c.hudGraph, v -> c.hudGraph = v, false, "Frame-time graph in the HUD overlay."));
		entries.add(new Entry(() -> "HUD corner: " + c.hudCorner.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '), () -> {
			GpuBoostConfig.HudCorner[] all = GpuBoostConfig.HudCorner.values();
			c.hudCorner = all[(c.hudCorner.ordinal() + 1) % all.length];
			rt.saveConfig();
		}, null));
		entries.add(new Entry(() -> "Benchmark length: " + c.benchDurationSec + " s", () -> {
			c.benchDurationSec = cycle(BENCH_SECONDS, c.benchDurationSec);
			rt.saveConfig();
		}, null));
		entries.add(new Entry(() -> "Benchmark camera: " + c.benchMode, () -> {
			c.benchMode = "spin".equals(c.benchMode) ? "static" : "spin";
			rt.saveConfig();
		}, "spin: camera turns at a constant speed (repeatable, covers all directions). static: camera does not move."));

		int columns = Math.max(1, Math.min(3, (this.width - 20) / (BUTTON_W + 10)));
		int rows = (entries.size() + columns - 1) / columns;
		int totalW = columns * BUTTON_W + (columns - 1) * 10;
		int x0 = (this.width - totalW) / 2;
		int y0 = 30;

		for (int k = 0; k < entries.size(); k++) {
			Entry e = entries.get(k);
			int col = k / rows;
			int row = k % rows;
			Button.Builder b = Button.builder(Component.literal(e.label().get()), btn -> {
						e.action().run();
						this.refreshers.forEach(Runnable::run);
					})
					.pos(x0 + col * (BUTTON_W + 10), y0 + row * (BUTTON_H + 4))
					.size(BUTTON_W, BUTTON_H);

			if (e.tooltip() != null) {
				b.tooltip(Tooltip.create(Component.literal(e.tooltip())));
			}

			Button button = addRenderableWidget(b.build());
			this.refreshers.add(() -> button.setMessage(Component.literal(e.label().get())));
		}

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.pos(this.width / 2 - 75, Math.min(this.height - 26, y0 + rows * (BUTTON_H + 4) + 8)).size(150, BUTTON_H).build());
	}

	private Entry toggle(String name, BooleanSupplier get, Consumer<Boolean> set, boolean performance, String tooltip) {
		GpuBoostRuntime rt = GpuBoostRuntime.get();
		return new Entry(() -> name + ": " + Fmt.onOff(get.getAsBoolean()), () -> {
			set.accept(!get.getAsBoolean());

			if (performance) {
				rt.onFeatureChanged();
			} else {
				rt.saveConfig();
			}
		}, tooltip);
	}

	private static int cycle(int[] values, int current) {
		for (int k = 0; k < values.length; k++) {
			if (values[k] > current) {
				return values[k];
			}
		}

		return values[0];
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		GpuBoostRuntime rt = GpuBoostRuntime.get();
		graphics.centeredText(this.font, "GpuBoost features  -  profile: " + rt.config().profile.displayName, this.width / 2, 12, 0xFFFFFFFF);
	}
}
