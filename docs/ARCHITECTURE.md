# GpuBoost: technical analysis and architecture

## 1. Analysis of the target (Minecraft Java 26.3 + Fabric)

These facts were checked against the 26.3 sources of FabricMC/fabric-example-mod (branch `26.3`), FabricMC/fabric (Fabric API, branch `26.3`) and CaffeineMC/sodium (`dev`, which targets 26.3 as Sodium 0.9.4-beta.2). Nothing below was guessed from older versions.

| Topic | 26.3 reality | Consequence for GpuBoost |
|---|---|---|
| Toolchain | Loom `1.19-SNAPSHOT` (plugin `net.fabricmc.fabric-loom`, no remapping), Fabric Loader `0.20.1`, Fabric API `0.166.2+26.3`, Gradle `9.8.2`, Java `25` | The project copies these values and the official Gradle wrapper. |
| Names | Minecraft 26.x ships **unobfuscated**, and mods compile directly against Mojang names | Mixin targets and the reflection fallbacks use Mojang names at runtime too. |
| Rendering API | A backend abstraction (`com.mojang.renderpearl…`: `GpuDevice`, device info with backend name and feature flags such as `multiDrawIndirect` and `persistentMapping`) with an **OpenGL** backend and a **Vulkan** backend (the Vulkan backend can be picked in the options) | Graphics API calls are only safe in backend-specific hooks. GpuBoost issues OpenGL calls only from the OpenGL swap hook and never touches Vulkan. |
| GUI | Screens and the HUD use the render-state *extraction* model (`extractRenderState(GuiGraphicsExtractor …)`); HUD elements go through Fabric's `HudElementRegistry` | The UI is written against that model. |
| Input | SDL-based input (`InputConstants`), `KeyMapping.Category.register` | Registered through Fabric's `KeyMappingHelper`. |
| Sodium 0.9.x | Draws terrain itself, with three draw backends: `gl_multidraw`, Vulkan `ext_multidraw` and Vulkan `indirect`. Also does section visibility culling and wraps `EntityRenderer.shouldRender` | GpuBoost must not replace or duplicate any of this. It reads Sodium's active draw backend for display, and its entity hook composes with Sodium's (`@WrapMethod` chains). |

### Realistic GPU-driven rendering opportunities

| Idea | Verdict |
|---|---|
| Indirect / multi-draw terrain | Already done by Sodium (and by vanilla where supported). A second terrain renderer would conflict with Sodium and Iris. **Not done; detected and reported.** |
| GPU occlusion culling / compute culling of chunk sections | Would require owning the terrain renderer and its buffers (Sodium's). Not doable as a compatible add-on. **Not done.** |
| Mesh shaders (NV/EXT) | No hook in vanilla or Sodium where terrain geometry could be routed through mesh shaders. On Vulkan, mods can't reach the device to create such pipelines. **Detected only.** |
| Persistent mapped buffers | Used by the game's backend and by Sodium (`features().persistentMapping()`). **Reported.** |
| Reduce CPU submission cost for entities | Fully compatible and measurable: skip far entities before the per-entity extraction and draw-submission work. **Implemented** (adaptive entity distance culling). |
| Avoid CPU↔GPU synchronisation in GpuBoost's own work | **Implemented**: timer queries are read only when `GL_QUERY_RESULT_AVAILABLE`, no `glFinish`, no readbacks. |

The honest conclusion: on 26.3, a compatible add-on can't move terrain rendering to the GPU. That belongs to the terrain renderer (Sodium or vanilla). What an add-on *can* do well is measure precisely, cut work that can't be seen, and adapt settings without destabilising the game. That's what GpuBoost does.

## 2. Module structure

```
src/main/java/dev/vconceited/gpuboost/core      platform-independent engine (no Minecraft imports, unit-tested)
  FrameTimeRing, FrameStats        allocation-free frame buffer; avg FPS, 1%/0.1% lows, percentiles, stutters
  WindowSample, BottleneckDetector signal voting + 5-window majority smoothing
  AdaptiveController               pressure levels, hysteresis, cooldowns, render-distance advice
  EntityCullPolicy                 culling decision and exemptions
  GpuBoostConfig, PerformanceProfile, MiniJson   validated config, presets, JSON
  BenchmarkRecorder, BenchmarkStore             warm-up/recording, JSON+CSV output, comparison table
  GpuVendor, CapabilityReport, GpuTelemetry     device classification, capability notes, nvidia-smi parsing

src/client/java/dev/vconceited/gpuboost/client   everything that touches the game
  GpuBoostClient          entrypoint: key binding, HUD, commands, lifecycle/chunk/connection events
  GpuBoostRuntime         central state and per-window pipeline
  mixin/MinecraftMixin    HEAD of Minecraft.renderFrame(boolean) -> frame timing
  mixin/GlSurfaceMixin    HEAD/RETURN of the static GlSurface.present (OpenGL swap) -> GPU timing, VRAM
  mixin/EntityRendererMixin  @WrapMethod EntityRenderer.shouldRender -> distance culling
  monitor/                FrameMonitor (per frame), JvmMetrics (thread CPU, process/system CPU, GC pauses, heap), ChunkMetrics
  gpu/                    DeviceInspector (game device info), GlTimer (timer queries, VRAM), NvidiaSmiMonitor, AmdSysfsMonitor
  optimize/               EntityCulling, VanillaOptionManager (reversible option changes), Recommendations
  bench/                  BenchmarkController (camera path, passive mode, file output)
  ui/                     GpuBoostScreen, FeaturesScreen, HudOverlay, FrameGraph
  command/                /gpuboost client commands
```

## 3. Data flow

```
every frame (render thread)
  Minecraft.renderFrame HEAD ──> FrameMonitor: interval -> ring buffer, benchmark recorder, spin camera
  GlSurface.present HEAD/RETURN (OpenGL only) ──> GlTimer: glGetInteger64(GL_TIMESTAMP) + glQueryCounter,
                                                   drain results that are already available, VRAM every 1 s
  EntityRenderer.shouldRender ──> EntityCulling: one distance compare; exemptions only for far entities

every evaluation window (1 s, 2 s in Weak CPU Mode)
  FrameMonitor.Window (frames, wall time, render-thread CPU time, GC pause time)
  + GlTimer averages (GPU queue latency, GPU frame upper bound)
  + telemetry (nvidia-smi / amdgpu sysfs), process/system CPU, heap, FPS limit, VSync, focus
  -> WindowSample -> BottleneckDetector.update -> AdaptiveController.update
  -> EntityCulling.reconfigure (distance x multiplier), VanillaOptionManager.applyParticleReduction,
     optional auto render distance (3 stable windows, 30 s cooldown, max ±2 chunks)
  -> benchmark sample, UI/HUD state
```

### How the bottleneck verdict is made

Signals vote, and the verdict is the majority of the last five windows:

- **GPU queue latency** (OpenGL): `query_timestamp(frame end on GPU) − GL clock when the CPU finished submitting`. A latency of 50% or more of the frame time is a strong GPU vote. Latency of 20% or less is a CPU vote (the GPU was idle and waiting).
- **Driver utilization**: 92% or more is a strong GPU vote, 60% or less a CPU vote.
- **Render-thread busy fraction** (thread CPU time ÷ wall time): 85% or more is a strong CPU vote; 55% or less is a GPU vote, because the thread is waiting.
- **System CPU** 90% or more turns a CPU verdict into *CPU (system saturated)*.
- **GC pause time** 5% or more of the window, or a nearly full heap with active GC, gives *Memory/GC*.
- **FPS cap**: FPS within 7% of the limit, or VSync on with no saturated component, gives *FPS cap*. The adaptive engine treats that as headroom.

On the Vulkan backend the timer-query signal isn't available. The remaining signals still produce a verdict, with lower confidence, and the UI says so.

## 4. Threading and safety

- Everything runs on the client/render thread except the `nvidia-smi` reader. That's one daemon thread blocked on a pipe, with no polling loop; it ends with the process. There's also a short-lived benchmark file writer.
- No game logic moves off-thread. No graphics API call is made outside the OpenGL swap hook.
- Per-frame paths don't allocate: fixed rings, a cached `MethodHandle` for entity flags, cached field readers.
- **Graceful fallback.** Mixins use `require = 0`. A hook that doesn't apply disables only its feature and is logged. Any unexpected exception in culling, the HUD or GL timing switches that feature off instead of breaking rendering.
- **Reversible settings.** Profile tweaks are snapshotted before the first change. Adaptive changes are recorded as temporary overrides and restored on recovery, disconnect, exit, or the next start after a crash. If you change a setting by hand while an override is active, GpuBoost keeps your value.
- No overclocking, no voltage or driver changes, no OS power settings, no fake counters, no artificial GPU load.

## 5. Verification status of this source release

See [BUILD.md](BUILD.md#what-has-been-verified).
