package dev.vconceited.gpuboost.client.monitor;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JvmMetrics {
	private final ThreadMXBean threads;
	private final boolean threadCpu;
	private final com.sun.management.OperatingSystemMXBean os;
	private final List<GarbageCollectorMXBean> pauseCollectors = new ArrayList<>();
	private final int cores = Runtime.getRuntime().availableProcessors();

	public JvmMetrics() {
		ThreadMXBean t = ManagementFactory.getThreadMXBean();
		boolean supported = false;

		try {
			if (t.isCurrentThreadCpuTimeSupported()) {
				if (!t.isThreadCpuTimeEnabled()) {
					t.setThreadCpuTimeEnabled(true);
				}

				supported = true;
			}
		} catch (UnsupportedOperationException | SecurityException e) {
			supported = false;
		}

		this.threads = t;
		this.threadCpu = supported;
		this.os = ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean sun ? sun : null;

		for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
			String n = gc.getName().toLowerCase(Locale.ROOT);

			if (n.contains("cycles") || n.contains("concurrent")) {
				continue;
			}

			this.pauseCollectors.add(gc);
		}
	}

	public int cores() {
		return this.cores;
	}

	public long currentThreadCpuNs() {
		return this.threadCpu ? this.threads.getCurrentThreadCpuTime() : -1;
	}

	public double processCpuPct() {
		if (this.os == null) {
			return Double.NaN;
		}

		double v = this.os.getProcessCpuLoad();
		return v < 0 ? Double.NaN : v * 100.0;
	}

	public double systemCpuPct() {
		if (this.os == null) {
			return Double.NaN;
		}

		double v = this.os.getCpuLoad();
		return v < 0 ? Double.NaN : v * 100.0;
	}

	public long gcPauseMsTotal() {
		long sum = 0;

		for (GarbageCollectorMXBean gc : this.pauseCollectors) {
			long t = gc.getCollectionTime();

			if (t > 0) {
				sum += t;
			}
		}

		return sum;
	}

	public double heapUsedMb() {
		Runtime r = Runtime.getRuntime();
		return (r.totalMemory() - r.freeMemory()) / (1024.0 * 1024.0);
	}

	public double heapMaxMb() {
		return Runtime.getRuntime().maxMemory() / (1024.0 * 1024.0);
	}
}
