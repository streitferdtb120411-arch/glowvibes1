package com.glowvibes.fx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.glowvibes.config.GlowConfig;
import com.glowvibes.config.Theme;
import com.glowvibes.util.Anim;
import com.glowvibes.util.ColorUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * The whole visual engine. Simulation runs per rendered frame (not per tick), so motion stays
 * smooth at any FPS. Player positions are sampled per tick and interpolated in between.
 */
public final class FxManager {
	private static final FxManager INSTANCE = new FxManager();

	public static final String[] TRAIL_MODES = {"Ribbon", "Helix", "Orbs"};
	public static final String[] JUMP_STYLES = {"Solid", "Dashed", "Double", "Pulse"};
	public static final String[] AMB_MODES = {"Auto", "Petals", "Fireflies", "Stars", "Crystals", "Snow", "Embers"};

	private static final int MAX_PARTICLES = 1400;

	private final Random rnd = new Random();
	private final List<FxParticle> particles = new ArrayList<>();
	private final List<Circle> circles = new ArrayList<>();
	private final List<Shock> shocks = new ArrayList<>();
	private final Map<UUID, ArrayDeque<double[]>> trails = new HashMap<>();

	// per player: x, y, z, height, spectator(1/0)
	private final Map<UUID, double[]> prevPos = new HashMap<>();
	private final Map<UUID, double[]> curPos = new HashMap<>();
	private UUID selfId;

	private long lastTickNanos = System.nanoTime();
	private long lastFrameNanos;
	private float time;

	private boolean wasOnGround = true;
	private double lastVy;

	private static final class Circle {
		double x, y, z;
		float age, life;
		boolean landing;
		float seed;
	}

	private static final class Shock {
		double x, y, z;
		float age, life;
		float size;
	}

	public static FxManager get() {
		return INSTANCE;
	}

	// ------------------------------------------------------------------ tick

	public void onTick(Minecraft mc) {
		lastTickNanos = System.nanoTime();
		if (mc.level == null || mc.player == null) {
			clearAll();
			return;
		}
		Set<UUID> seen = new HashSet<>();
		for (var pl : mc.level.players()) {
			UUID id = pl.getUUID();
			seen.add(id);
			AABB bb = pl.getBoundingBox();
			double[] now = {pl.getX(), pl.getY(), pl.getZ(), bb.maxY - bb.minY, pl.isSpectator() ? 1 : 0};
			double[] old = curPos.get(id);
			prevPos.put(id, old == null ? now.clone() : old);
			curPos.put(id, now);
		}
		curPos.keySet().retainAll(seen);
		prevPos.keySet().retainAll(seen);
		trails.keySet().retainAll(seen);

		var self = mc.player;
		selfId = self.getUUID();
		boolean onGround = self.onGround();
		double vy = self.getDeltaMovement().y;
		GlowConfig c = GlowConfig.get();
		if (c.jumpCircles && !self.isSpectator()) {
			if (wasOnGround && !onGround && vy > 0.15) {
				spawnCircle(self.getX(), self.getY(), self.getZ(), false);
			} else if (!wasOnGround && onGround && lastVy < -0.45 && c.landCircles) {
				spawnCircle(self.getX(), self.getY(), self.getZ(), true);
			}
		}
		wasOnGround = onGround;
		lastVy = vy;
	}

	private void clearAll() {
		particles.clear();
		circles.clear();
		shocks.clear();
		trails.clear();
		prevPos.clear();
		curPos.clear();
	}

	private void spawnCircle(double x, double y, double z, boolean landing) {
		GlowConfig c = GlowConfig.get();
		Circle ci = new Circle();
		ci.x = x;
		ci.y = y + 0.03;
		ci.z = z;
		ci.life = c.jumpDuration * (landing ? 0.75f : 1f);
		ci.landing = landing;
		ci.seed = rnd.nextFloat() * 6.28f;
		circles.add(ci);
		if (circles.size() > 12) {
			circles.remove(0);
		}
		if (c.jumpBurst) {
			int count = landing ? 18 : 12;
			for (int i = 0; i < count; i++) {
				double a = i * Math.PI * 2 / count + rnd.nextDouble() * 0.3;
				double sp = 1.2 + rnd.nextDouble() * 1.6;
				FxParticle p = newParticle(FxParticle.Kind.SPARK, x, y + 0.08, z);
				p.vx = Math.cos(a) * sp;
				p.vz = Math.sin(a) * sp;
				p.vy = 0.4 + rnd.nextDouble() * 1.4;
				p.gravity = 3.5f;
				p.drag = 2.8f;
				p.life = 0.45f + rnd.nextFloat() * 0.4f;
				p.size = 0.035f + rnd.nextFloat() * 0.03f;
				p.colorIndex = i;
			}
		}
	}

	public void onAttack(Entity target) {
		GlowConfig c = GlowConfig.get();
		if (!c.hitFx) {
			return;
		}
		AABB bb = target.getBoundingBox();
		double x = (bb.minX + bb.maxX) / 2, y = (bb.minY + bb.maxY) / 2, z = (bb.minZ + bb.maxZ) / 2;
		int count = Math.round(16 * c.hitIntensity);
		for (int i = 0; i < count; i++) {
			FxParticle p = newParticle(FxParticle.Kind.SPARK, x, y, z);
			double th = rnd.nextDouble() * Math.PI * 2, ph = Math.acos(2 * rnd.nextDouble() - 1);
			double sp = (2.0 + rnd.nextDouble() * 3.0) * (0.7 + 0.3 * c.hitIntensity);
			p.vx = Math.sin(ph) * Math.cos(th) * sp;
			p.vy = Math.cos(ph) * sp * 0.8 + 1.0;
			p.vz = Math.sin(ph) * Math.sin(th) * sp;
			p.gravity = 6f;
			p.drag = 3.2f;
			p.life = 0.35f + rnd.nextFloat() * 0.45f;
			p.size = 0.03f + rnd.nextFloat() * 0.035f;
			p.colorIndex = rnd.nextInt(8);
		}
		Shock s = new Shock();
		s.x = x;
		s.y = y;
		s.z = z;
		s.life = 0.4f;
		s.size = (float) Math.max(0.6, (bb.maxY - bb.minY) * 0.6) * (0.8f + 0.2f * c.hitIntensity);
		shocks.add(s);
		if (shocks.size() > 10) {
			shocks.remove(0);
		}
	}

	private FxParticle newParticle(FxParticle.Kind kind, double x, double y, double z) {
		FxParticle p = new FxParticle();
		p.kind = kind;
		p.x = x;
		p.y = y;
		p.z = z;
		p.phase = rnd.nextFloat() * 6.283f;
		if (particles.size() >= MAX_PARTICLES) {
			particles.remove(0);
		}
		particles.add(p);
		return p;
	}

	// ------------------------------------------------------------------ frame

	private double[] interp(UUID id, float pt) {
		double[] a = prevPos.get(id), b = curPos.get(id);
		if (a == null || b == null) {
			return null;
		}
		return new double[] {
				a[0] + (b[0] - a[0]) * pt,
				a[1] + (b[1] - a[1]) * pt,
				a[2] + (b[2] - a[2]) * pt,
				b[3],
				b[4]
		};
	}

	public void onFrame(QuadBuffer buf, Minecraft mc) {
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
		lastFrameNanos = now;
		time += dt;
		float pt = ColorUtil.clamp01((now - lastTickNanos) / 5.0e7f);

		GlowConfig c = GlowConfig.get();
		Theme th = Theme.current();

		double camX = buf.camX(), camY = buf.camY(), camZ = buf.camZ();

		if (c.ambience) {
			spawnAmbient(c, th, camX, camY, camZ);
		}
		updateParticles(dt, c, camX, camZ);

		// trails
		if (c.trails) {
			for (UUID id : curPos.keySet()) {
				boolean isSelf = id.equals(selfId);
				if (!isSelf && !c.trailOthers) {
					continue;
				}
				double[] p = interp(id, pt);
				if (p == null || p[4] > 0.5) {
					continue;
				}
				ArrayDeque<double[]> t = trails.computeIfAbsent(id, k -> new ArrayDeque<>());
				double[] last = t.peekLast();
				if (last != null && dist2(last, p) > 36) {
					t.clear(); // teleport or respawn: do not draw a streak across the map
					last = null;
				}
				if (last == null || dist2(last, p) > 0.0025) {
					t.addLast(new double[] {p[0], p[1], p[2], time});
				}
				while (!t.isEmpty() && time - t.peekFirst()[3] > c.trailLife) {
					t.removeFirst();
				}
				while (t.size() > 500) {
					t.removeFirst();
				}
				renderTrail(buf, t, p, c, th);
			}
		} else {
			trails.clear();
		}

		renderCircles(buf, dt, c, th);
		renderShocks(buf, dt, th);

		// aura around the local player (third person only, so it never blocks the view)
		if (selfId != null) {
			double[] p = interp(selfId, pt);
			if (p != null && p[4] < 0.5) {
				double eyeY = p[1] + p[3] * 0.9;
				double dx = camX - p[0], dy = camY - eyeY, dz = camZ - p[2];
				boolean firstPerson = dx * dx + dy * dy + dz * dz < 0.6 * 0.6;
				if (!firstPerson) {
					if (c.halo) {
						renderHalo(buf, p, c, th);
					}
					if (c.orbit) {
						renderOrbit(buf, p, c, th);
					}
				}
			}
		}

		renderParticles(buf, c, th);
	}

	private static double dist2(double[] a, double[] b) {
		double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
		return dx * dx + dy * dy + dz * dz;
	}

	// ------------------------------------------------------------------ ambience

	private int ambientCount() {
		int n = 0;
		for (FxParticle p : particles) {
			if (p.ambient) {
				n++;
			}
		}
		return n;
	}

	private void spawnAmbient(GlowConfig c, Theme th, double cx, double cy, double cz) {
		int target = Math.round(c.ambDensity);
		int have = ambientCount();
		int budget = Math.max(2, target / 40);
		int mode = c.ambMode == 0 ? th.defaultAmbience : c.ambMode;
		float range = c.ambRange;
		for (int i = 0; i < budget && have < target; i++, have++) {
			double ang = rnd.nextDouble() * Math.PI * 2;
			double rad = Math.sqrt(rnd.nextDouble()) * range;
			double x = cx + Math.cos(ang) * rad, z = cz + Math.sin(ang) * rad;
			FxParticle p;
			switch (mode) {
				case 1 -> { // petals
					p = newParticle(FxParticle.Kind.PETAL, x, cy + 1 + rnd.nextDouble() * range * 0.45, z);
					p.vx = 0.25 + rnd.nextDouble() * 0.2;
					p.vy = -(0.45 + rnd.nextDouble() * 0.45);
					p.vz = 0.1 + rnd.nextDouble() * 0.15;
					p.life = 8 + rnd.nextFloat() * 7;
					p.size = 0.08f + rnd.nextFloat() * 0.06f;
					p.rotV = (rnd.nextFloat() - 0.5f) * 3f;
					p.flipV = 1.2f + rnd.nextFloat() * 2.2f;
				}
				case 2 -> { // fireflies
					p = newParticle(FxParticle.Kind.ORB, x, cy - 2 + rnd.nextDouble() * 6, z);
					p.life = 5 + rnd.nextFloat() * 6;
					p.size = 0.035f + rnd.nextFloat() * 0.03f;
					p.drag = 1.2f;
				}
				case 3 -> { // stars
					p = newParticle(FxParticle.Kind.STAR, x, cy + rnd.nextDouble() * 8, z);
					p.vy = 0.05;
					p.life = 3 + rnd.nextFloat() * 4;
					p.size = 0.09f + rnd.nextFloat() * 0.1f;
					p.rot = rnd.nextFloat() * 0.8f;
				}
				case 4 -> { // crystals
					p = newParticle(FxParticle.Kind.CRYSTAL, x, cy + 0.5 + rnd.nextDouble() * 7, z);
					p.life = 7 + rnd.nextFloat() * 7;
					p.size = 0.08f + rnd.nextFloat() * 0.14f;
					p.rotV = 0.6f + rnd.nextFloat() * 1.2f;
					p.flip = rnd.nextFloat() * 0.5f;
					p.flipV = (rnd.nextFloat() - 0.5f) * 0.6f;
				}
				case 5 -> { // snow
					p = newParticle(FxParticle.Kind.SNOW, x, cy + 2 + rnd.nextDouble() * 9, z);
					p.vy = -(0.5 + rnd.nextDouble() * 0.5);
					p.life = 9 + rnd.nextFloat() * 5;
					p.size = 0.025f + rnd.nextFloat() * 0.03f;
				}
				default -> { // embers
					p = newParticle(FxParticle.Kind.EMBER, x, cy - 3 + rnd.nextDouble() * 3, z);
					p.vy = 0.35 + rnd.nextDouble() * 0.6;
					p.life = 4 + rnd.nextFloat() * 4;
					p.size = 0.022f + rnd.nextFloat() * 0.025f;
				}
			}
			p.ambient = true;
			p.size *= c.ambSize;
			p.colorIndex = rnd.nextInt(th.particleCount());
		}
	}

	private void updateParticles(float dt, GlowConfig c, double camX, double camZ) {
		float range = c.ambRange;
		Iterator<FxParticle> it = particles.iterator();
		while (it.hasNext()) {
			FxParticle p = it.next();
			p.age += dt;
			if (p.age >= p.life) {
				it.remove();
				continue;
			}
			if (p.ambient) {
				double dx = p.x - camX, dz = p.z - camZ;
				if (!c.ambience || dx * dx + dz * dz > range * range * 1.7) {
					it.remove();
					continue;
				}
			}
			switch (p.kind) {
				case PETAL -> {
					p.x += Math.sin(time * 1.3f + p.phase) * 0.35 * dt;
					p.z += Math.cos(time * 0.9f + p.phase) * 0.2 * dt;
				}
				case ORB -> {
					p.vx += (rnd.nextDouble() - 0.5) * 1.6 * dt;
					p.vy += (rnd.nextDouble() - 0.5) * 1.0 * dt;
					p.vz += (rnd.nextDouble() - 0.5) * 1.6 * dt;
				}
				case SNOW -> p.x += Math.sin(time * 1.7f + p.phase) * 0.25 * dt;
				case EMBER -> {
					p.vx += (rnd.nextDouble() - 0.5) * 0.8 * dt;
					p.vz += (rnd.nextDouble() - 0.5) * 0.8 * dt;
				}
				case CRYSTAL -> p.vy = Math.sin(time * 0.8f + p.phase) * 0.12;
				default -> {
				}
			}
			p.vy -= p.gravity * dt;
			if (p.drag > 0) {
				double k = Math.exp(-p.drag * dt);
				p.vx *= k;
				p.vy *= k;
				p.vz *= k;
			}
			p.x += p.vx * dt;
			p.y += p.vy * dt;
			p.z += p.vz * dt;
			p.rot += p.rotV * dt;
			p.flip += p.flipV * dt;
		}
	}

	private void renderParticles(QuadBuffer buf, GlowConfig c, Theme th) {
		boolean hq = c.glowQuality >= 2;
		double cx = buf.camX(), cz = buf.camZ();
		for (FxParticle p : particles) {
			float a = p.alpha();
			if (p.ambient) {
				double dx = p.x - cx, dz = p.z - cz;
				float d = (float) (Math.sqrt(dx * dx + dz * dz) / c.ambRange);
				a *= ColorUtil.clamp01((1.3f - d) / 0.35f);
			}
			if (a <= 0.01f) {
				continue;
			}
			int col = th.particle(p.colorIndex);
			switch (p.kind) {
				case PETAL -> {
					double w = p.size * (0.12 + 0.5 * Math.abs(Math.cos(p.flip)));
					int base = ColorUtil.withAlpha(col, a * 0.95f);
					int tip = ColorUtil.withAlpha(ColorUtil.brighten(col, 0.6f), a);
					int edge = ColorUtil.withAlpha(ColorUtil.lerp(col, th.accent2, 0.35f), a * 0.9f);
					buf.rhombus(p.x, p.y, p.z, w, p.size, p.rot, tip, edge, base, edge);
					if (hq) {
						buf.glow(p.x, p.y, p.z, p.size * 2.2, ColorUtil.withAlpha(col, a * 0.16f), 0);
					}
				}
				case ORB -> {
					float pulse = 0.55f + 0.45f * (float) Math.sin(time * 3f + p.phase);
					buf.glow(p.x, p.y, p.z, p.size * 5.5, ColorUtil.withAlpha(col, a * 0.32f * pulse), 0);
					buf.glow(p.x, p.y, p.z, p.size * 1.5, ColorUtil.withAlpha(ColorUtil.brighten(col, 0.65f), a * pulse), ColorUtil.withAlpha(col, 0));
				}
				case STAR -> {
					float tw = 0.5f + 0.5f * (float) Math.sin(time * 4f + p.phase);
					double h = p.size * (0.55 + 0.7 * tw);
					int core = ColorUtil.withAlpha(ColorUtil.brighten(col, 0.5f), a);
					int tipC = ColorUtil.withAlpha(col, 0f);
					buf.rhombus(p.x, p.y, p.z, p.size * 0.16, h, p.rot, tipC, core, tipC, core);
					buf.rhombus(p.x, p.y, p.z, p.size * 0.16, h * 0.8, p.rot + Math.PI / 2, tipC, core, tipC, core);
					if (hq) {
						buf.glow(p.x, p.y, p.z, p.size * 1.6, ColorUtil.withAlpha(col, a * 0.25f * tw), 0);
					}
				}
				case CRYSTAL -> {
					buf.crystal(p.x, p.y, p.z, p.size, p.rot, p.flip, ColorUtil.withAlpha(col, a * 0.6f));
					if (hq) {
						buf.glow(p.x, p.y, p.z, p.size * 3.2, ColorUtil.withAlpha(col, a * 0.18f), 0);
					}
				}
				case SNOW -> buf.glow(p.x, p.y, p.z, p.size * 2.2,
						ColorUtil.withAlpha(ColorUtil.brighten(col, 0.75f), a * 0.9f), ColorUtil.withAlpha(0xFFFFFFFF, 0));
				case EMBER -> {
					float fl = 0.65f + 0.35f * (float) Math.sin(time * 18f + p.phase);
					buf.glow(p.x, p.y, p.z, p.size * 4, ColorUtil.withAlpha(col, a * 0.35f * fl), 0);
					buf.glow(p.x, p.y, p.z, p.size * 1.1, ColorUtil.withAlpha(ColorUtil.brighten(col, 0.5f), a * fl), ColorUtil.withAlpha(col, 0));
				}
				case SPARK -> {
					int sc = p.fixedColor != 0 ? p.fixedColor : th.gradient(p.colorIndex * 0.11f);
					buf.glow(p.x, p.y, p.z, p.size * 3.0, ColorUtil.withAlpha(sc, a * 0.45f), 0);
					buf.glow(p.x, p.y, p.z, p.size, ColorUtil.withAlpha(ColorUtil.brighten(sc, 0.7f), a), ColorUtil.withAlpha(sc, 0));
				}
			}
		}
	}

	// ------------------------------------------------------------------ trails

	private void renderTrail(QuadBuffer buf, ArrayDeque<double[]> pts, double[] head, GlowConfig c, Theme th) {
		if (pts.isEmpty()) {
			return;
		}
		List<double[]> list = new ArrayList<>(pts.size() + 1);
		list.addAll(pts);
		list.add(new double[] {head[0], head[1], head[2], time});
		int n = list.size();
		if (n < 2) {
			return;
		}
		float life = c.trailLife;
		double height = c.trailHeight;
		double width = c.trailWidth;

		switch (c.trailMode) {
			case 0 -> { // ribbon: glowing vertical band + ground glow strip + bright core line
				for (int i = 0; i < n - 1; i++) {
					double[] A = list.get(i), B = list.get(i + 1);
					float fa = fadeOf(A, life), fb = fadeOf(B, life);
					float aa = (float) Math.pow(fa, 1.3) * c.trailOpacity, ab = (float) Math.pow(fb, 1.3) * c.trailOpacity;
					int ca = th.gradient((float) A[3] * 0.7f), cb = th.gradient((float) B[3] * 0.7f);
					double y0a = A[1] + 0.04, y0b = B[1] + 0.04;
					double ha = height * (0.35 + 0.65 * fa), hb = height * (0.35 + 0.65 * fb);
					buf.quad(A[0], y0a, A[2], ColorUtil.withAlpha(ca, aa * 0.7f),
							B[0], y0b, B[2], ColorUtil.withAlpha(cb, ab * 0.7f),
							B[0], y0b + hb, B[2], ColorUtil.withAlpha(cb, 0),
							A[0], y0a + ha, A[2], ColorUtil.withAlpha(ca, 0));
					int la = ColorUtil.withAlpha(ColorUtil.brighten(ca, 0.45f), aa);
					int lb = ColorUtil.withAlpha(ColorUtil.brighten(cb, 0.45f), ab);
					buf.quad(A[0], y0a, A[2], la, B[0], y0b, B[2], lb,
							B[0], y0b + 0.05, B[2], lb, A[0], y0a + 0.05, A[2], la);
					double dx = B[0] - A[0], dz = B[2] - A[2];
					double l = Math.sqrt(dx * dx + dz * dz);
					if (l > 1.0e-4) {
						double px = -dz / l * width * fa, pz = dx / l * width * fa;
						double qx = -dz / l * width * fb, qz = dx / l * width * fb;
						int ga = ColorUtil.withAlpha(ca, aa * 0.45f), gb = ColorUtil.withAlpha(cb, ab * 0.45f);
						buf.quad(A[0], y0a - 0.02, A[2], ga, B[0], y0b - 0.02, B[2], gb,
								B[0] + qx, y0b - 0.02, B[2] + qz, ColorUtil.withAlpha(cb, 0), A[0] + px, y0a - 0.02, A[2] + pz, ColorUtil.withAlpha(ca, 0));
						buf.quad(A[0], y0a - 0.02, A[2], ga, B[0], y0b - 0.02, B[2], gb,
								B[0] - qx, y0b - 0.02, B[2] - qz, ColorUtil.withAlpha(cb, 0), A[0] - px, y0a - 0.02, A[2] - pz, ColorUtil.withAlpha(ca, 0));
					}
				}
			}
			case 1 -> { // helix: two glowing strands spiralling around the path
				for (int i = 0; i < n; i++) {
					double[] P = list.get(i);
					double[] Q = list.get(Math.min(n - 1, i + 1));
					double[] R0 = list.get(Math.max(0, i - 1));
					double dx = Q[0] - R0[0], dz = Q[2] - R0[2];
					double l = Math.sqrt(dx * dx + dz * dz);
					if (l < 1.0e-4) {
						dx = 1;
						dz = 0;
						l = 1;
					}
					double px = -dz / l, pz = dx / l;
					float f = fadeOf(P, life);
					float a = (float) Math.pow(f, 1.2) * c.trailOpacity;
					double r = width * (0.35 + 0.65 * f);
					double cyy = P[1] + height * 0.5;
					for (int s = 0; s < 2; s++) {
						double ang = P[3] * 7.0 + s * Math.PI;
						double ox = px * Math.cos(ang) * r, oz = pz * Math.cos(ang) * r, oy = Math.sin(ang) * r * 0.9;
						int col = th.gradient((float) P[3] * 0.7f + s * 0.5f);
						double sz = 0.035 + 0.05 * f;
						if (i % 2 == 0) {
							buf.glow(P[0] + ox, cyy + oy, P[2] + oz, sz * 3.2, ColorUtil.withAlpha(col, a * 0.3f), 0);
						}
						buf.glow(P[0] + ox, cyy + oy, P[2] + oz, sz, ColorUtil.withAlpha(ColorUtil.brighten(col, 0.5f), a), ColorUtil.withAlpha(col, 0));
					}
				}
			}
			default -> { // orbs: tapering glowing beads
				for (int i = 0; i < n; i += 2) {
					double[] P = list.get(i);
					float f = fadeOf(P, life);
					float a = f * c.trailOpacity;
					int col = th.gradient((float) P[3] * 0.7f);
					double y = P[1] + height * 0.3 + Math.sin(P[3] * 5.0) * 0.06;
					double sz = (0.05 + 0.09 * f) * (width / 0.45);
					buf.glow(P[0], y, P[2], sz * 3, ColorUtil.withAlpha(col, a * 0.28f), 0);
					buf.glow(P[0], y, P[2], sz, ColorUtil.withAlpha(ColorUtil.brighten(col, 0.55f), a), ColorUtil.withAlpha(col, 0));
				}
			}
		}
	}

	private float fadeOf(double[] p, float life) {
		return ColorUtil.clamp01(1f - (float) (time - p[3]) / life);
	}

	// ------------------------------------------------------------------ jump circles

	private void renderCircles(QuadBuffer buf, float dt, GlowConfig c, Theme th) {
		int seg = c.glowQuality == 1 ? 32 : (c.glowQuality == 2 ? 48 : 72);
		Iterator<Circle> it = circles.iterator();
		while (it.hasNext()) {
			Circle ci = it.next();
			ci.age += dt;
			if (ci.age >= ci.life) {
				it.remove();
				continue;
			}
			float t = ci.age / ci.life;
			float fadeIn = Math.min(1f, t / 0.07f);
			float a = fadeIn * (float) Math.pow(1f - t, 1.4);
			float baseR = c.jumpRadius * (ci.landing ? 0.8f : 1f);
			drawRing(buf, ci, baseR * (0.12f + 0.88f * Anim.easeOutCubic(t)), a, t, seg, c, th, 0f);
			if (c.jumpStyle == 2) {
				float t2 = Math.max(0f, t - 0.18f) / 0.82f;
				if (t2 > 0) {
					drawRing(buf, ci, baseR * 0.7f * (0.12f + 0.88f * Anim.easeOutCubic(t2)), a * 0.85f, t2, seg, c, th, 0.5f);
				}
			}
		}
	}

	private void drawRing(QuadBuffer buf, Circle ci, float r, float a, float t, int seg, GlowConfig c, Theme th, float colorShift) {
		double thick = 0.05 + 0.09 * (1 - t);
		double glowW = 0.22 * (1 - t * 0.5);
		double wallH = c.jumpWall * (1 - t);
		double spin = ci.seed + time * (c.jumpStyle == 1 ? 1.6 : 0.4);
		float pulse = c.jumpStyle == 3 ? 0.65f + 0.35f * (float) Math.sin(time * 18f + ci.seed) : 1f;
		a *= pulse;
		boolean hq = c.glowQuality >= 2;
		for (int i = 0; i < seg; i++) {
			if (c.jumpStyle == 1 && ((i / 3) % 2 == 1)) {
				continue;
			}
			double a0 = spin + i * Math.PI * 2 / seg, a1 = spin + (i + 1) * Math.PI * 2 / seg;
			float u0 = (float) i / seg, u1 = (float) (i + 1) / seg;
			int c0 = th.gradient(u0 + colorShift + time * 0.2f), c1 = th.gradient(u1 + colorShift + time * 0.2f);
			if (ci.landing) {
				c0 = ColorUtil.brighten(c0, 0.25f);
				c1 = ColorUtil.brighten(c1, 0.25f);
			}
			int core0 = ColorUtil.withAlpha(ColorUtil.brighten(c0, 0.3f), a * 0.95f), core1 = ColorUtil.withAlpha(ColorUtil.brighten(c1, 0.3f), a * 0.95f);
			int g0 = ColorUtil.withAlpha(c0, a * 0.55f), g1 = ColorUtil.withAlpha(c1, a * 0.55f);
			int z0 = ColorUtil.withAlpha(c0, 0), z1 = ColorUtil.withAlpha(c1, 0);
			double rIn = Math.max(0.01, r - thick);
			// bright core band
			buf.ringSegment(ci.x, ci.y, ci.z, a0, a1, rIn, r, core0, core0, core1, core1);
			// outer glow
			buf.ringSegment(ci.x, ci.y, ci.z, a0, a1, r, r + glowW, g0, z0, g1, z1);
			if (hq) {
				// inner glow
				buf.ringSegment(ci.x, ci.y, ci.z, a0, a1, Math.max(0.01, rIn - glowW * 0.6), rIn, z0, g0, z1, g1);
				// faint inner fill, like a shockwave bowl
				buf.ringSegment(ci.x, ci.y - 0.005, ci.z, a0, a1, 0.01, rIn, ColorUtil.withAlpha(c0, 0), ColorUtil.withAlpha(c0, a * 0.12f),
						ColorUtil.withAlpha(c1, 0), ColorUtil.withAlpha(c1, a * 0.12f));
			}
			if (wallH > 0.01) {
				buf.wallSegment(ci.x, ci.y, ci.z, a0, a1, r, wallH, ColorUtil.withAlpha(c0, a * 0.5f), z0, ColorUtil.withAlpha(c1, a * 0.5f), z1);
			}
		}
	}

	private void renderShocks(QuadBuffer buf, float dt, Theme th) {
		Iterator<Shock> it = shocks.iterator();
		while (it.hasNext()) {
			Shock s = it.next();
			s.age += dt;
			if (s.age >= s.life) {
				it.remove();
				continue;
			}
			float t = s.age / s.life;
			float a = (1f - t) * (1f - t);
			double r = s.size * (0.3 + 0.9 * Anim.easeOutCubic(t));
			int col = th.gradient(time * 0.5f);
			buf.billboardRing(s.x, s.y, s.z, r * 0.82, r, 28, ColorUtil.withAlpha(ColorUtil.brighten(col, 0.4f), a * 0.9f), ColorUtil.withAlpha(col, a * 0.6f));
			buf.billboardRing(s.x, s.y, s.z, r, r * 1.25, 28, ColorUtil.withAlpha(col, a * 0.5f), ColorUtil.withAlpha(col, 0));
			buf.glow(s.x, s.y, s.z, r * 0.7, ColorUtil.withAlpha(ColorUtil.brighten(col, 0.6f), a * 0.35f), 0);
		}
	}

	// ------------------------------------------------------------------ aura

	private void renderHalo(QuadBuffer buf, double[] p, GlowConfig c, Theme th) {
		double y = p[1] + p[3] + 0.28 + Math.sin(time * 2.0) * 0.03;
		int seg = 40;
		double spin = time * 0.9;
		for (int i = 0; i < seg; i++) {
			double a0 = spin + i * Math.PI * 2 / seg, a1 = spin + (i + 1) * Math.PI * 2 / seg;
			int c0 = th.gradient((float) i / seg + time * 0.3f), c1 = th.gradient((float) (i + 1) / seg + time * 0.3f);
			float dash = (i / 2) % 2 == 0 ? 1f : 0.55f;
			buf.ringSegment(p[0], y, p[2], a0, a1, 0.25, 0.31,
					ColorUtil.withAlpha(ColorUtil.brighten(c0, 0.4f), 0.9f * dash), ColorUtil.withAlpha(ColorUtil.brighten(c0, 0.4f), 0.9f * dash),
					ColorUtil.withAlpha(ColorUtil.brighten(c1, 0.4f), 0.9f * dash), ColorUtil.withAlpha(ColorUtil.brighten(c1, 0.4f), 0.9f * dash));
			buf.ringSegment(p[0], y, p[2], a0, a1, 0.31, 0.44, ColorUtil.withAlpha(c0, 0.45f), ColorUtil.withAlpha(c0, 0), ColorUtil.withAlpha(c1, 0.45f), ColorUtil.withAlpha(c1, 0));
			buf.ringSegment(p[0], y, p[2], a0, a1, 0.14, 0.25, ColorUtil.withAlpha(c0, 0), ColorUtil.withAlpha(c0, 0.35f), ColorUtil.withAlpha(c1, 0), ColorUtil.withAlpha(c1, 0.35f));
		}
	}

	private void renderOrbit(QuadBuffer buf, double[] p, GlowConfig c, Theme th) {
		int count = c.orbitCount;
		for (int k = 0; k < count; k++) {
			double base = time * 2.2 + k * Math.PI * 2 / count;
			int col = th.gradient((float) k / count + time * 0.15f);
			for (int s = 0; s < 7; s++) {
				double ang = base - s * 0.09;
				double x = p[0] + Math.cos(ang) * 0.85;
				double z = p[2] + Math.sin(ang) * 0.85;
				double y = p[1] + p[3] * 0.55 + Math.sin(ang * 1.5 + k) * 0.22;
				float a = 1f - s / 7f;
				if (s == 0) {
					buf.glow(x, y, z, 0.2, ColorUtil.withAlpha(col, 0.35f), 0);
					buf.glow(x, y, z, 0.06, ColorUtil.withAlpha(ColorUtil.brighten(col, 0.7f), 1f), ColorUtil.withAlpha(col, 0));
				} else {
					buf.glow(x, y, z, 0.05 * a, ColorUtil.withAlpha(col, a * 0.7f), ColorUtil.withAlpha(col, 0));
				}
			}
		}
	}
}
