package com.glowvibes.test;

import com.glowvibes.config.GlowConfig;
import com.glowvibes.gui.GlowScreen;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

public class GlowClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		GlowConfig c = GlowConfig.get();
		c.halo = true;
		c.orbit = true;
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(creator -> creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			sp.getServer().runCommand("time set 1000");
			sp.getServer().runCommand("gamerule doDaylightCycle false");
			sp.getServer().runCommand("weather clear");
			sp.getConnection().waitForChunksRender();
			context.waitTicks(40);
			context.takeScreenshot("01_world_empty_hand");

			sp.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
			context.waitTicks(30);
			context.takeScreenshot("02_sword_lightning");

			context.getInput().holdKeyFor(options -> options.keyJump, 3);
			context.waitTicks(6);
			context.takeScreenshot("03_jump_circle");
			context.waitTicks(30);

			sp.getServer().runCommand("execute as @a at @s run summon minecraft:zombie ^ ^ ^2.2 {NoAI:1b,Silent:1b}");
			context.waitTicks(20);
			context.runOnClient(client -> {
				var p = client.player;
				p.setXRot(10f);
			});
			context.waitTicks(10);
			context.takeScreenshot("04_target_in_reach");

			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.getInput().holdKeyFor(options -> options.keyUp, 20);
			context.waitTicks(4);
			context.takeScreenshot("05_third_person_trail_aura");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

			context.setScreen(GlowScreen::new);
			context.waitTicks(30);
			context.takeScreenshot("06_menu_themes");
			context.setScreen(() -> null);
		}
	}
}
