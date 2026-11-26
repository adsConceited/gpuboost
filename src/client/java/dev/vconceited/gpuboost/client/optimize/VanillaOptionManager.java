package dev.vconceited.gpuboost.client.optimize;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

import dev.vconceited.gpuboost.client.GpuBoostClient;
import dev.vconceited.gpuboost.core.GpuBoostConfig;
import dev.vconceited.gpuboost.core.PerformanceProfile;

public final class VanillaOptionManager {
	public static final String PARTICLES = "particles";
	public static final String RENDER_DISTANCE = "renderDistance";

	private final GpuBoostConfig config;
	private final Runnable saveConfig;
	private final Map<String, Object> lastSetByUs = new LinkedHashMap<>();

	public VanillaOptionManager(GpuBoostConfig config, Runnable saveConfig) {
		this.config = config;
		this.saveConfig = saveConfig;
	}

	private static Options options() {
		Minecraft mc = Minecraft.getInstance();
		return mc == null ? null : mc.options;
	}

	static OptionInstance<?> option(Options o, String key) {
		return switch (key) {
			case "cloudStatus" -> o.cloudStatus();
			case "entityShadows" -> o.entityShadows();
			case "biomeBlendRadius" -> o.biomeBlendRadius();
			case "improvedTransparency" -> o.improvedTransparency();
			case PARTICLES -> o.particles();
			case RENDER_DISTANCE -> o.renderDistance();
			default -> null;
		};
	}

	@SuppressWarnings("unchecked")
	private static void set(OptionInstance<?> opt, Object value) {
		((OptionInstance<Object>) opt).set(value);
	}

	private static Object toJson(Object v) {
		return v instanceof Enum<?> e ? e.name() : v;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object fromJson(Object json, Object current) {
		if (current instanceof Enum<?> e) {
			return Enum.valueOf((Class) e.getDeclaringClass(), String.valueOf(json));
		}

		if (current instanceof Integer && json instanceof Number n) {
			return n.intValue();
		}

		if (current instanceof Double && json instanceof Number n) {
			return n.doubleValue();
		}

		if (current instanceof Boolean && json instanceof Boolean b) {
			return b;
		}

		throw new IllegalArgumentException("Cannot convert " + json + " for option value " + current);
	}

	private static int ordinal(Object v) {
		return v instanceof Enum<?> e ? e.ordinal() : -1;
	}

	private static Object enumAt(Object sample, int ordinal) {
		Object[] all = ((Enum<?>) sample).getDeclaringClass().getEnumConstants();
		return all[Math.max(0, Math.min(all.length - 1, ordinal))];
	}

	private static Object enumNamed(Object sample, String name) {
		for (Object c : ((Enum<?>) sample).getDeclaringClass().getEnumConstants()) {
			if (((Enum<?>) c).name().equals(name)) {
				return c;
			}
		}

		return null;
	}

	public static Map<String, Object> tweakTargets(Options o, PerformanceProfile profile, boolean weakCpu) {
		Map<String, Object> t = new LinkedHashMap<>();
		boolean max = profile == PerformanceProfile.MAX_FPS || (profile == PerformanceProfile.CUSTOM && !weakCpu);
		boolean weak = profile == PerformanceProfile.WEAK_CPU || (profile == PerformanceProfile.CUSTOM && weakCpu);

		if (!max && !weak) {
			return t;
		}

		t.put("entityShadows", Boolean.FALSE);

		int blend = o.biomeBlendRadius().get();
		t.put("biomeBlendRadius", Math.min(blend, max ? 0 : 1));

		Object particles = o.particles().get();
		t.put(PARTICLES, enumAt(particles, Math.max(ordinal(particles), 1)));

		if (max) {
			Object off = enumNamed(o.cloudStatus().get(), "OFF");

			if (off != null) {
				t.put("cloudStatus", off);
			}

			t.put("improvedTransparency", Boolean.FALSE);
		}

		return t;
	}

	public List<String> applyProfileTweaks(PerformanceProfile profile, boolean weakCpu) {
		List<String> changes = new ArrayList<>();
		Options o = options();

		if (o == null) {
			return changes;
		}

		restoreTemporary();

		for (Map.Entry<String, Object> e : tweakTargets(o, profile, weakCpu).entrySet()) {
			try {
				OptionInstance<?> opt = option(o, e.getKey());
				Object cur = opt.get();

				if (!cur.equals(e.getValue())) {
					this.config.vanillaSnapshot.putIfAbsent(e.getKey(), toJson(cur));
					set(opt, e.getValue());
					changes.add(e.getKey() + ": " + toJson(cur) + " -> " + toJson(e.getValue()));
				}
			} catch (RuntimeException | LinkageError ex) {
				GpuBoostClient.LOGGER.warn("[GpuBoost] Could not apply tweak {}: {}", e.getKey(), ex.toString());
			}
		}

		if (!changes.isEmpty()) {
			o.save();
			this.saveConfig.run();
			GpuBoostClient.LOGGER.info("[GpuBoost] Applied {} tweaks: {}", profile.displayName, changes);
		}

		return changes;
	}

	public boolean hasSnapshot() {
		return !this.config.vanillaSnapshot.isEmpty();
	}

	public int restoreSnapshot() {
		Options o = options();

		if (o == null) {
			return 0;
		}

		restoreTemporary();
		int restored = 0;

		for (Map.Entry<String, Object> e : new ArrayList<>(this.config.vanillaSnapshot.entrySet())) {
			try {
				OptionInstance<?> opt = option(o, e.getKey());

				if (opt != null) {
					set(opt, fromJson(e.getValue(), opt.get()));
					restored++;
				}
			} catch (RuntimeException | LinkageError ex) {
				GpuBoostClient.LOGGER.warn("[GpuBoost] Could not restore {}: {}", e.getKey(), ex.toString());
			}
		}

		this.config.vanillaSnapshot.clear();
		o.save();
		this.saveConfig.run();
		return restored;
	}

	public void applyParticleReduction(int steps) {
		Options o = options();

		if (o == null) {
			return;
		}

		if (steps <= 0) {
			restoreTemporary(PARTICLES);
			return;
		}

		try {
			OptionInstance<?> opt = o.particles();
			Object current = opt.get();
			Object stored = this.config.temporaryOverrides.get(PARTICLES);
			int base;

			if (stored instanceof String name && userUnchanged(PARTICLES, current)) {
				Object orig = enumNamed(current, name);
				base = orig != null ? ordinal(orig) : ordinal(current);
			} else {
				this.config.temporaryOverrides.remove(PARTICLES);
				base = ordinal(current);
			}

			Object target = enumAt(current, base + steps);

			if (target.equals(current) && this.config.temporaryOverrides.containsKey(PARTICLES)) {
				return;
			}

			if (ordinal(target) == base) {
				return;
			}

			this.config.temporaryOverrides.put(PARTICLES, toJson(enumAt(current, base)));
			set(opt, target);
			this.lastSetByUs.put(PARTICLES, target);
			this.saveConfig.run();
		} catch (RuntimeException | LinkageError e) {
			GpuBoostClient.LOGGER.warn("[GpuBoost] Adaptive particles unavailable: {}", e.toString());
		}
	}

	public void setTemporaryRenderDistance(int chunks) {
		Options o = options();

		if (o == null) {
			return;
		}

		try {
			OptionInstance<Integer> opt = o.renderDistance();
			Integer current = opt.get();

			if (!this.config.temporaryOverrides.containsKey(RENDER_DISTANCE) || !userUnchanged(RENDER_DISTANCE, current)) {
				this.config.temporaryOverrides.put(RENDER_DISTANCE, current);
			}

			if (current != chunks) {
				opt.set(chunks);
				this.lastSetByUs.put(RENDER_DISTANCE, opt.get());
				this.saveConfig.run();
			}
		} catch (RuntimeException | LinkageError e) {
			GpuBoostClient.LOGGER.warn("[GpuBoost] Auto render distance unavailable: {}", e.toString());
		}
	}

	public boolean hasTemporary(String key) {
		return this.config.temporaryOverrides.containsKey(key);
	}

	public Object originalValue(String key) {
		return this.config.temporaryOverrides.get(key);
	}

	private boolean userUnchanged(String key, Object current) {
		Object ours = this.lastSetByUs.get(key);
		return ours == null || ours.equals(current);
	}

	public void restoreTemporary() {
		restoreTemporary(PARTICLES);
		restoreTemporary(RENDER_DISTANCE);
	}

	private void restoreTemporary(String key) {
		if (!this.config.temporaryOverrides.containsKey(key)) {
			return;
		}

		Options o = options();

		if (o == null) {
			return;
		}

		try {
			OptionInstance<?> opt = option(o, key);
			Object current = opt.get();

			if (userUnchanged(key, current)) {
				set(opt, fromJson(this.config.temporaryOverrides.get(key), current));
			}
		} catch (RuntimeException | LinkageError e) {
			GpuBoostClient.LOGGER.warn("[GpuBoost] Could not restore {}: {}", key, e.toString());
		}

		this.config.temporaryOverrides.remove(key);
		this.lastSetByUs.remove(key);
		this.saveConfig.run();
	}

	public void recoverAfterCrash() {
		if (this.config.temporaryOverrides.isEmpty()) {
			return;
		}

		GpuBoostClient.LOGGER.info("[GpuBoost] Restoring settings left over from the previous session: {}", this.config.temporaryOverrides);
		this.lastSetByUs.clear();
		restoreTemporary();
		Options o = options();

		if (o != null) {
			o.save();
		}
	}
}
