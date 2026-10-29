# Jetstrike

Original jetpack deathmatch for Android, written in Kotlin with a plain Canvas game loop (no engine, no extra libraries).

- Landscape only, fullscreen
- Twin-stick touch controls: left stick moves and flies (push up), right stick aims and fires
- 4 bots, 3 skill levels, pickups, 4 weapons, first to 12 kills
- Synthesized sound effects, no audio assets

## Build

Every push to `main` runs the **Build release APK** workflow and publishes `Jetstrike.apk` under Releases.

Local build: `gradle assembleRelease` (needs JDK 17 and the Android SDK). The workflow decodes the release key from `signing/release.keystore.b64` into `app/release.keystore`.

The bundled key is for sideloading only. Use your own key before publishing to a store.


## v2

- **LAN multiplayer**: one phone taps HOST LAN, others tap JOIN LAN on the same Wi-Fi or hotspot. Team mode (up to 3 humans vs 3 bots) or Solo free-for-all (up to 4 humans plus bots).
- Two weapon slots with a tap-to-swap bar, grenades (players only), floating damage numbers.
- Sticks only respond on their own pads; dragging anywhere else pans the camera.

## Story mode
Offline only. Fight four waves, each on a higher tier of the map; clearing a wave teleports you up. The top tier holds the Overlord, a giant boss with rocket volleys, a flamethrower sweep, a slam and a charge, who also calls reinforcements. Dying restarts from wave 1 with no progress kept.

## Characters
Each of the 8 fighters has its own body shape and abilities (the old build only recolored one soldier). Pick one on the menu with the arrows.

| Name | Role | Ability | Body |
|---|---|---|---|
| Nova | Scout | 25% less jetpack fuel use, 15% faster reload | average |
| Blaze | Bruiser | +30% damage, 10% slower reload, 90 HP | short, wide, square head |
| Frost | Tank | 150 HP, takes 25% less damage, 12% slower, heavier jetpack use | big and broad |
| Viper | Assassin | 18% faster, +10% damage, heals 25% of damage dealt, 80 HP | tall and thin |
| Sol | Medic | Regenerates 5 HP/s after 3 s without being hit, 110 HP, -10% damage | round with a big head |
| Bloom | Pilot | Jetpack burns 55% less fuel and refills 2.2x faster, 8% faster, 85 HP | tiny, oversized jetpack |
| Onyx | Marksman | +55% damage with Sniper and Laser, 10% armor, 90 HP | tall, lean, angular |
| Rust | Berserker | Up to +80% damage as health drops, 105 HP, 5% faster | stocky with a cape |

Max HP differs per fighter, bullet hit areas follow each body shape (big targets are easier to hit, small ones harder), and bots and story-mode enemies use the same abilities. All of it is in the `Hero` class in `Game.kt`.

### Unlocking characters
Only Nova is available at first. The rest unlock from lifetime milestones, saved on the device (kills and wins count for the local player; story waves keep your best).

| Character | Milestone |
|---|---|
| Nova | Starter |
| Blaze | Get 20 kills |
| Frost | Win a match |
| Viper | Get 75 kills |
| Sol | Play 10 matches |
| Bloom | Clear 2 story waves |
| Onyx | Win 5 matches |
| Rust | Defeat the Overlord in Story |

Locked characters can still be previewed on the menu, with their requirement and progress shown. The milestones live in `Progress.kt`.

### Map
Natural rock edges are smoothed (relaxed outline plus curved edges, evenly spaced grass), and the collision grid has spikes under about 10 units removed and pits narrower than a soldier filled in, so nobody gets wedged in small notches.


## 3D branch (`3d`)

The `engine` package is a from-scratch OpenGL ES 2.0 engine in plain Kotlin, no libraries. It does not touch the 2D game yet; open the second launcher icon, **Jetstrike 3D**, to see the test scene (drag = orbit, pinch = range, double tap = switch map).

| File | What it does |
|---|---|
| `Engine.kt` | `Engine`, `GameLogic` callbacks, `Engine3DView` (GLSurfaceView) and the renderer: sky, frustum culling, opaque then sorted transparent pass, particles |
| `Scene.kt` | `Node` scene graph (position, Euler rotation, scale, tint, emissive, children) and `Scene` (sun, ambient, fog, sky, particles) |
| `Camera.kt` | follow/orbit camera, smoothing, shake, `zoomRange` for the scope button, `screenToPlane` to turn touches into gameplay-plane positions |
| `Mesh.kt` | `Mesh` plus `MeshBuilder`: box, sphere, cylinder/cone, and concave polygon extrusion (ear clipping) with auto-fixed winding |
| `Particles.kt` | pooled point-sprite particles, alpha and additive systems, `burst` and `jet` emitters |
| `Shaders.kt` | lit vertex-colour shader (sun + hemisphere + fog), point sprites, sky gradient |
| `Terrain3D.kt` | extrudes the existing `Terrain` polygons into a 3D level; world units are map pixels x 0.02, y up, gameplay plane z = 0 |
| `Prefabs.kt` | rigged low-poly soldier, crate and tracer |

Plan: keep `Game.kt` as the simulation (it is already 2D physics on a collision grid), and replace the Canvas drawing in `GameView.kt` with `Node`s driven from `Ent`, `Bullet`, `Grenade` and `Particle`.

### Jetstrike 3D (playable)
`engine/Game3D.kt` runs the real `Game` simulation and draws it with the engine: 3D soldiers, extruded map, glowing bullets, 3D explosions/sparks, name tags and a touch HUD (floating move/aim sticks, grenade, swap, reload, pick, camera-view button, drag the middle of the screen to orbit). Pause menu starts a new team match or Story mode.
