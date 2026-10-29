package com.xcluice.jetstrike.engine

import android.util.Log
import com.xcluice.jetstrike.TPoly
import com.xcluice.jetstrike.Terrain
import kotlin.random.Random

/**
 * Turns the existing 2D map (the traced Mini Outpost polygons or the boss arena) into a 3D level by extruding every
 * polygon into a slab. Gameplay and collision still use [Terrain]'s grid, so nothing about movement changes.
 *
 * Coordinates: world x = map x * S, world y = (mapH - map y) * S (up is +y), gameplay plane is z = 0.
 */
object Terrain3D {
    /** Map pixels to world units. A soldier (about 54 px tall) ends up about 1.1 units tall. */
    const val S = 0.02f
    /** Thickness of the playable slab. The gameplay plane is in the middle. */
    const val DEPTH = 4f

    fun wx(mapX: Float) = mapX * S
    fun wy(mapY: Float) = (Terrain.worldH - mapY) * S
    fun mapX(worldX: Float) = worldX / S
    fun mapY(worldY: Float) = Terrain.worldH - worldY / S

    private val BASE = intArrayOf(
        0, 0xFF9A8268.toInt(), 0xFFC9C3B8.toInt(), 0xFFA06A3C.toInt(), 0xFF5A4A40.toInt(), 0xFF7A838E.toInt()
    )

    /** Builds the whole level as a node: one merged mesh per material plus grass and a backdrop. */
    fun build(): Node {
        val t0 = System.nanoTime()
        val root = Node()
        root.name = "terrain"
        val h = DEPTH / 2f
        val rnd = Random(7)

        val builders = Array(6) { MeshBuilder() }
        for (pl in Terrain.polys) {
            val cls = pl.cls.coerceIn(1, 5)
            val pts = worldOutline(pl)
            if (pts.size < 6) continue
            val k = 0.82f + rnd.nextFloat() * 0.34f
            val b = builders[cls].shade(BASE[cls], k)
            when (cls) {
                1, 2 -> b.extrude(pts, -DEPTH, -0.04f)
                3 -> b.extrude(pts, -DEPTH + 0.25f, -0.06f)
                4 -> b.extrude(pts, -h - 0.8f, -h + 0.6f)
                else -> b.extrude(pts, -h - 1.4f, -h - 0.2f)
            }
        }
        for (cls in 1..5) {
            val m = builders[cls].build()
            if (m.vertexCount > 0) {
                val n = Node(m)
                n.name = "terrain$cls"
                root.add(n)
            }
        }

        val gb = MeshBuilder()
        for (line in Terrain.grass) {
            if (line.size < 4) continue
            val n = line.size / 2
            val strip = FloatArray(n * 4)
            for (i in 0 until n) {
                strip[i * 2] = wx(line[i * 2]); strip[i * 2 + 1] = wy(line[i * 2 + 1]) + 0.09f
                val j = 2 * n + (n - 1 - i) * 2
                strip[j] = wx(line[i * 2]); strip[j + 1] = wy(line[i * 2 + 1]) - 0.09f
            }
            gb.shade(0xFF6FB94A.toInt(), 0.85f + rnd.nextFloat() * 0.2f).extrude(strip, -DEPTH, -0.03f)
        }
        val gm = gb.build()
        if (gm.vertexCount > 0) root.add(Node(gm).also { it.name = "grass" })

        // dark wall far behind the slab so the sky never shows through cave gaps
        val ww = Terrain.worldW * S; val wh = Terrain.worldH * S
        val back = MeshBuilder().color(0xFF2E3640.toInt()).box(ww / 2, wh / 2, -h - 2.2f, ww + 80f, wh + 60f, 0.5f).build()
        root.add(Node(back).also { it.name = "backdrop"; it.cull = false })

        Log.i(GL.TAG, "terrain built in ${(System.nanoTime() - t0) / 1_000_000} ms")
        return root
    }

    /** Map polygon to world-space outline; rounded polygons (TPoly.smooth) are sampled along their curve. */
    private fun worldOutline(pl: TPoly): FloatArray {
        val q = pl.pts
        val n = q.size / 2
        if (n < 3) return FloatArray(0)
        if (!pl.smooth) {
            val o = FloatArray(n * 2)
            for (i in 0 until n) { o[i * 2] = wx(q[i * 2]); o[i * 2 + 1] = wy(q[i * 2 + 1]) }
            return o
        }
        val steps = 3
        val o = FloatArray(n * steps * 2)
        var m = 0
        for (i in 0 until n) {
            val p = (i + n - 1) % n; val nx = (i + 1) % n
            val ax = (q[p * 2] + q[i * 2]) / 2f; val ay = (q[p * 2 + 1] + q[i * 2 + 1]) / 2f
            val bx = (q[i * 2] + q[nx * 2]) / 2f; val by = (q[i * 2 + 1] + q[nx * 2 + 1]) / 2f
            for (s in 1..steps) {
                val t = s / steps.toFloat(); val u = 1f - t
                o[m++] = wx(u * u * ax + 2f * u * t * q[i * 2] + t * t * bx)
                o[m++] = wy(u * u * ay + 2f * u * t * q[i * 2 + 1] + t * t * by)
            }
        }
        return o
    }

    /** Spawn point [i] in world coordinates (feet position on the gameplay plane), or null if the map has none. */
    fun spawn(i: Int): FloatArray? {
        val sp = Terrain.spawns
        if (sp.isEmpty()) return null
        val s = sp[i % sp.size]
        if (s.size < 2) return null
        return floatArrayOf(wx(s[0]), wy(s[1]), 0f)
    }
}
