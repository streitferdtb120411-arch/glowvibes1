package com.glowvibes.util;

/** Frame-rate independent exponential smoothing plus a few easing curves. */
public final class Anim {
	public float value;
	public float target;
	public float speed;
	private boolean primed;

	public Anim(float speed) {
		this.speed = speed;
	}

	public Anim(float start, float speed) {
		this.value = start;
		this.target = start;
		this.speed = speed;
		this.primed = true;
	}

	/** Jumps to the target the first time it is used so widgets do not animate in from zero. */
	public Anim prime(float v) {
		if (!primed) {
			value = v;
			target = v;
			primed = true;
		}
		return this;
	}

	public float update(float dt) {
		float k = 1f - (float) Math.exp(-speed * dt);
		value += (target - value) * k;
		if (Math.abs(target - value) < 0.0005f) {
			value = target;
		}
		return value;
	}

	public static float easeOutCubic(float t) {
		t = ColorUtil.clamp01(t);
		float u = 1f - t;
		return 1f - u * u * u;
	}

	public static float easeOutBack(float t) {
		t = ColorUtil.clamp01(t);
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		float u = t - 1f;
		return 1f + c3 * u * u * u + c1 * u * u;
	}

	public static float easeInOutSine(float t) {
		t = ColorUtil.clamp01(t);
		return (float) (-(Math.cos(Math.PI * t) - 1.0) / 2.0);
	}

	public static float smooth(float t) {
		t = ColorUtil.clamp01(t);
		return t * t * (3f - 2f * t);
	}
}
