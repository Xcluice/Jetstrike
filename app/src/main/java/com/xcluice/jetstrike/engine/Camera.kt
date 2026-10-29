package com.xcluice.jetstrike.engine

import android.opengl.Matrix
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Orbit/follow camera. It looks at a smoothed target from [distance] away along ([yaw], [pitch]).
 * yaw = 0, pitch = 0 is the classic side-scroller view (looking down -z). Raise pitch or yaw to swing into true 3D.
 * [distance] is also the "range": the scope button maps to [zoomRange].
 */
class Camera {
    var fov = 50f
    var near = 0.5f
    var far = 400f

    var yaw = 0f
    var pitch = 0.12f
    /** How quickly the camera chases its goals. Higher = tighter. */
    var smooth = 7f

    var goalX = 0f; var goalY = 0f; var goalZ = 0f
    var goalDistance = 16f
    var tx = 0f; var ty = 0f; var tz = 0f
    var distance = 16f

    var ex = 0f
        private set
    var ey = 0f
        private set
    var ez = 0f
        private set

    val view = FloatArray(16)
    val proj = FloatArray(16)
    val viewProj = FloatArray(16)
    private val invVP = FloatArray(16)
    private val vin = FloatArray(4)
    private val va = FloatArray(4)
    private val vb = FloatArray(4)

    private var shakeAmt = 0f

    fun follow(x: Float, y: Float, z: Float = 0f) { goalX = x; goalY = y; goalZ = z }

    /** Jump straight to the goal with no smoothing (level start, respawn). */
    fun snap() { tx = goalX; ty = goalY; tz = goalZ; distance = goalDistance }

    /** Scope / range: factor > 1 pulls back to see more of the map, < 1 zooms in. */
    fun zoomRange(factor: Float, min: Float = 6f, max: Float = 70f) {
        goalDistance = (goalDistance * factor).coerceIn(min, max)
    }

    fun orbit(dYaw: Float, dPitch: Float) {
        yaw += dYaw
        pitch = (pitch + dPitch).coerceIn(-0.35f, 1.3f)
    }

    fun shake(amount: Float) { shakeAmt = maxOf(shakeAmt, amount) }

    fun update(dt: Float, aspect: Float) {
        val k = 1f - exp(-smooth * dt)
        tx += (goalX - tx) * k; ty += (goalY - ty) * k; tz += (goalZ - tz) * k
        distance += (goalDistance - distance) * k
        shakeAmt = if (shakeAmt < 0.002f) 0f else shakeAmt * exp(-9f * dt)
        val sx = if (shakeAmt > 0f) (Random.nextFloat() - .5f) * shakeAmt else 0f
        val sy = if (shakeAmt > 0f) (Random.nextFloat() - .5f) * shakeAmt else 0f
        val cp = cos(pitch)
        ex = tx + distance * sin(yaw) * cp + sx
        ey = ty + distance * sin(pitch) + sy
        ez = tz + distance * cos(yaw) * cp
        Matrix.setLookAtM(view, 0, ex, ey, ez, tx + sx, ty + sy, tz, 0f, 1f, 0f)
        Matrix.perspectiveM(proj, 0, fov, aspect, near, far)
        Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)
        Matrix.invertM(invVP, 0, viewProj, 0)
    }

    /**
     * Casts a ray through the screen point and intersects it with the plane z = [planeZ].
     * Writes world x, y, z into [out] and returns false if the ray is parallel to the plane.
     * This is how touch aiming maps onto the 2.5D gameplay plane (planeZ = 0).
     */
    fun screenToPlane(sx: Float, sy: Float, vw: Int, vh: Int, planeZ: Float, out: FloatArray): Boolean {
        val nx = 2f * sx / vw - 1f
        val ny = 1f - 2f * sy / vh
        vin[0] = nx; vin[1] = ny; vin[2] = -1f; vin[3] = 1f
        Matrix.multiplyMV(va, 0, invVP, 0, vin, 0)
        vin[2] = 1f
        Matrix.multiplyMV(vb, 0, invVP, 0, vin, 0)
        for (a in arrayOf(va, vb)) { a[0] /= a[3]; a[1] /= a[3]; a[2] /= a[3] }
        val dz = vb[2] - va[2]
        if (abs(dz) < 1e-6f) return false
        val t = (planeZ - va[2]) / dz
        out[0] = va[0] + (vb[0] - va[0]) * t
        out[1] = va[1] + (vb[1] - va[1]) * t
        out[2] = planeZ
        return true
    }
}
