package dev.vconceited.gpuboost.core;

public enum PerformanceProfile {
	MAX_FPS("MAX FPS", "Maximum performance. Aggressive entity distance, reduced particles, clouds/shadows/expensive transparency off."),
	GPU_POWER("GPU POWER", "Keeps GPU-side visuals, removes CPU-side waste (far entities), full GPU monitoring. Best with a strong GPU and Sodium."),
	WEAK_CPU("WEAK CPU", "Enables Weak CPU Mode: minimal render-thread work, lower monitoring overhead, cheaper chunk meshing settings."),
	BALANCED("BALANCED", "Keeps visual quality; only trims work that cannot be seen and adapts when FPS drops below target."),
	CUSTOM("CUSTOM", "Your own combination of feature toggles.");

	public final String displayName;
	public final String description;

	PerformanceProfile(String displayName, String description) {
		this.displayName = displayName;
		this.description = description;
	}

	public PerformanceProfile next() {
		PerformanceProfile[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	public static PerformanceProfile parse(String s, PerformanceProfile fallback) {
		if (s == null) {
			return fallback;
		}

		String norm = s.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_').replace('-', '_');

		for (PerformanceProfile p : values()) {
			if (p.name().equals(norm)) {
				return p;
			}
		}

		return fallback;
	}

	public void applyTo(GpuBoostConfig cfg) {
		cfg.profile = this;

		switch (this) {
			case MAX_FPS -> {
				cfg.adaptiveEngine = true;
				cfg.entityCulling = true;
				cfg.entityCullDistance = 48;
				cfg.adaptiveParticles = true;
				cfg.applyVanillaTweaks = true;
				cfg.weakCpuMode = false;
				cfg.gpuTimerQueries = true;
				cfg.driverTelemetry = true;
				cfg.evaluationIntervalMs = 1000;
			}
			case GPU_POWER -> {
				cfg.adaptiveEngine = true;
				cfg.entityCulling = true;
				cfg.entityCullDistance = 128;
				cfg.adaptiveParticles = true;
				cfg.applyVanillaTweaks = false;
				cfg.weakCpuMode = false;
				cfg.gpuTimerQueries = true;
				cfg.driverTelemetry = true;
				cfg.evaluationIntervalMs = 1000;
			}
			case WEAK_CPU -> {
				cfg.adaptiveEngine = true;
				cfg.entityCulling = true;
				cfg.entityCullDistance = 48;
				cfg.adaptiveParticles = true;
				cfg.applyVanillaTweaks = true;
				cfg.weakCpuMode = true;
				cfg.gpuTimerQueries = true;
				cfg.driverTelemetry = false;
				cfg.hudGraph = false;
				cfg.evaluationIntervalMs = 2000;
			}
			case BALANCED -> {
				cfg.adaptiveEngine = true;
				cfg.entityCulling = true;
				cfg.entityCullDistance = 96;
				cfg.adaptiveParticles = true;
				cfg.applyVanillaTweaks = false;
				cfg.weakCpuMode = false;
				cfg.gpuTimerQueries = true;
				cfg.driverTelemetry = true;
				cfg.evaluationIntervalMs = 1000;
			}
			case CUSTOM -> {
			}
		}
	}
}
