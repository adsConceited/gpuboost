package dev.vconceited.gpuboost.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ConfigTest {
	@Test
	void roundTripThroughFile() throws Exception {
		Path dir = Files.createTempDirectory("gpuboost-test");
		Path file = dir.resolve("gpuboost.json");

		GpuBoostConfig c = new GpuBoostConfig();
		PerformanceProfile.WEAK_CPU.applyTo(c);
		c.targetFps = 75;
		c.neverCullEntities.add("mymod:boss");
		c.vanillaSnapshot.put("entityShadows", true);
		c.temporaryOverrides.put("particles", 0);
		c.save(file);

		GpuBoostConfig loaded = GpuBoostConfig.load(file);
		assertEquals(PerformanceProfile.WEAK_CPU, loaded.profile);
		assertTrue(loaded.weakCpuMode);
		assertEquals(75, loaded.targetFps);
		assertTrue(loaded.neverCullEntities.contains("mymod:boss"));
		assertEquals(Boolean.TRUE, loaded.vanillaSnapshot.get("entityShadows"));
		assertEquals(0.0, ((Number) loaded.temporaryOverrides.get("particles")).doubleValue(), 0);
		assertFalse(Files.exists(dir.resolve("gpuboost.json.tmp")));
	}

	@Test
	void importNeverAdoptsForeignRestoreState() {
		GpuBoostConfig other = new GpuBoostConfig();
		other.vanillaSnapshot.put("renderDistance", 2);
		other.targetFps = 144;
		Map<String, Object> exported = other.toJsonMap(false);
		assertFalse(exported.containsKey("vanillaSnapshot"));

		GpuBoostConfig mine = new GpuBoostConfig();
		mine.vanillaSnapshot.put("renderDistance", 16);
		mine.copySettingsFrom(GpuBoostConfig.fromJsonMap(exported, false));
		assertEquals(144, mine.targetFps);
		assertEquals(16, mine.vanillaSnapshot.get("renderDistance"));
	}

	@Test
	void invalidValuesAreClamped() {
		Map<String, Object> m = MiniJson.parseObject("{\"targetFps\": 5, \"entityCullDistance\": 99999, \"minRenderDistance\": 20,"
				+ " \"maxRenderDistance\": 3, \"benchMode\": \"weird\", \"profile\": \"nope\", \"hudCorner\": \"MIDDLE\","
				+ " \"spinDegreesPerSecond\": -4}");
		GpuBoostConfig c = GpuBoostConfig.fromJsonMap(m, true);
		assertEquals(20, c.targetFps);
		assertEquals(1024, c.entityCullDistance);
		assertEquals(20, c.minRenderDistance);
		assertEquals(20, c.maxRenderDistance);
		assertEquals("spin", c.benchMode);
		assertEquals(PerformanceProfile.BALANCED, c.profile);
		assertEquals(GpuBoostConfig.HudCorner.TOP_RIGHT, c.hudCorner);
		assertEquals(12.0, c.spinDegreesPerSecond, 0);
	}

	@Test
	void brokenFileFallsBackToDefaultsAndKeepsCopy() throws Exception {
		Path dir = Files.createTempDirectory("gpuboost-test");
		Path file = dir.resolve("gpuboost.json");
		Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);

		GpuBoostConfig c = GpuBoostConfig.load(file);
		assertEquals(PerformanceProfile.BALANCED, c.profile);
		assertTrue(Files.exists(dir.resolve("gpuboost.json.broken")));
	}

	@Test
	void profilesSetDistinctPresets() {
		GpuBoostConfig c = new GpuBoostConfig();
		PerformanceProfile.MAX_FPS.applyTo(c);
		assertTrue(c.applyVanillaTweaks);
		assertEquals(48, c.entityCullDistance);
		assertFalse(c.weakCpuMode);

		PerformanceProfile.BALANCED.applyTo(c);
		assertFalse(c.applyVanillaTweaks);
		assertEquals(96, c.entityCullDistance);

		c.entityCullDistance = 200;
		PerformanceProfile.CUSTOM.applyTo(c);
		assertEquals(200, c.entityCullDistance);
		assertEquals(PerformanceProfile.CUSTOM, c.profile);

		assertEquals(PerformanceProfile.GPU_POWER, PerformanceProfile.parse("gpu-power", null));
		assertEquals(PerformanceProfile.MAX_FPS, PerformanceProfile.parse("max fps", null));
		assertEquals(PerformanceProfile.MAX_FPS, PerformanceProfile.CUSTOM.next());
	}
}
