package com.glowvibes.mixin;

import com.glowvibes.fx.WorldTint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;

@Mixin(Lightmap.class)
public abstract class LightmapMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void glowvibes$tintLight(LightmapRenderState renderState, CallbackInfo ci) {
		if (WorldTint.active()) {
			renderState.skyLightColor = WorldTint.light(renderState.skyLightColor);
			renderState.ambientColor = WorldTint.light(renderState.ambientColor);
		}
	}
}
