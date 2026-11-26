# Compatibility

GpuBoost injects into three places only:

| Hook | Kind | Shared with |
|---|---|---|
| `Minecraft.renderFrame(boolean)` HEAD | `@Inject` (read-only timing) | Sodium injects into the same method for its frame statistics; both run. |
| `GlSurface.present` HEAD/RETURN (OpenGL backend) | `@Inject` (timer queries) | Sodium injects at RETURN for context checks; both run. |
| `EntityRenderer.shouldRender` | MixinExtras `@WrapMethod` | Sodium wraps it for section-visibility culling and Entity Culling adds occlusion culling. Wrappers chain; GpuBoost only adds an early distance rejection. |

All injectors are `require = 0`. If a hook can't be applied, only that feature turns off (and the log says so).

| Mod | Status |
|---|---|
| **Sodium 0.9.x (26.3)** | Recommended. GpuBoost doesn't replace or modify its renderer. It reads Sodium's active draw backend to display it. |
| **Iris** | Works. Shader packs make most setups GPU-bound; GpuBoost measures that and suggests shader settings. It doesn't modify shader rendering. |
| **Lithium**, **FerriteCore**, **ModernFix**, **C2ME** | No overlap. |
| **ImmediatelyFast** | No overlap. The HUD overlay uses normal GUI drawing and benefits from its batching. |
| **Entity Culling** | Complementary: occlusion (theirs) plus distance (GpuBoost). |
| **More Culling** | No overlap (block faces). |
| **Other mods that change video settings** | GpuBoost only changes a setting when you pick a profile, or temporarily under adaptive pressure. It never overwrites a value you changed by hand. |

## Graphics backends
- **OpenGL**: all features, including GPU queue-latency timing and VRAM via `GL_NVX_gpu_memory_info` / `GL_ATI_meminfo`.
- **Vulkan**: everything except OpenGL timer queries (mods have no access to the Vulkan device). The bottleneck verdict uses render-thread timing, CPU load and driver telemetry.

## GPUs and operating systems
| | GPU load % | VRAM |
|---|---|---|
| NVIDIA, Windows/Linux | ✅ `nvidia-smi` (ships with the driver) | ✅ |
| AMD, Linux | ✅ amdgpu sysfs | ✅ |
| AMD, Windows | n/a | free VRAM on OpenGL (`GL_ATI_meminfo`) |
| Intel | n/a | n/a |

Everything else (adaptive engine, culling, profiles, benchmark) works on every GPU and OS that Minecraft 26.3 supports.
