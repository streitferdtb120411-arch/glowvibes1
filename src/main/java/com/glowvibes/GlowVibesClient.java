package com.glowvibes;

import com.glowvibes.config.GlowConfig;
import com.glowvibes.fx.FxManager;
import com.glowvibes.fx.FxRenderer;
import com.glowvibes.gui.GlowScreen;
import com.glowvibes.hud.GlowHud;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

public class GlowVibesClient implements ClientModInitializer {
	public static final String MOD_ID = "glowvibes";
	public static KeyMapping openMenu;

	@Override
	public void onInitializeClient() {
		GlowConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.glowvibes.open", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_RSHIFT, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenu.consumeClick()) {
				if (client.gui.screen() == null) {
					client.gui.setScreen(new GlowScreen());
				}
			}
			if (client.gui.screen() instanceof GlowScreen screen && screen.isFinished()) {
				client.gui.setScreen(null);
			}
			FxManager.get().onTick(client);
		});

		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(FxRenderer::render);

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "hud"), GlowHud::extract);

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (player == Minecraft.getInstance().player) {
				FxManager.get().onAttack(entity);
			}
			return InteractionResult.PASS;
		});
	}
}
