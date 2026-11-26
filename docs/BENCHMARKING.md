# Benchmarking

GpuBoost includes a repeatable benchmark. It records every frame plus one telemetry sample per second and writes the results to `gpuboost/benchmarks/`. All numbers come from the run itself; nothing is estimated.

## Making runs comparable
1. Use the **same world, position and view direction** for every run. In a creative test world, `/tp @s <x> <y> <z> <yaw> 0` puts you back at the same spot.
2. Use **identical video settings** and window size. Each result file records the settings, and `tools/compare_benchmarks.py` warns if runs differ.
3. Close other heavy programs. Plug in laptops.
4. Run each configuration **at least 3 times** and compare averages.

Camera modes (*Features → Benchmark camera*):
- `spin` (default): the camera turns at a constant 12°/s, so every direction gets rendered the same way each run.
- `static`: the camera doesn't move.

Each run has a warm-up (default 5 s, discarded) followed by the recording (default 60 s).

## The three-way comparison

| Configuration | How to run it |
|---|---|
| **Vanilla** | Mods: Fabric API + GpuBoost, no Sodium. `/gpuboost bench baseline 60 vanilla` (GpuBoost optimizations off, measurement only) |
| **Sodium alone** | Mods: Fabric API + Sodium + GpuBoost. `/gpuboost bench baseline 60 sodium` |
| **Sodium + GpuBoost** | Same mods. `/gpuboost bench start 60 sodium-gpuboost` |

Then run `/gpuboost bench compare` in game, or `python3 tools/compare_benchmarks.py .minecraft/gpuboost/benchmarks` on the desktop. Use the BALANCED profile for these comparisons, or record that you used a profile that changes video settings, because its tweaks stay active in baseline mode.

> Baseline mode still has GpuBoost's lightweight hooks installed (frame timing and one distance check that returns immediately). For a reference with no GpuBoost at all, measure with an external tool such as PresentMon/CapFrameX (Windows) or MangoHud (Linux), using the same scene.

## Output
- `<date>_<label>.json`: metadata (mods and versions, GPU, driver, backend, all relevant video settings, position) and a summary: average FPS, 1% and 0.1% low, median/p95/p99/max frame time, standard deviation, stutter count, average process/system CPU, render-thread busy %, GPU utilization, GPU latency, peak heap and VRAM, dominant bottleneck. It also has one sample per second.
- `<date>_<label>.frames.csv`: every frame time in ms.

Definitions: average FPS = frames ÷ total time. 1% low = 1000 ÷ the mean of the slowest 1% of frame times. A stutter is a frame longer than 2× the median and at least 8 ms.

No benchmark results are shipped with GpuBoost. Publish only numbers you measured yourself.
