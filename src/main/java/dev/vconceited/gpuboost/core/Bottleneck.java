package dev.vconceited.gpuboost.core;

public enum Bottleneck {
	UNKNOWN("Unknown", "Not enough data yet."),
	FPS_CAP("FPS cap / VSync", "Frames are limited by VSync or the frame-rate limit, not by hardware."),
	CPU_MAIN_THREAD("CPU (render thread)", "The game's render thread is busy almost the entire frame."),
	CPU_SYSTEM("CPU (system saturated)", "All CPU cores are near 100%; the integrated server, chunk builders or other programs compete with rendering."),
	GPU("GPU", "The GPU finishes frames later than the CPU submits them; frames queue up on the GPU."),
	MEMORY_GC("Memory / GC", "Garbage collection or a nearly full Java heap is costing frame time."),
	BALANCED("Balanced", "No single subsystem dominates; neither CPU nor GPU is saturated.");

	public final String label;
	public final String explanation;

	Bottleneck(String label, String explanation) {
		this.label = label;
		this.explanation = explanation;
	}
}
