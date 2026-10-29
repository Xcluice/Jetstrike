package com.xcluice.jetstrike.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Static triangle mesh. Interleaved layout per vertex: position(3) normal(3) colour(4) = 10 floats, no index buffer.
 * The vertex data stays on the CPU so the mesh can re-upload itself after a GL context loss.
 */
class Mesh(private val data: FloatArray, val vertexCount: Int) {
    var cx = 0f
        private set
    var cy = 0f
        private set
    var cz = 0f
        private set
    /** Bounding sphere around (cx, cy, cz), used for frustum culling. */
    var radius = 0f
        private set

    private var vbo = 0
    private var epoch = -1

    init {
        if (vertexCount > 0) {
            var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var z0 = Float.MAX_VALUE
            var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE; var z1 = -Float.MAX_VALUE
            for (i in 0 until vertexCount) {
                val o = i * STRIDE
                x0 = minOf(x0, data[o]); x1 = maxOf(x1, data[o])
                y0 = minOf(y0, data[o + 1]); y1 = maxOf(y1, data[o + 1])
                z0 = minOf(z0, data[o + 2]); z1 = maxOf(z1, data[o + 2])
            }
            cx = (x0 + x1) / 2; cy = (y0 + y1) / 2; cz = (z0 + z1) / 2
            val dx = x1 - cx; val dy = y1 - cy; val dz = z1 - cz
            radius = sqrt(dx * dx + dy * dy + dz * dz)
        }
    }

    private fun upload() {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        val fb = ByteBuffer.allocateDirect(vertexCount * STRIDE * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(data, 0, vertexCount * STRIDE).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexCount * STRIDE * 4, fb, GLES20.GL_STATIC_DRAW)
        epoch = GL.epoch
    }

    fun draw(p: Program) {
        if (vertexCount == 0) return
        if (vbo == 0 || epoch != GL.epoch) upload()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        val aPos = p.a("aPos"); val aNor = p.a("aNormal"); val aCol = p.a("aColor")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, STRIDE * 4, 0)
        GLES20.glEnableVertexAttribArray(aNor)
        GLES20.glVertexAttribPointer(aNor, 3, GLES20.GL_FLOAT, false, STRIDE * 4, 12)
        GLES20.glEnableVertexAttribArray(aCol)
        GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_FLOAT, false, STRIDE * 4, 24)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
    }

    /** Frees the GPU buffer (only call on the GL thread). The mesh re-uploads if drawn again. */
    fun release() {
        if (vbo != 0 && epoch == GL.epoch) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
        vbo = 0
    }

    companion object { const val STRIDE = 10 }
}

/**
 * Builds meshes from primitives. Every triangle fixes its own winding from the normal you pass in, so primitives
 * can't come out inside-out. Set a colour with [color], then add shapes; colours are baked into the vertices.
 */
class MeshBuilder {
    private var d = FloatArray(Mesh.STRIDE * 3 * 512)
    private var n = 0
    private var cr = 1f; private var cg = 1f; private var cb = 1f; private var ca = 1f

    val vertexCount: Int get() = n / Mesh.STRIDE

    fun color(argb: Int): MeshBuilder {
        ca = ((argb ushr 24) and 255) / 255f
        cr = ((argb shr 16) and 255) / 255f
        cg = ((argb shr 8) and 255) / 255f
        cb = (argb and 255) / 255f
        if (ca == 0f) ca = 1f
        return this
    }

    fun color(r: Float, g: Float, b: Float, a: Float = 1f): MeshBuilder { cr = r; cg = g; cb = b; ca = a; return this }

    /** Shades the current colour: 1 = unchanged, <1 darker, >1 lighter. */
    fun shade(argb: Int, k: Float): MeshBuilder {
        color(argb)
        cr = (cr * k).coerceIn(0f, 1f); cg = (cg * k).coerceIn(0f, 1f); cb = (cb * k).coerceIn(0f, 1f)
        return this
    }

    private fun put(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
        if (n + Mesh.STRIDE > d.size) d = d.copyOf(d.size * 2)
        d[n++] = x; d[n++] = y; d[n++] = z
        d[n++] = nx; d[n++] = ny; d[n++] = nz
        d[n++] = cr; d[n++] = cg; d[n++] = cb; d[n++] = ca
    }

    /** One flat-shaded triangle; (hx, hy, hz) is the direction the face must point to. */
    fun flatTri(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float,
        hx: Float, hy: Float, hz: Float
    ) {
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val vx = cx - ax; val vy = cy - ay; val vz = cz - az
        var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
        val len = sqrt(nx * nx + ny * ny + nz * nz)
        if (len < 1e-12f) return
        nx /= len; ny /= len; nz /= len
        if (nx * hx + ny * hy + nz * hz < 0f) {
            put(ax, ay, az, -nx, -ny, -nz); put(cx, cy, cz, -nx, -ny, -nz); put(bx, by, bz, -nx, -ny, -nz)
        } else {
            put(ax, ay, az, nx, ny, nz); put(bx, by, bz, nx, ny, nz); put(cx, cy, cz, nx, ny, nz)
        }
    }

    fun quad(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float,
        hx: Float, hy: Float, hz: Float
    ) {
        flatTri(ax, ay, az, bx, by, bz, cx, cy, cz, hx, hy, hz)
        flatTri(ax, ay, az, cx, cy, cz, dx, dy, dz, hx, hy, hz)
    }

    /** Smooth-shaded triangle with per-vertex normals. */
    private fun smoothTri(p: FloatArray, nr: FloatArray) {
        val ux = p[3] - p[0]; val uy = p[4] - p[1]; val uz = p[5] - p[2]
        val vx = p[6] - p[0]; val vy = p[7] - p[1]; val vz = p[8] - p[2]
        val gx = uy * vz - uz * vy; val gy = uz * vx - ux * vz; val gz = ux * vy - uy * vx
        if (gx * gx + gy * gy + gz * gz < 1e-18f) return
        val dot = gx * (nr[0] + nr[3] + nr[6]) + gy * (nr[1] + nr[4] + nr[7]) + gz * (nr[2] + nr[5] + nr[8])
        if (dot < 0f) {
            put(p[0], p[1], p[2], nr[0], nr[1], nr[2]); put(p[6], p[7], p[8], nr[6], nr[7], nr[8]); put(p[3], p[4], p[5], nr[3], nr[4], nr[5])
        } else {
            put(p[0], p[1], p[2], nr[0], nr[1], nr[2]); put(p[3], p[4], p[5], nr[3], nr[4], nr[5]); put(p[6], p[7], p[8], nr[6], nr[7], nr[8])
        }
    }

    fun box(cx: Float, cy: Float, cz: Float, w: Float, h: Float, dp: Float): MeshBuilder {
        val x0 = cx - w / 2; val x1 = cx + w / 2
        val y0 = cy - h / 2; val y1 = cy + h / 2
        val z0 = cz - dp / 2; val z1 = cz + dp / 2
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0f, 0f, 1f)
        quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0f, 0f, -1f)
        quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1f, 0f, 0f)
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1f, 0f, 0f)
        quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0f, 1f, 0f)
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0f, -1f, 0f)
        return this
    }

    fun sphere(cx: Float, cy: Float, cz: Float, r: Float, seg: Int = 12, rings: Int = 8): MeshBuilder {
        val p = FloatArray(9); val nr = FloatArray(9)
        fun setV(i: Int, t: Double, ph: Double) {
            val sx = (sin(t) * cos(ph)).toFloat(); val sy = cos(t).toFloat(); val sz = (sin(t) * sin(ph)).toFloat()
            nr[i * 3] = sx; nr[i * 3 + 1] = sy; nr[i * 3 + 2] = sz
            p[i * 3] = cx + sx * r; p[i * 3 + 1] = cy + sy * r; p[i * 3 + 2] = cz + sz * r
        }
        for (i in 0 until rings) {
            val t0 = PI * i / rings; val t1 = PI * (i + 1) / rings
            for (j in 0 until seg) {
                val p0 = 2 * PI * j / seg; val p1 = 2 * PI * (j + 1) / seg
                setV(0, t0, p0); setV(1, t0, p1); setV(2, t1, p1); smoothTri(p, nr)
                setV(0, t0, p0); setV(1, t1, p1); setV(2, t1, p0); smoothTri(p, nr)
            }
        }
        return this
    }

    /** Vertical cylinder or cone frustum from y = cy up to cy + height. */
    fun cylinder(
        cx: Float, cy: Float, cz: Float, rBottom: Float, rTop: Float, height: Float,
        seg: Int = 12, capTop: Boolean = true, capBottom: Boolean = true
    ): MeshBuilder {
        val p = FloatArray(9); val nr = FloatArray(9)
        val slope = (rBottom - rTop) / max(height, 1e-6f)
        val top = cy + height
        for (j in 0 until seg) {
            val a0 = 2 * PI * j / seg; val a1 = 2 * PI * (j + 1) / seg
            val c0 = cos(a0).toFloat(); val s0 = sin(a0).toFloat(); val c1 = cos(a1).toFloat(); val s1 = sin(a1).toFloat()
            fun nrm(i: Int, c: Float, s: Float) {
                val l = sqrt(c * c + slope * slope + s * s)
                nr[i * 3] = c / l; nr[i * 3 + 1] = slope / l; nr[i * 3 + 2] = s / l
            }
            fun pt(i: Int, x: Float, y: Float, z: Float) { p[i * 3] = x; p[i * 3 + 1] = y; p[i * 3 + 2] = z }
            pt(0, cx + c0 * rBottom, cy, cz + s0 * rBottom); nrm(0, c0, s0)
            pt(1, cx + c1 * rBottom, cy, cz + s1 * rBottom); nrm(1, c1, s1)
            pt(2, cx + c1 * rTop, top, cz + s1 * rTop); nrm(2, c1, s1)
            smoothTri(p, nr)
            pt(0, cx + c0 * rBottom, cy, cz + s0 * rBottom); nrm(0, c0, s0)
            pt(1, cx + c1 * rTop, top, cz + s1 * rTop); nrm(1, c1, s1)
            pt(2, cx + c0 * rTop, top, cz + s0 * rTop); nrm(2, c0, s0)
            smoothTri(p, nr)
            if (capTop && rTop > 0f) flatTri(cx, top, cz, cx + c0 * rTop, top, cz + s0 * rTop, cx + c1 * rTop, top, cz + s1 * rTop, 0f, 1f, 0f)
            if (capBottom && rBottom > 0f) flatTri(cx, cy, cz, cx + c0 * rBottom, cy, cz + s0 * rBottom, cx + c1 * rBottom, cy, cz + s1 * rBottom, 0f, -1f, 0f)
        }
        return this
    }

    /**
     * Extrudes a 2D outline (x,y pairs, any winding, may be concave) along z from [z0] to [z1].
     * Front cap faces +z, back cap faces -z, and every edge gets a flat side wall.
     */
    fun extrude(outline: FloatArray, z0: Float, z1: Float, caps: Boolean = true, sides: Boolean = true): MeshBuilder {
        val poly = dedupe(outline)
        val cnt = poly.size / 2
        if (cnt < 3) return this
        var area = 0f
        for (i in 0 until cnt) {
            val j = (i + 1) % cnt
            area += poly[i * 2] * poly[j * 2 + 1] - poly[j * 2] * poly[i * 2 + 1]
        }
        val ccw = area > 0f
        if (caps) {
            val t = triangulate(poly, ccw)
            var i = 0
            while (i < t.size) {
                val a = t[i] * 2; val b = t[i + 1] * 2; val c = t[i + 2] * 2
                flatTri(poly[a], poly[a + 1], z1, poly[b], poly[b + 1], z1, poly[c], poly[c + 1], z1, 0f, 0f, 1f)
                flatTri(poly[a], poly[a + 1], z0, poly[b], poly[b + 1], z0, poly[c], poly[c + 1], z0, 0f, 0f, -1f)
                i += 3
            }
        }
        if (sides) {
            for (i in 0 until cnt) {
                val j = (i + 1) % cnt
                val x0 = poly[i * 2]; val y0 = poly[i * 2 + 1]; val x1 = poly[j * 2]; val y1 = poly[j * 2 + 1]
                val ex = x1 - x0; val ey = y1 - y0
                val hx = if (ccw) ey else -ey
                val hy = if (ccw) -ex else ex
                quad(x0, y0, z0, x1, y1, z0, x1, y1, z1, x0, y0, z1, hx, hy, 0f)
            }
        }
        return this
    }

    fun build(): Mesh = Mesh(d.copyOf(n), n / Mesh.STRIDE)

    companion object {
        private fun dedupe(p: FloatArray): FloatArray {
            val out = FloatArray(p.size)
            var m = 0
            var i = 0
            while (i < p.size - 1) {
                val x = p[i]; val y = p[i + 1]
                if (m == 0 || kotlin.math.abs(x - out[m - 2]) + kotlin.math.abs(y - out[m - 1]) > 1e-4f) { out[m++] = x; out[m++] = y }
                i += 2
            }
            if (m >= 4 && kotlin.math.abs(out[0] - out[m - 2]) + kotlin.math.abs(out[1] - out[m - 1]) <= 1e-4f) m -= 2
            return out.copyOf(m)
        }

        /** Ear clipping. Always terminates: if no ear exists (self-intersecting input) it clips a vertex anyway. */
        private fun triangulate(p: FloatArray, ccw: Boolean): IntArray {
            val n = p.size / 2
            val idx = ArrayList<Int>(n)
            if (ccw) for (i in 0 until n) idx.add(i) else for (i in n - 1 downTo 0) idx.add(i)
            val out = ArrayList<Int>((n - 2) * 3)
            var k = 0
            var fails = 0
            while (idx.size > 3) {
                val m = idx.size
                if (k >= m) k = 0
                val ia = idx[(k + m - 1) % m]; val ib = idx[k]; val ic = idx[(k + 1) % m]
                var ear = convex(p, ia, ib, ic)
                if (ear) {
                    for (t in 0 until m) {
                        val ip = idx[t]
                        if (ip == ia || ip == ib || ip == ic) continue
                        if (inside(p, ip, ia, ib, ic)) { ear = false; break }
                    }
                }
                if (ear || fails > m) {
                    out.add(ia); out.add(ib); out.add(ic)
                    idx.removeAt(k)
                    fails = 0
                    if (k > 0) k--
                } else {
                    fails++
                    k = (k + 1) % m
                }
            }
            if (idx.size == 3) { out.add(idx[0]); out.add(idx[1]); out.add(idx[2]) }
            return out.toIntArray()
        }

        private fun convex(p: FloatArray, a: Int, b: Int, c: Int): Boolean {
            val abx = p[b * 2] - p[a * 2]; val aby = p[b * 2 + 1] - p[a * 2 + 1]
            val bcx = p[c * 2] - p[b * 2]; val bcy = p[c * 2 + 1] - p[b * 2 + 1]
            return abx * bcy - aby * bcx > 1e-9f
        }

        private fun inside(p: FloatArray, q: Int, a: Int, b: Int, c: Int): Boolean {
            val px = p[q * 2]; val py = p[q * 2 + 1]
            val d1 = side(px, py, p[a * 2], p[a * 2 + 1], p[b * 2], p[b * 2 + 1])
            val d2 = side(px, py, p[b * 2], p[b * 2 + 1], p[c * 2], p[c * 2 + 1])
            val d3 = side(px, py, p[c * 2], p[c * 2 + 1], p[a * 2], p[a * 2 + 1])
            return d1 >= 0f && d2 >= 0f && d3 >= 0f
        }

        private fun side(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float =
            (bx - ax) * (py - ay) - (by - ay) * (px - ax)
    }
}
