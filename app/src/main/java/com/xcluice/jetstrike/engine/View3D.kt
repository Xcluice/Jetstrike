package com.xcluice.jetstrike.engine

import android.graphics.Bitmap
import com.xcluice.jetstrike.Skin

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

/** One fighter as the GL thread needs to see it. */
class SnapEnt {
    @JvmField var id = 0
    @JvmField var skin: Skin? = null
    @JvmField var wp = "pistol"
    @JvmField var alive = false
    @JvmField var boss = false
    @JvmField var onG = false
    @JvmField var jetting = false
    @JvmField var x = 0f
    @JvmField var y = 0f
    @JvmField var vx = 0f
    @JvmField var aim = 0f
    @JvmField var run = 0f
    @JvmField var hurt = 0f
    @JvmField var sc = 1f
}

/**
 * Copy of everything worth drawing, filled by the game thread right after Game.update and read by the GL thread,
 * so the two never touch the game's own lists at the same time.
 */
object Snap3D {
    @JvmField val lock = Any()
    const val MAX_ENT = 24
    const val MAX_BUL = 400
    const val MAX_GRE = 40
    const val MAX_PICK = 64
    const val MAX_PART = 900

    @JvmField val ents: Array<SnapEnt> = makeSnapEnts(MAX_ENT)
    @JvmField var nEnt = 0
    /** x, y, vx, vy, kind, big per bullet */
    @JvmField val bul = FloatArray(MAX_BUL * 6)
    @JvmField var nBul = 0
    @JvmField val gre = FloatArray(MAX_GRE * 2)
    @JvmField var nGre = 0
    /** x, y, cooldown per pickup; type name alongside */
    @JvmField val pick = FloatArray(MAX_PICK * 3)
    @JvmField val pickT = arrayOfNulls<String>(MAX_PICK)
    @JvmField var nPick = 0
    /** x, y, life fraction, size, glow(0/1) per particle, colour in partC */
    @JvmField val part = FloatArray(MAX_PART * 5)
    @JvmField val partC = IntArray(MAX_PART)
    @JvmField var nPart = 0
    @JvmField var mapVer = 0
}

// built in a plain function: D8 chokes on an inlined Array(n) { } loop inside the object's static initializer
private fun makeSnapEnts(n: Int): Array<SnapEnt> {
    val a = ArrayList<SnapEnt>(n)
    for (i in 0 until n) a.add(SnapEnt())
    return a.toTypedArray()
}
