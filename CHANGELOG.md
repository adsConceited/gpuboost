# Changelog

## 1.0.0 (Minecraft 26.3, Fabric)
First release.
- Bottleneck detector: OpenGL GPU queue latency, driver telemetry (nvidia-smi / amdgpu sysfs), render-thread CPU time, system CPU, GC.
- Adaptive engine: entity render distance and particle level, with hysteresis and cooldowns. Optional auto render distance.
- Entity distance culling with exemptions. Composes with Sodium and Entity Culling.
- Weak CPU Mode. Profiles: MAX FPS, GPU POWER, WEAK CPU, BALANCED, CUSTOM, with reversible video-setting tweaks.
- GPU detection and capability report for OpenGL and Vulkan.
- Main menu, feature toggles, frame-time graph, HUD overlay, config export/import.
- Repeatable benchmark with a baseline mode, JSON/CSV output and comparison.
