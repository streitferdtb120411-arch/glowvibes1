package com.glowvibes.hud;

import com.glowvibes.config.GlowConfig;
import com.glowvibes.config.Theme;
import com.glowvibes.fx.FxManager;
import com.glowvibes.gui.Draw;
import com.glowvibes.util.Anim;
import com.glowvibes.util.ColorUtil;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Crosshair target brackets, hit marker, combo counter and reach readout. Pure display. */
public final class PvpHud {
	private PvpHud() {
	}

	public static void render(GuiGraphicsExtractor g, Font font, GlowConfig c, Theme th, float time) {
		FxManager fx = FxManager.get();
		int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
		int red = c.pvpTargetThemeColor ? th.gradient(time * 0.3f) : 0xFFFF3040;

		// target brackets around the crosshair
		float ta = fx.targetAlpha();
		if (c.pvpCrosshair && ta > 0.01f) {
			float k = Anim.easeOutBack(ta);
			float r = 14f - 6f * k;
			int col = ColorUtil.withAlpha(red, ta);
			g.pose().pushMatrix();
			g.pose().rotateAbout((1f - ta) * 0.8f, cx, cy);
			for (int i = 0; i < 4; i++) {
				float sx = (i == 0 || i == 3) ? -1 : 1, sy = i < 2 ? -1 : 1;
				float bx = cx + sx * r, by = cy + sy * r;
				g.pose().pushMatrix();
				g.pose().translate(bx, by);
				int hx = sx < 0 ? 0 : -3, hy = sy < 0 ? 0 : -1;
				g.fill(hx, hy, hx + 4, hy + 1, col);
				int vx = sx < 0 ? 0 : -1, vy = sy < 0 ? 0 : -3;
				g.fill(vx, vy, vx + 1, vy + 4, col);
				g.pose().popMatrix();
			}
			g.pose().popMatrix();
			if (c.reachDisplay && fx.target != null) {
				String d = String.format(java.util.Locale.ROOT, "%.1fm", fx.targetDistance);
				int w = font.width(d);
				g.text(font, d, cx - w / 2, cy + 14, ColorUtil.withAlpha(red, ta * 0.9f), true);
			}
		}

		float since = time - fx.lastHitTime;

		// hit marker
		if (c.hitMarker && since < 0.3f) {
			float p = since / 0.3f;
			float a = 1f - p;
			float r0 = 4f + p * 3f, r1 = 9f + p * 3f;
			int col = ColorUtil.withAlpha(0xFFFFFFFF, a);
			int glow = ColorUtil.withAlpha(red, a * 0.5f);
			for (int i = 0; i < 4; i++) {
				double ang = Math.PI / 4 + i * Math.PI / 2;
				float x0 = cx + (float) Math.cos(ang) * r0, y0 = cy + (float) Math.sin(ang) * r0;
				float x1 = cx + (float) Math.cos(ang) * r1, y1 = cy + (float) Math.sin(ang) * r1;
				ScreenFx.segment(g, x0, y0, x1, y1, 3, glow);
				ScreenFx.segment(g, x0, y0, x1, y1, 1, col);
			}
		}

		// combo counter
		if (c.comboCounter && fx.combo >= 2 && since < 2.5f) {
			float fade = ColorUtil.clamp01((2.5f - since) / 0.5f);
			float pop = 1f + 0.35f * (1f - Anim.easeOutCubic(ColorUtil.clamp01(since / 0.2f)));
			String s = "x" + fx.combo;
			g.pose().pushMatrix();
			g.pose().translate(cx + 22, cy - 10);
			g.pose().scale(1.4f * pop, 1.4f * pop);
			Draw.gradientText(g, font, s, 0, 0, th, time * 0.4f, fade, true);
			g.pose().popMatrix();
			g.text(font, "COMBO", cx + 22, cy + 4, ColorUtil.withAlpha(th.textDim, fade), false);
		}

		// reach of the last hit
		if (c.reachDisplay && fx.lastHitReach >= 0 && since < 1.6f) {
			float fade = ColorUtil.clamp01((1.6f - since) / 0.5f);
			String s = String.format(java.util.Locale.ROOT, "%.2f blocks", fx.lastHitReach);
			int w = font.width(s) + 10;
			int x = cx - w / 2, y = g.guiHeight() - 72;
			Draw.round(g, x, y, w, 13, 4, ColorUtil.withAlpha(th.bg, 0.65f * fade));
			g.text(font, s, x + 5, y + 3, ColorUtil.withAlpha(th.text, fade), false);
		}
	}
}
