package com.fnva.winkeydisable;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public class WinkeyDisableMod implements ClientModInitializer {

	private static final WinKeyHook hook = new WinKeyHook();

	@Override
	public void onInitializeClient() {
		if (!System.getProperty("os.name").toLowerCase().contains("win")) {
			return;
		}



		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean focused = Minecraft.getInstance().isWindowActive();

			if (focused && !hook.isActive()) {
				hook.install();
			} else if (!focused && hook.isActive()) {
				hook.uninstall();
			}
		});

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (hook.isActive()) hook.uninstall();
		}));
	}
}