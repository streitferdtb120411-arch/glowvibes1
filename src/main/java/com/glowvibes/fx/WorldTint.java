package com.glowvibes.fx;

import com.glowvibes.config.GlowConfig;
import com.glowvibes.config.Theme;
import com.glowvibes.hud.ScreenFx;
import com.glowvibes.util.ColorUtil;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import net.minecraft.client.renderer.state.level.SkyRenderState;

/** Pulls sky, fog and daylight towards the ambience colour, keeping their brightness. */
public final class WorldTint {
	private WorldTint() {
	}

	private static float strength() {
		GlowConfig c = GlowConfig.get();
		return c.worldTint ? ColorUtil.clamp01(c.worldTintStrength) : 0f;
	}

	private static float[] tintRgb() {
		int col = ScreenFx.ambienceColor(Theme.current(), ColorUtil.seconds());
		return new float[] {((col >> 16) & 255) / 255f, ((col >> 8) & 255) / 255f, (col & 255) / 255f};
	}

	/** Tints an rgb triple towards the ambience colour at the same perceived brightness. */
	private static float[] apply(float r, float g, float b, float s) {
		float[] t = tintRgb();
		float lum = r * 0.3f + g * 0.59f + b * 0.11f;
		float tl = Math.max(0.05f, t[0] * 0.3f + t[1] * 0.59f + t[2] * 0.11f);
		float k = lum / tl;
		float tr = Math.min(1f, t[0] * k), tg = Math.min(1f, t[1] * k), tb = Math.min(1f, t[2] * k);
		return new float[] {r + (tr - r) * s, g + (tg - g) * s, b + (tb - b) * s};
	}

	public static void fog(Vector4f dest) {
		float s = strength();
		if (s <= 0f) {
			return;
		}
		float[] o = apply(dest.x, dest.y, dest.z, s);
		dest.set(o[0], o[1], o[2], dest.w);
	}

	public static void sky(SkyRenderState sky) {
		float s = strength();
		if (s <= 0f) {
			return;
		}
		Vector3fc c = sky.skyColor;
		if (c != null) {
			float[] o = apply(c.x(), c.y(), c.z(), s);
			sky.skyColor = new Vector3f(o[0], o[1], o[2]);
		}
		Vector4fc sun = sky.sunriseAndSunsetColor;
		if (sun != null) {
			float[] o = apply(sun.x(), sun.y(), sun.z(), s * 0.7f);
			sky.sunriseAndSunsetColor = new Vector4f(o[0], o[1], o[2], sun.w());
		}
	}

	/** Light colour for the lightmap (sky light / ambient), softer so the world stays readable. */
	public static Vector3f light(Vector3fc c) {
		float s = strength() * 0.55f;
		if (s <= 0f || c == null) {
			return c == null ? null : new Vector3f(c);
		}
		float[] o = apply(c.x(), c.y(), c.z(), s);
		return new Vector3f(o[0], o[1], o[2]);
	}

	public static boolean active() {
		return strength() > 0f;
	}
}
