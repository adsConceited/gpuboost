package dev.vconceited.gpuboost.client.optimize;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

import dev.vconceited.gpuboost.client.GpuBoostClient;
import dev.vconceited.gpuboost.core.EntityCullPolicy;
import dev.vconceited.gpuboost.core.GpuBoostConfig;

public final class EntityCulling {
	private static volatile boolean enabled;
	private static volatile double distance = 96;
	private static volatile boolean keepPlayers = true;
	private static volatile boolean keepNamed = true;
	private static volatile boolean keepGlowing = true;
	private static volatile Set<String> neverCullIds = Set.of();
	private static final Map<EntityType<?>, Boolean> NEVER_CULL_CACHE = new ConcurrentHashMap<>();
	private static volatile boolean broken;

	private static final MethodHandle HAS_CUSTOM_NAME = findFlag("hasCustomName");
	private static final MethodHandle IS_CURRENTLY_GLOWING = findFlag("isCurrentlyGlowing");

	private static long considered;
	private static long culled;

	private EntityCulling() {
	}

	public static void reconfigure(GpuBoostConfig cfg, double pressureMultiplier, boolean passive) {
		enabled = cfg.entityCulling && !passive && !broken;
		distance = EntityCullPolicy.effectiveDistance(cfg.entityCullDistance, pressureMultiplier);
		keepPlayers = cfg.alwaysRenderPlayers;
		keepNamed = cfg.alwaysRenderNamed;
		keepGlowing = cfg.alwaysRenderGlowing;
		neverCullIds = Set.copyOf(cfg.neverCullEntities);
		NEVER_CULL_CACHE.clear();
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static boolean isBroken() {
		return broken;
	}

	public static double effectiveDistance() {
		return distance;
	}

	public static long[] consumeCounters() {
		long[] r = {considered, culled};
		considered = 0;
		culled = 0;
		return r;
	}

	public static boolean shouldSkip(Entity entity, double camX, double camY, double camZ) {
		if (!enabled) {
			return false;
		}

		try {
			considered++;
			double d2 = entity.distanceToSqr(camX, camY, camZ);
			double limit = distance;

			if (d2 <= limit * limit) {
				return false;
			}

			Minecraft mc = Minecraft.getInstance();
			boolean self = entity == mc.getCameraEntity() || entity == mc.player;
			boolean never = !neverCullIds.isEmpty() && NEVER_CULL_CACHE.computeIfAbsent(entity.getType(), EntityCulling::isNeverCullType);
			boolean cull = EntityCullPolicy.shouldCull(d2, limit, self, entity instanceof Player, flag(HAS_CUSTOM_NAME, entity),
					flag(IS_CURRENTLY_GLOWING, entity), never, keepPlayers, keepNamed, keepGlowing);

			if (cull) {
				culled++;
			}

			return cull;
		} catch (RuntimeException | LinkageError e) {
			broken = true;
			enabled = false;
			GpuBoostClient.LOGGER.error("[GpuBoost] Entity culling disabled after an unexpected error", e);
			return false;
		}
	}

	private static MethodHandle findFlag(String name) {
		try {
			return MethodHandles.publicLookup().findVirtual(Entity.class, name, MethodType.methodType(boolean.class));
		} catch (ReflectiveOperationException | RuntimeException e) {
			GpuBoostClient.LOGGER.warn("[GpuBoost] Entity.{}() not found; that culling exemption is inactive.", name);
			return null;
		}
	}

	private static boolean flag(MethodHandle handle, Entity entity) {
		if (handle == null) {
			return false;
		}

		try {
			return (boolean) handle.invokeExact(entity);
		} catch (Throwable t) {
			return false;
		}
	}

	private static Boolean isNeverCullType(EntityType<?> type) {
		return neverCullIds.contains(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
	}
}
