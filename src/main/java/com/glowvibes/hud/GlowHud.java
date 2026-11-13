package com.glowvibes.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.glowvibes.config.GlowConfig;
import com.glowvibes.config.Theme;
import com.glowvibes.fx.FxManager;
import com.glowvibes.gui.Draw;
import com.glowvibes.util.Anim;
import com.glowvibes.util.ColorUtil;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Animated watermark and feature list (entries slide in and out). */
public final class GlowHud {
	private static final Map<String, Anim> SLIDE = new LinkedHashMap<>();
	private static long last;
	private static long fpsWindowStart;
	private static int frames;
	private static int fps;

	private GlowHud() {
	}

	private record Entry(String name, String info, boolean on) {
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		GlowConfig c = GlowConfig.get();
		Theme th = Theme.current();
		Font font = mc.font;

		long now = System.nanoTime();
		float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1.0e9f);
		last = now;
		frames++;
		if (now - fpsWindowStart >= 1_000_000_000L) {
			fps = frames;
			frames = 0;
			fpsWindowStart = now;
		}
		float time = ColorUtil.seconds();

		PvpHud.render(g, font, c, th, time);

		if (c.hudWatermark) {
			String name = "GlowVibes";
			String info = fps + " fps";
			int nw = font.width(name), iw = font.width(info);
			int w = 22 + nw + 8 + iw + 8, h = 16;
			int x = 5, y = 5;
			Draw.glow(g, x, y, w, h, 6, th.gradient(time * 0.2f), 3, 0.45f);
			Draw.round(g, x, y, w, h, 6, ColorUtil.withAlpha(th.bg, 0.82f));
			Draw.hGradient(g, x + 5, y, w - 10, 1, th.gradient(time * 0.3f), th.gradient(time * 0.3f + 0.5f));
			g.text(font, "✿", x + 7, y + 4, th.gradient(time * 0.4f), true);
			Draw.gradientText(g, font, name, x + 19, y + 4, th, time * 0.35f, 1f, true);
			g.fill(x + 22 + nw + 2, y + 4, x + 22 + nw + 3, y + 12, ColorUtil.withAlpha(th.textDim, 0.6f));
			g.text(font, info, x + 22 + nw + 8, y + 4, th.textDim, false);
		}

		if (c.hudList) {
			List<Entry> entries = new ArrayList<>();
			entries.add(new Entry("Theme", th.display, true));
			entries.add(new Entry("Trails", FxManager.TRAIL_MODES[c.trailMode], c.trails));
			entries.add(new Entry("Jump Circles", FxManager.JUMP_STYLES[c.jumpStyle], c.jumpCircles));
			entries.add(new Entry("Ambience", FxManager.AMB_MODES[c.ambMode == 0 ? th.defaultAmbience : c.ambMode], c.ambience));
			entries.add(new Entry("Halo", "", c.halo));
			entries.add(new Entry("Orbit", String.valueOf(c.orbitCount), c.orbit));
			entries.add(new Entry("Hit FX", "", c.hitFx));
			entries.add(new Entry("Chroma", "", c.chroma));
			entries.add(new Entry("Screen Tint", "", c.screenTint));
			entries.add(new Entry("World Tint", "", c.worldTint));
			entries.add(new Entry("Lightning", c.handFx && c.itemFx ? "Hand+Item" : (c.handFx ? "Hand" : "Item"), c.handFx || c.itemFx));
			entries.sort((a, b) -> Integer.compare(width(font, b), width(font, a)));

			int sw = g.guiWidth();
			float y = 4;
			int i = 0;
			for (Entry e : entries) {
				Anim a = SLIDE.computeIfAbsent(e.name(), k -> new Anim(10f));
				a.target = e.on() ? 1f : 0f;
				a.update(dt);
				if (a.value < 0.01f) {
					continue;
				}
				float k = Anim.easeOutCubic(a.value);
				int w = width(font, e) + 10;
				float x = sw - 4 - w * k;
				int col = th.gradient(i * 0.09f + time * 0.25f);
				int h = 11;
				g.pose().pushMatrix();
				g.pose().translate(x, y);
				Draw.round(g, 0, 0, w, h, 2, ColorUtil.withAlpha(th.bg, 0.62f * k));
				g.fill(w - 2, 0, w, h, ColorUtil.withAlpha(col, k));
				g.text(font, e.name(), 4, 2, ColorUtil.withAlpha(col, k), true);
				if (!e.info().isEmpty()) {
					g.text(font, e.info(), 4 + font.width(e.name()) + 4, 2, ColorUtil.withAlpha(th.textDim, k), false);
				}
				g.pose().popMatrix();
				y += (h + 1) * k;
				i++;
			}
		}
	}

	private static int width(Font font, Entry e) {
		return font.width(e.name()) + (e.info().isEmpty() ? 0 : 4 + font.width(e.info()));
	}
}
