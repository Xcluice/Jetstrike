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

    private fun mixC(a: Int, c: Int, t: Float): Int {
        fun ch(sh: Int) = (((a shr sh) and 255) * (1f - t) + ((c shr sh) and 255) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun walls(b: MeshB, p: FloatArray, smooth: Boolean, z0: Float, z1: Float, side: Int = 0, top: Int = 0) {
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
            if (top != 0) {
                fun gc(ny0: Float) = mixC(side, top, ((ny0 - 0.3f) / 0.35f).coerceIn(0f, 1f))
                val ca = gc(ay); val cb2 = gc(by)
                b.color(ca); b.v(x0, y0, z0, ax, ay, 0f, u0, v0); b.color(cb2); b.v(x1, y1, z0, bx, by, 0f, u1, v0); b.v(x1, y1, z1, bx, by, 0f, u1, v1)
                b.color(ca); b.v(x0, y0, z0, ax, ay, 0f, u0, v0); b.color(cb2); b.v(x1, y1, z1, bx, by, 0f, u1, v1); b.color(ca); b.v(x0, y0, z1, ax, ay, 0f, u0, v1)
            } else {
                b.v(x0, y0, z0, ax, ay, 0f, u0, v0); b.v(x1, y1, z0, bx, by, 0f, u1, v0); b.v(x1, y1, z1, bx, by, 0f, u1, v1)
                b.v(x0, y0, z0, ax, ay, 0f, u0, v0); b.v(x1, y1, z1, bx, by, 0f, u1, v1); b.v(x0, y0, z1, ax, ay, 0f, u0, v1)
            }
            run += len
        }
    }

    const val ZH = 70f

    /**
     * Version for the behind-shoulder and first-person cameras: the level becomes a real corridor. Solids are extruded
     * both ways from the play plane, and the sides, ends and floor are closed with big rock panels.
     */
    fun build3p(polys: List<TPoly>, worldW: Float, worldH: Float, arena: Boolean): Result {
        val solid = MeshB()
        val back = MeshB()
        val rock = if (arena) 0xFF6A5A66.toInt() else 0xFFD2A168.toInt()
        val conc = if (arena) 0xFF59606E.toInt() else 0xFFC4CBD2.toInt()
        val wood = 0xFFB9824A.toInt()
        val caveC = if (arena) 0xFF2A1A30.toInt() else 0xFF6A4A34.toInt()
        val roomC = if (arena) 0xFF332238.toInt() else 0xFF8A8E94.toInt()
        val grassC = if (arena) 0 else 0xFF5FC13F.toInt()
        for (pl in polys) {
            val o = outline(pl)
            if (o.size < 6) continue
            when (pl.cls) {
                4, 5 -> { back.color(if (pl.cls == 4) caveC else roomC); cap(back, o, -ZH - 3.2f, 1f) }
                1, 2, 3 -> {
                    val c = when (pl.cls) { 1 -> rock; 2 -> conc; else -> wood }
                    solid.color(c); cap(solid, o, ZH, 1f)
                    solid.color(tint(c, 0.8f)); cap(solid, o, -ZH, -1f)
                    solid.color(tint(c, 0.9f)); walls(solid, o, pl.smooth, -ZH, ZH, tint(c, 0.9f), if (pl.cls == 1) grassC else 0)
                }
            }
        }
        val pc = if (arena) 0xFF3A2A44.toInt() else 0xFF9A8A70.toInt()
        val top = 900f
        val bot = -(worldH + 300f)
        val cy = (top + bot) / 2f
        val hh = top - bot
        val wx = worldW + 400f
        back.color(pc); back.push(); back.translate(worldW / 2f, cy, -ZH - 5f); back.box(wx, hh, 2f); back.pop()
        back.color(tint(pc, .85f)); back.push(); back.translate(worldW / 2f, cy, ZH + 5f); back.box(wx, hh, 2f); back.pop()
        back.color(tint(pc, .7f))
        back.push(); back.translate(-1f, cy, 0f); back.box(2f, hh, 2f * ZH + 12f); back.pop()
        back.push(); back.translate(worldW + 1f, cy, 0f); back.box(2f, hh, 2f * ZH + 12f); back.pop()
        back.color(tint(pc, .6f)); back.push(); back.translate(worldW / 2f, -worldH - 1f, 0f); back.box(wx, 2f, 2f * ZH + 12f); back.pop()
        return Result(solid.build(), back.build(), MeshB().build(), emptyList())
    }

    fun build(polys: List<TPoly>, windows: List<FloatArray>, worldW: Float, worldH: Float, arena: Boolean): Result {
        val solid = MeshB()
        val back = MeshB()
        val glass = MeshB()

        // class colours (tex is multiplied on top)
        val rock = if (arena) 0xFF6A5A66.toInt() else 0xFFD2A168.toInt()
        val conc = if (arena) 0xFF59606E.toInt() else 0xFFC4CBD2.toInt()
        val wood = 0xFFB9824A.toInt()
        val caveC = if (arena) 0xFF2A1A30.toInt() else 0xFF6A4A34.toInt()
        val roomC = if (arena) 0xFF332238.toInt() else 0xFF8A8E94.toInt()
        val grassC = if (arena) 0 else 0xFF5FC13F.toInt()

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
                    walls(solid, o, pl.smooth, Z_FRONT, Z_BACK, tint(c, 0.78f), if (pl.cls == 1) grassC else 0)
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

        // distant floating islands with grassy tops (arena keeps dark crags)
        val ridges = ArrayList<Mesh3>()
        val ib = MeshB()
        val n = if (arena) 8 else 15
        for (i in 0 until n) {
            val hx = hash(i * 3 + 1); val hy = hash(i * 3 + 2); val hz = hash(i * 3 + 3)
            val x = -600f + hx * (worldW + 1400f)
            val y = -(180f + hy * (worldH - 150f))
            val z = -(520f + hz * 1000f)
            val r = 80f + hash(i * 7 + 5) * 170f
            ib.push(); ib.translate(x, y, z); ib.scale(1f, 0.85f + hash(i + 40) * .4f, 0.7f)
            ib.color(if (arena) 0xFF3A2A48.toInt() else tint(0xFFC08E5A.toInt(), .85f + hash(i + 9) * .3f))
            ib.push(); ib.translate(0f, -r * .55f, 0f); ib.cylY(r * .1f, r, r * 1.1f, 14, true); ib.pop()
            ib.color(if (arena) 0xFF4A3358.toInt() else 0xFF68C646.toInt())
            ib.push(); ib.translate(0f, r * .03f, 0f); ib.cylY(r * 1.03f, r * 1.03f, r * .16f, 14, true); ib.pop()
            if (!arena) {
                ib.color(0xFF3E9A3A.toInt()); ib.push(); ib.translate(r * .3f, r * .2f, 0f); ib.sphere(r * .22f, 8, 6); ib.pop()
            }
            ib.pop()
        }
        ridges.add(ib.build())
        return Result(solid.build(), back.build(), glass.build(), ridges)
    }
}
