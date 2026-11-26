package dev.vconceited.gpuboost.client.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import dev.vconceited.gpuboost.client.GpuBoostRuntime;
import dev.vconceited.gpuboost.client.ui.Fmt;
import dev.vconceited.gpuboost.client.ui.GpuBoostScreen;
import dev.vconceited.gpuboost.core.BenchmarkStore;
import dev.vconceited.gpuboost.core.FrameStats;
import dev.vconceited.gpuboost.core.GpuBoostConfig;
import dev.vconceited.gpuboost.core.PerformanceProfile;

public final class GpuBoostCommands {
	private GpuBoostCommands() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(ClientCommands.literal("gpuboost")
				.executes(ctx -> {
					GpuBoostRuntime rt = GpuBoostRuntime.get();
					rt.openScreenNextTick(new GpuBoostScreen(null));
					return 1;
				})
				.then(ClientCommands.literal("status").executes(GpuBoostCommands::status))
				.then(ClientCommands.literal("hud").executes(ctx -> {
					GpuBoostConfig c = GpuBoostRuntime.get().config();
					c.hudEnabled = !c.hudEnabled;
					GpuBoostRuntime.get().saveConfig();
					ctx.getSource().sendFeedback(Component.literal("GpuBoost HUD " + Fmt.onOff(c.hudEnabled)));
					return 1;
				}))
				.then(ClientCommands.literal("profile")
						.then(ClientCommands.argument("name", StringArgumentType.word())
								.suggests((ctx, builder) -> {
									for (PerformanceProfile p : PerformanceProfile.values()) {
										builder.suggest(p.name().toLowerCase(Locale.ROOT));
									}

									return builder.buildFuture();
								})
								.executes(GpuBoostCommands::profile)))
				.then(ClientCommands.literal("bench")
						.then(benchStart("start", false))
						.then(benchStart("baseline", true))
						.then(ClientCommands.literal("stop").executes(ctx -> {
							GpuBoostRuntime.get().bench().cancel("stopped by command");
							return 1;
						}))
						.then(ClientCommands.literal("compare").executes(GpuBoostCommands::compare)))
				.then(ClientCommands.literal("config")
						.then(ClientCommands.literal("export").executes(ctx -> {
							try {
								Path p = GpuBoostRuntime.get().exportConfig();
								ctx.getSource().sendFeedback(Component.literal("Exported " + p));
								return 1;
							} catch (IOException e) {
								ctx.getSource().sendError(Component.literal("Export failed: " + e.getMessage()));
								return 0;
							}
						}))
						.then(ClientCommands.literal("import")
								.executes(ctx -> importConfig(ctx, null))
								.then(ClientCommands.argument("file", StringArgumentType.greedyString())
										.executes(ctx -> importConfig(ctx, StringArgumentType.getString(ctx, "file"))))))
				.then(ClientCommands.literal("restore").executes(ctx -> {
					GpuBoostRuntime rt = GpuBoostRuntime.get();
					rt.vanilla().restoreTemporary();
					int n = rt.vanilla().restoreSnapshot();
					rt.config().applyVanillaTweaks = false;
					rt.onFeatureChanged();
					ctx.getSource().sendFeedback(Component.literal("Restored " + n + " video settings."));
					return 1;
				})));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> benchStart(String name, boolean passive) {
		return ClientCommands.literal(name)
				.executes(ctx -> startBench(ctx, GpuBoostRuntime.get().config().benchDurationSec, null, passive))
				.then(ClientCommands.argument("seconds", IntegerArgumentType.integer(5, 1800))
						.executes(ctx -> startBench(ctx, IntegerArgumentType.getInteger(ctx, "seconds"), null, passive))
						.then(ClientCommands.argument("label", StringArgumentType.greedyString())
								.executes(ctx -> startBench(ctx, IntegerArgumentType.getInteger(ctx, "seconds"),
										StringArgumentType.getString(ctx, "label"), passive))));
	}

	private static int startBench(CommandContext<FabricClientCommandSource> ctx, int seconds, String label, boolean passive) {
		GpuBoostRuntime rt = GpuBoostRuntime.get();
		String error = rt.bench().start(seconds, rt.config().benchMode, label, passive);

		if (error != null) {
			ctx.getSource().sendError(Component.literal(error));
			return 0;
		}

		ctx.getSource().sendFeedback(Component.literal("GpuBoost benchmark started: " + seconds + " s after a " + rt.config().benchWarmupSec
				+ " s warm-up" + (passive ? ", GpuBoost optimizations OFF (baseline)" : "") + ". Do not touch mouse or keyboard."));
		return 1;
	}

	private static int status(CommandContext<FabricClientCommandSource> ctx) {
		GpuBoostRuntime rt = GpuBoostRuntime.get();
		FrameStats.Summary s = rt.rolling();
		var v = rt.verdict();
		var src = ctx.getSource();
		src.sendFeedback(Component.literal("GpuBoost " + rt.config().profile.displayName + " | FPS " + Fmt.num(s.avgFps(), 1)
				+ " | 1% low " + Fmt.num(s.onePercentLowFps(), 1) + " | p99 " + Fmt.num(s.p99Ms(), 2) + " ms"));
		src.sendFeedback(Component.literal("Bottleneck: " + v.bottleneck().label + " (" + Fmt.pct(v.confidence() * 100) + ") " + v.reason()));
		src.sendFeedback(Component.literal("GPU: " + rt.gpu().deviceName() + " [" + rt.gpu().backend() + "], load "
				+ Fmt.pct(rt.telemetry().utilizationPct()) + ", adaptive pressure " + rt.adaptive().pressure()));
		return 1;
	}

	private static int profile(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		PerformanceProfile p = PerformanceProfile.parse(name, null);

		if (p == null) {
			ctx.getSource().sendError(Component.literal("Unknown profile '" + name + "'. Use max_fps, gpu_power, weak_cpu, balanced or custom."));
			return 0;
		}

		List<String> changes = GpuBoostRuntime.get().setProfile(p);
		ctx.getSource().sendFeedback(Component.literal("Profile " + p.displayName + " active."
				+ (changes.isEmpty() ? "" : " Changed: " + String.join(", ", changes))));
		return 1;
	}

	private static int importConfig(CommandContext<FabricClientCommandSource> ctx, String file) {
		GpuBoostRuntime rt = GpuBoostRuntime.get();

		try {
			Path p = null;

			if (file != null) {
				p = Path.of(file);

				if (!p.isAbsolute()) {
					p = rt.dataDir().resolve(file);
				}
			}

			Path used = rt.importConfig(p);
			ctx.getSource().sendFeedback(Component.literal("Imported " + used.getFileName() + " (profile " + rt.config().profile.displayName + ")"));
			return 1;
		} catch (IOException | RuntimeException e) {
			ctx.getSource().sendError(Component.literal("Import failed: " + e.getMessage()));
			return 0;
		}
	}

	private static int compare(CommandContext<FabricClientCommandSource> ctx) {
		GpuBoostRuntime rt = GpuBoostRuntime.get();

		try {
			List<Path> files = BenchmarkStore.listSummaries(rt.benchmarkDir());

			if (files.isEmpty()) {
				ctx.getSource().sendError(Component.literal("No benchmark results yet. Run /gpuboost bench baseline and /gpuboost bench start."));
				return 0;
			}

			List<BenchmarkStore.Row> rows = new ArrayList<>();

			for (Path f : files.subList(Math.max(0, files.size() - 12), files.size())) {
				try {
					rows.add(BenchmarkStore.readRow(f));
				} catch (IOException | RuntimeException e) {
				}
			}

			String table = BenchmarkStore.compareTable(rows);
			Path out = rt.benchmarkDir().resolve("comparison.txt");
			Files.writeString(out, table, StandardCharsets.UTF_8);

			BenchmarkStore.Row base = rows.get(0);

			for (BenchmarkStore.Row r : rows) {
				String rel = r == base || base.avgFps() <= 0 ? "" : String.format(Locale.ROOT, " (%+.1f%% vs first)", (r.avgFps() / base.avgFps() - 1) * 100);
				ctx.getSource().sendFeedback(Component.literal(r.name() + " [" + r.mods() + "]: avg " + Fmt.num(r.avgFps(), 1)
						+ ", 1% low " + Fmt.num(r.low1(), 1) + ", p99 " + Fmt.num(r.p99Ms(), 2) + " ms" + rel));
			}

			ctx.getSource().sendFeedback(Component.literal("Full table: gpuboost/benchmarks/comparison.txt"));
			return 1;
		} catch (IOException e) {
			ctx.getSource().sendError(Component.literal("Could not read results: " + e.getMessage()));
			return 0;
		}
	}
}
