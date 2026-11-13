package com.glowvibes.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/** Bridges the FX engine into the 26.3 submit-node pipeline (same pattern as Fabric API's own LevelRenderEventsTests). */
public final class FxRenderer {
	private static final QuadBuffer BUFFER = new QuadBuffer();

	private FxRenderer() {
	}

	public static void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		Vec3 cam = context.levelState().cameraRenderState.pos;
		BUFFER.begin(cam.x, cam.y, cam.z);
		FxManager.get().onFrame(BUFFER, mc);

		final int n = BUFFER.size();
		if (n == 0) {
			return;
		}
		final float[] pos = BUFFER.positions();
		final int[] col = BUFFER.colors();
		// Vertices are already camera-relative, so no translate is needed (avoids float jitter far from 0,0).
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugFilledBox(), (pose, buffer) -> {
			for (int i = 0; i < n; i++) {
				buffer.addVertex(pose, pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]).setColor(col[i]);
			}
		});
	}
}
