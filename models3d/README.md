# Jetstrike 3D models

3D versions of every gun and fighter in the game, built procedurally with Three.js (no asset files).

- **Guns** (`models.js`): pistol (revolver), SMG (AK), assault rifle, shotgun, sniper, rocket launcher, flamethrower, laser rifle and the grenade. Each one is traced rectangle by rectangle from its sprite in `app/src/main/res/drawable-nodpi`, so silhouette, colours and details (drum, bipod, scope, vents, yellow band, tank) match.
- **Fighters**: Nova, Blaze, Frost, Viper, Sol, Bloom, Onyx, Rust and the Overlord boss. Body proportions come from the `Hero` class in `Game.kt`; gear (antenna, mohawk, halo, cape, scarf, horns and so on) follows `GameView.kt`. Each fighter can hold any of the guns, has a working jetpack with flames, and can walk.

## Run it

Serve the folder over HTTP (ES modules do not load from `file://`):

```
cd models3d
python3 -m http.server 8080
```

Open `http://localhost:8080`. Drag to orbit, pinch to zoom. The download button exports the current model as a `.glb`.

Three.js loads from jsDelivr, so the page needs an internet connection.
