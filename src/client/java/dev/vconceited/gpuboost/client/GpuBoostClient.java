package dev.vconceited.gpuboost.client;

import java.nio.file.Path;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;

import dev.vconceited.gpuboost.client.command.GpuBoostCommands;
import dev.vconceited.gpuboost.client.ui.GpuBoostScreen;
import dev.vconceited.gpuboost.client.ui.HudOverlay;

public final class GpuBoostClient implements ClientModInitializer {
	public static final String MOD_ID = "gpuboost";
	public static final Logger LOGGER = LoggerFactory.getLogger("GpuBoost");

	@Override
	public void onInitializeClient() {
		FabricLoader loader = FabricLoader.getInstance();
		Path configFile = loader.getConfigDir().resolve("gpuboost.json");
		Path dataDir = loader.getGameDir().resolve("gpuboost");
		GpuBoostRuntime rt = GpuBoostRuntime.init(configFile, dataDir);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		KeyMapping openMenu = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.gpuboost.open_menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_U, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenu.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new GpuBoostScreen(null));
				}
			}

			rt.onClientTick(client);
		});

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "overlay"), new HudOverlay());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> GpuBoostCommands.register(dispatcher));
		ClientLifecycleEvents.CLIENT_STARTED.register(rt::onClientStarted);
		ClientLifecycleEvents.CLIENT_STOPPING.register(rt::onClientStopping);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> rt.chunks().onChunkLoad());
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> rt.chunks().onChunkUnload());
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> rt.chunks().onJoin());
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> rt.onDisconnect());

		LOGGER.info("[GpuBoost] Initialised (profile {}). Press U or run /gpuboost to open the menu.", rt.config().profile.displayName);
	}
}
