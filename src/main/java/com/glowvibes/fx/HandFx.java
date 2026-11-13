package com.glowvibes.fx;

import java.util.Random;

import com.glowvibes.config.GlowConfig;
import com.glowvibes.config.Theme;
import com.glowvibes.util.ColorUtil;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Real 3D lightning around the first-person arm and the held item.
 * Called from FirstPersonHandsMixin with the exact pose vanilla uses, so it follows every swing.
 * Uses vanilla's additive lightning render type for a true glow.
 */
public final class HandFx {
	private static final Random RND = new Random();

	private static final class BoltSet {
		final int count;
		final float[][] px, py, pz;
		final float[] age, life;
		long lastNanos;

		BoltSet(int count, int segs) {
			this.count = count;
			px = new float[count][segs + 1];
			py = new float[count][segs + 1];
			pz = new float[count][segs + 1];
			age = new float[count];
			life = new float[count];
		}
	}

	private static final int SEGS = 7;
	private static final BoltSet ARM = new BoltSet(3, SEGS);
	private static final BoltSet MAIN_ITEM = new BoltSet(4, SEGS);
	private static final BoltSet OFF_ITEM = new BoltSet(3, SEGS);

	private HandFx() {
	}

	/** Same first translation vanilla's renderPlayerArm applies: puts us at the fist. */
	public static void arm(PoseStack poseStack, SubmitNodeCollector collector, float inverseArmHeight, float attackValue, HumanoidArm arm) {
		GlowConfig c = GlowConfig.get();
		if (!c.handFx) {
			return;
		}
		float invert = arm != HumanoidArm.LEFT ? 1f : -1f;
		float sqrtA = (float) Math.sqrt(attackValue);
		float xs = -0.3f * (float) Math.sin(sqrtA * Math.PI);
		float ys = 0.4f * (float) Math.sin(sqrtA * Math.PI * 2);
		float zs = -0.4f * (float) Math.sin(attackValue * Math.PI);
		poseStack.pushPose();
		poseStack.translate(invert * (xs + 0.64f), ys - 0.6f + inverseArmHeight * -0.6f, zs - 0.72f);
		submit(poseStack, collector, ARM, invert * -0.12f, 0.1f, -0.02f, 0.2f, c);
		poseStack.popPose();
	}

	/** Pose is vanilla's item pose right before the item model is submitted. */
	public static void item(PoseStack poseStack, SubmitNodeCollector collector, boolean mainHand) {
		GlowConfig c = GlowConfig.get();
		if (!c.itemFx) {
			return;
		}
		submit(poseStack, collector, mainHand ? MAIN_ITEM : OFF_ITEM, 0f, 0.3f, 0f, 0.3f, c);
	}

	private static void regen(BoltSet s, int b, float cx, float cy, float cz, float r) {
		s.age[b] = 0;
		s.life[b] = 0.05f + RND.nextFloat() * 0.13f;
		// start and end on opposite-ish sides of a sphere
		float[] a = randomUnit(), e = randomUnit();
		e[0] = -a[0] * 0.6f + e[0] * 0.4f;
		e[1] = -a[1] * 0.6f + e[1] * 0.4f;
		e[2] = -a[2] * 0.6f + e[2] * 0.4f;
		float ax = cx + a[0] * r, ay = cy + a[1] * r, az = cz + a[2] * r;
		float ex = cx + e[0] * r, ey = cy + e[1] * r, ez = cz + e[2] * r;
		float len = (float) Math.sqrt((ex - ax) * (ex - ax) + (ey - ay) * (ey - ay) + (ez - az) * (ez - az));
		float jitter = len * 0.22f;
		for (int i = 0; i <= SEGS; i++) {
			float f = (float) i / SEGS;
			boolean end = i == 0 || i == SEGS;
			s.px[b][i] = ax + (ex - ax) * f + (end ? 0 : (RND.nextFloat() - 0.5f) * 2 * jitter);
			s.py[b][i] = ay + (ey - ay) * f + (end ? 0 : (RND.nextFloat() - 0.5f) * 2 * jitter);
			s.pz[b][i] = az + (ez - az) * f + (end ? 0 : (RND.nextFloat() - 0.5f) * 2 * jitter);
		}
	}

	private static float[] randomUnit() {
		float x, y, z, l;
		do {
			x = RND.nextFloat() * 2 - 1;
			y = RND.nextFloat() * 2 - 1;
			z = RND.nextFloat() * 2 - 1;
			l = x * x + y * y + z * z;
		} while (l < 0.01f || l > 1f);
		l = (float) Math.sqrt(l);
		return new float[] {x / l, y / l, z / l};
	}

	private static void submit(PoseStack poseStack, SubmitNodeCollector collector, BoltSet s, float cx, float cy, float cz, float r, GlowConfig c) {
		long now = System.nanoTime();
		float dt = s.lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - s.lastNanos) / 1.0e9f);
		s.lastNanos = now;
		float k = c.handFxIntensity;
		Theme th = Theme.current();
		float time = ColorUtil.seconds();

		// 3 floats + 1 colour per vertex; each segment = 2 crossed ribbons x 2 layers x 2 windings x 4 verts
		int maxV = s.count * SEGS * 32;
		final float[] v = new float[maxV * 3];
		final int[] col = new int[maxV];
		final float[] gv = new float[maxV * 3];
		final int[] gcol = new int[maxV];
		int n = 0;
		int gn = 0;
		for (int b = 0; b < s.count; b++) {
			s.age[b] += dt;
			if (s.age[b] >= s.life[b] || s.life[b] == 0) {
				regen(s, b, cx, cy, cz, r);
			}
			float t = s.age[b] / s.life[b];
			float flick = RND.nextFloat() < 0.12f ? 0.35f : 1f;
			float a = ColorUtil.clamp01((1f - t * 0.6f) * flick * k);
			int base = th.gradient(b * 0.2f + time * 0.3f);
			int core = ColorUtil.withAlpha(ColorUtil.brighten(base, 0.15f), a * 0.85f);
			int glow = ColorUtil.withAlpha(base, a * 0.32f);
			for (int i = 0; i < SEGS; i++) {
				gn = ribbon(gv, gcol, gn, s.px[b][i], s.py[b][i], s.pz[b][i], s.px[b][i + 1], s.py[b][i + 1], s.pz[b][i + 1], 0.014f, glow);
				n = ribbon(v, col, n, s.px[b][i], s.py[b][i], s.pz[b][i], s.px[b][i + 1], s.py[b][i + 1], s.pz[b][i + 1], 0.0035f, core);
			}
		}
		final int count = n;
		final int gcount = gn;
		if (count == 0) {
			return;
		}
		// soft coloured halo (alpha blended, keeps the theme colour even against a bright sky)
		collector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
			for (int i = 0; i < gcount; i++) {
				buffer.addVertex(pose, gv[i * 3], gv[i * 3 + 1], gv[i * 3 + 2]).setColor(gcol[i]);
			}
		});
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			for (int i = 0; i < count; i++) {
				buffer.addVertex(pose, v[i * 3], v[i * 3 + 1], v[i * 3 + 2]).setColor(col[i]);
			}
		});
	}

	/** Two crossed, double-sided ribbons so the segment is visible from every angle. */
	private static int ribbon(float[] v, int[] col, int n, float x0, float y0, float z0, float x1, float y1, float z1, float w, int color) {
		float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
		float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (l < 1.0e-5f) {
			return n;
		}
		dx /= l;
		dy /= l;
		dz /= l;
		// u = d x up (fallback d x X)
		float ux = dy * 0 - dz * 1, uy = dz * 0 - dx * 0, uz = dx * 1 - dy * 0;
		float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
		if (ul < 1.0e-3f) {
			ux = 0;
			uy = dz;
			uz = -dy;
			ul = (float) Math.sqrt(uy * uy + uz * uz);
		}
		ux /= ul;
		uy /= ul;
		uz /= ul;
		float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
		n = quad2(v, col, n, x0, y0, z0, x1, y1, z1, ux * w, uy * w, uz * w, color);
		n = quad2(v, col, n, x0, y0, z0, x1, y1, z1, vx * w, vy * w, vz * w, color);
		return n;
	}

	private static int quad2(float[] v, int[] col, int n, float x0, float y0, float z0, float x1, float y1, float z1, float ox, float oy, float oz, int c) {
		float[][] q = {
				{x0 + ox, y0 + oy, z0 + oz}, {x1 + ox, y1 + oy, z1 + oz}, {x1 - ox, y1 - oy, z1 - oz}, {x0 - ox, y0 - oy, z0 - oz}
		};
		int[] order = {0, 1, 2, 3, 3, 2, 1, 0};
		for (int idx : order) {
			v[n * 3] = q[idx][0];
			v[n * 3 + 1] = q[idx][1];
			v[n * 3 + 2] = q[idx][2];
			col[n] = c;
			n++;
		}
		return n;
	}
}
