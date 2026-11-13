package com.glowvibes.mixin;

import com.glowvibes.fx.WorldTint;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "computeFogColor", at = @At("TAIL"))
	private void glowvibes$tintFog(Camera camera, float partialTicks, ClientLevel level, int renderDistance, float darkenWorldAmount,
			Vector4f dest, CallbackInfo ci) {
		WorldTint.fog(dest);
	}
}
