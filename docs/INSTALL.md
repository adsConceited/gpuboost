# Installing GpuBoost

1. Install **Fabric Loader 0.20.1 or newer** for **Minecraft 26.3** (installer from fabricmc.net, or a launcher such as Prism, Modrinth App or CurseForge App).
2. Put these in `.minecraft/mods`:
   - `fabric-api` for 26.3 (required)
   - `gpuboost-1.0.0+mc26.3.jar`
   - recommended: **Sodium** for 26.3. GpuBoost is designed to sit on top of it.
3. Start the game with Java 25 (launchers handle this automatically).
4. In game, press **U** (rebindable under *Controls → GpuBoost*) or type `/gpuboost`.

## First steps
- Leave the default **BALANCED** profile for a while and watch the **Bottleneck** line in the menu.
- If it says *CPU*, try **WEAK CPU**. If it says *GPU*, try **MAX FPS** or **GPU POWER**.
- Set **Target FPS** under *Features* to your monitor's refresh rate, or the FPS you want.
- Turn on the **HUD overlay** to see FPS, 1% low, the verdict and GPU load while playing.

## Files GpuBoost creates
| Path | Contents |
|---|---|
| `config/gpuboost.json` | Settings, plus the values needed to restore your video settings |
| `gpuboost/exports/` | Exported configs |
| `gpuboost/import.json` | Optional; put a config here and press *Import config* |
| `gpuboost/benchmarks/` | Benchmark results (`.json` summary + `.frames.csv`) and `comparison.txt` |

## Uninstalling
Click **Restore vanilla settings** (or run `/gpuboost restore`) first. That puts back every video setting a profile changed. Then remove the jar.
