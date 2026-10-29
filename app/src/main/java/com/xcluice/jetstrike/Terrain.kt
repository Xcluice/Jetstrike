package com.xcluice.jetstrike

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.max
import kotlin.math.min

class TPoly(val cls: Int, val pts: FloatArray, val smooth: Boolean = false)

/** Everything that describes one level. */
class MapDef(
    val w: Float, val h: Float,
    val polys: List<TPoly>, val grass: List<FloatArray>, val windows: List<FloatArray>, val pebbles: FloatArray,
    val spawns: List<FloatArray>, val picks: List<Triple<String, Float, Float>>
)

/**
 * The active level, rasterised into a collision grid. Map 0 is the traced Mini Outpost layout (see MapData),
 * map 1 is the boss arena (see ArenaData). Classes: 1 rock, 2 concrete, 3 wood, 4 dark rock (solid only when
 * [darkSolid]) and 5 background room (never solid).
 */
object Terrain {
    private const val CELL = 2f
    private const val INV = 1f / CELL

    /** Dark rock is the cave backdrop: passable by default. Set to true to make it solid. */
    var darkSolid = false

    var mapId = 0
        private set
    /** Bumps every time a different map is loaded, so the renderer knows to rebuild its art. */
    var version = 0
        private set

    var worldW = MapData.W
        private set
    var worldH = MapData.H
        private set
    var polys: List<TPoly> = emptyList()
        private set
    var grass: List<FloatArray> = emptyList()
        private set
    var windows: List<FloatArray> = emptyList()
        private set
    var pebbles: FloatArray = FloatArray(0)
        private set
    var spawns: List<FloatArray> = emptyList()
        private set
    var picks: List<Triple<String, Float, Float>> = emptyList()
        private set

    private var gw = 0
    private var gh = 0
    private var grid = ByteArray(0)

    private val defs = HashMap<Int, MapDef>()
    private val grids = HashMap<Int, ByteArray>()

    init { load(0) }

    private fun floats(line: String): FloatArray {
        val t = line.trim().split(' ')
        return FloatArray(t.size) { t[it].toFloat() }
    }

    private fun outpostDef(): MapDef {
        val ps = ArrayList<TPoly>()
        for (chunk in MapData.POLYS) for (line in chunk.lineSequence()) {
            if (line.isBlank()) continue
            val f = floats(line)
            val cls = f[0].toInt()
            var pts = f.copyOfRange(1, f.size)
            // natural rock (1) and cave (4) outlines were traced by hand and are full of tiny bumps: relax them
            val soft = (cls == 1 || cls == 4) && pts.size >= 24
            if (soft) pts = relax(pts, 3, true)
            ps.add(TPoly(cls, pts, soft))
        }
        val gr = ArrayList<FloatArray>()
        for (chunk in MapData.GRASS) for (line in chunk.lineSequence()) if (line.isNotBlank()) gr.add(relax(floats(line), 3, false))
        val wi = ArrayList<FloatArray>()
        for (chunk in MapData.WINDOWS) for (line in chunk.lineSequence()) if (line.isNotBlank()) wi.add(floats(line))
        val pb = ArrayList<Float>()
        for (chunk in MapData.PEBBLES) for (line in chunk.lineSequence()) if (line.isNotBlank()) for (v in floats(line)) pb.add(v)
        return MapDef(MapData.W, MapData.H, ps, gr, wi, pb.toFloatArray(), MapData.SPAWNS, MapData.PICKS)
    }

    /** Laplacian smoothing of a polyline or polygon (x y pairs). Open lines keep their end points. */
    private fun relax(src: FloatArray, passes: Int, closed: Boolean): FloatArray {
        val n = src.size / 2
        if (n < 3) return src
        var a = src
        repeat(passes) {
            val b = a.copyOf()
            for (i in 0 until n) {
                if (!closed && (i == 0 || i == n - 1)) continue
                val p = (i + n - 1) % n
                val q = (i + 1) % n
                b[i * 2] = (a[p * 2] + 2f * a[i * 2] + a[q * 2]) * .25f
                b[i * 2 + 1] = (a[p * 2 + 1] + 2f * a[i * 2 + 1] + a[q * 2 + 1]) * .25f
            }
            a = b
        }
        return a
    }

    fun load(id: Int) {
        if (id == mapId && grid.isNotEmpty()) return
        val d = defs.getOrPut(id) { if (id == 1) ArenaData.build() else outpostDef() }
        mapId = id
        worldW = d.w; worldH = d.h
        polys = d.polys; grass = d.grass; windows = d.windows; pebbles = d.pebbles
        spawns = d.spawns; picks = d.picks
        gw = (d.w / CELL).toInt(); gh = (d.h / CELL).toInt()
        grid = grids.getOrPut(id) { rasterize() }
        version++
    }

    fun classPath(cls: Int): Path {
        val path = Path()
        path.fillType = Path.FillType.EVEN_ODD
        for (pl in polys) {
            if (pl.cls != cls) continue
            val q = pl.pts
            if (pl.smooth) {
                // curve through the edge mid points, so corners become round
                val n = q.size / 2
                path.moveTo((q[0] + q[2]) / 2f, (q[1] + q[3]) / 2f)
                for (i in 1..n) {
                    val a = i % n
                    val b = (i + 1) % n
                    path.quadTo(q[a * 2], q[a * 2 + 1], (q[a * 2] + q[b * 2]) / 2f, (q[a * 2 + 1] + q[b * 2 + 1]) / 2f)
                }
            } else {
                path.moveTo(q[0], q[1])
                var i = 2
                while (i < q.size) { path.lineTo(q[i], q[i + 1]); i += 2 }
            }
            path.close()
        }
        return path
    }

    private fun rasterize(): ByteArray {
        val g = ByteArray(gw * gh)
        val bmp = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.scale(INV, INV)
        val p = Paint()
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        p.isAntiAlias = false
        for (cls in 1..4) {
            if (cls == 4 && !darkSolid) continue
            cv.drawPath(classPath(cls), p)
        }
        val row = IntArray(gw)
        for (y in 0 until gh) {
            bmp.getPixels(row, 0, gw, 0, y, gw, 1)
            val o = y * gw
            for (x in 0 until gw) g[o + x] = if ((row[x] ushr 24) > 100) 1 else 0
        }
        bmp.recycle()
        // remove spikes thinner than ~10 units, then fill pits and notches narrower than a soldier can enter
        morph(g, 2, false); morph(g, 2, true)
        morph(g, 4, true); morph(g, 4, false)
        smoothGrid(g)
        return g
    }

    /** Number of solid cells in the (2r+1) square around every cell, clamped at the map edge. */
    private fun boxCount(g: ByteArray, r: Int): IntArray {
        val h = IntArray(gw * gh)
        for (y in 0 until gh) {
            val o = y * gw
            var run = 0
            for (x in 0..min(r, gw - 1)) run += g[o + x]
            for (x in 0 until gw) {
                h[o + x] = run
                val add = x + r + 1
                if (add < gw) run += g[o + add]
                val rem = x - r
                if (rem >= 0) run -= g[o + rem]
            }
        }
        val out = IntArray(gw * gh)
        for (x in 0 until gw) {
            var run = 0
            for (y in 0..min(r, gh - 1)) run += h[y * gw + x]
            for (y in 0 until gh) {
                out[y * gw + x] = run
                val add = y + r + 1
                if (add < gh) run += h[add * gw + x]
                val rem = y - r
                if (rem >= 0) run -= h[rem * gw + x]
            }
        }
        return out
    }

    /** Square dilation (grow solid) or erosion (shrink solid). Closing = dilate then erode, opening = erode then dilate. */
    private fun morph(g: ByteArray, r: Int, dilate: Boolean) {
        val cnt = boxCount(g, r)
        for (y in 0 until gh) {
            val hh = min(gh - 1, y + r) - max(0, y - r) + 1
            for (x in 0 until gw) {
                val i = y * gw + x
                val full = (min(gw - 1, x + r) - max(0, x - r) + 1) * hh
                g[i] = if (dilate) (if (cnt[i] > 0) 1 else 0) else (if (cnt[i] == full) 1 else 0)
            }
        }
    }

    /**
     * The polygons were traced by hand, so their edges are jagged at the cell level (1-cell spikes and notches)
     * and snag soldiers. Two passes of a 3x3 majority filter round those off without moving real edges.
     */
    private fun smoothGrid(g: ByteArray) {
        repeat(2) {
            val src = g.copyOf()
            for (y in 1 until gh - 1) for (x in 1 until gw - 1) {
                var n = 0
                for (dy in -1..1) for (dx in -1..1) n += src[(y + dy) * gw + x + dx]
                val i = y * gw + x
                if (n >= 6) g[i] = 1 else if (n <= 3) g[i] = 0
            }
        }
    }

    fun solid(x: Float, y: Float): Boolean {
        if (y >= worldH) return true
        if (y < 0f) return false
        if (x < 0f || x >= worldW) return true
        return grid[(y * INV).toInt() * gw + (x * INV).toInt()].toInt() != 0
    }

    /** Narrow version used for vertical movement, so bumps on a wall beside the soldier never act as ground. */
    fun coreHit(x: Float, y: Float): Boolean {
        for (r in 0 until 5) {
            val py = y - 0.5f - r * 10.75f
            if (solid(x - 6f, py) || solid(x, py) || solid(x + 6f, py)) return true
        }
        return false
    }

    /** Does a soldier standing with his feet at (x, y) overlap the terrain? */
    fun boxHit(x: Float, y: Float): Boolean {
        for (r in 0 until 5) {
            val py = y - 0.5f - r * 10.75f
            if (solid(x - 11f, py) || solid(x, py) || solid(x + 11f, py)) return true
        }
        return false
    }
}
