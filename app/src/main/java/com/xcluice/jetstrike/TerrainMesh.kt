package com.xcluice.jetstrike

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds the 3D level from the same traced polygons the 2D game uses for collision, so what you see is what you
 * stand on. Solids (rock, concrete, wood) are extruded into the screen; cave and room backdrops are flat slabs at
 * the back, seen through the openings. Scene space: x = game x, y = -game y, z = 0 is the soldier plane.
 */
object TerrainMesh {
    const val Z_FRONT = -14f
    const val Z_BACK = -190f
    private const val UVS = 1f / 90f

    class Result(val solid: Mesh3, val back: Mesh3, val glass: Mesh3, val ridges: List<Mesh3>)

    /** Smoothed polygons follow the same curve the 2D renderer draws (through the edge mid points). */
    private fun outline(pl: TPoly): FloatArray {
        val q = pl.pts
        val n = q.size / 2
        if (!pl.smooth || n < 3) return q
        val out = ArrayList<Float>()
        fun mid(a: Int, b: Int, k: Int) = (q[a * 2 + k] + q[b * 2 + k]) / 2f
        for (i in 0 until n) {
            val a = i
            val b = (i + 1) % n
            val c = (i + 2) % n
            val sx = mid(a, b, 0); val sy = mid(a, b, 1)
            val ex = mid(b, c, 0); val ey = mid(b, c, 1)
            val cx = q[b * 2]; val cy = q[b * 2 + 1]
            val steps = 3
            for (s in 0 until steps) {
                val t = s / steps.toFloat()
                val u = 1f - t
                out.add(u * u * sx + 2f * u * t * cx + t * t * ex)
                out.add(u * u * sy + 2f * u * t * cy + t * t * ey)
            }
        }
        return out.toFloatArray()
    }

    private fun area(p: FloatArray): Float {
        var a = 0f
        val n = p.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            a += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1]
        }
        return a / 2f
    }

    /** Ear clipping. Returns index triples; on self-intersecting input it clips the sharpest remaining corner. */
    private fun triangulate(p: FloatArray): IntArray {
        val n = p.size / 2
        if (n < 3) return IntArray(0)
        val idx = ArrayList<Int>(n)
        val ccw = area(p) > 0f
        if (ccw) for (i in 0 until n) idx.add(i) else for (i in n - 1 downTo 0) idx.add(i)
        val out = ArrayList<Int>((n - 2) * 3)
        fun cross(a: Int, b: Int, c: Int) =
            (p[b * 2] - p[a * 2]) * (p[c * 2 + 1] - p[a * 2 + 1]) - (p[b * 2 + 1] - p[a * 2 + 1]) * (p[c * 2] - p[a * 2])
        fun inside(a: Int, b: Int, c: Int, q: Int): Boolean {
            val d1 = cross(a, b, q); val d2 = cross(b, c, q); val d3 = cross(c, a, q)
            return d1 >= 0f && d2 >= 0f && d3 >= 0f
        }
        var guard = n * n + 10
        var i = 0
        while (idx.size > 3 && guard-- > 0) {
            val m = idx.size
            var found = -1
            var best = -1
            var bestV = 1e30f
            for (k in 0 until m) {
                val a = idx[(i + k) % m]; val b = idx[(i + k + 1) % m]; val c = idx[(i + k + 2) % m]
                val cr = cross(a, b, c)
                if (cr > 1e-6f) {
                    var ok = true
                    for (j in 0 until m) {
                        val q = idx[j]
                        if (q == a || q == b || q == c) continue
                        if (inside(a, b, c, q)) { ok = false; break }
                    }
                    if (ok) { found = (i + k + 1) % m; break }
                }
                if (abs(cr) < bestV) { bestV = abs(cr); best = (i + k + 1) % m }
            }
            val at = if (found >= 0) found else best
            val m2 = idx.size
            val a = idx[(at + m2 - 1) % m2]; val b = idx[at]; val c = idx[(at + 1) % m2]
            if (found >= 0) { out.add(a); out.add(b); out.add(c) }
            idx.removeAt(at)
            i = if (at > 0) at - 1 else 0
        }
        if (idx.size == 3 && cross(idx[0], idx[1], idx[2]) > 0f) { out.add(idx[0]); out.add(idx[1]); out.add(idx[2]) }
        return out.toIntArray()
    }

    private fun tint(c: Int, k: Float): Int {
        fun ch(s: Int) = min(255, (((c shr s) and 255) * k).toInt())
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun hash(i: Int): Float {
        val x = sin(i * 127.1f + 311.7f) * 43758.547f
        return x - kotlin.math.floor(x)
    }

    private fun noise1(x: Float, s: Int): Float {
        val i = kotlin.math.floor(x).toInt()
        val f = x - i
        val u = f * f * (3f - 2f * f)
        return hash(i + s) * (1f - u) + hash(i + 1 + s) * u
    }

    /** Flat face of a polygon at depth z (normal towards the camera). */
    private fun cap(b: MeshB, p: FloatArray, z: Float, nz: Float) {
        val tri = triangulate(p)
        var i = 0
        while (i + 2 < tri.size) {
            for (k in 0 until 3) {
                val v = tri[i + k]
                val x = p[v * 2]; val y = -p[v * 2 + 1]
                b.v(x, y, z, 0f, 0f, nz, x * UVS, y * UVS)
            }
            i += 3
        }
    }

    private fun walls(b: MeshB, p: FloatArray, smooth: Boolean, z0: Float, z1: Float) {
        val n = p.size / 2
        val ccw = area(p) > 0f
        val nx = FloatArray(n); val ny = FloatArray(n)
        // outward edge normals in scene space (y flipped); sign depends on winding
        val en = FloatArray(n * 2)
        for (i in 0 until n) {
            val j = (i + 1) % n
            val dx = p[j * 2] - p[i * 2]
            val dy = -(p[j * 2 + 1] - p[i * 2 + 1])
            val l = max(sqrt(dx * dx + dy * dy), 1e-4f)
            // scene space winding is flipped relative to game space
            val s = if (ccw) 1f else -1f
            en[i * 2] = dy / l * s
            en[i * 2 + 1] = -dx / l * s
        }
        for (i in 0 until n) {
            val h = (i + n - 1) % n
            var ax = en[h * 2] + en[i * 2]; var ay = en[h * 2 + 1] + en[i * 2 + 1]
            val l = sqrt(ax * ax + ay * ay)
            if (l > 1e-4f) { ax /= l; ay /= l } else { ax = en[i * 2]; ay = en[i * 2 + 1] }
            nx[i] = ax; ny[i] = ay
        }
        var run = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            val x0 = p[i * 2]; val y0 = -p[i * 2 + 1]
            val x1 = p[j * 2]; val y1 = -p[j * 2 + 1]
            val len = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
            val u0 = run * UVS; val u1 = (run + len) * UVS
            val v0 = z0 * UVS; val v1 = z1 * UVS
            val ax = if (smooth) nx[i] else en[i * 2]; val ay = if (smooth) ny[i] else en[i * 2 + 1]
            val bx = if (smooth) nx[j] else en[i * 2]; val by = if (smooth) ny[j] else en[i * 2 + 1]
            b.v(x0, y0, z0, ax, ay, 0f, u0, v0); b.v(x1, y1, z0, bx, by, 0f, u1, v0); b.v(x1, y1, z1, bx, by, 0f, u1, v1)
            b.v(x0, y0, z0, ax, ay, 0f, u0, v0); b.v(x1, y1, z1, bx, by, 0f, u1, v1); b.v(x0, y0, z1, ax, ay, 0f, u0, v1)
            run += len
        }
    }

    fun build(polys: List<TPoly>, windows: List<FloatArray>, worldW: Float, worldH: Float, arena: Boolean): Result {
        val solid = MeshB()
        val back = MeshB()
        val glass = MeshB()

        // class colours (tex is multiplied on top)
        val rock = if (arena) 0xFF6A5A66.toInt() else 0xFF8C8272.toInt()
        val conc = if (arena) 0xFF59606E.toInt() else 0xFF9AA2A8.toInt()
        val wood = 0xFF93663C.toInt()
        val caveC = if (arena) 0xFF2A1A30.toInt() else 0xFF4A4238.toInt()
        val roomC = if (arena) 0xFF332238.toInt() else 0xFF6E7378.toInt()

        for (pl in polys) {
            val o = outline(pl)
            if (o.size < 6) continue
            when (pl.cls) {
                4, 5 -> {
                    back.color(if (pl.cls == 4) caveC else roomC)
                    cap(back, o, Z_BACK + 2f, 1f)
                }
                1, 2, 3 -> {
                    val c = when (pl.cls) { 1 -> rock; 2 -> conc; else -> wood }
                    solid.color(c)
                    cap(solid, o, Z_FRONT, 1f)
                    solid.color(tint(c, 0.78f))
                    walls(solid, o, pl.smooth, Z_FRONT, Z_BACK)
                    // lighter lip on the front edge so silhouettes read like the 2D outline
                    solid.color(tint(c, 0.55f))
                    walls(solid, o, pl.smooth, Z_FRONT + 0.01f, Z_FRONT - 4f)
                }
            }
        }

        // bunker windows: glass pane with a dark frame in front of the concrete face
        for (w in windows) {
            if (w.size < 4) continue
            val x0 = w[0]; val x1 = w[0] + w[2]
            val y0 = -(w[1] + w[3]); val y1 = -w[1]
            val z = Z_FRONT + 0.6f
            glass.color(0xFF22262A.toInt())
            glass.push(); glass.translate((x0 + x1) / 2f, (y0 + y1) / 2f, z + 1f); glass.box(w[2] + 6f, w[3] + 6f, 3f); glass.pop()
            glass.color(0xFFBFE4EC.toInt())
            glass.push(); glass.translate((x0 + x1) / 2f, (y0 + y1) / 2f, z + 2.8f); glass.box(w[2], w[3], 1f); glass.pop()
            glass.color(0xFF22262A.toInt())
            glass.push(); glass.translate((x0 + x1) / 2f, (y0 + y1) / 2f, z + 3.4f); glass.box(2f, w[3], 1f); glass.pop()
        }

        // far mountain ridges, blue-grey (fogged by the shader)
        val ridges = ArrayList<Mesh3>()
        val defs = if (arena)
            arrayOf(floatArrayOf(-700f, 330f, 0.004f, 0f, 0xFF3A2A48.toInt().toFloat()), floatArrayOf(-1250f, 520f, 0.0028f, 5f, 0xFF2E2038.toInt().toFloat()))
        else
            arrayOf(floatArrayOf(-650f, 300f, 0.0042f, 0f, 0xFF7A8E9E.toInt().toFloat()), floatArrayOf(-1150f, 480f, 0.003f, 5f, 0xFF8FA3B2.toInt().toFloat()), floatArrayOf(-1750f, 650f, 0.0021f, 11f, 0xFFA6B8C4.toInt().toFloat()))
        for (d in defs) {
            val rb = MeshB()
            val z = d[0]; val hgt = d[1]; val fr = d[2]; val s = d[3].toInt()
            rb.color(d[4].toInt())
            val spread = abs(z) * 0.9f
            val x0 = -spread - 200f; val x1 = worldW + spread + 200f
            val base = -(worldH + 900f)
            val horizon = -(worldH * 0.5f) - 120f
            var x = x0
            val step = 60f
            while (x < x1) {
                val xn = x + step
                fun top(px: Float) = horizon + (noise1(px * fr, s) * 0.7f + noise1(px * fr * 3.1f, s + 7) * 0.3f) * hgt * 1.4f
                val ya = top(x); val yb = top(xn)
                rb.v(x, base, z, 0f, 0f, 1f); rb.v(xn, base, z, 0f, 0f, 1f); rb.v(xn, yb, z, 0f, 0f, 1f)
                rb.v(x, base, z, 0f, 0f, 1f); rb.v(xn, yb, z, 0f, 0f, 1f); rb.v(x, ya, z, 0f, 0f, 1f)
                x = xn
            }
            ridges.add(rb.build())
        }
        return Result(solid.build(), back.build(), glass.build(), ridges)
    }
}
