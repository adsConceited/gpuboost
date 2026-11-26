package dev.vconceited.gpuboost.client.gpu;

import java.util.Set;

public final class GpuProbeSink {
	public interface Listener {
		void onOpenGl(String vendor, String renderer, String version, Set<String> extensions);
	}

	private record Pending(String vendor, String renderer, String version, Set<String> extensions) {
	}

	private static Listener listener;
	private static Pending pending;

	private GpuProbeSink() {
	}

	public static synchronized void setListener(Listener l) {
		listener = l;

		if (pending != null && l != null) {
			l.onOpenGl(pending.vendor(), pending.renderer(), pending.version(), pending.extensions());
			pending = null;
		}
	}

	static synchronized void publishOpenGl(String vendor, String renderer, String version, Set<String> extensions) {
		if (listener != null) {
			listener.onOpenGl(vendor, renderer, version, extensions);
		} else {
			pending = new Pending(vendor, renderer, version, Set.copyOf(extensions));
		}
	}
}
