package com.glowvibes.config;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

public final class GlowConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static GlowConfig instance = new GlowConfig();

	// Theme
	public String theme = "SAKURA";
	public boolean chroma = false;
	public float chromaSpeed = 0.6f;
	public float guiAnimSpeed = 1.0f;
	public int glowQuality = 2; // 1..3

	// Trails
	public boolean trails = true;
	public int trailMode = 0; // 0 Ribbon, 1 Helix, 2 Orbs
	public float trailLife = 1.2f;
	public float trailWidth = 0.45f;
	public float trailHeight = 1.1f;
	public float trailOpacity = 0.75f;
	public boolean trailOthers = false;

	// Jump circles
	public boolean jumpCircles = true;
	public int jumpStyle = 0; // 0 Solid, 1 Dashed, 2 Double, 3 Pulse
	public float jumpRadius = 1.3f;
	public float jumpDuration = 0.9f;
	public float jumpWall = 0.45f;
	public boolean landCircles = true;
	public boolean jumpBurst = true;

	// Ambience
	public boolean ambience = true;
	public int ambMode = 0; // 0 Auto, 1 Petals, 2 Fireflies, 3 Stars, 4 Crystals, 5 Snow, 6 Embers
	public float ambDensity = 140f;
	public float ambRange = 18f;
	public float ambSize = 1.0f;

	// Aura and hits
	public boolean halo = false;
	public boolean orbit = true;
	public int orbitCount = 3;
	public boolean hitFx = true;
	public float hitIntensity = 1.0f;

	// HUD
	public boolean hudWatermark = true;
	public boolean hudList = true;

	public static GlowConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("glowvibes.json");
	}

	public static void load() {
		Path p = path();
		if (!Files.exists(p)) {
			save();
			return;
		}
		try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
			GlowConfig loaded = GSON.fromJson(r, GlowConfig.class);
			if (loaded != null) {
				instance = loaded;
			}
		} catch (Exception e) {
			instance = new GlowConfig();
		}
		instance.sanitize();
	}

	public static void save() {
		try {
			Path p = path();
			Files.createDirectories(p.getParent());
			try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, w);
			}
		} catch (Exception ignored) {
			// Config is a convenience; never crash the game over it.
		}
	}

	private void sanitize() {
		glowQuality = Math.max(1, Math.min(3, glowQuality));
		trailMode = Math.floorMod(trailMode, 3);
		jumpStyle = Math.floorMod(jumpStyle, 4);
		ambMode = Math.floorMod(ambMode, 7);
		orbitCount = Math.max(1, Math.min(8, orbitCount));
		if (guiAnimSpeed < 0.3f) {
			guiAnimSpeed = 1f;
		}
		if (theme == null) {
			theme = "SAKURA";
		}
	}
}
