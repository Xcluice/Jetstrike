package com.xcluice.jetstrike.engine

import android.graphics.Bitmap

/**
 * Hand-off between the 2D game view (which draws soldiers, terrain art, bullets and the whole HUD on a transparent
 * layer) and the OpenGL layer underneath (background, extruded terrain depth, scenery).
 * The game thread writes, the GL thread reads.
 */
object View3D {
    /** Camera target (world units), distance and tilt, computed by GameView so both layers use the exact same camera. */
    @Volatile @JvmField var tx = 0f
    @Volatile @JvmField var ty = 0f
    @Volatile @JvmField var dist = 18f
    @Volatile @JvmField var yaw = 0f
    @Volatile @JvmField var pitch = 0.12f
    @Volatile @JvmField var active = false

    /** The 2D game's sky/sun/cloud/mountain background, baked small and refreshed ~30 times a second. */
    @Volatile @JvmField var bg: Bitmap? = null
    @Volatile @JvmField var bgVer = 0
}
