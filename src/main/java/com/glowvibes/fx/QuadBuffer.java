package com.glowvibes.fx;

import java.util.Arrays;

/**
 * Collects camera-relative, double-sided, per-vertex coloured quads.
 * Everything visual in the world (trails, circles, petals, crystals, glow) is built from these.
 */
public final class QuadBuffer {
	private float[] pos = new float[3 * 16384];
	private int[] col = new int[16384];
	private int n;
	private double cx, cy, cz;

	// billboard basis (camera facing)
	private double rx, ry, rz, ux, uy, uz;

	public void begin(double camX, double camY, double camZ) {
		this.cx = camX;
		this.cy = camY;
		this.cz = camZ;
		this.n = 0;
	}

	public int size() {
		return n;
	}

	public float[] positions() {
		return Arrays.copyOf(pos, n * 3);
	}

	public int[] colors() {
		return Arrays.copyOf(col, n);
	}

	public double camX() {
		return cx;
	}

	public double camY() {
		return cy;
	}

	public double camZ() {
		return cz;
	}

	private void ensure(int add) {
		if (n + add > col.length) {
			int cap = Math.max(col.length * 2, n + add);
			col = Arrays.copyOf(col, cap);
			pos = Arrays.copyOf(pos, cap * 3);
		}
	}

	private void vtx(double x, double y, double z, int c) {
		int i = n * 3;
		pos[i] = (float) (x - cx);
		pos[i + 1] = (float) (y - cy);
		pos[i + 2] = (float) (z - cz);
		col[n] = c;
		n++;
	}

	/** Emits the quad in both windings so face culling can never hide it. */
	public void quad(double x1, double y1, double z1, int c1,
					double x2, double y2, double z2, int c2,
					double x3, double y3, double z3, int c3,
					double x4, double y4, double z4, int c4) {
		if (((c1 | c2 | c3 | c4) >>> 24) == 0) {
			return; // fully transparent
		}
		ensure(8);
		vtx(x1, y1, z1, c1);
		vtx(x2, y2, z2, c2);
		vtx(x3, y3, z3, c3);
		vtx(x4, y4, z4, c4);
		vtx(x4, y4, z4, c4);
		vtx(x3, y3, z3, c3);
		vtx(x2, y2, z2, c2);
		vtx(x1, y1, z1, c1);
	}

	public void tri(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int c) {
		quad(x1, y1, z1, c, x2, y2, z2, c, x3, y3, z3, c, x3, y3, z3, c);
	}

	/** Computes a camera-facing basis for a point. */
	public void basis(double x, double y, double z) {
		double fx = x - cx, fy = y - cy, fz = z - cz;
		double l = Math.sqrt(fx * fx + fy * fy + fz * fz);
		if (l < 1.0e-4) {
			fx = 0;
			fy = 0;
			fz = 1;
		} else {
			fx /= l;
			fy /= l;
			fz /= l;
		}
		double rxx = -fz, rzz = fx;
		double rl = Math.sqrt(rxx * rxx + rzz * rzz);
		if (rl < 1.0e-4) {
			rxx = 1;
			rzz = 0;
			rl = 1;
		}
		rx = rxx / rl;
		ry = 0;
		rz = rzz / rl;
		ux = ry * fz - rz * fy;
		uy = rz * fx - rx * fz;
		uz = rx * fy - ry * fx;
	}

	/** Soft radial glow disk, bright centre to transparent edge. */
	public void glow(double x, double y, double z, double r, int center, int edge) {
		basis(x, y, z);
		double[] px = new double[8], py = new double[8], pz = new double[8];
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4.0;
			double ca = Math.cos(a) * r, sa = Math.sin(a) * r;
			px[k] = x + rx * ca + ux * sa;
			py[k] = y + ry * ca + uy * sa;
			pz[k] = z + rz * ca + uz * sa;
		}
		for (int k = 0; k < 8; k += 2) {
			int k1 = k + 1, k2 = (k + 2) % 8;
			quad(x, y, z, center, px[k], py[k], pz[k], edge, px[k1], py[k1], pz[k1], edge, px[k2], py[k2], pz[k2], edge);
		}
	}

	/** Camera-facing rhombus (petal, star spike). Colours: top, right, bottom, left. */
	public void rhombus(double x, double y, double z, double w, double h, double angle, int cTop, int cRight, int cBottom, int cLeft) {
		basis(x, y, z);
		double ca = Math.cos(angle), sa = Math.sin(angle);
		// local axes rotated in the billboard plane
		double arx = rx * ca + ux * sa, ary = ry * ca + uy * sa, arz = rz * ca + uz * sa;
		double aux = -rx * sa + ux * ca, auy = -ry * sa + uy * ca, auz = -rz * sa + uz * ca;
		quad(x + aux * h, y + auy * h, z + auz * h, cTop,
				x + arx * w, y + ary * w, z + arz * w, cRight,
				x - aux * h, y - auy * h, z - auz * h, cBottom,
				x - arx * w, y - ary * w, z - arz * w, cLeft);
	}

	/** Camera-facing ring (shockwave). */
	public void billboardRing(double x, double y, double z, double rIn, double rOut, int segments, int cIn, int cOut) {
		basis(x, y, z);
		double brx = rx, bry = ry, brz = rz, bux = ux, buy = uy, buz = uz;
		for (int i = 0; i < segments; i++) {
			double a0 = i * Math.PI * 2 / segments, a1 = (i + 1) * Math.PI * 2 / segments;
			double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
			quad(x + (brx * c0 + bux * s0) * rIn, y + (bry * c0 + buy * s0) * rIn, z + (brz * c0 + buz * s0) * rIn, cIn,
					x + (brx * c1 + bux * s1) * rIn, y + (bry * c1 + buy * s1) * rIn, z + (brz * c1 + buz * s1) * rIn, cIn,
					x + (brx * c1 + bux * s1) * rOut, y + (bry * c1 + buy * s1) * rOut, z + (brz * c1 + buz * s1) * rOut, cOut,
					x + (brx * c0 + bux * s0) * rOut, y + (bry * c0 + buy * s0) * rOut, z + (brz * c0 + buz * s0) * rOut, cOut);
		}
	}

	/** One horizontal annulus segment between angles a0 and a1. */
	public void ringSegment(double x, double y, double z, double a0, double a1, double rIn, double rOut, int cIn0, int cOut0, int cIn1, int cOut1) {
		double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
		quad(x + c0 * rIn, y, z + s0 * rIn, cIn0,
				x + c1 * rIn, y, z + s1 * rIn, cIn1,
				x + c1 * rOut, y, z + s1 * rOut, cOut1,
				x + c0 * rOut, y, z + s0 * rOut, cOut0);
	}

	/** One vertical cylinder wall segment. */
	public void wallSegment(double x, double y, double z, double a0, double a1, double r, double h, int cBot0, int cTop0, int cBot1, int cTop1) {
		double c0 = Math.cos(a0) * r, s0 = Math.sin(a0) * r, c1 = Math.cos(a1) * r, s1 = Math.sin(a1) * r;
		quad(x + c0, y, z + s0, cBot0,
				x + c1, y, z + s1, cBot1,
				x + c1, y + h, z + s1, cTop1,
				x + c0, y + h, z + s0, cTop0);
	}

	/** Rotating octahedron crystal with per-face shading. */
	public void crystal(double x, double y, double z, double size, double rotY, double tilt, int color) {
		double[][] v = {
				{0, size * 1.6, 0}, {0, -size * 1.6, 0},
				{size, 0, 0}, {0, 0, size}, {-size, 0, 0}, {0, 0, -size}
		};
		double cy0 = Math.cos(rotY), sy0 = Math.sin(rotY), ct = Math.cos(tilt), st = Math.sin(tilt);
		for (double[] p : v) {
			double px = p[0] * cy0 - p[2] * sy0;
			double pz = p[0] * sy0 + p[2] * cy0;
			double py = p[1] * ct - pz * st;
			pz = p[1] * st + pz * ct;
			p[0] = x + px;
			p[1] = y + py;
			p[2] = z + pz;
		}
		for (int i = 0; i < 4; i++) {
			int a = 2 + i, b = 2 + (i + 1) % 4;
			float shade = 0.55f + 0.45f * (float) ((Math.cos(i * Math.PI / 2 + rotY) + 1) / 2);
			int top = com.glowvibes.util.ColorUtil.lerp(color & 0xFF000000, color, shade);
			int topLit = com.glowvibes.util.ColorUtil.brighten(top, 0.35f);
			int bottom = com.glowvibes.util.ColorUtil.lerp(color & 0xFF000000, color, shade * 0.7f);
			tri(v[0][0], v[0][1], v[0][2], v[a][0], v[a][1], v[a][2], v[b][0], v[b][1], v[b][2], topLit);
			tri(v[1][0], v[1][1], v[1][2], v[a][0], v[a][1], v[a][2], v[b][0], v[b][1], v[b][2], bottom);
		}
	}
}
