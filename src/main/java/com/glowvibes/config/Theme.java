package com.glowvibes.config;

import com.glowvibes.util.Anim;
import com.glowvibes.util.ColorUtil;

public enum Theme {
	//            name      bg          panel       panel2      accent1     accent2     text        textDim     particles                                                   default ambience
	SAKURA("Sakura", 0xFF1A1116, 0xFF241820, 0xFF33222C, 0xFFFFB7D5, 0xFFFF7EB6, 0xFFFFF1F7, 0xFFB79AA9, new int[] {0xFFFFC2DC, 0xFFFFE3EE, 0xFFFF94C2, 0xFFFFFFFF}, 1),
	DARK("Dark", 0xFF0A0A0F, 0xFF12121A, 0xFF1C1C28, 0xFF8B5CF6, 0xFF6366F1, 0xFFECECF4, 0xFF8E8EA6, new int[] {0xFFA78BFA, 0xFF8B5CF6, 0xFF6366F1, 0xFFC4B5FD}, 4),
	BLUE("Blue", 0xFF06101B, 0xFF0B1928, 0xFF122338, 0xFF38BDF8, 0xFF3B82F6, 0xFFE6F4FF, 0xFF8AA6C1, new int[] {0xFF7DD3FC, 0xFF38BDF8, 0xFF22D3EE, 0xFFE0F2FE}, 2),
	RED("Red", 0xFF130808, 0xFF1D0D0D, 0xFF2A1313, 0xFFFF4D4D, 0xFFFF8A3D, 0xFFFFEDEA, 0xFFC09A96, new int[] {0xFFFF6B6B, 0xFFFF8A3D, 0xFFFFC145, 0xFFFF3B3B}, 6),
	PINK("Pink", 0xFF160915, 0xFF210F21, 0xFF2E162E, 0xFFFF4FD8, 0xFFC026D3, 0xFFFFEBFB, 0xFFC293BC, new int[] {0xFFFF77E9, 0xFFFF4FD8, 0xFFF0ABFC, 0xFFFFFFFF}, 3);

	public final String display;
	public final int bg;
	public final int panel;
	public final int panel2;
	public final int accent1;
	public final int accent2;
	public final int text;
	public final int textDim;
	private final int[] particles;
	public final int defaultAmbience;

	Theme(String display, int bg, int panel, int panel2, int accent1, int accent2, int text, int textDim, int[] particles, int defaultAmbience) {
		this.display = display;
		this.bg = bg;
		this.panel = panel;
		this.panel2 = panel2;
		this.accent1 = accent1;
		this.accent2 = accent2;
		this.text = text;
		this.textDim = textDim;
		this.particles = particles;
		this.defaultAmbience = defaultAmbience;
	}

	public static Theme current() {
		String name = GlowConfig.get().theme;
		for (Theme t : values()) {
			if (t.name().equals(name)) {
				return t;
			}
		}
		return SAKURA;
	}

	/** Static accent gradient of this theme, ignoring chroma. t wraps every 1.0. */
	public int baseGradient(float t) {
		float f = t - (float) Math.floor(t);
		float k = f < 0.5f ? f * 2f : (1f - f) * 2f;
		return ColorUtil.lerp(accent1, accent2, Anim.smooth(k));
	}

	/** Accent gradient that honours the chroma (rainbow) option. */
	public int gradient(float t) {
		GlowConfig c = GlowConfig.get();
		if (c.chroma) {
			return ColorUtil.hsv(t * 0.5f + ColorUtil.seconds() * c.chromaSpeed * 0.15f, 0.55f, 1f);
		}
		return baseGradient(t);
	}

	public int particle(int index) {
		GlowConfig c = GlowConfig.get();
		if (c.chroma) {
			return ColorUtil.hsv(index * 0.137f + ColorUtil.seconds() * c.chromaSpeed * 0.15f, 0.5f, 1f);
		}
		return particles[Math.floorMod(index, particles.length)];
	}

	public int particleCount() {
		return particles.length;
	}
}
