package dev.vconceited.gpuboost.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EntityCullPolicyTest {
	private static boolean cull(double dist, double limit, boolean self, boolean player, boolean named, boolean glowing, boolean never) {
		return EntityCullPolicy.shouldCull(dist * dist, limit, self, player, named, glowing, never, true, true, true);
	}

	@Test
	void cullsOnlyBeyondDistance() {
		assertFalse(cull(47.9, 48, false, false, false, false, false));
		assertFalse(cull(48, 48, false, false, false, false, false));
		assertTrue(cull(48.1, 48, false, false, false, false, false));
	}

	@Test
	void exemptionsAreHonoured() {
		assertFalse(cull(500, 48, true, false, false, false, false), "camera entity / vehicle");
		assertFalse(cull(500, 48, false, true, false, false, false), "players");
		assertFalse(cull(500, 48, false, false, true, false, false), "named");
		assertFalse(cull(500, 48, false, false, false, true, false), "glowing");
		assertFalse(cull(500, 48, false, false, false, false, true), "never-cull list");
	}

	@Test
	void exemptionsCanBeDisabled() {
		assertTrue(EntityCullPolicy.shouldCull(500 * 500, 48, false, true, true, true, false, false, false, false));
	}

	@Test
	void effectiveDistanceHasFloor() {
		assertEquals(48.0, EntityCullPolicy.effectiveDistance(96, 0.5), 1e-9);
		assertEquals(EntityCullPolicy.MIN_DISTANCE, EntityCullPolicy.effectiveDistance(20, 0.45), 1e-9);
	}
}
