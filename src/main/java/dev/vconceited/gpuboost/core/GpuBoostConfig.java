package dev.vconceited.gpuboost.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GpuBoostConfig {
	public static final int CURRENT_VERSION = 1;

	public enum HudCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

	public PerformanceProfile profile = PerformanceProfile.BALANCED;
	public boolean adaptiveEngine = true;
	public int targetFps = 60;
	public int adaptCooldownSec = 5;
	public int evaluationIntervalMs = 1000;

	public boolean entityCulling = true;
	public int entityCullDistance = 96;
	public boolean alwaysRenderPlayers = true;
	public boolean alwaysRenderNamed = true;
	public boolean alwaysRenderGlowing = true;
	public List<String> neverCullEntities = new ArrayList<>(List.of(
			"minecraft:ender_dragon", "minecraft:wither", "minecraft:elder_guardian",
			"minecraft:warden", "minecraft:ghast", "minecraft:happy_ghast"));

	public boolean adaptiveParticles = true;
	public boolean applyVanillaTweaks = false;
	public boolean autoRenderDistance = false;
	public int minRenderDistance = 6;
	public int maxRenderDistance = 32;

	public boolean weakCpuMode = false;

	public boolean gpuTimerQueries = true;
	public boolean driverTelemetry = true;

	public boolean hudEnabled = false;
	public boolean hudGraph = true;
	public HudCorner hudCorner = HudCorner.TOP_RIGHT;

	public int benchDurationSec = 60;
	public int benchWarmupSec = 5;
	public String benchMode = "spin";
	public double spinDegreesPerSecond = 12.0;

	public Map<String, Object> vanillaSnapshot = new LinkedHashMap<>();
	public Map<String, Object> temporaryOverrides = new LinkedHashMap<>();

	public void validate() {
		this.targetFps = clamp(this.targetFps, 20, 1000);
		this.adaptCooldownSec = clamp(this.adaptCooldownSec, 2, 120);
		this.evaluationIntervalMs = clamp(this.evaluationIntervalMs, 250, 5000);
		this.entityCullDistance = clamp(this.entityCullDistance, 16, 1024);
		this.minRenderDistance = clamp(this.minRenderDistance, 2, 32);
		this.maxRenderDistance = clamp(this.maxRenderDistance, this.minRenderDistance, 64);
		this.benchDurationSec = clamp(this.benchDurationSec, 5, 1800);
		this.benchWarmupSec = clamp(this.benchWarmupSec, 0, 120);

		if (!"spin".equals(this.benchMode) && !"static".equals(this.benchMode)) {
			this.benchMode = "spin";
		}

		if (!(this.spinDegreesPerSecond >= 1 && this.spinDegreesPerSecond <= 360)) {
			this.spinDegreesPerSecond = 12.0;
		}

		if (this.profile == null) {
			this.profile = PerformanceProfile.BALANCED;
		}

		if (this.hudCorner == null) {
			this.hudCorner = HudCorner.TOP_RIGHT;
		}

		if (this.neverCullEntities == null) {
			this.neverCullEntities = new ArrayList<>();
		}

		this.neverCullEntities.removeIf(s -> s == null || s.isBlank() || s.length() > 128);

		if (this.vanillaSnapshot == null) {
			this.vanillaSnapshot = new LinkedHashMap<>();
		}

		if (this.temporaryOverrides == null) {
			this.temporaryOverrides = new LinkedHashMap<>();
		}
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	public Map<String, Object> toJsonMap(boolean includeInternalState) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("configVersion", CURRENT_VERSION);
		m.put("profile", this.profile.name());
		m.put("adaptiveEngine", this.adaptiveEngine);
		m.put("targetFps", this.targetFps);
		m.put("adaptCooldownSec", this.adaptCooldownSec);
		m.put("evaluationIntervalMs", this.evaluationIntervalMs);
		m.put("entityCulling", this.entityCulling);
		m.put("entityCullDistance", this.entityCullDistance);
		m.put("alwaysRenderPlayers", this.alwaysRenderPlayers);
		m.put("alwaysRenderNamed", this.alwaysRenderNamed);
		m.put("alwaysRenderGlowing", this.alwaysRenderGlowing);
		m.put("neverCullEntities", new ArrayList<>(this.neverCullEntities));
		m.put("adaptiveParticles", this.adaptiveParticles);
		m.put("applyVanillaTweaks", this.applyVanillaTweaks);
		m.put("autoRenderDistance", this.autoRenderDistance);
		m.put("minRenderDistance", this.minRenderDistance);
		m.put("maxRenderDistance", this.maxRenderDistance);
		m.put("weakCpuMode", this.weakCpuMode);
		m.put("gpuTimerQueries", this.gpuTimerQueries);
		m.put("driverTelemetry", this.driverTelemetry);
		m.put("hudEnabled", this.hudEnabled);
		m.put("hudGraph", this.hudGraph);
		m.put("hudCorner", this.hudCorner.name());
		m.put("benchDurationSec", this.benchDurationSec);
		m.put("benchWarmupSec", this.benchWarmupSec);
		m.put("benchMode", this.benchMode);
		m.put("spinDegreesPerSecond", this.spinDegreesPerSecond);

		if (includeInternalState) {
			m.put("vanillaSnapshot", new LinkedHashMap<>(this.vanillaSnapshot));
			m.put("temporaryOverrides", new LinkedHashMap<>(this.temporaryOverrides));
		}

		return m;
	}

	public static GpuBoostConfig fromJsonMap(Map<String, Object> m, boolean includeInternalState) {
		GpuBoostConfig c = new GpuBoostConfig();
		c.profile = PerformanceProfile.parse(MiniJson.getString(m, "profile", c.profile.name()), c.profile);
		c.adaptiveEngine = MiniJson.getBool(m, "adaptiveEngine", c.adaptiveEngine);
		c.targetFps = MiniJson.getInt(m, "targetFps", c.targetFps);
		c.adaptCooldownSec = MiniJson.getInt(m, "adaptCooldownSec", c.adaptCooldownSec);
		c.evaluationIntervalMs = MiniJson.getInt(m, "evaluationIntervalMs", c.evaluationIntervalMs);
		c.entityCulling = MiniJson.getBool(m, "entityCulling", c.entityCulling);
		c.entityCullDistance = MiniJson.getInt(m, "entityCullDistance", c.entityCullDistance);
		c.alwaysRenderPlayers = MiniJson.getBool(m, "alwaysRenderPlayers", c.alwaysRenderPlayers);
		c.alwaysRenderNamed = MiniJson.getBool(m, "alwaysRenderNamed", c.alwaysRenderNamed);
		c.alwaysRenderGlowing = MiniJson.getBool(m, "alwaysRenderGlowing", c.alwaysRenderGlowing);
		c.neverCullEntities = new ArrayList<>(MiniJson.getStringList(m, "neverCullEntities", c.neverCullEntities));
		c.adaptiveParticles = MiniJson.getBool(m, "adaptiveParticles", c.adaptiveParticles);
		c.applyVanillaTweaks = MiniJson.getBool(m, "applyVanillaTweaks", c.applyVanillaTweaks);
		c.autoRenderDistance = MiniJson.getBool(m, "autoRenderDistance", c.autoRenderDistance);
		c.minRenderDistance = MiniJson.getInt(m, "minRenderDistance", c.minRenderDistance);
		c.maxRenderDistance = MiniJson.getInt(m, "maxRenderDistance", c.maxRenderDistance);
		c.weakCpuMode = MiniJson.getBool(m, "weakCpuMode", c.weakCpuMode);
		c.gpuTimerQueries = MiniJson.getBool(m, "gpuTimerQueries", c.gpuTimerQueries);
		c.driverTelemetry = MiniJson.getBool(m, "driverTelemetry", c.driverTelemetry);
		c.hudEnabled = MiniJson.getBool(m, "hudEnabled", c.hudEnabled);
		c.hudGraph = MiniJson.getBool(m, "hudGraph", c.hudGraph);

		try {
			c.hudCorner = HudCorner.valueOf(MiniJson.getString(m, "hudCorner", c.hudCorner.name()));
		} catch (IllegalArgumentException ignored) {
		}

		c.benchDurationSec = MiniJson.getInt(m, "benchDurationSec", c.benchDurationSec);
		c.benchWarmupSec = MiniJson.getInt(m, "benchWarmupSec", c.benchWarmupSec);
		c.benchMode = MiniJson.getString(m, "benchMode", c.benchMode);
		c.spinDegreesPerSecond = MiniJson.getDouble(m, "spinDegreesPerSecond", c.spinDegreesPerSecond);

		if (includeInternalState) {
			Map<String, Object> snap = MiniJson.getObject(m, "vanillaSnapshot");
			Map<String, Object> over = MiniJson.getObject(m, "temporaryOverrides");

			if (snap != null) {
				c.vanillaSnapshot = new LinkedHashMap<>(snap);
			}

			if (over != null) {
				c.temporaryOverrides = new LinkedHashMap<>(over);
			}
		}

		c.validate();
		return c;
	}

	public void copySettingsFrom(GpuBoostConfig other) {
		Map<String, Object> keepSnap = this.vanillaSnapshot;
		Map<String, Object> keepOver = this.temporaryOverrides;
		GpuBoostConfig copy = fromJsonMap(other.toJsonMap(false), false);

		this.profile = copy.profile;
		this.adaptiveEngine = copy.adaptiveEngine;
		this.targetFps = copy.targetFps;
		this.adaptCooldownSec = copy.adaptCooldownSec;
		this.evaluationIntervalMs = copy.evaluationIntervalMs;
		this.entityCulling = copy.entityCulling;
		this.entityCullDistance = copy.entityCullDistance;
		this.alwaysRenderPlayers = copy.alwaysRenderPlayers;
		this.alwaysRenderNamed = copy.alwaysRenderNamed;
		this.alwaysRenderGlowing = copy.alwaysRenderGlowing;
		this.neverCullEntities = copy.neverCullEntities;
		this.adaptiveParticles = copy.adaptiveParticles;
		this.applyVanillaTweaks = copy.applyVanillaTweaks;
		this.autoRenderDistance = copy.autoRenderDistance;
		this.minRenderDistance = copy.minRenderDistance;
		this.maxRenderDistance = copy.maxRenderDistance;
		this.weakCpuMode = copy.weakCpuMode;
		this.gpuTimerQueries = copy.gpuTimerQueries;
		this.driverTelemetry = copy.driverTelemetry;
		this.hudEnabled = copy.hudEnabled;
		this.hudGraph = copy.hudGraph;
		this.hudCorner = copy.hudCorner;
		this.benchDurationSec = copy.benchDurationSec;
		this.benchWarmupSec = copy.benchWarmupSec;
		this.benchMode = copy.benchMode;
		this.spinDegreesPerSecond = copy.spinDegreesPerSecond;
		this.vanillaSnapshot = keepSnap;
		this.temporaryOverrides = keepOver;
	}

	public static GpuBoostConfig load(Path file) {
		if (!Files.isRegularFile(file)) {
			return new GpuBoostConfig();
		}

		try {
			String text = Files.readString(file, StandardCharsets.UTF_8);
			return fromJsonMap(MiniJson.parseObject(text), true);
		} catch (IOException | RuntimeException e) {
			try {
				Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
			}

			return new GpuBoostConfig();
		}
	}

	public void save(Path file) throws IOException {
		writeAtomically(file, MiniJson.write(toJsonMap(true)));
	}

	public static void writeAtomically(Path file, String content) throws IOException {
		Path parent = file.toAbsolutePath().getParent();

		if (parent != null) {
			Files.createDirectories(parent);
		}

		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, content, StandardCharsets.UTF_8);

		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
