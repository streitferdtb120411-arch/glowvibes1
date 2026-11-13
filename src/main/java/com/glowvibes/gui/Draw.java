package com.glowvibes.gui;

import com.glowvibes.config.Theme;
import com.glowvibes.util.ColorUtil;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small 2D helpers on top of GuiGraphicsExtractor: rounded rects, horizontal gradients, gradient text. */
public final class Draw {
	private Draw() {
	}

	public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0 || ColorUtil.alpha(color) == 0) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		int x2 = x + w, y2 = y + h;
		if (r == 0) {
			g.fill(x, y, x2, y2, color);
			return;
		}
		g.fill(x, y + r, x2, y2 - r, color);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
			g.fill(x + inset, y + i, x2 - inset, y + i + 1, color);
			g.fill(x + inset, y2 - i - 1, x2 - inset, y2 - i, color);
		}
	}

	/** Rounded rect at a sub-pixel position (used for anything that slides, so motion is smooth). */
	public static void roundF(GuiGraphicsExtractor g, float x, float y, int w, int h, int r, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		round(g, 0, 0, w, h, r, color);
		g.pose().popMatrix();
	}

	public static void hGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
		int step = 2;
		for (int i = 0; i < w; i += step) {
			int c = ColorUtil.lerp(c1, c2, w <= 1 ? 0f : (float) i / (w - 1));
			g.fill(x + i, y, Math.min(x + w, x + i + step), y + h, c);
		}
	}

	/** Soft outer glow made of stacked translucent rounded rects. */
	public static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color, int layers, float strength) {
		for (int i = layers; i >= 1; i--) {
			float a = strength * (1f - (float) i / (layers + 1)) / layers;
			round(g, x - i * 2, y - i * 2, w + i * 4, h + i * 4, r + i * 2, ColorUtil.fade(color, a * 2.2f));
		}
	}

	public static int gradientText(GuiGraphicsExtractor g, Font font, String s, int x, int y, Theme th, float offset, float alpha, boolean shadow) {
		int cx = x;
		for (int i = 0; i < s.length(); i++) {
			String ch = String.valueOf(s.charAt(i));
			int col = ColorUtil.withAlpha(th.gradient(offset + i * 0.06f), alpha);
			g.text(font, ch, cx, y, col, shadow);
			cx += font.width(ch);
		}
		return cx - x;
	}
}
