import csv
import glob
import json
import os
import sys

USAGE = "Usage: python3 compare_benchmarks.py <.minecraft/gpuboost/benchmarks> [--csv out.csv]"

KEYS = ["renderDistance", "simulationDistance", "framerateLimit", "vsync", "particles", "clouds", "entityShadows",
        "biomeBlendRadius", "improvedTransparency", "windowWidth", "windowHeight", "mode", "durationSec", "position"]


def fmt(v, digits=1):
    return "n/a" if v is None else f"{v:.{digits}f}"


def main(argv):
    if len(argv) < 2:
        print(USAGE)
        return 2

    files = sorted(glob.glob(os.path.join(argv[1], "*.json")))
    runs = []

    for f in files:
        try:
            with open(f, encoding="utf-8") as fh:
                data = json.load(fh)
        except (OSError, ValueError):
            continue
        if data.get("format") != "gpuboost-benchmark-v1":
            continue
        runs.append((os.path.basename(f)[:-5], data["metadata"], data["summary"]))

    if not runs:
        print("No GpuBoost benchmark results found in", argv[1])
        return 1

    base = runs[0][2].get("avgFps")
    header = f"{'run':34} {'configuration':22} {'avg FPS':>8} {'1% low':>8} {'0.1%low':>8} {'p99 ms':>8} {'stdev':>7} {'stutter':>7} {'CPU%':>6} {'GPU%':>6} {'vs #1':>8}"
    print(header)
    print("-" * len(header))
    rows = []

    for name, meta, s in runs:
        avg = s.get("avgFps")
        rel = "n/a" if not base or avg is None else f"{(avg / base - 1) * 100:+.1f}%"
        print(f"{name[:34]:34} {meta.get('configuration', '?')[:22]:22} {fmt(avg):>8} {fmt(s.get('onePercentLowFps')):>8} "
              f"{fmt(s.get('pointOnePercentLowFps')):>8} {fmt(s.get('p99FrameMs'), 2):>8} {fmt(s.get('frameTimeStdDevMs'), 2):>7} "
              f"{s.get('stutters', 0):>7} {fmt(s.get('avgProcessCpuPct'), 0):>6} {fmt(s.get('avgGpuUtilPct'), 0):>6} {rel:>8}")
        rows.append({"run": name, "configuration": meta.get("configuration"), **s})


    first = runs[0][1]
    for name, meta, _ in runs[1:]:
        diffs = [k for k in KEYS if meta.get(k) != first.get(k)]
        if diffs:
            print(f"\nWARNING: {name} differs from the first run in: {', '.join(diffs)}")

    if "--csv" in argv:
        out = argv[argv.index("--csv") + 1]
        with open(out, "w", newline="", encoding="utf-8") as fh:
            w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()), extrasaction="ignore")
            w.writeheader()
            w.writerows(rows)
        print(f"\nWrote {out}")

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
