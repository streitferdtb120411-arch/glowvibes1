# GlowVibes 2.0 (Fabric 26.3)

Client-only visual mod: trails, jump circles, ambient particles, aura, hit effects, 5 themes, animated menu and HUD.

## Installieren ins GitHub-Repo
1. Alles im Repo `glowvibes` löschen (außer `.git`), dann den Inhalt dieses ZIPs hochladen.
   Wichtig: `gradlew`, `gradle/wrapper/` und `.github/workflows/build.yml` müssen mit hoch.
2. Actions läuft automatisch. Fertige `.jar` unter Actions → Run → Artifacts → `glowvibes-mod`.
3. Jar in `.minecraft/mods`, zusammen mit Fabric API für 26.3.

## Bedienung
- **Rechte Shift-Taste** öffnet das Menü (änderbar unter Steuerung).
- Einstellungen: `config/glowvibes.json`.

## Features
| Bereich | Optionen |
|---|---|
| Themes | Sakura, Dark, Blue, Red, Pink, Chroma-Modus, Glow-Qualität |
| Trails | Ribbon / Helix / Orbs, Länge, Breite, Höhe, Deckkraft, andere Spieler |
| Jump Circles | Solid / Dashed / Double / Pulse, Radius, Dauer, Glow-Wand, Landing-Kreise, Funken |
| Ambience | Auto (pro Theme), Petals, Fireflies, Stars, Crystals, Snow, Embers |
| Aura | Halo, kreisende Orbs (nur Third Person) |
| Combat | Hit-Funken + Schockwelle |
| HUD | Watermark mit FPS, animierte Feature-Liste |
