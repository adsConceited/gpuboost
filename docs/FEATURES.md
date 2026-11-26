# Feature matrix: specification vs. implementation

Legend: ✅ implemented in GpuBoost · 🔁 handled by Sodium/vanilla; GpuBoost detects and reports it · ℹ️ detected/advisory only · ❌ not implemented (reason given)

## GPU-driven rendering (spec 4A)
| Item | Status | Notes |
|---|---|---|
| Indirect drawing | 🔁 | Sodium 0.9 uses `gl_multidraw` on OpenGL and `ext_multidraw` / `indirect` on Vulkan. GpuBoost shows which one is active. |
| Reduce CPU submission overhead | ✅ | Entity distance culling removes far entities before their extraction and draw submission. |
| GPU-based visibility / culling | ❌ | Needs ownership of the terrain renderer's buffers, which belong to Sodium or vanilla. A parallel implementation would conflict. |
| Minimise redundant geometry | ✅ / 🔁 | Entities: GpuBoost. Terrain faces and sections: Sodium, More Culling. |
| Persistent GPU buffers | 🔁 | Used by the game backend and Sodium (`persistentMapping` feature). Reported. |
| Avoid GPU synchronisation | ✅ | GpuBoost's own GPU queries are non-blocking (`GL_QUERY_RESULT_AVAILABLE`). |

## Intelligent culling (4B)
| Item | Status | Notes |
|---|---|---|
| Frustum culling | 🔁 | Vanilla and Sodium. GpuBoost's check runs before it for far entities. |
| Occlusion culling | 🔁 | Sections: vanilla and Sodium. Entities: use the *Entity Culling* mod; GpuBoost stacks with it. |
| Entity visibility optimisation | ✅ | Adaptive distance culling with exemptions (players, named, glowing, configurable never-cull list). |
| Block-face culling | 🔁 | Sodium / More Culling. |
| Chunk visibility | 🔁 | Sodium / vanilla section occlusion graph. |

## Chunk rendering (4C)
| Item | Status | Notes |
|---|---|---|
| Mesh generation, rebuilds, uploads, workers, prioritisation | 🔁 | Owned by Sodium (or vanilla). GpuBoost doesn't touch the chunk pipeline, to avoid conflicts. |
| Cheaper meshing on weak CPUs | ✅ | WEAK CPU / MAX FPS profiles lower biome blend radius (reversible). |
| Chunk loading metrics | ✅ | Loaded chunks, arrival rate, initial load time after joining. |

## NVIDIA-specific (4D)
| Item | Status | Notes |
|---|---|---|
| Detect NVIDIA GPUs | ✅ | From the game's device info and GL strings / PCI vendor id. |
| Detect OpenGL/Vulkan capabilities | ✅ | Capability report (extensions plus the game's feature flags). |
| Mesh shaders | ℹ️ | Detected only; no safe hook exists (see ARCHITECTURE.md). |
| VRAM / indirect features | ✅ / ℹ️ | VRAM via `GL_NVX_gpu_memory_info` or `nvidia-smi`. Indirect features reported. |
| No proprietary API required | ✅ | `nvidia-smi` is optional telemetry and can be switched off. |

## Weak CPU Mode (5)
| Item | Status | Notes |
|---|---|---|
| Minimise render-thread overhead | ✅ | Aggressive entity budget (down to 45% of the base distance under pressure). |
| Reduce CPU-side visibility work | ✅ | Far entities are rejected with one distance compare. |
| Reduce entity processing | ✅ | Rendering side only. Ticking is never touched, so gameplay stays correct. |
| Task scheduling / background workers | ✅ | GpuBoost's only background process (`nvidia-smi`) is disabled and monitoring runs every 2 s. GpuBoost never adds worker threads. |
| GC pressure / allocations | ✅ | GpuBoost's per-frame code doesn't allocate. GC time is measured and reported, with heap advice. |
| Chunk update scheduling | 🔁 | Sodium. |
| Adjust workload to CPU resources | ✅ | Adaptive engine uses render-thread busy time and system CPU load. |
| Simulation distance | ℹ️ | Recommended, never changed automatically because it affects gameplay. |

## Adaptive engine (6)
| Item | Status |
|---|---|
| Average FPS, 1% low, frame times | ✅ |
| CPU frame time | ✅ (render-thread CPU ms per frame) |
| GPU frame time | ✅ OpenGL: queue latency and an upper-bound GPU frame time. Vulkan: not measurable by mods, so driver utilization is used instead. |
| RAM | ✅ heap and GC |
| VRAM | ✅ where measurable: NVX/ATI on OpenGL, nvidia-smi, amdgpu sysfs |
| Chunk loading time | ✅ |
| Rendering workload | ✅ entity render checks and culled counts |
| Automatic profiles / bottleneck detection | ✅ |
| Dynamic render-distance recommendations | ✅ always shown; auto-apply is opt-in |
| Adaptive entity distance / particles | ✅ |
| Chunk update prioritisation | 🔁 Sodium |
| No flicker / instability | ✅ discrete levels, hysteresis, dead band, cooldowns |

## Profiles (7), UI (8), compatibility (9), safety (10), benchmarking (11)
All ✅. Known limits:
- **GPU usage %** is available with NVIDIA (`nvidia-smi`, Windows and Linux) and AMD on Linux (amdgpu sysfs). On Intel GPUs and AMD on Windows it shows "n/a", because there is no lightweight, dependency-free source. GpuBoost never estimates this number.
- **Benchmarking "Vanilla"** without GpuBoost loaded can't be measured *by* GpuBoost. Baseline mode (all GpuBoost optimizations off, measurement only) is the in-game equivalent. For a mod-free reference, use an external frame-time tool (see BENCHMARKING.md).
- **Mod Menu** button: not included. Use the key binding (U) or `/gpuboost`.
