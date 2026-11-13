package com.glowvibes.util;

public final class ColorUtil {
	private static final long START = System.nanoTime();

	private ColorUtil() {
	}

	public static float seconds() {
		return (System.nanoTime() - START) / 1.0e9f;
	}

	public static float clamp01(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}

	public static int alpha(int c) {
		return (c >>> 24) & 0xFF;
	}

	/** Replaces the alpha channel with {@code alpha} (0..1). */
	public static int withAlpha(int c, float alpha) {
		int a = Math.round(clamp01(alpha) * 255f);
		return (a << 24) | (c & 0x00FFFFFF);
	}

	/** Multiplies the existing alpha channel by {@code factor}. */
	public static int fade(int c, float factor) {
		int a = Math.round(alpha(c) * clamp01(factor));
		return (a << 24) | (c & 0x00FFFFFF);
	}

	public static int lerp(int c1, int c2, float t) {
		t = clamp01(t);
		int a = (int) (((c1 >>> 24) & 0xFF) + ((((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t));
		int r = (int) (((c1 >> 16) & 0xFF) + ((((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t));
		int g = (int) (((c1 >> 8) & 0xFF) + ((((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t));
		int b = (int) ((c1 & 0xFF) + (((c2 & 0xFF) - (c1 & 0xFF)) * t));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static int brighten(int c, float t) {
		return lerp(c, (c & 0xFF000000) | 0x00FFFFFF, t);
	}

	/** HSV to opaque ARGB. h wraps, s and v in 0..1. */
	public static int hsv(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		float hh = h * 6f;
		int sector = (int) hh;
		float f = hh - sector;
		float p = v * (1f - s);
		float q = v * (1f - s * f);
		float t = v * (1f - s * (1f - f));
		float r, g, b;
		switch (sector) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return 0xFF000000 | ((int) (r * 255f) << 16) | ((int) (g * 255f) << 8) | (int) (b * 255f);
	}
}
