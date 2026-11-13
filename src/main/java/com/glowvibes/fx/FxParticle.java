package com.glowvibes.fx;

final class FxParticle {
	enum Kind { PETAL, ORB, STAR, CRYSTAL, SNOW, EMBER, SPARK }

	Kind kind;
	boolean ambient;
	double x, y, z;
	double vx, vy, vz;
	float age, life;
	float size;
	float rot, rotV;
	float flip, flipV;
	float phase;
	float gravity;
	float drag;
	int colorIndex;
	int fixedColor; // used when != 0 (sparks)

	float alpha() {
		float in = Math.min(1f, age / (ambient ? 0.9f : 0.05f));
		float out = Math.min(1f, (life - age) / (ambient ? 1.4f : life * 0.6f));
		return Math.max(0f, Math.min(in, out));
	}
}
