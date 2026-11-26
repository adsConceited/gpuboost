package dev.vconceited.gpuboost.core;

public final class EntityCullPolicy {
	public static final double MIN_DISTANCE = 16.0;

	private EntityCullPolicy() {
	}

	public static double effectiveDistance(int configuredDistance, double pressureMultiplier) {
		return Math.max(MIN_DISTANCE, configuredDistance * pressureMultiplier);
	}

	public static boolean shouldCull(double distanceSq, double effectiveDistance,
	                                 boolean isCameraEntityOrVehicleOrPassenger, boolean isPlayer, boolean hasCustomName,
	                                 boolean isGlowing, boolean isNeverCullType,
	                                 boolean alwaysRenderPlayers, boolean alwaysRenderNamed, boolean alwaysRenderGlowing) {
		if (distanceSq <= effectiveDistance * effectiveDistance) {
			return false;
		}

		if (isCameraEntityOrVehicleOrPassenger || isNeverCullType) {
			return false;
		}

		if (isPlayer && alwaysRenderPlayers) {
			return false;
		}

		if (hasCustomName && alwaysRenderNamed) {
			return false;
		}

		return !(isGlowing && alwaysRenderGlowing);
	}
}
