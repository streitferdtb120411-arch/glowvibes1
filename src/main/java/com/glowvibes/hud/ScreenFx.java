package com.glowvibes.hud;

import java.util.Random;

import com.glowvibes.config.GlowConfig;
import com.glowvibes.config.Theme;
import com.glowvibes.fx.FxManager;
import com.glowvibes.gui.Draw;
import com.glowvibes.util.ColorUtil;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Full-screen colour grading that follows the ambience colour, plus an animated
 * lightning / aura overlay where the first-person hand sits (bottom right).
 */
public final class ScreenFx {
	private static final Random RND = new Random();
	private static final int BOLTS = 4;
	private static final int SEGS = 9;
	private static final float[][] BOLT_X = new float[BOLTS][SEGS + 1];
	private static final float[][] BOLT_Y = new float[BOLTS][SEGS + 1];
	private static final float[] BOLT_AGE = new float[BOLTS];
	private static final float[] BOLT_LIFE = new float[BOLTS];
	private static final float[] SPARK_X = new float[24];
	private static final float[] SPARK_Y = new float[24];
	private static final float[] SPARK_V = new float[24];
	private static final float[] SPARK_P = new float[24];
	private static boolean sparksInit;

	private static long last;

	private ScreenFx() {
	}

	/** HUD element registered first, so the tint sits under the hotbar and every other HUD element. */
	public static void extract(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker deltaTracker) {
		long now = System.nanoTime();
		float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1.0e9f);
		last = now;
		GlowConfig c = GlowConfig.get();
		Theme th = Theme.current();
		float time = ColorUtil.seconds();
		if (c.screenTint) {
			tint(g, c, th, time);
		}
		if (c.handFx) {
			hand(g, c, th, time, dt);
		}
	}

	/** The colour the whole scene is graded towards: the theme's ambience colour, slowly drifting. */
	public static int ambienceColor(Theme th, float time) {
		return ColorUtil.lerp(th.particle(0), th.gradient(time * 0.05f), 0.5f);
	}

	public static void tint(GuiGraphicsExtractor g, GlowConfig c, Theme th, float time) {
		int w = g.guiWidth(), h = g.guiHeight();
		int col = ambienceColor(th, time);
		float s = c.tintStrength;
		// soft overall wash
		g.fill(0, 0, w, h, ColorUtil.withAlpha(col, s * 0.22f));
		if (c.vignette) {
			int vh = h / 3, vw = w / 4;
			int edge = ColorUtil.withAlpha(col, s * 0.75f);
			int none = ColorUtil.withAlpha(col, 0f);
			g.fillGradient(0, 0, w, vh, edge, none);
			g.fillGradient(0, h - vh, w, h, none, edge);
			Draw.hGradient(g, 0, 0, vw, h, ColorUtil.withAlpha(col, s * 0.55f), none);
			Draw.hGradient(g, w - vw, 0, vw, h, none, ColorUtil.withAlpha(col, s * 0.55f));
		}
	}

	public static void hand(GuiGraphicsExtractor g, GlowConfig c, Theme th, float time, float dt) {
		if (!FxManager.get().firstPerson) {
			return;
		}
		int w = g.guiWidth(), h = g.guiHeight();
		// rough area of the right arm in first person
		float hx0 = w * 0.66f, hx1 = w * 0.92f, hy0 = h * 0.62f, hy1 = h * 1.02f;
		float k = c.handFxIntensity;
		int col = ambienceColor(th, time);
		boolean aura = c.handFxMode != 0;
		boolean lightning = c.handFxMode != 1;

		if (aura) {
			float pulse = 0.75f + 0.25f * (float) Math.sin(time * 3.0);
			int cx = Math.round((hx0 + hx1) / 2), cy = Math.round((hy0 + hy1) / 2);
			for (int i = 6; i >= 1; i--) {
				int r = Math.round((hx1 - hx0) * 0.12f * i);
				Draw.round(g, cx - r, cy - r, r * 2, r * 2, r, ColorUtil.withAlpha(col, 0.035f * k * pulse));
			}
			if (!sparksInit) {
				for (int i = 0; i < SPARK_X.length; i++) {
					respawnSpark(i, hx0, hx1, hy0, hy1);
					SPARK_Y[i] = hy0 + RND.nextFloat() * (hy1 - hy0);
				}
				sparksInit = true;
			}
			for (int i = 0; i < SPARK_X.length; i++) {
				SPARK_Y[i] -= SPARK_V[i] * dt;
				SPARK_X[i] += (float) Math.sin(time * 2 + SPARK_P[i]) * 8f * dt;
				if (SPARK_Y[i] < hy0 - 30) {
					respawnSpark(i, hx0, hx1, hy0, hy1);
				}
				float a = ColorUtil.clamp01((SPARK_Y[i] - (hy0 - 30)) / 40f) * k;
				int sx = Math.round(SPARK_X[i]), sy = Math.round(SPARK_Y[i]);
				Draw.round(g, sx - 3, sy - 3, 6, 6, 3, ColorUtil.withAlpha(col, 0.25f * a));
				g.fill(sx, sy, sx + 1, sy + 1, ColorUtil.withAlpha(ColorUtil.brighten(col, 0.7f), a));
			}
		}

		if (lightning) {
			for (int b = 0; b < BOLTS; b++) {
				BOLT_AGE[b] += dt;
				if (BOLT_AGE[b] >= BOLT_LIFE[b]) {
					newBolt(b, hx0, hx1, hy0, hy1);
				}
				float t = BOLT_AGE[b] / BOLT_LIFE[b];
				float flick = (RND.nextFloat() < 0.15f) ? 0.4f : 1f;
				float a = (1f - t) * flick * k;
				if (a <= 0.02f) {
					continue;
				}
				int core = ColorUtil.withAlpha(ColorUtil.brighten(col, 0.75f), a);
				int glow = ColorUtil.withAlpha(col, a * 0.3f);
				for (int s = 0; s < SEGS; s++) {
					segment(g, BOLT_X[b][s], BOLT_Y[b][s], BOLT_X[b][s + 1], BOLT_Y[b][s + 1], 5, glow);
					segment(g, BOLT_X[b][s], BOLT_Y[b][s], BOLT_X[b][s + 1], BOLT_Y[b][s + 1], 1, core);
				}
			}
		}
	}

	private static void respawnSpark(int i, float hx0, float hx1, float hy0, float hy1) {
		SPARK_X[i] = hx0 + RND.nextFloat() * (hx1 - hx0);
		SPARK_Y[i] = hy1;
		SPARK_V[i] = 25 + RND.nextFloat() * 45;
		SPARK_P[i] = RND.nextFloat() * 6.28f;
	}

	private static void newBolt(int b, float hx0, float hx1, float hy0, float hy1) {
		BOLT_AGE[b] = 0;
		BOLT_LIFE[b] = 0.08f + RND.nextFloat() * 0.18f;
		float x = hx0 + RND.nextFloat() * (hx1 - hx0);
		float y = hy1;
		float tx = hx0 + RND.nextFloat() * (hx1 - hx0);
		float ty = hy0 - RND.nextFloat() * (hy1 - hy0) * 0.25f;
		float jitter = (hx1 - hx0) * 0.12f;
		for (int s = 0; s <= SEGS; s++) {
			float f = (float) s / SEGS;
			float off = (s == 0 || s == SEGS) ? 0 : (RND.nextFloat() - 0.5f) * 2 * jitter;
			BOLT_X[b][s] = x + (tx - x) * f + off;
			BOLT_Y[b][s] = y + (ty - y) * f;
		}
	}

	/** Thick line as a rotated rect. */
	public static void segment(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int width, int color) {
		float dx = x1 - x0, dy = y1 - y0;
		int len = Math.round((float) Math.sqrt(dx * dx + dy * dy));
		if (len <= 0) {
			return;
		}
		float ang = (float) Math.atan2(dy, dx);
		g.pose().pushMatrix();
		g.pose().translate(x0, y0);
		g.pose().rotateAbout(ang, 0f, 0f);
		int half = Math.max(0, width / 2);
		g.fill(0, -half, len, -half + width, color);
		g.pose().popMatrix();
	}
}
