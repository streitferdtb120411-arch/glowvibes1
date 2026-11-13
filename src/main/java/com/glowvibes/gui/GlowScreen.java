package com.glowvibes.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.glowvibes.config.GlowConfig;
import com.glowvibes.config.Theme;
import com.glowvibes.fx.FxManager;
import com.glowvibes.util.Anim;
import com.glowvibes.util.ColorUtil;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Immediate-mode, fully animated settings screen. Every element eases (hover, toggles, sliders,
 * tab indicator, scroll, open/close), so nothing ever snaps.
 */
public final class GlowScreen extends Screen {
	private static final String[] TABS = {"Themes", "Trails", "Jump Circles", "Ambience", "Aura & Hits", "HUD"};
	private static final String[] ICONS = {"✿", "≈", "◎", "✦", "✸", "▣"};
	private static int lastTab = 0;

	private final Anim open = new Anim(0f, 9f);
	private final Anim tabAnim = new Anim(lastTab, 14f);
	private final Map<String, Anim> anims = new HashMap<>();
	private final List<Petal> petals = new ArrayList<>();
	private final Random rnd = new Random();

	private int tab = lastTab;
	private boolean closing;
	private boolean finished;
	private long lastNanos;
	private float dt;
	private float ga = 1f; // global alpha

	private float scroll;
	private float scrollTarget;
	private boolean click;
	private boolean mouseDown;
	private String dragging;
	private float lmx, lmy;
	private int clipTop, clipBottom;

	private static final class Petal {
		float x, y, vx, vy, rot, vr, size, phase;
		int colorIndex;
	}

	public GlowScreen() {
		super(Component.literal("GlowVibes"));
	}

	public boolean isFinished() {
		return finished;
	}

	// no @Override on purpose: if 26.3 renamed this, the build still succeeds
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		closing = true;
		GlowConfig.save();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// intentionally empty: we draw our own animated backdrop instead of the vanilla blur
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		click = true;
		mouseDown = true;
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		mouseDown = false;
		if (dragging != null) {
			GlowConfig.save();
		}
		dragging = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scrollTarget -= (float) vertical * 28f;
		return true;
	}

	private Anim anim(String id, float speed) {
		return anims.computeIfAbsent(id, k -> new Anim(speed));
	}

	private int col(int c) {
		return ColorUtil.fade(c, ga);
	}

	private boolean hover(int x, int y, int w, int h) {
		return lmx >= x && lmx < x + w && lmy >= y && lmy < y + h && lmy >= clipTop && lmy < clipBottom;
	}

	private boolean clicked(int x, int y, int w, int h) {
		if (click && !closing && hover(x, y, w, h)) {
			click = false;
			return true;
		}
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;

		GlowConfig c = GlowConfig.get();
		Theme th = Theme.current();
		float spd = Math.max(0.3f, c.guiAnimSpeed);

		open.target = closing ? 0f : 1f;
		open.speed = 9f * spd;
		open.update(dt);
		if (closing && open.value < 0.03f) {
			finished = true;
		}
		float t = open.value;
		ga = ColorUtil.clamp01(t * 1.15f);

		// backdrop
		g.fillGradient(0, 0, this.width, this.height, ColorUtil.withAlpha(th.bg, 0.35f * t), ColorUtil.withAlpha(th.bg, 0.78f * t));
		drawPetals(g, th, t);

		int W = Math.min(470, this.width - 16);
		int H = Math.min(290, this.height - 16);
		int px = (this.width - W) / 2;
		int py = (this.height - H) / 2;
		float cx = this.width / 2f, cy = this.height / 2f;
		float s = 0.86f + 0.14f * Anim.easeOutBack(t);
		float slide = (1f - t) * 18f;

		g.pose().pushMatrix();
		g.pose().translate(cx, cy + slide);
		g.pose().scale(s, s);
		g.pose().translate(-cx, -cy);
		lmx = (mouseX - cx) / s + cx;
		lmy = (mouseY - cy - slide) / s + cy;
		clipTop = -100000;
		clipBottom = 100000;

		drawPanel(g, c, th, px, py, W, H);

		g.pose().popMatrix();
		click = false;
	}

	// ------------------------------------------------------------------ backdrop petals

	private void drawPetals(GuiGraphicsExtractor g, Theme th, float t) {
		boolean sakura = th == Theme.SAKURA;
		int want = sakura ? 46 : 34;
		while (petals.size() < want) {
			Petal p = new Petal();
			p.x = rnd.nextFloat() * this.width;
			p.y = petals.isEmpty() || rnd.nextBoolean() ? rnd.nextFloat() * this.height : -10;
			p.vx = 6 + rnd.nextFloat() * 10;
			p.vy = sakura ? 14 + rnd.nextFloat() * 18 : -(6 + rnd.nextFloat() * 10);
			p.rot = rnd.nextFloat() * 6.28f;
			p.vr = (rnd.nextFloat() - 0.5f) * 2.5f;
			p.size = 2 + rnd.nextFloat() * 3;
			p.phase = rnd.nextFloat() * 6.28f;
			p.colorIndex = rnd.nextInt(th.particleCount());
			petals.add(p);
		}
		float time = ColorUtil.seconds();
		for (Petal p : petals) {
			p.y += p.vy * dt;
			p.x += (p.vx * (sakura ? 1f : 0.2f) + (float) Math.sin(time * 1.4f + p.phase) * 10f) * dt;
			p.rot += p.vr * dt;
			if (p.y > this.height + 12 || p.y < -14 || p.x > this.width + 12) {
				p.x = rnd.nextFloat() * this.width - 20;
				p.y = sakura ? -10 : this.height + 10;
			}
			int color = th.particle(p.colorIndex);
			if (sakura) {
				g.pose().pushMatrix();
				g.pose().rotateAbout(p.rot, p.x, p.y);
				int w = Math.round(p.size * 2), h = Math.max(2, Math.round(p.size));
				Draw.round(g, Math.round(p.x) - w / 2, Math.round(p.y) - h / 2, w, h, h / 2, ColorUtil.withAlpha(color, 0.75f * t));
				g.pose().popMatrix();
			} else {
				float tw = 0.5f + 0.5f * (float) Math.sin(time * 2f + p.phase);
				int sz = Math.max(1, Math.round(p.size * 0.7f));
				Draw.round(g, Math.round(p.x) - sz * 2, Math.round(p.y) - sz * 2, sz * 4, sz * 4, sz * 2, ColorUtil.withAlpha(color, 0.12f * t * tw));
				Draw.round(g, Math.round(p.x) - sz / 2, Math.round(p.y) - sz / 2, Math.max(1, sz), Math.max(1, sz), 0, ColorUtil.withAlpha(color, 0.8f * t * tw));
			}
		}
	}

	// ------------------------------------------------------------------ panel

	private void drawPanel(GuiGraphicsExtractor g, GlowConfig c, Theme th, int px, int py, int W, int H) {
		float time = ColorUtil.seconds();
		int side = 122;

		Draw.glow(g, px, py, W, H, 10, th.gradient(time * 0.15f), 5, 0.55f * ga);
		Draw.round(g, px, py, W, H, 10, col(ColorUtil.withAlpha(th.panel, 0.96f)));
		// sidebar
		Draw.round(g, px, py, side, H, 10, col(ColorUtil.withAlpha(th.bg, 0.97f)));
		g.fill(px + side - 10, py, px + side, py + H, col(ColorUtil.withAlpha(th.bg, 0.97f)));
		g.fill(px + side, py + 8, px + side + 1, py + H - 8, col(ColorUtil.withAlpha(th.panel2, 0.9f)));
		// animated accent line on top
		Draw.hGradient(g, px + 10, py, W - 20, 1, col(th.gradient(time * 0.3f)), col(th.gradient(time * 0.3f + 0.5f)));

		// logo
		g.text(this.font, ICONS[0], px + 12, py + 13, col(th.gradient(time * 0.4f)), true);
		Draw.gradientText(g, this.font, "GlowVibes", px + 24, py + 13, th, time * 0.35f, ga, true);
		g.text(this.font, "visual suite", px + 24, py + 25, col(th.textDim), false);
		g.fill(px + 10, py + 40, px + side - 10, py + 41, col(ColorUtil.withAlpha(th.panel2, 1f)));

		// tabs
		int tabY0 = py + 48;
		int tabH = 22;
		tabAnim.target = tab;
		tabAnim.update(dt);
		float hy = tabY0 + tabAnim.value * (tabH + 2);
		Draw.roundF(g, px + 7, hy, side - 14, tabH, 6, col(ColorUtil.withAlpha(th.accent1, 0.16f)));
		Draw.roundF(g, px + 7, hy + 5, 2, tabH - 10, 1, col(th.gradient(time * 0.4f)));
		for (int i = 0; i < TABS.length; i++) {
			int ty = tabY0 + i * (tabH + 2);
			boolean hov = hover(px + 7, ty, side - 14, tabH);
			Anim ha = anim("tab" + i, 12f);
			ha.target = hov ? 1f : 0f;
			ha.update(dt);
			if (ha.value > 0.01f && i != tab) {
				Draw.round(g, px + 7, ty, side - 14, tabH, 6, col(ColorUtil.withAlpha(th.panel2, 0.7f * ha.value)));
			}
			float sel = Math.max(ha.value * 0.6f, 1f - Math.min(1f, Math.abs(tabAnim.value - i)));
			int tc = ColorUtil.lerp(th.textDim, th.text, sel);
			float shift = ha.value * 2f;
			g.pose().pushMatrix();
			g.pose().translate(shift, 0f);
			g.text(this.font, ICONS[i], px + 15, ty + 7, col(ColorUtil.lerp(th.textDim, th.gradient(i * 0.15f + time * 0.2f), sel)), false);
			g.text(this.font, TABS[i], px + 28, ty + 7, col(tc), false);
			g.pose().popMatrix();
			if (clicked(px + 7, ty, side - 14, tabH) && tab != i) {
				tab = i;
				lastTab = i;
				scrollTarget = 0;
			}
		}
		g.text(this.font, "RShift • v2.0", px + 12, py + H - 14, col(ColorUtil.withAlpha(th.textDim, 0.7f)), false);

		// content
		int x0 = px + side + 12;
		int w = W - side - 24;
		g.pose().pushMatrix();
		g.pose().translate(x0, py + 12);
		g.pose().scale(1.35f, 1.35f);
		g.text(this.font, TABS[tab], 0, 0, col(th.text), true);
		g.pose().popMatrix();
		int titleW = Math.round(this.font.width(TABS[tab]) * 1.35f);
		Draw.hGradient(g, x0, py + 27, Math.max(20, titleW), 2, col(th.gradient(time * 0.3f)), col(ColorUtil.withAlpha(th.gradient(time * 0.3f + 0.4f), 0f)));

		int top = py + 36;
		int bottom = py + H - 8;
		clipTop = top;
		clipBottom = bottom;
		scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-16f * dt));

		g.enableScissor(x0 - 4, top, x0 + w + 6, bottom);
		int y = top + 2 - Math.round(scroll);
		int startY = y;
		y = switch (tab) {
			case 0 -> tabThemes(g, c, th, x0, y, w);
			case 1 -> tabTrails(g, c, th, x0, y, w);
			case 2 -> tabJump(g, c, th, x0, y, w);
			case 3 -> tabAmbience(g, c, th, x0, y, w);
			case 4 -> tabAura(g, c, th, x0, y, w);
			default -> tabHud(g, c, th, x0, y, w);
		};
		g.disableScissor();

		int contentH = y - startY;
		int viewH = bottom - top;
		float maxScroll = Math.max(0, contentH - viewH + 4);
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		if (maxScroll > 0) {
			float frac = viewH / (float) contentH;
			int barH = Math.max(18, Math.round(viewH * frac));
			float barY = top + (viewH - barH) * (scroll / maxScroll);
			Draw.roundF(g, x0 + w + 3, barY, 2, barH, 1, col(ColorUtil.withAlpha(th.accent1, 0.6f)));
		}
		clipTop = -100000;
		clipBottom = 100000;
	}

	// ------------------------------------------------------------------ tabs

	private int tabThemes(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w) {
		Theme[] all = Theme.values();
		int cols = 3, gap = 6;
		int cw = (w - gap * (cols - 1)) / cols, ch = 50;
		float time = ColorUtil.seconds();
		for (int i = 0; i < all.length; i++) {
			Theme t = all[i];
			int cx = x + (i % cols) * (cw + gap);
			int cy = y + (i / cols) * (ch + gap);
			boolean sel = t == th;
			Anim sa = anim("theme" + i, 10f).prime(sel ? 1 : 0);
			sa.target = sel ? 1f : 0f;
			sa.update(dt);
			Anim ha = anim("themeH" + i, 12f);
			ha.target = hover(cx, cy, cw, ch) ? 1f : 0f;
			ha.update(dt);
			float lift = ha.value * 2f;
			g.pose().pushMatrix();
			g.pose().translate(0f, -lift);
			if (sa.value > 0.01f) {
				Draw.glow(g, cx, cy, cw, ch, 8, t.baseGradient(time * 0.3f), 3, 0.6f * sa.value * ga);
				Draw.round(g, cx - 1, cy - 1, cw + 2, ch + 2, 8, col(ColorUtil.withAlpha(t.baseGradient(time * 0.3f), sa.value)));
			}
			Draw.round(g, cx, cy, cw, ch, 7, col(t.panel));
			Draw.hGradient(g, cx + 6, cy + 6, cw - 12, 4, col(t.accent1), col(t.accent2));
			for (int k = 0; k < 4; k++) {
				Draw.round(g, cx + 7 + k * 11, cy + 15, 8, 8, 4, col(t.particle(k)));
			}
			g.text(this.font, t.display, cx + 7, cy + 31, col(t.text), false);
			if (sa.value > 0.02f) {
				String tag = "Active";
				int tw = this.font.width(tag);
				g.text(this.font, tag, cx + cw - tw - 6, cy + 31, col(ColorUtil.withAlpha(t.accent1, sa.value)), false);
			}
			g.pose().popMatrix();
			if (clicked(cx, cy, cw, ch)) {
				c.theme = t.name();
				GlowConfig.save();
			}
		}
		int rows = (all.length + cols - 1) / cols;
		y += rows * (ch + gap) + 6;
		y = section(g, th, "Color", x, y);
		boolean chroma = toggle(g, th, "chroma", "Chroma (rainbow) mode", c.chroma, x, y, w);
		if (chroma != c.chroma) {
			c.chroma = chroma;
			GlowConfig.save();
		}
		y += 26;
		c.chromaSpeed = slider(g, th, "chromaSpeed", "Chroma speed", c.chromaSpeed, 0.1f, 3f, 1, "x", x, y, w);
		y += 32;
		c.guiAnimSpeed = slider(g, th, "guiSpeed", "Menu animation speed", c.guiAnimSpeed, 0.5f, 2.5f, 1, "x", x, y, w);
		y += 32;
		c.glowQuality = Math.round(slider(g, th, "quality", "Glow quality", c.glowQuality, 1, 3, 0, "", x, y, w));
		y += 32;
		return y;
	}

	private int tabTrails(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w) {
		c.trails = saveIfChanged(c.trails, toggle(g, th, "trails", "Enable trails", c.trails, x, y, w));
		y += 26;
		c.trailMode = mode(g, th, "trailMode", "Style", FxManager.TRAIL_MODES, c.trailMode, x, y, w);
		y += 26;
		c.trailLife = slider(g, th, "trailLife", "Length", c.trailLife, 0.2f, 3f, 1, "s", x, y, w);
		y += 32;
		c.trailWidth = slider(g, th, "trailWidth", "Width", c.trailWidth, 0.1f, 1.2f, 2, "", x, y, w);
		y += 32;
		c.trailHeight = slider(g, th, "trailHeight", "Height", c.trailHeight, 0.2f, 2.0f, 2, "", x, y, w);
		y += 32;
		c.trailOpacity = slider(g, th, "trailOpacity", "Opacity", c.trailOpacity, 0.1f, 1f, 2, "", x, y, w);
		y += 32;
		c.trailOthers = saveIfChanged(c.trailOthers, toggle(g, th, "trailOthers", "Show on other players", c.trailOthers, x, y, w));
		y += 26;
		return y;
	}

	private int tabJump(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w) {
		c.jumpCircles = saveIfChanged(c.jumpCircles, toggle(g, th, "jump", "Enable jump circles", c.jumpCircles, x, y, w));
		y += 26;
		c.jumpStyle = mode(g, th, "jumpStyle", "Style", FxManager.JUMP_STYLES, c.jumpStyle, x, y, w);
		y += 26;
		c.jumpRadius = slider(g, th, "jumpRadius", "Radius", c.jumpRadius, 0.5f, 3f, 1, "", x, y, w);
		y += 32;
		c.jumpDuration = slider(g, th, "jumpDur", "Duration", c.jumpDuration, 0.3f, 2.5f, 1, "s", x, y, w);
		y += 32;
		c.jumpWall = slider(g, th, "jumpWall", "Glow wall height", c.jumpWall, 0f, 1.5f, 2, "", x, y, w);
		y += 32;
		c.landCircles = saveIfChanged(c.landCircles, toggle(g, th, "land", "Landing circles", c.landCircles, x, y, w));
		y += 26;
		c.jumpBurst = saveIfChanged(c.jumpBurst, toggle(g, th, "burst", "Spark burst", c.jumpBurst, x, y, w));
		y += 26;
		return y;
	}

	private int tabAmbience(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w) {
		c.ambience = saveIfChanged(c.ambience, toggle(g, th, "amb", "Enable ambience", c.ambience, x, y, w));
		y += 26;
		c.ambMode = mode(g, th, "ambMode", "Particles", FxManager.AMB_MODES, c.ambMode, x, y, w);
		y += 26;
		if (c.ambMode == 0) {
			g.text(this.font, "Auto uses: " + FxManager.AMB_MODES[th.defaultAmbience] + " (" + th.display + ")", x + 8, y, col(th.textDim), false);
			y += 14;
		}
		c.ambDensity = slider(g, th, "ambDensity", "Density", c.ambDensity, 10f, 600f, 0, "", x, y, w);
		y += 32;
		c.ambRange = slider(g, th, "ambRange", "Range", c.ambRange, 6f, 40f, 0, "m", x, y, w);
		y += 32;
		c.ambSize = slider(g, th, "ambSize", "Size", c.ambSize, 0.4f, 2.5f, 1, "x", x, y, w);
		y += 32;
		return y;
	}

	private int tabAura(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w) {
		y = section(g, th, "Aura (third person)", x, y);
		c.halo = saveIfChanged(c.halo, toggle(g, th, "halo", "Halo", c.halo, x, y, w));
		y += 26;
		c.orbit = saveIfChanged(c.orbit, toggle(g, th, "orbit", "Orbiting orbs", c.orbit, x, y, w));
		y += 26;
		c.orbitCount = Math.round(slider(g, th, "orbitCount", "Orb count", c.orbitCount, 1, 8, 0, "", x, y, w));
		y += 32;
		y = section(g, th, "Combat", x, y);
		c.hitFx = saveIfChanged(c.hitFx, toggle(g, th, "hit", "Hit effects", c.hitFx, x, y, w));
		y += 26;
		c.hitIntensity = slider(g, th, "hitInt", "Hit intensity", c.hitIntensity, 0.3f, 3f, 1, "x", x, y, w);
		y += 32;
		return y;
	}

	private int tabHud(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w) {
		c.hudWatermark = saveIfChanged(c.hudWatermark, toggle(g, th, "wm", "Watermark", c.hudWatermark, x, y, w));
		y += 26;
		c.hudList = saveIfChanged(c.hudList, toggle(g, th, "list", "Feature list", c.hudList, x, y, w));
		y += 26;
		y += 6;
		g.text(this.font, "Open this menu: Right Shift (rebind in Controls)", x + 8, y, col(th.textDim), false);
		y += 12;
		g.text(this.font, "Settings save to config/glowvibes.json", x + 8, y, col(th.textDim), false);
		y += 14;
		return y;
	}

	private static boolean saveIfChanged(boolean oldV, boolean newV) {
		if (oldV != newV) {
			// saved on close too; this keeps toggles persistent even after a crash
			GlowConfig.save();
		}
		return newV;
	}

	// ------------------------------------------------------------------ widgets

	private int section(GuiGraphicsExtractor g, Theme th, String label, int x, int y) {
		g.text(this.font, label.toUpperCase(), x + 2, y + 2, col(ColorUtil.withAlpha(th.accent1, 0.9f)), false);
		return y + 14;
	}

	private void rowBg(GuiGraphicsExtractor g, Theme th, String id, int x, int y, int w, int h) {
		Anim ha = anim(id + "#h", 14f);
		ha.target = hover(x, y, w, h) ? 1f : 0f;
		ha.update(dt);
		Draw.round(g, x, y, w, h, 6, col(ColorUtil.withAlpha(th.panel2, 0.55f + 0.4f * ha.value)));
		if (ha.value > 0.01f) {
			Draw.roundF(g, x, y + 4, 2, h - 8, 1, col(ColorUtil.withAlpha(th.accent1, ha.value)));
		}
	}

	private boolean toggle(GuiGraphicsExtractor g, Theme th, String id, String label, boolean value, int x, int y, int w) {
		int h = 22;
		rowBg(g, th, id, x, y, w, h);
		g.text(this.font, label, x + 9, y + 7, col(th.text), false);
		if (clicked(x, y, w, h)) {
			value = !value;
		}
		Anim ta = anim(id, 13f).prime(value ? 1f : 0f);
		ta.target = value ? 1f : 0f;
		ta.update(dt);
		int sw = 24, sh = 12;
		int sx = x + w - sw - 8, sy = y + (h - sh) / 2;
		int bg = ColorUtil.lerp(ColorUtil.brighten(th.panel2, 0.08f), th.gradient(ColorUtil.seconds() * 0.2f), ta.value);
		if (ta.value > 0.01f) {
			Draw.round(g, sx - 2, sy - 2, sw + 4, sh + 4, 8, col(ColorUtil.withAlpha(th.accent1, 0.22f * ta.value)));
		}
		Draw.round(g, sx, sy, sw, sh, 6, col(bg));
		float kx = sx + 2 + (sw - 12) * Anim.easeInOutSine(ta.value);
		Draw.roundF(g, kx, sy + 2, 8, 8, 4, col(ColorUtil.lerp(th.textDim, 0xFFFFFFFF, ta.value)));
		return value;
	}

	private float slider(GuiGraphicsExtractor g, Theme th, String id, String label, float value, float min, float max, int decimals, String suffix, int x, int y, int w) {
		int h = 28;
		rowBg(g, th, id, x, y, w, h);
		g.text(this.font, label, x + 9, y + 5, col(th.text), false);
		int tx = x + 9, tw = w - 18, ty = y + 18;
		if (clicked(x, y, w, h)) {
			dragging = id;
		}
		if (id.equals(dragging) && mouseDown) {
			float f = ColorUtil.clamp01((lmx - tx) / tw);
			value = min + f * (max - min);
			float pow = (float) Math.pow(10, decimals);
			value = Math.round(value * pow) / pow;
		}
		float frac = ColorUtil.clamp01((value - min) / (max - min));
		Anim fa = anim(id + "#f", 18f).prime(frac);
		fa.target = frac;
		fa.update(dt);
		String vs = decimals == 0 ? String.valueOf(Math.round(value)) : String.format(java.util.Locale.ROOT, "%." + decimals + "f", value);
		vs += suffix;
		g.text(this.font, vs, x + w - 9 - this.font.width(vs), y + 5, col(th.accent1), false);
		Draw.round(g, tx, ty, tw, 4, 2, col(ColorUtil.brighten(th.panel2, 0.1f)));
		int fw = Math.max(0, Math.round(tw * fa.value));
		if (fw > 0) {
			Draw.hGradient(g, tx, ty, fw, 4, col(th.gradient(0f)), col(th.gradient(0.45f)));
		}
		Anim ka = anim(id + "#k", 14f);
		ka.target = (hover(x, y, w, h) || id.equals(dragging)) ? 1f : 0f;
		ka.update(dt);
		int ks = 6 + Math.round(2 * ka.value);
		float kx = tx + tw * fa.value - ks / 2f;
		if (ka.value > 0.01f) {
			Draw.roundF(g, kx - 3, ty + 2 - ks / 2f - 3, ks + 6, ks + 6, (ks + 6) / 2, col(ColorUtil.withAlpha(th.accent1, 0.25f * ka.value)));
		}
		Draw.roundF(g, kx, ty + 2 - ks / 2f, ks, ks, ks / 2, col(0xFFFFFFFF));
		return value;
	}

	private int mode(GuiGraphicsExtractor g, Theme th, String id, String label, String[] options, int value, int x, int y, int w) {
		int h = 22;
		rowBg(g, th, id, x, y, w, h);
		g.text(this.font, label, x + 9, y + 7, col(th.text), false);
		int pw = 92;
		int pxl = x + w - pw - 6;
		Anim sw = anim(id + "#s", 14f);
		if (clicked(pxl, y, pw / 3, h)) {
			value = Math.floorMod(value - 1, options.length);
			sw.value = -1f;
			GlowConfig.save();
		} else if (clicked(x, y, w, h)) {
			value = Math.floorMod(value + 1, options.length);
			sw.value = 1f;
			GlowConfig.save();
		}
		sw.target = 0f;
		sw.update(dt);
		Draw.round(g, pxl, y + 4, pw, h - 8, 5, col(ColorUtil.brighten(th.panel2, 0.08f)));
		g.text(this.font, "‹", pxl + 5, y + 7, col(th.accent1), false);
		g.text(this.font, "›", pxl + pw - 9, y + 7, col(th.accent1), false);
		String opt = options[value];
		int ow = this.font.width(opt);
		float off = sw.value * 10f;
		g.enableScissor(pxl + 12, y + 4, pxl + pw - 12, y + h - 4);
		g.pose().pushMatrix();
		g.pose().translate(off, 0f);
		g.text(this.font, opt, pxl + (pw - ow) / 2, y + 7, col(ColorUtil.withAlpha(th.text, 1f - Math.abs(sw.value) * 0.7f)), false);
		g.pose().popMatrix();
		g.disableScissor();
		return value;
	}
}
