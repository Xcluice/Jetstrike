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
