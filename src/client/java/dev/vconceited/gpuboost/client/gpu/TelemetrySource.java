package dev.vconceited.gpuboost.client.gpu;

import dev.vconceited.gpuboost.core.GpuTelemetry;

public interface TelemetrySource {
	String name();

	GpuTelemetry latest();

	default void poll() {
	}

	boolean isAlive();

	void stop();
}
