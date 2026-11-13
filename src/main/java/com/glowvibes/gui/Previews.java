package com.glowvibes.gui;

import java.util.Random;

import com.glowvibes.config.GlowConfig;
import com.glowvibes.config.Theme;
import com.glowvibes.hud.ScreenFx;
import com.glowvibes.util.Anim;
import com.glowvibes.util.ColorUtil;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Live 2D previews of each effect, drawn inside the menu so settings show their result instantly. */
final class Previews {
	private Previews() {
	}

	private static float frac(float v) {
		return v - (float) Math.floor(v);
	}

	static void dot(GuiGraphicsExtractor g, float x, float y, float size, int color) {
		int s = Math.max(1, Math.round(size));
		Draw.roundF(g, x - s / 2f, y - s / 2f, s, s, s / 2, color);
	}

	static void glowDot(GuiGraphicsExtractor g, float x, float y, float size, int color, float alpha) {
		dot(g, x, y, size * 3.2f, ColorUtil.withAlpha(color, alpha * 0.14f));
		dot(g, x, y, size * 1.9f, ColorUtil.withAlpha(color, alpha * 0.3f));
		dot(g, x, y, size, ColorUtil.withAlpha(ColorUtil.brighten(color, 0.45f), alpha));
	}

	static void draw(GuiGraphicsExtractor g, Font font, int tab, GlowConfig c, Theme th, int x, int y, int w, int h, float ga) {
		float t = ColorUtil.seconds();
		// stage background: soft vertical gradient + floor line
		g.fillGradient(x, y, x + w, y + h, ColorUtil.withAlpha(th.bg, 0.75f * ga), ColorUtil.withAlpha(ColorUtil.lerp(th.bg, th.accent2, 0.12f), 0.85f * ga));
		g.enableScissor(x, y, x + w, y + h);
		switch (tab) {
			case 1 -> trails(g, c, th, x, y, w, h, t, ga);
			case 2 -> jump(g, c, th, x, y, w, h, t, ga);
			case 3 -> ambience(g, c, th, x, y, w, h, t, ga);
			case 4 -> aura(g, c, th, x, y, w, h, t, ga);
			case 5 -> pvp(g, font, c, th, x, y, w, h, t, ga);
			case 6 -> screen(g, c, th, x, y, w, h, t, ga);
			case 7 -> hud(g, font, c, th, x, y, w, h, t, ga);
			default -> {
			}
		}
		g.disableScissor();
	}

	private static void trails(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w, int h, float t, float ga) {
		if (!c.trails) {
			return;
		}
		float cx = x + w / 2f, cy = y + h / 2f;
		int n = 46;
		float span = 0.035f * (c.trailLife / 1.2f);
		for (int k = n; k >= 0; k--) {
			float s = t - k * span;
			float f = 1f - (float) k / n;
			float px = cx + (float) Math.sin(s * 1.3f) * w * 0.38f;
			float py = cy + (float) Math.sin(s * 2.6f) * h * 0.26f;
			int col = th.gradient(s * 0.7f);
			float a = f * c.trailOpacity * ga;
			float size = 1.5f + f * 4f * (c.trailWidth / 0.45f);
			switch (c.trailMode) {
				case 0 -> {
					dot(g, px, py, size * 2.2f, ColorUtil.withAlpha(col, a * 0.18f));
					dot(g, px, py, size, ColorUtil.withAlpha(ColorUtil.brighten(col, 0.3f), a));
				}
				case 1 -> {
					float off = (float) Math.sin(s * 9f) * (3f + 5f * f) * (c.trailWidth / 0.45f);
					dot(g, px, py + off, 1.5f + f * 2.5f, ColorUtil.withAlpha(ColorUtil.brighten(col, 0.4f), a));
					dot(g, px, py - off, 1.5f + f * 2.5f, ColorUtil.withAlpha(ColorUtil.brighten(th.gradient(s * 0.7f + 0.5f), 0.4f), a));
				}
				default -> {
					if (k % 3 == 0) {
						glowDot(g, px, py, 1.5f + f * 3.5f, col, a);
					}
				}
			}
		}
		float hx = cx + (float) Math.sin(t * 1.3f) * w * 0.38f, hy = cy + (float) Math.sin(t * 2.6f) * h * 0.26f;
		glowDot(g, hx, hy, 5, th.gradient(t * 0.7f), ga);
	}

	private static void jump(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w, int h, float t, float ga) {
		float cx = x + w / 2f, gy = y + h * 0.72f;
		float period = c.jumpDuration + 0.45f;
		float tp = t % period;
		// player
		float jumpH = (float) Math.max(0, Math.sin(Math.PI * Math.min(1f, tp / 0.55f))) * h * 0.32f;
		Draw.roundF(g, cx - 4, gy - 16 - jumpH, 8, 16, 2, ColorUtil.withAlpha(th.text, 0.9f * ga));
		Draw.roundF(g, cx - 3, gy - 22 - jumpH, 6, 6, 1, ColorUtil.withAlpha(th.text, 0.9f * ga));
		if (!c.jumpCircles) {
			return;
		}
		ring(g, c, th, cx, gy, w, tp / c.jumpDuration, t, ga, 0f, 1f);
		if (c.jumpStyle == 2) {
			ring(g, c, th, cx, gy, w, (tp - 0.18f * c.jumpDuration) / c.jumpDuration, t, ga * 0.85f, 0.5f, 0.7f);
		}
	}

	private static void ring(GuiGraphicsExtractor g, GlowConfig c, Theme th, float cx, float gy, int w, float p, float t, float ga, float shift, float scale) {
		if (p <= 0f || p >= 1f) {
			return;
		}
		float r = (0.12f + 0.88f * Anim.easeOutCubic(p)) * w * 0.3f * Math.min(1.6f, c.jumpRadius / 1.3f) * scale;
		float ry = r * 0.3f;
		float a = Math.min(1f, p / 0.07f) * (float) Math.pow(1f - p, 1.4) * ga;
		if (c.jumpStyle == 3) {
			a *= 0.65f + 0.35f * (float) Math.sin(t * 18f);
		}
		int n = 72;
		float spin = t * (c.jumpStyle == 1 ? 1.6f : 0.4f);
		float wall = c.jumpWall * 14f * (1f - p);
		for (int i = 0; i < n; i++) {
			if (c.jumpStyle == 1 && (i / 4) % 2 == 1) {
				continue;
			}
			double ang = spin + i * Math.PI * 2 / n;
			float px = cx + (float) Math.cos(ang) * r, py = gy + (float) Math.sin(ang) * ry;
			int col = th.gradient((float) i / n + shift + t * 0.2f);
			dot(g, px, py, 5, ColorUtil.withAlpha(col, a * 0.18f));
			dot(g, px, py, 2, ColorUtil.withAlpha(ColorUtil.brighten(col, 0.35f), a));
			if (wall > 1 && i % 2 == 0) {
				int ix = Math.round(px), iy = Math.round(py);
				g.fillGradient(ix, iy - Math.round(wall), ix + 1, iy, ColorUtil.withAlpha(col, 0f), ColorUtil.withAlpha(col, a * 0.45f));
			}
		}
	}

	private static void ambience(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w, int h, float t, float ga) {
		if (!c.ambience) {
			return;
		}
		int mode = c.ambMode == 0 ? th.defaultAmbience : c.ambMode;
		int n = Math.max(8, Math.min(46, Math.round(c.ambDensity / 12f)));
		float sz = c.ambSize;
		for (int i = 0; i < n; i++) {
			float fx = frac(i * 0.6180339f), fy = frac(i * 0.4142136f + 0.3f), fz = frac(i * 0.7548777f);
			int col = th.particle(i);
			switch (mode) {
				case 1 -> { // petals falling with sway and spin
					float px = x + frac(fx + t * 0.03f) * w + (float) Math.sin(t * 1.3f + i) * 6f;
					float py = y + frac(fy + t * (0.08f + fz * 0.08f)) * (h + 10) - 5;
					g.pose().pushMatrix();
					g.pose().rotateAbout(t * (fz - 0.5f) * 4f + i, px, py);
					int pw = Math.max(2, Math.round(6 * sz * (0.3f + 0.7f * Math.abs((float) Math.cos(t * 2f + i))))), ph = Math.max(2, Math.round(3 * sz));
					Draw.round(g, Math.round(px) - pw / 2, Math.round(py) - ph / 2, pw, ph, ph / 2, ColorUtil.withAlpha(col, 0.9f * ga));
					g.pose().popMatrix();
				}
				case 2 -> { // fireflies
					float px = x + fx * w + (float) Math.sin(t * 0.7f + i) * 10f;
					float py = y + fy * h + (float) Math.cos(t * 0.9f + i * 1.3f) * 6f;
					float pulse = 0.55f + 0.45f * (float) Math.sin(t * 3f + i);
					glowDot(g, px, py, 2f * sz, col, pulse * ga);
				}
				case 3 -> { // stars
					float px = x + fx * w, py = y + fy * h;
					float tw = 0.5f + 0.5f * (float) Math.sin(t * 4f + i * 2.1f);
					int L = Math.max(1, Math.round((2 + 3 * tw) * sz));
					int ix = Math.round(px), iy = Math.round(py);
					int cc = ColorUtil.withAlpha(ColorUtil.brighten(col, 0.4f), tw * ga);
					g.fill(ix - L, iy, ix + L + 1, iy + 1, cc);
					g.fill(ix, iy - L, ix + 1, iy + L + 1, cc);
					dot(g, px, py, 4 * sz, ColorUtil.withAlpha(col, 0.2f * tw * ga));
				}
				case 4 -> { // crystals
					float px = x + fx * w, py = y + fy * h + (float) Math.sin(t * 0.8f + i) * 3f;
					int s = Math.max(2, Math.round((3 + fz * 3) * sz));
					dot(g, px, py, s * 3, ColorUtil.withAlpha(col, 0.15f * ga));
					g.pose().pushMatrix();
					g.pose().rotateAbout((float) (Math.PI / 4) + t * (0.3f + fz), px, py);
					Draw.round(g, Math.round(px) - s / 2, Math.round(py) - s / 2, s, s, 0, ColorUtil.withAlpha(col, 0.75f * ga));
					g.pose().popMatrix();
				}
				case 5 -> { // snow
					float px = x + frac(fx + (float) Math.sin(t * 0.5f + i) * 0.01f) * w;
					float py = y + frac(fy + t * (0.06f + fz * 0.05f)) * h;
					glowDot(g, px, py, 1.6f * sz, ColorUtil.brighten(col, 0.7f), 0.9f * ga);
				}
				default -> { // embers rising
					float px = x + fx * w + (float) Math.sin(t * 1.1f + i) * 4f;
					float py = y + h - frac(fy + t * (0.1f + fz * 0.1f)) * h;
					float fl = 0.65f + 0.35f * (float) Math.sin(t * 18f + i);
					glowDot(g, px, py, 1.6f * sz, col, fl * ga);
				}
			}
		}
	}

	private static void aura(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w, int h, float t, float ga) {
		float cx = x + w * 0.38f, baseY = y + h - 8;
		// figure
		Draw.roundF(g, cx - 5, baseY - 30, 10, 22, 2, ColorUtil.withAlpha(th.text, 0.9f * ga));
		Draw.roundF(g, cx - 4, baseY - 39, 8, 8, 1, ColorUtil.withAlpha(th.text, 0.9f * ga));
		if (c.halo) {
			float hy = baseY - 45 + (float) Math.sin(t * 2f) * 1.2f;
			for (int i = 0; i < 40; i++) {
				double a = t * 0.9 + i * Math.PI * 2 / 40;
				int col = th.gradient(i / 40f + t * 0.3f);
				float dash = (i / 2) % 2 == 0 ? 1f : 0.5f;
				dot(g, cx + (float) Math.cos(a) * 9, hy + (float) Math.sin(a) * 2.5f, 2, ColorUtil.withAlpha(ColorUtil.brighten(col, 0.4f), dash * ga));
			}
		}
		if (c.orbit) {
			for (int k = 0; k < c.orbitCount; k++) {
				double base = t * 2.2 + k * Math.PI * 2 / c.orbitCount;
				int col = th.gradient((float) k / c.orbitCount + t * 0.15f);
				for (int s = 6; s >= 0; s--) {
					double a = base - s * 0.12;
					float px = cx + (float) Math.cos(a) * 20, py = baseY - 20 + (float) Math.sin(a) * 6 + (float) Math.sin(a * 1.5 + k) * 3;
					float f = 1f - s / 7f;
					if (s == 0) {
						glowDot(g, px, py, 3, col, ga);
					} else {
						dot(g, px, py, 2 * f + 0.5f, ColorUtil.withAlpha(col, f * 0.7f * ga));
					}
				}
			}
		}
		if (c.hitFx) {
			float tx = x + w * 0.75f, ty = baseY - 18;
			Draw.roundF(g, tx - 6, ty - 12, 12, 24, 2, ColorUtil.withAlpha(th.textDim, 0.5f * ga));
			float p = (t % 1.1f) / 0.55f;
			if (p < 1f) {
				float a = (1f - p) * ga;
				int cnt = Math.round(12 * c.hitIntensity);
				for (int i = 0; i < cnt; i++) {
					double ang = i * 2.39996;
					float r = Anim.easeOutCubic(p) * (14 + (i % 3) * 5) * (0.7f + 0.3f * c.hitIntensity);
					glowDot(g, tx + (float) Math.cos(ang) * r, ty + (float) Math.sin(ang) * r + p * p * 6, 1.6f, th.gradient(i * 0.11f), a);
				}
				dot(g, tx, ty, 6 + p * 22, ColorUtil.withAlpha(th.gradient(t * 0.5f), a * 0.18f));
			}
		}
	}

	private static final Anim REACH_ANIM = new Anim(10f);

	private static void pvp(GuiGraphicsExtractor g, Font font, GlowConfig c, Theme th, int x, int y, int w, int h, float t, float ga) {
		int red = c.pvpTargetThemeColor ? th.gradient(t * 0.3f) : 0xFFFF3040;
		// an enemy walks in and out of reach
		float ex = x + w * 0.5f + (float) Math.sin(t * 0.9f) * w * 0.3f;
		float baseY = y + h - 8;
		boolean inReach = Math.abs(ex - (x + w * 0.5f)) < w * 0.13f;
		REACH_ANIM.target = inReach ? 1f : 0f;
		REACH_ANIM.update(0.016f);
		float k = REACH_ANIM.value;
		if (c.pvpTarget && k > 0.01f) {
			float pulse = 0.75f + 0.25f * (float) Math.sin(t * 9f);
			Draw.roundF(g, ex - 10, baseY - 44, 20, 46, 4, ColorUtil.withAlpha(red, 0.14f * k * pulse * ga));
			Draw.roundF(g, ex - 7, baseY - 41, 14, 40, 3, ColorUtil.withAlpha(red, 0.28f * k * pulse * ga));
		}
		Draw.roundF(g, ex - 5, baseY - 30, 10, 22, 2, ColorUtil.withAlpha(th.text, 0.9f * ga));
		Draw.roundF(g, ex - 4, baseY - 39, 8, 8, 1, ColorUtil.withAlpha(th.text, 0.9f * ga));
		int cx = Math.round(x + w * 0.5f), cy = Math.round(baseY - 24);
		g.fill(cx - 4, cy, cx + 5, cy + 1, ColorUtil.withAlpha(0xFFFFFFFF, 0.8f * ga));
		g.fill(cx, cy - 4, cx + 1, cy + 5, ColorUtil.withAlpha(0xFFFFFFFF, 0.8f * ga));
		if (c.pvpCrosshair && k > 0.01f) {
			float r = 14f - 6f * Anim.easeOutBack(k);
			int col = ColorUtil.withAlpha(red, k * ga);
			for (int i = 0; i < 4; i++) {
				int sx = (i == 0 || i == 3) ? -1 : 1, sy = i < 2 ? -1 : 1;
				int bx = Math.round(cx + sx * r), by = Math.round(cy + sy * r);
				g.fill(sx < 0 ? bx : bx - 3, by, sx < 0 ? bx + 4 : bx + 1, by + 1, col);
				g.fill(bx, sy < 0 ? by : by - 3, bx + 1, sy < 0 ? by + 4 : by + 1, col);
			}
		}
		if (c.comboCounter && k > 0.01f) {
			int n = 2 + (int) ((t * 2.5f) % 5);
			Draw.gradientText(g, font, "x" + n, cx + 22, cy - 6, th, t * 0.4f, ga * k, true);
		}
		if (c.reachDisplay && k > 0.01f) {
			String d = String.format(java.util.Locale.ROOT, "%.1fm", 2.4 + Math.abs(Math.sin(t * 0.9f)) * 0.6);
			g.text(font, d, cx - font.width(d) / 2, cy + 12, ColorUtil.withAlpha(red, k * ga), true);
		}
	}

	private static final Random BOLT_RND = new Random();

	private static void screen(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w, int h, float t, float ga) {
		int sw = Math.round(w * 0.62f), sh = h - 10;
		int sx = x + (w - sw) / 2, sy = y + 5;
		// fake scene: sky and ground
		g.fillGradient(sx, sy, sx + sw, sy + sh * 2 / 3, ColorUtil.withAlpha(0xFF7EC8FF, ga), ColorUtil.withAlpha(0xFFBFE6FF, ga));
		g.fillGradient(sx, sy + sh * 2 / 3, sx + sw, sy + sh, ColorUtil.withAlpha(0xFF5FA341, ga), ColorUtil.withAlpha(0xFF3C7A2A, ga));
		int col = ScreenFx.ambienceColor(th, t);
		if (c.screenTint) {
			float s = c.tintStrength;
			g.fill(sx, sy, sx + sw, sy + sh, ColorUtil.withAlpha(col, s * 0.22f * ga));
			if (c.vignette) {
				g.fillGradient(sx, sy, sx + sw, sy + sh / 3, ColorUtil.withAlpha(col, s * 0.75f * ga), ColorUtil.withAlpha(col, 0f));
				g.fillGradient(sx, sy + sh * 2 / 3, sx + sw, sy + sh, ColorUtil.withAlpha(col, 0f), ColorUtil.withAlpha(col, s * 0.75f * ga));
				Draw.hGradient(g, sx, sy, sw / 4, sh, ColorUtil.withAlpha(col, s * 0.55f * ga), ColorUtil.withAlpha(col, 0f));
				Draw.hGradient(g, sx + sw - sw / 4, sy, sw / 4, sh, ColorUtil.withAlpha(col, 0f), ColorUtil.withAlpha(col, s * 0.55f * ga));
			}
		}
		// hand
		int hx = sx + sw - 26, hy = sy + sh - 22;
		Draw.round(g, hx, hy, 16, 26, 3, ColorUtil.withAlpha(0xFFE8B896, ga));
		if (c.handFx) {
			if (c.handFxMode != 0) {
				float pulse = 0.75f + 0.25f * (float) Math.sin(t * 3f);
				dot(g, hx + 8, hy + 10, 34, ColorUtil.withAlpha(col, 0.16f * pulse * c.handFxIntensity * ga));
				dot(g, hx + 8, hy + 10, 20, ColorUtil.withAlpha(col, 0.2f * pulse * c.handFxIntensity * ga));
			}
			if (c.handFxMode != 1) {
				BOLT_RND.setSeed((long) (t * 9));
				for (int b = 0; b < 3; b++) {
					float bx = hx + BOLT_RND.nextFloat() * 16, by = hy + 26;
					float ex = hx - 4 + BOLT_RND.nextFloat() * 24, ey = hy - 8 - BOLT_RND.nextFloat() * 8;
					float lx = bx, ly = by;
					for (int s = 1; s <= 6; s++) {
						float f = s / 6f;
						float nx = bx + (ex - bx) * f + (s == 6 ? 0 : (BOLT_RND.nextFloat() - 0.5f) * 8);
						float ny = by + (ey - by) * f;
						float a = Math.min(1f, c.handFxIntensity) * ga;
						ScreenFx.segment(g, lx, ly, nx, ny, 3, ColorUtil.withAlpha(col, 0.3f * a));
						ScreenFx.segment(g, lx, ly, nx, ny, 1, ColorUtil.withAlpha(ColorUtil.brighten(col, 0.75f), a));
						lx = nx;
						ly = ny;
					}
				}
			}
		}
		// screen frame
		g.fill(sx, sy, sx + sw, sy + 1, ColorUtil.withAlpha(th.text, 0.25f * ga));
		g.fill(sx, sy + sh - 1, sx + sw, sy + sh, ColorUtil.withAlpha(th.text, 0.25f * ga));
		g.fill(sx, sy, sx + 1, sy + sh, ColorUtil.withAlpha(th.text, 0.25f * ga));
		g.fill(sx + sw - 1, sy, sx + sw, sy + sh, ColorUtil.withAlpha(th.text, 0.25f * ga));
	}

	private static void hud(GuiGraphicsExtractor g, Font font, GlowConfig c, Theme th, int x, int y, int w, int h, float t, float ga) {
		if (c.hudWatermark) {
			int wx = x + 8, wy = y + 8;
			int ww = font.width("GlowVibes") + 22;
			Draw.round(g, wx, wy, ww, 14, 5, ColorUtil.withAlpha(th.bg, 0.85f * ga));
			Draw.hGradient(g, wx + 4, wy, ww - 8, 1, ColorUtil.withAlpha(th.gradient(t * 0.3f), ga), ColorUtil.withAlpha(th.gradient(t * 0.3f + 0.5f), ga));
			g.text(font, "✿", wx + 4, wy + 3, ColorUtil.withAlpha(th.gradient(t * 0.4f), ga), true);
			Draw.gradientText(g, font, "GlowVibes", wx + 15, wy + 3, th, t * 0.35f, ga, true);
		}
		if (c.hudList) {
			String[] items = {"Jump Circles", "Ambience", "Trails", "Theme"};
			int ly = y + 6;
			for (int i = 0; i < items.length; i++) {
				int iw = font.width(items[i]) + 8;
				int lx = x + w - 6 - iw;
				int col = th.gradient(i * 0.09f + t * 0.25f);
				Draw.round(g, lx, ly, iw, 11, 2, ColorUtil.withAlpha(th.bg, 0.65f * ga));
				g.fill(lx + iw - 2, ly, lx + iw, ly + 11, ColorUtil.withAlpha(col, ga));
				g.text(font, items[i], lx + 3, ly + 2, ColorUtil.withAlpha(col, ga), true);
				ly += 12;
			}
		}
	}
}
