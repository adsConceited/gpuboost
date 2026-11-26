package dev.vconceited.gpuboost.client.gpu;

import java.util.LinkedHashSet;
import java.util.Set;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;

import dev.vconceited.gpuboost.client.GpuBoostClient;

public final class GlTimer {
	private static final int RING = 8;
	private static final int GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX = 0x9048;
	private static final int GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX = 0x9049;
	private static final int GL_VBO_FREE_MEMORY_ATI = 0x87FB;

	private static volatile boolean enabled = true;
	private static volatile long lastHookNanos;

	private static boolean probed;
	private static boolean timingSupported;
	private static boolean nvxMemInfo;
	private static boolean atiMemInfo;
	private static int[] queries;
	private static final long[] submitGl = new long[RING];
	private static final long[] frameStartGl = new long[RING];
	private static int writeIdx;
	private static int readIdx;
	private static int inFlight;
	private static long lastPresentReturnGl = -1;
	private static long lastFrameDoneGl = -1;
	private static long lastVramSampleNanos;
	private static final int[] atiScratch = new int[4];

	private static final Object LOCK = new Object();
	private static double latencySumMs;
	private static int latencyCount;
	private static double busySumMs;
	private static int busyCount;

	private static volatile double vramTotalMb = Double.NaN;
	private static volatile double vramUsedMb = Double.NaN;
	private static volatile double vramFreeMb = Double.NaN;

	private GlTimer() {
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	public static boolean isHookActive() {
		return System.nanoTime() - lastHookNanos < 2_000_000_000L;
	}

	public static boolean isTimingSupported() {
		return timingSupported;
	}

	public static double vramTotalMb() {
		return vramTotalMb;
	}

	public static double vramUsedMb() {
		return vramUsedMb;
	}

	public static double vramFreeMb() {
		return vramFreeMb;
	}

	public static double[] consumeWindowAverages() {
		synchronized (LOCK) {
			double lat = latencyCount > 0 ? latencySumMs / latencyCount : Double.NaN;
			double busy = busyCount > 0 ? busySumMs / busyCount : Double.NaN;
			latencySumMs = 0;
			latencyCount = 0;
			busySumMs = 0;
			busyCount = 0;
			return new double[]{lat, busy};
		}
	}

	public static void beforePresent() {
		lastHookNanos = System.nanoTime();

		try {
			if (!probed) {
				probe();
			}

			if (!enabled || !timingSupported) {
				return;
			}

			drain();

			if (inFlight >= RING) {
				return;
			}

			long now = GL32C.glGetInteger64(GL33C.GL_TIMESTAMP);
			GL33C.glQueryCounter(queries[writeIdx], GL33C.GL_TIMESTAMP);
			submitGl[writeIdx] = now;
			frameStartGl[writeIdx] = lastPresentReturnGl;
			writeIdx = (writeIdx + 1) % RING;
			inFlight++;
		} catch (RuntimeException | LinkageError e) {
			disable(e);
		}
	}

	public static void afterPresent() {
		if (!probed) {
			return;
		}

		try {
			if (enabled && timingSupported) {
				lastPresentReturnGl = GL32C.glGetInteger64(GL33C.GL_TIMESTAMP);
			}

			long now = System.nanoTime();

			if (now - lastVramSampleNanos >= 1_000_000_000L) {
				lastVramSampleNanos = now;
				sampleVram();
			}
		} catch (RuntimeException | LinkageError e) {
			disable(e);
		}
	}

	private static void probe() {
		probed = true;
		String vendor = safeString(GL11C.GL_VENDOR);
		String renderer = safeString(GL11C.GL_RENDERER);
		String version = safeString(GL11C.GL_VERSION);
		Set<String> ext = new LinkedHashSet<>();
		int count = GL11C.glGetInteger(GL30C.GL_NUM_EXTENSIONS);

		for (int k = 0; k < count; k++) {
			String e = GL30C.glGetStringi(GL11C.GL_EXTENSIONS, k);

			if (e != null) {
				ext.add(e);
			}
		}

		timingSupported = glAtLeast(version, 3, 3) || ext.contains("GL_ARB_timer_query");
		nvxMemInfo = ext.contains("GL_NVX_gpu_memory_info");
		atiMemInfo = ext.contains("GL_ATI_meminfo");

		if (timingSupported) {
			queries = new int[RING];
			GL15C.glGenQueries(queries);
		}

		GpuBoostClient.LOGGER.info("[GpuBoost] OpenGL: {} | {} | {} | timer queries: {} | VRAM query: {}",
				vendor, renderer, version, timingSupported, nvxMemInfo ? "NVX" : atiMemInfo ? "ATI" : "none");
		GpuProbeSink.publishOpenGl(vendor, renderer, version, ext);
	}

	private static void drain() {
		while (inFlight > 0) {
			int q = queries[readIdx];

			if (GL15C.glGetQueryObjecti(q, GL15C.GL_QUERY_RESULT_AVAILABLE) == GL11C.GL_FALSE) {
				return;
			}

			long done = GL33C.glGetQueryObjecti64(q, GL15C.GL_QUERY_RESULT);
			long latency = done - submitGl[readIdx];
			long start = Math.max(frameStartGl[readIdx], lastFrameDoneGl);

			synchronized (LOCK) {
				if (latency >= 0 && latency < 1_000_000_000L) {
					latencySumMs += latency / 1e6;
					latencyCount++;
				}

				if (frameStartGl[readIdx] >= 0 && lastFrameDoneGl >= 0) {
					long busy = done - start;

					if (busy >= 0 && busy < 1_000_000_000L) {
						busySumMs += busy / 1e6;
						busyCount++;
					}
				}
			}

			lastFrameDoneGl = done;
			readIdx = (readIdx + 1) % RING;
			inFlight--;
		}
	}

	private static void sampleVram() {
		if (nvxMemInfo) {
			int totalKb = GL11C.glGetInteger(GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX);
			int freeKb = GL11C.glGetInteger(GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX);

			if (totalKb > 0) {
				vramTotalMb = totalKb / 1024.0;
				vramFreeMb = freeKb / 1024.0;
				vramUsedMb = (totalKb - freeKb) / 1024.0;
			}
		} else if (atiMemInfo) {
			GL11C.glGetIntegerv(GL_VBO_FREE_MEMORY_ATI, atiScratch);
			vramFreeMb = atiScratch[0] / 1024.0;
		}
	}

	private static void disable(Throwable t) {
		if (timingSupported || nvxMemInfo || atiMemInfo) {
			GpuBoostClient.LOGGER.warn("[GpuBoost] OpenGL timing disabled after an error: {}", t.toString());
		}

		timingSupported = false;
		nvxMemInfo = false;
		atiMemInfo = false;
		probed = true;
	}

	private static String safeString(int name) {
		String s = GL11C.glGetString(name);
		return s == null ? "" : s;
	}

	static boolean glAtLeast(String version, int major, int minor) {
		try {
			String[] parts = version.trim().split("[ .]");
			int ma = Integer.parseInt(parts[0]);
			int mi = Integer.parseInt(parts[1]);
			return ma > major || (ma == major && mi >= minor);
		} catch (RuntimeException e) {
			return false;
		}
	}
}
