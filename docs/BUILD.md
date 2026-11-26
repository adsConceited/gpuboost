# Building GpuBoost

## Requirements
- **JDK 25** (Minecraft 26.3 requires Java 25). Microsoft Build of OpenJDK, Eclipse Temurin or Azul Zulu all work. Set `JAVA_HOME` to it.
- Internet access on the first build. Gradle downloads Gradle 9.8.2, Fabric Loom, Minecraft 26.3, Fabric Loader/API and JUnit, then caches them.

## Windows
```
cd GpuBoost
gradlew.bat build
```

## Linux / macOS
```
cd GpuBoost
chmod +x gradlew
./gradlew build
```

The result is `build/libs/gpuboost-1.0.0+mc26.3.jar`. The `-sources.jar` next to it isn't needed to play.

Other useful tasks:
- `gradlew.bat test`: runs the engine's unit tests (bottleneck detector, adaptive controller, statistics, config, benchmark files).
- `gradlew.bat runClient`: starts a development client with the mod loaded.
- `python3 branding/make_branding.py`: regenerates the icon and banner (needs Pillow).

## Troubleshooting
- *"Unsupported class file major version"* or *"invalid source release: 25"*: Gradle is running on an older JDK. Point `JAVA_HOME` to JDK 25.
- *A compile error naming a Minecraft class or method*: every Minecraft/Fabric API used was checked against the 26.3 sources of Fabric API and Sodium. If a 26.3 hotfix renamed something, the error names it; the fix is a one-line rename in the file it points to.

## What has been verified

The project was written and checked in a sandbox without access to Mojang's or Fabric's Maven servers. That means:

| Check | Status |
|---|---|
| Versions (Loom, Loader, Fabric API, Gradle, Java) | Taken from the official `fabric-example-mod` 26.3 branch |
| Minecraft/Fabric API names | Each one used was matched against the 26.3 sources of Fabric API and Sodium. The few that couldn't be matched (`Entity.hasCustomName/isCurrentlyGlowing`, `Minecraft.isWindowActive`, `Options.hideGui`, window size) are resolved at runtime with fallbacks, so they can't break the build. |
| Engine (`src/main`) | Compiled with `-Xlint:all -Werror`; **46 unit tests pass** |
| Client layer (`src/client`) | Type-checked with `-Werror` against compile-only stand-ins of the 26.3 API it uses. Behaviour checks of the frame monitor, chunk metrics, option snapshot/restore, adaptive particles, crash recovery and entity culling pass. |
| Full Gradle build, Mixin application in the real game, in-game test, real benchmarks | **Not done yet.** Run `gradlew.bat build`, then test in game. |
