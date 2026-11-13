package com.glowvibes.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
 * Immediate-mode, fully animated settings screen.
 * Layout: grouped sidebar, header with title/description/reset/close, a live preview card,
 * grouped setting cards, and a hint bar. Everything eases: open/close, staggered row entry,
 * tab indicator, hovers, toggles, segmented controls, sliders, scroll, click ripples.
 */
public final class GlowScreen extends Screen {
	private static final String[] TABS = {"Themes", "Trails", "Jump Circles", "Ambience", "Aura & Hits", "PvP HUD", "Screen & Hand", "HUD"};
	private static final String[] ICONS = {"✿", "≈", "◎", "✦", "✸", "⚔", "⚡", "▣"};
	private static final String[] DESCS = {
			"Pick a look. Every effect follows it.",
			"Glowing paths that follow you.",
			"Rings when you jump and land.",
			"Particles floating around you.",
			"Halo, orbiting orbs and hit sparks.",
			"Target glow, hit marker, combo, reach.",
			"Color the whole world, charge your hand.",
			"Watermark and feature list."
	};
	private static final int[] GROUP_START = {0, 1, 6};
	private static final String[] GROUP_NAMES = {"STYLE", "WORLD", "SCREEN"};
	private static final String[] HAND_MODES = {"Lightning", "Aura", "Both"};
	private static final String VERSION = "v2.4";

	private static final int ROW_T = 26; // toggle / mode row incl. gap
	private static final int ROW_S = 32; // slider row incl. gap
	private static final int CARD_HEAD = 20;
	private static final int CARD_GAP = 8;

	private static int lastTab = 0;

	private final Anim open = new Anim(0f, 9f);
	private final Anim tabIndicator = new Anim(14f);
	private final Anim hintAnim = new Anim(10f);
	private final Map<String, Anim> anims = new HashMap<>();
	private final List<Petal> petals = new ArrayList<>();
	private final List<float[]> ripples = new ArrayList<>();
	private final Random rnd = new Random();

	private int tab = lastTab;
	private boolean closing;
	private boolean finished;
	private long lastNanos;
	private float dt;
	private float now;
	private float openedAt = -1f;
	private float tabEnterAt;
	private float ga = 1f;
	private float savedGa = 1f;
	private int rowIndex;

	private float scroll;
	private float scrollTarget;
	private boolean click;
	private boolean mouseDown;
	private String dragging;
	private float lmx, lmy;
	private int clipTop = -100000, clipBottom = 100000;
	private String hoverHint;
	private String shownHint = "";

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
		// intentionally empty: custom animated backdrop instead of the vanilla blur
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
		scrollTarget -= (float) vertical * 30f;
		return true;
	}

	// ------------------------------------------------------------------ helpers

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

	/** Staggered entry: each row slides in and fades a little after the previous one. */
	private void beginRow(GuiGraphicsExtractor g) {
		float e = Anim.easeOutCubic(ColorUtil.clamp01((now - tabEnterAt - rowIndex * 0.035f) / 0.3f));
		rowIndex++;
		g.pose().pushMatrix();
		g.pose().translate((1f - e) * 18f, 0f);
		savedGa = ga;
		ga *= e;
	}

	private void endRow(GuiGraphicsExtractor g) {
		ga = savedGa;
		g.pose().popMatrix();
	}

	private void switchTab(int i) {
		if (i == tab) {
			return;
		}
		tab = i;
		lastTab = i;
		scrollTarget = 0;
		scroll = 0;
		tabEnterAt = now;
	}

	// ------------------------------------------------------------------ frame

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		long nanos = System.nanoTime();
		dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (nanos - lastNanos) / 1.0e9f);
		lastNanos = nanos;
		now = ColorUtil.seconds();
		if (openedAt < 0) {
			openedAt = now;
			tabEnterAt = now + 0.08f;
		}

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

		g.fillGradient(0, 0, this.width, this.height, ColorUtil.withAlpha(th.bg, 0.4f * t), ColorUtil.withAlpha(th.bg, 0.82f * t));
		drawPetals(g, th, t, mouseX, mouseY);

		int W = Math.min(540, this.width - 16);
		int H = Math.min(320, this.height - 16);
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
		hoverHint = null;

		if (click && !closing) {
			ripples.add(new float[] {lmx, lmy, now});
		}

		drawPanel(g, c, th, px, py, W, H);
		drawRipples(g, th);

		g.pose().popMatrix();
		click = false;
	}

	// ------------------------------------------------------------------ backdrop

	private void drawPetals(GuiGraphicsExtractor g, Theme th, float t, int mouseX, int mouseY) {
		boolean sakura = th == Theme.SAKURA;
		int want = sakura ? 50 : 38;
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
		// gentle parallax towards the mouse
		float parX = (mouseX - this.width / 2f) * -0.015f, parY = (mouseY - this.height / 2f) * -0.015f;
		for (Petal p : petals) {
			p.y += p.vy * dt;
			p.x += (p.vx * (sakura ? 1f : 0.2f) + (float) Math.sin(now * 1.4f + p.phase) * 10f) * dt;
			p.rot += p.vr * dt;
			if (p.y > this.height + 12 || p.y < -14 || p.x > this.width + 12) {
				p.x = rnd.nextFloat() * this.width - 20;
				p.y = sakura ? -10 : this.height + 10;
			}
			float depth = 0.5f + p.size / 5f;
			float dx = p.x + parX * depth, dy = p.y + parY * depth;
			int color = th.particle(p.colorIndex);
			if (sakura) {
				g.pose().pushMatrix();
				g.pose().rotateAbout(p.rot, dx, dy);
				int w = Math.round(p.size * 2), h = Math.max(2, Math.round(p.size));
				Draw.round(g, Math.round(dx) - w / 2, Math.round(dy) - h / 2, w, h, h / 2, ColorUtil.withAlpha(color, 0.75f * t));
				g.pose().popMatrix();
			} else {
				float tw = 0.5f + 0.5f * (float) Math.sin(now * 2f + p.phase);
				Previews.glowDot(g, dx, dy, Math.max(1.2f, p.size * 0.6f), color, 0.8f * t * tw);
			}
		}
	}

	private void drawRipples(GuiGraphicsExtractor g, Theme th) {
		Iterator<float[]> it = ripples.iterator();
		while (it.hasNext()) {
			float[] r = it.next();
			float age = now - r[2];
			if (age > 0.45f) {
				it.remove();
				continue;
			}
			float p = age / 0.45f;
			float rad = 3 + Anim.easeOutCubic(p) * 26;
			int a = Math.round(rad * 2);
			Draw.roundF(g, r[0] - rad, r[1] - rad, a, a, a / 2, ColorUtil.withAlpha(th.accent1, (1f - p) * 0.22f * ga));
		}
	}

	// ------------------------------------------------------------------ panel

	private int tabY(int index, int top) {
		int y = top;
		for (int i = 0; i <= index; i++) {
			for (int gIdx = 0; gIdx < GROUP_START.length; gIdx++) {
				if (GROUP_START[gIdx] == i) {
					y += 14;
				}
			}
			if (i < index) {
				y += 22;
			}
		}
		return y;
	}

	private void drawPanel(GuiGraphicsExtractor g, GlowConfig c, Theme th, int px, int py, int W, int H) {
		int side = 136;

		// drop shadow, outer glow, body
		Draw.glow(g, px, py + 6, W, H, 12, 0xFF000000, 5, 0.6f * ga);
		Draw.glow(g, px, py, W, H, 12, th.gradient(now * 0.15f), 4, 0.5f * ga);
		Draw.round(g, px, py, W, H, 12, col(ColorUtil.withAlpha(th.panel, 0.97f)));
		g.fillGradient(px + 12, py + 1, px + W - 12, py + 40, col(ColorUtil.withAlpha(0xFFFFFFFF, 0.035f)), col(ColorUtil.withAlpha(0xFFFFFFFF, 0f)));
		flowingBorder(g, th, px, py, W, H, 12);

		// cursor spotlight
		if (hover(px, py, W, H)) {
			for (int i = 3; i >= 1; i--) {
				int r = 22 * i;
				Draw.roundF(g, lmx - r, lmy - r, r * 2, r * 2, r, col(ColorUtil.withAlpha(th.accent1, 0.018f)));
			}
		}

		// sidebar
		Draw.round(g, px, py, side, H, 12, col(ColorUtil.withAlpha(th.bg, 0.92f)));
		g.fill(px + side - 12, py, px + side, py + H, col(ColorUtil.withAlpha(th.bg, 0.92f)));
		g.fillGradient(px + side, py + 10, px + side + 1, py + H - 10, col(ColorUtil.withAlpha(th.accent1, 0.0f)), col(ColorUtil.withAlpha(th.accent1, 0.35f)));

		// logo
		float iconPulse = 0.5f + 0.5f * (float) Math.sin(now * 2.2f);
		Draw.round(g, px + 10, py + 11, 20, 20, 6, col(ColorUtil.withAlpha(th.gradient(now * 0.3f), 0.18f + 0.1f * iconPulse)));
		g.text(this.font, ICONS[0], px + 16, py + 17, col(th.gradient(now * 0.4f)), true);
		Draw.gradientText(g, this.font, "GlowVibes", px + 36, py + 12, th, now * 0.35f, ga, true);
		g.text(this.font, "visual suite " + VERSION, px + 36, py + 23, col(th.textDim), false);

		// tabs
		int tabsTop = py + 40;
		int tabH = 20;
		tabIndicator.prime(tabY(tab, tabsTop));
		tabIndicator.target = tabY(tab, tabsTop);
		tabIndicator.update(dt);
		float sinceOpen = now - openedAt;

		for (int gIdx = 0; gIdx < GROUP_START.length; gIdx++) {
			int gy = tabY(GROUP_START[gIdx], tabsTop) - 12;
			float e = Anim.easeOutCubic(ColorUtil.clamp01((sinceOpen - GROUP_START[gIdx] * 0.04f) / 0.35f));
			g.text(this.font, GROUP_NAMES[gIdx], px + 13 - Math.round((1 - e) * 10), gy, col(ColorUtil.withAlpha(th.textDim, 0.55f * e)), false);
		}

		float hy = tabIndicator.value;
		Draw.roundF(g, px + 7, hy, side - 14, tabH, 7, col(ColorUtil.withAlpha(th.accent1, 0.14f)));
		Draw.roundF(g, px + 7, hy + 5, 2, tabH - 10, 1, col(th.gradient(now * 0.4f)));

		for (int i = 0; i < TABS.length; i++) {
			int ty = tabY(i, tabsTop);
			float e = Anim.easeOutCubic(ColorUtil.clamp01((sinceOpen - 0.05f - i * 0.04f) / 0.35f));
			boolean hov = hover(px + 7, ty, side - 14, tabH);
			Anim ha = anim("tab" + i, 12f);
			ha.target = hov ? 1f : 0f;
			ha.update(dt);
			if (ha.value > 0.01f && i != tab) {
				Draw.round(g, px + 7, ty, side - 14, tabH, 7, col(ColorUtil.withAlpha(th.panel2, 0.6f * ha.value)));
			}
			Anim sa = anim("tabSel" + i, 12f).prime(i == tab ? 1f : 0f);
			sa.target = i == tab ? 1f : 0f;
			sa.update(dt);
			float lit = Math.max(ha.value * 0.6f, sa.value);
			float shift = ha.value * 2f + (1 - e) * -12f;
			float baseGa = ga;
			ga *= e;
			g.pose().pushMatrix();
			g.pose().translate(shift, 0f);
			// icon badge
			int badge = ColorUtil.lerp(ColorUtil.withAlpha(th.panel2, 0.9f), th.gradient(i * 0.12f + now * 0.2f), sa.value);
			Draw.round(g, px + 12, ty + 3, 15, 15, 5, col(badge));
			int iconCol = ColorUtil.lerp(th.textDim, 0xFFFFFFFF, Math.max(sa.value, ha.value * 0.5f));
			int iw = this.font.width(ICONS[i]);
			g.text(this.font, ICONS[i], px + 12 + (15 - iw) / 2 + 1, ty + 7, col(iconCol), false);
			g.text(this.font, TABS[i], px + 33, ty + 7, col(ColorUtil.lerp(th.textDim, th.text, lit)), false);
			g.pose().popMatrix();
			ga = baseGa;
			if (clicked(px + 7, ty, side - 14, tabH)) {
				switchTab(i);
			}
		}

		// sidebar footer chip
		int chipY = py + H - 24;
		Draw.round(g, px + 9, chipY, side - 18, 16, 5, col(ColorUtil.withAlpha(th.panel2, 0.7f)));
		Draw.round(g, px + 14, chipY + 5, 6, 6, 3, col(th.gradient(now * 0.3f)));
		g.text(this.font, th.display + " theme", px + 25, chipY + 4, col(th.textDim), false);

		// header
		int x0 = px + side + 14;
		int w = W - side - 28;
		g.pose().pushMatrix();
		g.pose().translate(x0, py + 12);
		g.pose().scale(1.5f, 1.5f);
		g.text(this.font, TABS[tab], 0, 0, col(th.text), true);
		g.pose().popMatrix();
		g.text(this.font, DESCS[tab], x0, py + 28, col(th.textDim), false);
		int titleW = Math.round(this.font.width(TABS[tab]) * 1.5f);
		float uline = Anim.easeOutCubic(ColorUtil.clamp01((now - tabEnterAt) / 0.4f));
		Draw.hGradient(g, x0, py + 38, Math.max(4, Math.round(titleW * uline)), 2, col(th.gradient(now * 0.3f)), col(ColorUtil.withAlpha(th.gradient(now * 0.3f + 0.4f), 0.2f)));

		headerButtons(g, c, th, px + W - 14, py + 12);

		// content
		int top = py + 46;
		int bottom = py + H - 24;
		clipTop = top;
		clipBottom = bottom;
		scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-16f * dt));
		rowIndex = 0;

		g.enableScissor(x0 - 4, top, x0 + w + 8, bottom);
		int y = top + 4 - Math.round(scroll);
		int startY = y;
		y = switch (tab) {
			case 0 -> tabThemes(g, c, th, x0, y, w);
			case 1 -> tabTrails(g, c, th, x0, y, w);
			case 2 -> tabJump(g, c, th, x0, y, w);
			case 3 -> tabAmbience(g, c, th, x0, y, w);
			case 4 -> tabAura(g, c, th, x0, y, w);
			case 5 -> tabPvp(g, c, th, x0, y, w);
			case 6 -> tabScreen(g, c, th, x0, y, w);
			default -> tabHud(g, c, th, x0, y, w);
		};
		g.disableScissor();
		// fade the scroll edges so content melts into the panel
		g.fillGradient(x0 - 4, top, x0 + w + 8, top + 6, col(ColorUtil.withAlpha(th.panel, 0.97f)), col(ColorUtil.withAlpha(th.panel, 0f)));
		g.fillGradient(x0 - 4, bottom - 8, x0 + w + 8, bottom, col(ColorUtil.withAlpha(th.panel, 0f)), col(ColorUtil.withAlpha(th.panel, 0.97f)));

		int contentH = y - startY;
		int viewH = bottom - top;
		float maxScroll = Math.max(0, contentH - viewH + 6);
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		if (maxScroll > 0) {
			int barH = Math.max(18, Math.round(viewH * (viewH / (float) contentH)));
			float barY = top + (viewH - barH) * (scroll / maxScroll);
			Draw.roundF(g, x0 + w + 5, barY, 2, barH, 1, col(ColorUtil.withAlpha(th.accent1, 0.55f)));
		}
		clipTop = -100000;
		clipBottom = 100000;

		// hint bar
		hintAnim.target = hoverHint != null ? 1f : 0f;
		hintAnim.update(dt);
		if (hoverHint != null) {
			shownHint = hoverHint;
		}
		int hbY = py + H - 18;
		g.fill(x0, hbY - 4, x0 + w, hbY - 3, col(ColorUtil.withAlpha(th.panel2, 0.8f)));
		if (hintAnim.value > 0.02f && !shownHint.isEmpty()) {
			g.text(this.font, "ⓘ " + shownHint, x0 + Math.round((1 - hintAnim.value) * 6), hbY + 1, col(ColorUtil.withAlpha(th.textDim, hintAnim.value)), false);
		} else {
			g.text(this.font, "Hover a setting for details", x0, hbY + 1, col(ColorUtil.withAlpha(th.textDim, 0.45f)), false);
		}
		String keys = "RShift • Esc";
		g.text(this.font, keys, x0 + w - this.font.width(keys), hbY + 1, col(ColorUtil.withAlpha(th.textDim, 0.5f)), false);
	}

	private void flowingBorder(GuiGraphicsExtractor g, Theme th, int x, int y, int w, int h, int r) {
		float f = now * 0.25f;
		int a1 = col(ColorUtil.withAlpha(th.gradient(f), 0.55f));
		int a2 = col(ColorUtil.withAlpha(th.gradient(f + 0.25f), 0.55f));
		int a3 = col(ColorUtil.withAlpha(th.gradient(f + 0.5f), 0.55f));
		int a4 = col(ColorUtil.withAlpha(th.gradient(f + 0.75f), 0.55f));
		Draw.hGradient(g, x + r, y, w - 2 * r, 1, a1, a2);
		g.fillGradient(x + w - 1, y + r, x + w, y + h - r, a2, a3);
		Draw.hGradient(g, x + r, y + h - 1, w - 2 * r, 1, a4, a3);
		g.fillGradient(x, y + r, x + 1, y + h - r, a1, a4);
	}

	private void headerButtons(GuiGraphicsExtractor g, GlowConfig c, Theme th, int right, int y) {
		// close
		int bx = right - 18;
		Anim hc = anim("btnClose", 14f);
		hc.target = hover(bx, y, 18, 18) ? 1f : 0f;
		hc.update(dt);
		Draw.round(g, bx, y, 18, 18, 6, col(ColorUtil.lerp(ColorUtil.withAlpha(th.panel2, 0.8f), ColorUtil.withAlpha(0xFFFF5A6E, 0.85f), hc.value)));
		g.pose().pushMatrix();
		g.pose().rotateAbout(hc.value * (float) (Math.PI / 2), bx + 9f, y + 9f);
		int xw = this.font.width("×");
		g.text(this.font, "×", bx + (18 - xw) / 2 + 1, y + 5, col(ColorUtil.lerp(th.textDim, 0xFFFFFFFF, hc.value)), false);
		g.pose().popMatrix();
		if (hover(bx, y, 18, 18)) {
			hoverHint = "Close and save";
		}
		if (clicked(bx, y, 18, 18)) {
			onClose();
		}
		// reset
		String label = "↺ Reset";
		int rw = this.font.width(label) + 14;
		int rx = bx - 6 - rw;
		Anim hr = anim("btnReset", 14f);
		hr.target = hover(rx, y, rw, 18) ? 1f : 0f;
		hr.update(dt);
		Draw.round(g, rx, y, rw, 18, 6, col(ColorUtil.lerp(ColorUtil.withAlpha(th.panel2, 0.8f), ColorUtil.withAlpha(th.accent1, 0.3f), hr.value)));
		g.text(this.font, label, rx + 7, y + 5, col(ColorUtil.lerp(th.textDim, th.text, hr.value)), false);
		if (hover(rx, y, rw, 18)) {
			hoverHint = "Reset this tab to defaults";
		}
		if (clicked(rx, y, rw, 18)) {
			resetTab(c);
		}
	}

	private void resetTab(GlowConfig c) {
		GlowConfig d = new GlowConfig();
		switch (tab) {
			case 0 -> {
				c.theme = d.theme;
				c.chroma = d.chroma;
				c.chromaSpeed = d.chromaSpeed;
				c.guiAnimSpeed = d.guiAnimSpeed;
				c.glowQuality = d.glowQuality;
			}
			case 1 -> {
				c.trails = d.trails;
				c.trailMode = d.trailMode;
				c.trailLife = d.trailLife;
				c.trailWidth = d.trailWidth;
				c.trailHeight = d.trailHeight;
				c.trailOpacity = d.trailOpacity;
				c.trailOthers = d.trailOthers;
			}
			case 2 -> {
				c.jumpCircles = d.jumpCircles;
				c.jumpStyle = d.jumpStyle;
				c.jumpRadius = d.jumpRadius;
				c.jumpDuration = d.jumpDuration;
				c.jumpWall = d.jumpWall;
				c.landCircles = d.landCircles;
				c.jumpBurst = d.jumpBurst;
			}
			case 3 -> {
				c.ambience = d.ambience;
				c.ambMode = d.ambMode;
				c.ambDensity = d.ambDensity;
				c.ambRange = d.ambRange;
				c.ambSize = d.ambSize;
			}
			case 4 -> {
				c.halo = d.halo;
				c.orbit = d.orbit;
				c.orbitCount = d.orbitCount;
				c.hitFx = d.hitFx;
				c.hitIntensity = d.hitIntensity;
			}
			case 5 -> {
				c.pvpTarget = d.pvpTarget;
				c.pvpTargetThemeColor = d.pvpTargetThemeColor;
				c.pvpCrosshair = d.pvpCrosshair;
				c.hitMarker = d.hitMarker;
				c.comboCounter = d.comboCounter;
				c.reachDisplay = d.reachDisplay;
			}
			case 6 -> {
				c.screenTint = d.screenTint;
				c.tintStrength = d.tintStrength;
				c.vignette = d.vignette;
				c.worldTint = d.worldTint;
				c.worldTintStrength = d.worldTintStrength;
				c.itemFx = d.itemFx;
				c.handFx = d.handFx;
				c.handFxIntensity = d.handFxIntensity;
			}
			default -> {
				c.hudWatermark = d.hudWatermark;
				c.hudList = d.hudList;
			}
		}
		GlowConfig.save();
		tabEnterAt = now; // replay the entry animation as feedback
	}

	// ------------------------------------------------------------------ cards

	/** Draws a card background with a title; returns the y of its first row. */
	private int card(GuiGraphicsExtractor g, Theme th, String title, int x, int y, int w, int innerH) {
		beginRow(g);
		int h = CARD_HEAD + innerH + 2;
		Draw.round(g, x, y, w, h, 9, col(ColorUtil.withAlpha(th.panel2, 0.5f)));
		g.fill(x + 9, y, x + w - 9, y + 1, col(ColorUtil.withAlpha(0xFFFFFFFF, 0.06f)));
		Draw.round(g, x + 9, y + 8, 3, 3, 1, col(th.gradient(now * 0.3f)));
		g.text(this.font, title.toUpperCase(), x + 16, y + 6, col(ColorUtil.withAlpha(th.accent1, 0.95f)), false);
		endRow(g);
		return y + CARD_HEAD;
	}

	private int afterCard(int cardY, int innerH) {
		return cardY + CARD_HEAD + innerH + 2 + CARD_GAP;
	}

	private int preview(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w) {
		int ph = 74;
		int rowY = card(g, th, "Live preview", x, y, w, ph);
		beginRow(g);
		Previews.draw(g, this.font, tab, c, th, x + 6, rowY, w - 12, ph - 6, ga);
		endRow(g);
		return afterCard(y, ph);
	}

	// ------------------------------------------------------------------ tabs

	private int tabThemes(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w) {
		Theme[] all = Theme.values();
		int cols = 3, gap = 6;
		int ch = 54;
		int rows = (all.length + cols - 1) / cols;
		int gridH = rows * (ch + gap);
		int rowY = card(g, th, "Theme", x, y, w, gridH);
		int gx = x + 6, gw = w - 12;
		int cw = (gw - gap * (cols - 1)) / cols;
		for (int i = 0; i < all.length; i++) {
			Theme t = all[i];
			int cx = gx + (i % cols) * (cw + gap);
			int cy = rowY + (i / cols) * (ch + gap);
			beginRow(g);
			boolean sel = t == th;
			Anim sa = anim("theme" + i, 10f).prime(sel ? 1 : 0);
			sa.target = sel ? 1f : 0f;
			sa.update(dt);
			Anim ha = anim("themeH" + i, 12f);
			boolean hov = hover(cx, cy, cw, ch);
			ha.target = hov ? 1f : 0f;
			ha.update(dt);
			if (hov) {
				hoverHint = "Switch to the " + t.display + " theme";
			}
			g.pose().pushMatrix();
			g.pose().translate(0f, -ha.value * 2f);
			if (sa.value > 0.01f) {
				Draw.glow(g, cx, cy, cw, ch, 8, t.baseGradient(now * 0.3f), 3, 0.7f * sa.value * ga);
				Draw.round(g, cx - 1, cy - 1, cw + 2, ch + 2, 9, col(ColorUtil.withAlpha(t.baseGradient(now * 0.3f), sa.value)));
			}
			Draw.round(g, cx, cy, cw, ch, 8, col(t.panel));
			// mini scene: gradient sky band with drifting dots in the theme's colours
			g.enableScissor(cx + 5, cy + 5, cx + cw - 5, cy + 27);
			g.fillGradient(cx + 5, cy + 5, cx + cw - 5, cy + 27, col(t.bg), col(ColorUtil.lerp(t.bg, t.accent2, 0.35f)));
			for (int k = 0; k < 7; k++) {
				float fx = (k * 0.618f + now * (0.04f + k * 0.006f)) % 1f;
				float fy = (k * 0.414f + 0.2f) % 1f;
				Previews.glowDot(g, cx + 5 + fx * (cw - 10), cy + 7 + fy * 18, 2f, t.particle(k), ga);
			}
			g.disableScissor();
			Draw.hGradient(g, cx + 5, cy + 27, cw - 10, 2, col(t.accent1), col(t.accent2));
			g.text(this.font, t.display, cx + 7, cy + 36, col(t.text), false);
			if (sa.value > 0.02f) {
				int ckx = cx + cw - 16, cky = cy + 34;
				Draw.round(g, ckx, cky, 11, 11, 5, col(ColorUtil.withAlpha(t.accent1, sa.value)));
				g.text(this.font, "✓", ckx + 2, cky + 2, col(ColorUtil.withAlpha(t.bg, sa.value)), false);
			}
			g.pose().popMatrix();
			endRow(g);
			if (clicked(cx, cy, cw, ch)) {
				c.theme = t.name();
				GlowConfig.save();
			}
		}
		y = afterCard(y, gridH);

		int innerH = ROW_T + ROW_S * 3;
		int ry = card(g, th, "Color & quality", x, y, w, innerH);
		boolean chroma = toggle(g, th, "chroma", "Chroma mode", "Rainbow colors that shift over time", c.chroma, x + 4, ry, w - 8);
		if (chroma != c.chroma) {
			c.chroma = chroma;
			GlowConfig.save();
		}
		ry += ROW_T;
		c.chromaSpeed = slider(g, th, "chromaSpeed", "Chroma speed", "How fast the rainbow cycles", c.chromaSpeed, 0.1f, 3f, 1, "x", x + 4, ry, w - 8);
		ry += ROW_S;
		c.guiAnimSpeed = slider(g, th, "guiSpeed", "Menu animation speed", "Speed of every animation in this menu", c.guiAnimSpeed, 0.5f, 2.5f, 1, "x", x + 4, ry, w - 8);
		ry += ROW_S;
		c.glowQuality = Math.round(slider(g, th, "quality", "Glow quality", "More glow layers and smoother rings. Lower if FPS drops", c.glowQuality, 1, 3, 0, "", x + 4, ry, w - 8));
		return afterCard(y, innerH);
	}

	private int tabTrails(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w) {
		y = preview(g, c, th, x, y, w);
		int innerH = ROW_T * 3 + ROW_S * 4;
		int ry = card(g, th, "Trail", x, y, w, innerH);
		c.trails = saveIfChanged(c.trails, toggle(g, th, "trails", "Enable trails", "Draws a glowing path behind you", c.trails, x + 4, ry, w - 8));
		ry += ROW_T;
		c.trailMode = segmented(g, th, "trailMode", "Style", "Ribbon band, double helix or glowing beads", FxManager.TRAIL_MODES, c.trailMode, x + 4, ry, w - 8);
		ry += ROW_T;
		c.trailLife = slider(g, th, "trailLife", "Length", "How long the trail stays visible", c.trailLife, 0.2f, 3f, 1, "s", x + 4, ry, w - 8);
		ry += ROW_S;
		c.trailWidth = slider(g, th, "trailWidth", "Width", "Ground glow width / helix radius", c.trailWidth, 0.1f, 1.2f, 2, "", x + 4, ry, w - 8);
		ry += ROW_S;
		c.trailHeight = slider(g, th, "trailHeight", "Height", "Height of the ribbon band", c.trailHeight, 0.2f, 2.0f, 2, "", x + 4, ry, w - 8);
		ry += ROW_S;
		c.trailOpacity = slider(g, th, "trailOpacity", "Opacity", "Overall trail transparency", c.trailOpacity, 0.1f, 1f, 2, "", x + 4, ry, w - 8);
		ry += ROW_S;
		c.trailOthers = saveIfChanged(c.trailOthers, toggle(g, th, "trailOthers", "Show on other players", "Visible only to you", c.trailOthers, x + 4, ry, w - 8));
		return afterCard(y, innerH);
	}

	private int tabJump(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w) {
		y = preview(g, c, th, x, y, w);
		int innerH = ROW_T * 2 + ROW_S * 3;
		int ry = card(g, th, "Circle", x, y, w, innerH);
		c.jumpCircles = saveIfChanged(c.jumpCircles, toggle(g, th, "jump", "Enable jump circles", "A glowing ring expands when you jump", c.jumpCircles, x + 4, ry, w - 8));
		ry += ROW_T;
		c.jumpStyle = segmented(g, th, "jumpStyle", "Style", "Solid, dashed, two rings, or pulsing", FxManager.JUMP_STYLES, c.jumpStyle, x + 4, ry, w - 8);
		ry += ROW_T;
		c.jumpRadius = slider(g, th, "jumpRadius", "Radius", "Final size of the ring in blocks", c.jumpRadius, 0.5f, 3f, 1, "", x + 4, ry, w - 8);
		ry += ROW_S;
		c.jumpDuration = slider(g, th, "jumpDur", "Duration", "How long one ring lives", c.jumpDuration, 0.3f, 2.5f, 1, "s", x + 4, ry, w - 8);
		ry += ROW_S;
		c.jumpWall = slider(g, th, "jumpWall", "Glow wall height", "Vertical light curtain rising from the ring", c.jumpWall, 0f, 1.5f, 2, "", x + 4, ry, w - 8);
		y = afterCard(y, innerH);

		int inner2 = ROW_T * 2;
		ry = card(g, th, "Extras", x, y, w, inner2);
		c.landCircles = saveIfChanged(c.landCircles, toggle(g, th, "land", "Landing circles", "Extra ring when landing from a big fall", c.landCircles, x + 4, ry, w - 8));
		ry += ROW_T;
		c.jumpBurst = saveIfChanged(c.jumpBurst, toggle(g, th, "burst", "Spark burst", "Sparks shoot out from the ring", c.jumpBurst, x + 4, ry, w - 8));
		return afterCard(y, inner2);
	}

	private int tabAmbience(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w) {
		y = preview(g, c, th, x, y, w);
		int innerH = ROW_T * 2 + ROW_S * 3;
		int ry = card(g, th, "Particles", x, y, w, innerH);
		c.ambience = saveIfChanged(c.ambience, toggle(g, th, "amb", "Enable ambience", "Particles drift through the world around you", c.ambience, x + 4, ry, w - 8));
		ry += ROW_T;
		String autoHint = "Auto uses " + FxManager.AMB_MODES[th.defaultAmbience] + " for the " + th.display + " theme";
		c.ambMode = mode(g, th, "ambMode", "Type", autoHint, FxManager.AMB_MODES, c.ambMode, x + 4, ry, w - 8);
		ry += ROW_T;
		c.ambDensity = slider(g, th, "ambDensity", "Density", "Number of particles around you", c.ambDensity, 10f, 600f, 0, "", x + 4, ry, w - 8);
		ry += ROW_S;
		c.ambRange = slider(g, th, "ambRange", "Range", "How far away particles spawn", c.ambRange, 6f, 40f, 0, "m", x + 4, ry, w - 8);
		ry += ROW_S;
		c.ambSize = slider(g, th, "ambSize", "Size", "Particle size multiplier", c.ambSize, 0.4f, 2.5f, 1, "x", x + 4, ry, w - 8);
		return afterCard(y, innerH);
	}

	private int tabAura(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w) {
		y = preview(g, c, th, x, y, w);
		int innerH = ROW_T * 2 + ROW_S;
		int ry = card(g, th, "Aura (third person)", x, y, w, innerH);
		c.halo = saveIfChanged(c.halo, toggle(g, th, "halo", "Halo", "Rotating glowing ring above your head", c.halo, x + 4, ry, w - 8));
		ry += ROW_T;
		c.orbit = saveIfChanged(c.orbit, toggle(g, th, "orbit", "Orbiting orbs", "Glowing orbs with tails circle you", c.orbit, x + 4, ry, w - 8));
		ry += ROW_T;
		c.orbitCount = Math.round(slider(g, th, "orbitCount", "Orb count", "How many orbs circle you", c.orbitCount, 1, 8, 0, "", x + 4, ry, w - 8));
		y = afterCard(y, innerH);

		int inner2 = ROW_T + ROW_S;
		ry = card(g, th, "Combat visuals", x, y, w, inner2);
		c.hitFx = saveIfChanged(c.hitFx, toggle(g, th, "hit", "Hit effects", "Sparks and a shockwave when you hit something", c.hitFx, x + 4, ry, w - 8));
		ry += ROW_T;
		c.hitIntensity = slider(g, th, "hitInt", "Intensity", "Amount and speed of hit sparks", c.hitIntensity, 0.3f, 3f, 1, "x", x + 4, ry, w - 8);
		return afterCard(y, inner2);
	}

	private int tabPvp(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w) {
		y = preview(g, c, th, x, y, w);
		int innerH = ROW_T * 3;
		int ry = card(g, th, "Target", x, y, w, innerH);
		c.pvpTarget = saveIfChanged(c.pvpTarget, toggle(g, th, "pvpTarget", "Glow target in reach", "The player under your crosshair glows when you can hit them", c.pvpTarget, x + 4, ry, w - 8));
		ry += ROW_T;
		c.pvpCrosshair = saveIfChanged(c.pvpCrosshair, toggle(g, th, "pvpCross", "Crosshair brackets", "Brackets snap onto your crosshair when a hit would land", c.pvpCrosshair, x + 4, ry, w - 8));
		ry += ROW_T;
		c.pvpTargetThemeColor = saveIfChanged(c.pvpTargetThemeColor, toggle(g, th, "pvpThemeCol", "Use theme color", "Off = red, on = your theme gradient", c.pvpTargetThemeColor, x + 4, ry, w - 8));
		y = afterCard(y, innerH);

		int inner2 = ROW_T * 3;
		ry = card(g, th, "Feedback", x, y, w, inner2);
		c.hitMarker = saveIfChanged(c.hitMarker, toggle(g, th, "hitMarker", "Hit marker", "A quick X flashes on every hit", c.hitMarker, x + 4, ry, w - 8));
		ry += ROW_T;
		c.comboCounter = saveIfChanged(c.comboCounter, toggle(g, th, "combo", "Combo counter", "Counts hits landed within 2.5 seconds", c.comboCounter, x + 4, ry, w - 8));
		ry += ROW_T;
		c.reachDisplay = saveIfChanged(c.reachDisplay, toggle(g, th, "reach", "Reach display", "Distance to your target and of your last hit", c.reachDisplay, x + 4, ry, w - 8));
		return afterCard(y, inner2);
	}

	private int tabScreen(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w) {
		y = preview(g, c, th, x, y, w);
		int innerH = ROW_T + ROW_S;
		int ry = card(g, th, "World color", x, y, w, innerH);
		c.worldTint = saveIfChanged(c.worldTint, toggle(g, th, "worldTint", "Tint sky, fog & light", "The whole world takes on the ambience color, no blur", c.worldTint, x + 4, ry, w - 8));
		ry += ROW_T;
		c.worldTintStrength = slider(g, th, "worldStr", "World strength", "How strongly sky, fog and daylight are colored", c.worldTintStrength, 0.05f, 1f, 2, "", x + 4, ry, w - 8);
		y = afterCard(y, innerH);

		int innerO = ROW_T * 2 + ROW_S;
		ry = card(g, th, "Screen overlay", x, y, w, innerO);
		c.screenTint = saveIfChanged(c.screenTint, toggle(g, th, "tint", "Color overlay", "A light, even color wash over your view", c.screenTint, x + 4, ry, w - 8));
		ry += ROW_T;
		c.tintStrength = slider(g, th, "tintStr", "Overlay strength", "Keep it low for a clean look", c.tintStrength, 0.02f, 0.6f, 2, "", x + 4, ry, w - 8);
		ry += ROW_S;
		c.vignette = saveIfChanged(c.vignette, toggle(g, th, "vig", "Colored edges", "Soft glow at the screen edges", c.vignette, x + 4, ry, w - 8));
		y = afterCard(y, innerO);

		int inner2 = ROW_T * 2 + ROW_S;
		ry = card(g, th, "Lightning (first person)", x, y, w, inner2);
		c.handFx = saveIfChanged(c.handFx, toggle(g, th, "hand", "On your hand", "3D lightning crackles around your fist and follows every swing", c.handFx, x + 4, ry, w - 8));
		ry += ROW_T;
		c.itemFx = saveIfChanged(c.itemFx, toggle(g, th, "itemFx", "On weapons & items", "3D lightning around whatever you hold", c.itemFx, x + 4, ry, w - 8));
		ry += ROW_T;
		c.handFxIntensity = slider(g, th, "handInt", "Intensity", "Brightness of the lightning", c.handFxIntensity, 0.2f, 2f, 1, "x", x + 4, ry, w - 8);
		return afterCard(y, inner2);
	}

	private int tabHud(GuiGraphicsExtractor g, GlowConfig c, Theme th, int x, int y, int w) {
		y = preview(g, c, th, x, y, w);
		int innerH = ROW_T * 2;
		int ry = card(g, th, "Elements", x, y, w, innerH);
		c.hudWatermark = saveIfChanged(c.hudWatermark, toggle(g, th, "wm", "Watermark", "Logo and FPS in the top left", c.hudWatermark, x + 4, ry, w - 8));
		ry += ROW_T;
		c.hudList = saveIfChanged(c.hudList, toggle(g, th, "list", "Feature list", "Active effects in the top right", c.hudList, x + 4, ry, w - 8));
		y = afterCard(y, innerH);

		int inner2 = 34;
		ry = card(g, th, "Info", x, y, w, inner2);
		beginRow(g);
		g.text(this.font, "Open this menu with Right Shift (rebind in Controls).", x + 12, ry + 2, col(th.textDim), false);
		g.text(this.font, "Settings save to config/glowvibes.json", x + 12, ry + 15, col(th.textDim), false);
		endRow(g);
		return afterCard(y, inner2);
	}

	private static boolean saveIfChanged(boolean oldV, boolean newV) {
		if (oldV != newV) {
			GlowConfig.save();
		}
		return newV;
	}

	// ------------------------------------------------------------------ widgets

	private boolean rowHover(GuiGraphicsExtractor g, Theme th, String id, String hint, int x, int y, int w, int h) {
		boolean hov = hover(x, y, w, h);
		Anim ha = anim(id + "#h", 14f);
		ha.target = hov ? 1f : 0f;
		ha.update(dt);
		if (ha.value > 0.01f) {
			Draw.round(g, x, y, w, h, 7, col(ColorUtil.withAlpha(th.panel2, 0.85f * ha.value)));
			Draw.roundF(g, x + 1, y + 5, 2, h - 10, 1, col(ColorUtil.withAlpha(th.gradient(now * 0.3f), ha.value)));
		}
		if (hov) {
			hoverHint = hint;
		}
		return hov;
	}

	private boolean toggle(GuiGraphicsExtractor g, Theme th, String id, String label, String hint, boolean value, int x, int y, int w) {
		beginRow(g);
		int h = 24;
		rowHover(g, th, id, hint, x, y, w, h);
		if (clicked(x, y, w, h)) {
			value = !value;
		}
		Anim ta = anim(id, 13f).prime(value ? 1f : 0f);
		ta.target = value ? 1f : 0f;
		ta.update(dt);
		g.text(this.font, label, x + 10, y + 8, col(ColorUtil.lerp(th.textDim, th.text, 0.55f + 0.45f * ta.value)), false);
		int sw = 26, sh = 14;
		int sx = x + w - sw - 8, sy = y + (h - sh) / 2;
		int on = th.gradient(now * 0.2f);
		if (ta.value > 0.01f) {
			Draw.round(g, sx - 3, sy - 3, sw + 6, sh + 6, 10, col(ColorUtil.withAlpha(on, 0.2f * ta.value)));
		}
		Draw.round(g, sx, sy, sw, sh, 7, col(ColorUtil.lerp(ColorUtil.brighten(th.panel2, 0.1f), on, ta.value)));
		float k = Anim.easeOutBack(ta.value);
		float kx = sx + 2 + (sw - 14) * Math.max(0f, Math.min(1.05f, k));
		Draw.roundF(g, kx, sy + 2, 10, 10, 5, col(ColorUtil.lerp(th.textDim, 0xFFFFFFFF, ta.value)));
		endRow(g);
		return value;
	}

	private float slider(GuiGraphicsExtractor g, Theme th, String id, String label, String hint, float value, float min, float max, int decimals, String suffix, int x, int y, int w) {
		beginRow(g);
		int h = 30;
		boolean hov = rowHover(g, th, id, hint, x, y, w, h);
		g.text(this.font, label, x + 10, y + 6, col(th.text), false);
		int tx = x + 10, tw = w - 20, ty = y + 20;
		if (clicked(x, y, w, h)) {
			dragging = id;
		}
		boolean drag = id.equals(dragging) && mouseDown;
		if (drag) {
			float f = ColorUtil.clamp01((lmx - tx) / tw);
			value = min + f * (max - min);
			float pow = (float) Math.pow(10, decimals);
			value = Math.round(value * pow) / pow;
		}
		float frac = ColorUtil.clamp01((value - min) / (max - min));
		Anim fa = anim(id + "#f", 18f).prime(frac);
		fa.target = frac;
		fa.update(dt);
		String vs = (decimals == 0 ? String.valueOf(Math.round(value)) : String.format(java.util.Locale.ROOT, "%." + decimals + "f", value)) + suffix;
		g.text(this.font, vs, x + w - 10 - this.font.width(vs), y + 6, col(th.accent1), false);
		Draw.round(g, tx, ty, tw, 4, 2, col(ColorUtil.brighten(th.panel2, 0.12f)));
		// tick marks
		for (int i = 1; i < 4; i++) {
			int mx = tx + tw * i / 4;
			g.fill(mx, ty + 1, mx + 1, ty + 3, col(ColorUtil.withAlpha(th.textDim, 0.25f)));
		}
		int fw = Math.max(0, Math.round(tw * fa.value));
		if (fw > 0) {
			Draw.hGradient(g, tx, ty, fw, 4, col(th.gradient(now * 0.15f)), col(th.gradient(now * 0.15f + 0.45f)));
		}
		Anim ka = anim(id + "#k", 14f);
		ka.target = (hov || drag) ? 1f : 0f;
		ka.update(dt);
		int ks = 8 + Math.round(2 * ka.value);
		float kx = tx + tw * fa.value - ks / 2f;
		if (ka.value > 0.01f) {
			Draw.roundF(g, kx - 3, ty + 2 - ks / 2f - 3, ks + 6, ks + 6, (ks + 6) / 2, col(ColorUtil.withAlpha(th.accent1, 0.25f * ka.value)));
		}
		Draw.roundF(g, kx, ty + 2 - ks / 2f, ks, ks, ks / 2, col(0xFFFFFFFF));
		// value bubble while dragging
		Anim ba = anim(id + "#b", 16f);
		ba.target = drag ? 1f : 0f;
		ba.update(dt);
		if (ba.value > 0.02f) {
			int bw = this.font.width(vs) + 8;
			float bx = kx + ks / 2f - bw / 2f;
			float by = ty - 16 - ba.value * 3;
			float rowGa = ga;
			ga *= ba.value;
			Draw.roundF(g, bx, by, bw, 12, 4, col(ColorUtil.withAlpha(th.accent1, 0.95f)));
			g.pose().pushMatrix();
			g.pose().translate(bx + 4, by + 2);
			g.text(this.font, vs, 0, 0, col(th.bg), false);
			g.pose().popMatrix();
			ga = rowGa;
		}
		endRow(g);
		return value;
	}

	/** Segmented control with a sliding highlight. Used for small option sets. */
	private int segmented(GuiGraphicsExtractor g, Theme th, String id, String label, String hint, String[] opts, int value, int x, int y, int w) {
		beginRow(g);
		int h = 24;
		rowHover(g, th, id, hint, x, y, w, h);
		g.text(this.font, label, x + 10, y + 8, col(th.text), false);
		int segW = 0;
		for (String o : opts) {
			segW = Math.max(segW, this.font.width(o) + 12);
		}
		int sw = segW * opts.length;
		int sx = x + w - sw - 6;
		Draw.round(g, sx, y + 3, sw, h - 6, 7, col(ColorUtil.withAlpha(th.bg, 0.7f)));
		Anim pa = anim(id + "#seg", 15f).prime(value);
		for (int i = 0; i < opts.length; i++) {
			if (clicked(sx + i * segW, y, segW, h)) {
				value = i;
				GlowConfig.save();
			}
		}
		pa.target = value;
		pa.update(dt);
		float hx = sx + pa.value * segW;
		Draw.roundF(g, hx + 1, y + 4, segW - 2, h - 8, 6, col(ColorUtil.withAlpha(th.gradient(now * 0.2f), 0.85f)));
		for (int i = 0; i < opts.length; i++) {
			float near = 1f - Math.min(1f, Math.abs(pa.value - i));
			int tc = ColorUtil.lerp(th.textDim, th.bg, near);
			int ow = this.font.width(opts[i]);
			g.text(this.font, opts[i], sx + i * segW + (segW - ow) / 2, y + 8, col(tc), false);
		}
		endRow(g);
		return value;
	}

	/** Arrow selector for larger option sets. */
	private int mode(GuiGraphicsExtractor g, Theme th, String id, String label, String hint, String[] options, int value, int x, int y, int w) {
		beginRow(g);
		int h = 24;
		rowHover(g, th, id, hint, x, y, w, h);
		g.text(this.font, label, x + 10, y + 8, col(th.text), false);
		int pw = 110;
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
		Draw.round(g, pxl, y + 3, pw, h - 6, 7, col(ColorUtil.withAlpha(th.bg, 0.7f)));
		g.text(this.font, "‹", pxl + 6, y + 8, col(th.accent1), false);
		g.text(this.font, "›", pxl + pw - 10, y + 8, col(th.accent1), false);
		// position dots
		int dotsW = options.length * 4;
		for (int i = 0; i < options.length; i++) {
			int dx = pxl + (pw - dotsW) / 2 + i * 4;
			g.fill(dx, y + h - 6, dx + 2, y + h - 5, col(ColorUtil.withAlpha(i == value ? th.accent1 : th.textDim, i == value ? 1f : 0.35f)));
		}
		String opt = options[value];
		int ow = this.font.width(opt);
		g.enableScissor(pxl + 14, y + 3, pxl + pw - 14, y + h - 3);
		g.pose().pushMatrix();
		g.pose().translate(sw.value * 12f, -1f);
		g.text(this.font, opt, pxl + (pw - ow) / 2, y + 8, col(ColorUtil.withAlpha(th.text, 1f - Math.abs(sw.value) * 0.7f)), false);
		g.pose().popMatrix();
		g.disableScissor();
		endRow(g);
		return value;
	}
}
