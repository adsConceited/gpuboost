# Project page text (Modrinth / CurseForge)

**Name:** GpuBoost

**Summary (one line):**
Finds out what is really limiting your FPS (CPU, GPU, memory or a cap) and adapts entity and particle load to keep you at your target. Works with Sodium.

**Categories / tags:** Optimization, Utility · Client-side · Fabric · Minecraft 26.3

**Dependencies:** Fabric API (required), Sodium (recommended)

---

![GpuBoost](https://raw.githubusercontent.com/YOUR_GITHUB/GpuBoost/main/branding/banner.png)

## Stop guessing why your game stutters

Most "FPS mods" flip a few settings and hope. **GpuBoost measures first.** Every second it checks what is actually holding your frames back:

- 🟥 **CPU**: the game's render thread is busy the whole frame, or every core is maxed out
- 🟩 **GPU**: frames are queuing up on your graphics card
- 🟨 **Memory**: garbage-collection pauses are eating frame time
- ⬜ **FPS cap / VSync**: you already reach your limit

Then it only cuts what helps, only while it helps, and puts everything back when your FPS recovers.

## Features

**⚡ Adaptive engine.** Set a target FPS. If you stay below it, GpuBoost shrinks how far away entities are rendered and lowers particles, step by step. It reacts with hysteresis and cooldowns, so settings never flicker back and forth.

**👁 Entity distance culling.** Mobs, items and other entities far away are skipped by the renderer before any work is done for them. Players, named, glowing and boss mobs are always shown. Only rendering is affected: farms, AI and redstone keep working exactly the same.

**🐢 Weak CPU Mode.** Made for older or low-power processors. It gives a tighter entity budget, lower monitoring overhead, no background processes, and cheaper chunk-meshing settings.

**🎛 Five profiles.** MAX FPS · GPU POWER · WEAK CPU · BALANCED · CUSTOM. Any video setting a profile changes is saved first, and one click on **Restore vanilla settings** brings it all back.

**📊 Live performance panel** (press **U**):
- FPS, 1% low and 0.1% low, frame-time graph
- Bottleneck verdict with the reason ("GPU finishes 6.1 ms after submit")
- Render-thread CPU time, process and system CPU load
- GPU model, driver, OpenGL or Vulkan backend, GPU load and VRAM (NVIDIA on Windows/Linux, AMD on Linux)
- RAM and GC time, entity and chunk statistics
- Recommendations (render distance for your target, simulation distance in singleplayer, memory)

**🖥 HUD overlay** with the key numbers and a mini frame-time graph, drawn from cached text so it costs almost nothing.

**🔬 Built-in benchmark.** A repeatable camera path, warm-up, and a **baseline** mode that switches all GpuBoost optimizations off, so you can measure the difference yourself. Results are saved as JSON + CSV, and `/gpuboost bench compare` shows them side by side.

**🧩 GPU capability report.** Shows whether your GPU supports multi-draw indirect, mesh shaders, persistent buffers and so on, and honestly states who uses each feature.

## Works with your favourite mods
✅ **Sodium** (recommended: GpuBoost works *on top of* it and never replaces its renderer) · ✅ Iris · ✅ Lithium · ✅ ImmediatelyFast · ✅ Entity Culling · ✅ More Culling · ✅ FerriteCore

## Honest by design
- No fake FPS counters, no artificial GPU load to make usage graphs look high
- No overclocking, voltage, driver or Windows power-plan changes
- Every changed setting is reversible, including after a crash
- If a part of the game changes in an update, that one feature switches itself off instead of crashing your game

## Commands
`/gpuboost` · `/gpuboost status` · `/gpuboost profile <name>` · `/gpuboost hud` · `/gpuboost bench start|baseline|stop|compare` · `/gpuboost config export|import` · `/gpuboost restore`

## FAQ
**Does it replace Sodium?** No. Use both. Sodium makes terrain rendering fast; GpuBoost measures, adapts and trims entity and particle work.

**How much FPS will I gain?** It depends on what limits you. Busy scenes with many entities on a CPU-limited PC gain the most. If you are GPU-limited by a shader pack, GpuBoost will tell you so and point at the right settings. Run the built-in benchmark to see your own numbers.

**Is it client-side only?** Yes. It works on any server, and the commands never contact the server.

**Vulkan?** Supported. A few GPU timing details are only available on the OpenGL backend; everything else works on both.

---
Made by **vConceited** · MIT License
