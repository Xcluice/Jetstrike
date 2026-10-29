package com.xcluice.jetstrike.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Pooled point-sprite particles. Emitting past capacity silently drops the extra particles. */
class ParticleSystem(private val cap: Int, val additive: Boolean) {
    private val pos = FloatArray(cap * 3)
    private val vel = FloatArray(cap * 3)
    private val life = FloatArray(cap)
    private val maxLife = FloatArray(cap)
    private val s0 = FloatArray(cap)
    private val s1 = FloatArray(cap)
    private val grav = FloatArray(cap)
    private val c0 = IntArray(cap)
    private val c1 = IntArray(cap)
    private val out = FloatArray(cap * 8)
    private val fb = ByteBuffer.allocateDirect(cap * 8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    var count = 0
        private set

    fun emit(
        x: Float, y: Float, z: Float, vx: Float, vy: Float, vz: Float,
        lifeSec: Float, size0: Float, size1: Float, color0: Int, color1: Int, gravity: Float = 0f
    ) {
        if (count >= cap) return
        val i = count++
        pos[i * 3] = x; pos[i * 3 + 1] = y; pos[i * 3 + 2] = z
        vel[i * 3] = vx; vel[i * 3 + 1] = vy; vel[i * 3 + 2] = vz
        life[i] = lifeSec; maxLife[i] = lifeSec
        s0[i] = size0; s1[i] = size1
        c0[i] = color0; c1[i] = color1
        grav[i] = gravity
    }

    /** Sprays [n] particles in random directions (a ball), [speed] +/- 40%. */
    fun burst(
        x: Float, y: Float, z: Float, n: Int, speed: Float, lifeSec: Float, size: Float,
        color0: Int, color1: Int, gravity: Float = 0f
    ) {
        repeat(n) {
            var dx: Float; var dy: Float; var dz: Float
            do {
                dx = Random.nextFloat() * 2 - 1; dy = Random.nextFloat() * 2 - 1; dz = Random.nextFloat() * 2 - 1
            } while (dx * dx + dy * dy + dz * dz > 1f)
            val s = speed * (0.6f + Random.nextFloat() * 0.8f)
            emit(x, y, z, dx * s, dy * s, dz * s, lifeSec * (0.7f + Random.nextFloat() * 0.6f), size, size * 0.2f, color0, color1, gravity)
        }
    }

    /** A narrow cone of particles along (dx, dy, dz): muzzle flashes, flames, jet exhaust. */
    fun jet(
        x: Float, y: Float, z: Float, dx: Float, dy: Float, dz: Float, n: Int, speed: Float, spread: Float,
        lifeSec: Float, size0: Float, size1: Float, color0: Int, color1: Int, gravity: Float = 0f
    ) {
        repeat(n) {
            val s = speed * (0.7f + Random.nextFloat() * 0.6f)
            emit(
                x, y, z,
                dx * s + (Random.nextFloat() - .5f) * spread * speed,
                dy * s + (Random.nextFloat() - .5f) * spread * speed,
                dz * s + (Random.nextFloat() - .5f) * spread * speed,
                lifeSec * (0.7f + Random.nextFloat() * 0.6f), size0, size1, color0, color1, gravity
            )
        }
    }

    fun update(dt: Float) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                val l = --count
                if (i != l) move(l, i)
                continue
            }
            vel[i * 3 + 1] -= grav[i] * dt
            pos[i * 3] += vel[i * 3] * dt
            pos[i * 3 + 1] += vel[i * 3 + 1] * dt
            pos[i * 3 + 2] += vel[i * 3 + 2] * dt
            i++
        }
    }

    private fun move(from: Int, to: Int) {
        for (k in 0 until 3) { pos[to * 3 + k] = pos[from * 3 + k]; vel[to * 3 + k] = vel[from * 3 + k] }
        life[to] = life[from]; maxLife[to] = maxLife[from]
        s0[to] = s0[from]; s1[to] = s1[from]; grav[to] = grav[from]
        c0[to] = c0[from]; c1[to] = c1[from]
    }

    fun draw(p: Program) {
        if (count == 0) return
        for (i in 0 until count) {
            val t = 1f - life[i] / maxLife[i]
            val o = i * 8
            out[o] = pos[i * 3]; out[o + 1] = pos[i * 3 + 1]; out[o + 2] = pos[i * 3 + 2]
            val a = c0[i]; val b = c1[i]
            out[o + 3] = lerp(((a shr 16) and 255) / 255f, ((b shr 16) and 255) / 255f, t)
            out[o + 4] = lerp(((a shr 8) and 255) / 255f, ((b shr 8) and 255) / 255f, t)
            out[o + 5] = lerp((a and 255) / 255f, (b and 255) / 255f, t)
            out[o + 6] = lerp(((a ushr 24) and 255) / 255f, ((b ushr 24) and 255) / 255f, t)
            out[o + 7] = lerp(s0[i], s1[i], t)
        }
        fb.clear()
        fb.put(out, 0, count * 8)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        val ap = p.a("aPos"); val ac = p.a("aColor"); val asz = p.a("aSize")
        fb.position(0)
        GLES20.glEnableVertexAttribArray(ap)
        GLES20.glVertexAttribPointer(ap, 3, GLES20.GL_FLOAT, false, 32, fb)
        fb.position(3)
        GLES20.glEnableVertexAttribArray(ac)
        GLES20.glVertexAttribPointer(ac, 4, GLES20.GL_FLOAT, false, 32, fb)
        fb.position(7)
        GLES20.glEnableVertexAttribArray(asz)
        GLES20.glVertexAttribPointer(asz, 1, GLES20.GL_FLOAT, false, 32, fb)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
