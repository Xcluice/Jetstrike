package com.xcluice.jetstrike.engine

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A transform in the scene graph, optionally carrying a [Mesh]. Rotation is Euler radians applied Y, then X, then Z.
 * To make something face left, rotate it by PI around Y; don't use negative scale, it flips the winding.
 * Scale should be uniform for correct lighting.
 */
open class Node(var mesh: Mesh? = null) {
    var name = ""
    var x = 0f; var y = 0f; var z = 0f
    var rx = 0f; var ry = 0f; var rz = 0f
    var sx = 1f; var sy = 1f; var sz = 1f
    /** Colour multiplier. Alpha below 1 puts the node in the sorted transparent pass. */
    var r = 1f; var g = 1f; var b = 1f; var a = 1f
    /** Adds self-lit colour: 0 = normal, 1 = glowing. Handy for hit flashes and flames. */
    var emissive = 0f
    var visible = true
    /** Set false for things that should never be culled (very large meshes that are always on screen). */
    var cull = true

    var parent: Node? = null
        private set
    val children = ArrayList<Node>()

    /** World matrix, refreshed by [updateWorld] every frame. */
    val world = FloatArray(16)
    @JvmField var sortKey = 0f

    fun add(c: Node): Node {
        c.parent?.children?.remove(c)
        c.parent = this
        children.add(c)
        return c
    }

    fun remove(c: Node) {
        if (children.remove(c)) c.parent = null
    }

    fun find(n: String): Node? {
        if (name == n) return this
        for (c in children) c.find(n)?.let { return it }
        return null
    }

    fun tint(argb: Int) {
        a = ((argb ushr 24) and 255) / 255f
        r = ((argb shr 16) and 255) / 255f
        g = ((argb shr 8) and 255) / 255f
        b = (argb and 255) / 255f
    }

    fun setPos(px: Float, py: Float, pz: Float) { x = px; y = py; z = pz }

    fun updateWorld(pw: FloatArray?) {
        val cx = cos(rx); val sxn = sin(rx)
        val cy = cos(ry); val syn = sin(ry)
        val cz = cos(rz); val szn = sin(rz)
        val r00 = cy * cz + syn * sxn * szn; val r01 = -cy * szn + syn * sxn * cz; val r02 = syn * cx
        val r10 = cx * szn; val r11 = cx * cz; val r12 = -sxn
        val r20 = -syn * cz + cy * sxn * szn; val r21 = syn * szn + cy * sxn * cz; val r22 = cy * cx
        val w = world
        if (pw == null) {
            w[0] = r00 * sx; w[1] = r10 * sx; w[2] = r20 * sx; w[3] = 0f
            w[4] = r01 * sy; w[5] = r11 * sy; w[6] = r21 * sy; w[7] = 0f
            w[8] = r02 * sz; w[9] = r12 * sz; w[10] = r22 * sz; w[11] = 0f
            w[12] = x; w[13] = y; w[14] = z; w[15] = 1f
        } else {
            val l = tmp
            l[0] = r00 * sx; l[1] = r10 * sx; l[2] = r20 * sx; l[3] = 0f
            l[4] = r01 * sy; l[5] = r11 * sy; l[6] = r21 * sy; l[7] = 0f
            l[8] = r02 * sz; l[9] = r12 * sz; l[10] = r22 * sz; l[11] = 0f
            l[12] = x; l[13] = y; l[14] = z; l[15] = 1f
            android.opengl.Matrix.multiplyMM(w, 0, pw, 0, l, 0)
        }
        for (i in 0 until children.size) children[i].updateWorld(w)
    }

    private companion object {
        // updateWorld only ever runs on the GL thread, so one shared scratch matrix is safe
        val tmp = FloatArray(16)
    }
}

/** Everything the renderer needs besides the camera: node tree, lighting, fog, sky and particles. */
class Scene {
    val root = Node()

    /** Direction pointing TOWARD the sun. Setting it normalizes it. */
    var sunDir = floatArrayOf(0.35f, 0.8f, 0.5f)
        set(v) {
            val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            field = floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
        }
    var sunColor = rgb(0xFFFFF1D6.toInt(), 0.85f)
    var ambientSky = rgb(0xFF8FA9D6.toInt(), 0.55f)
    var ambientGround = rgb(0xFF4A4036.toInt(), 0.45f)
    var fogColor = rgb(0xFF9DB7DE.toInt(), 1f)
    var fogStart = 40f
    var fogEnd = 160f
    var skyTop = rgb(0xFF4F86D6.toInt(), 1f)
    var skyBottom = rgb(0xFFBFD8F2.toInt(), 1f)

    val particles = ParticleSystem(2500, false)
    /** Additive particles: fire, sparks, muzzle flashes. */
    val glow = ParticleSystem(2500, true)

    fun add(n: Node): Node = root.add(n)

    fun clear() { root.children.toList().forEach { root.remove(it) } }

    companion object {
        fun rgb(argb: Int, k: Float = 1f): FloatArray = floatArrayOf(
            ((argb shr 16) and 255) / 255f * k,
            ((argb shr 8) and 255) / 255f * k,
            (argb and 255) / 255f * k
        )
    }
}
