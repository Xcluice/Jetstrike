package com.xcluice.jetstrike

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

class TPoly(val cls: Int, val pts: FloatArray)

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
            ps.add(TPoly(f[0].toInt(), f.copyOfRange(1, f.size)))
        }
        val gr = ArrayList<FloatArray>()
        for (chunk in MapData.GRASS) for (line in chunk.lineSequence()) if (line.isNotBlank()) gr.add(floats(line))
        val wi = ArrayList<FloatArray>()
        for (chunk in MapData.WINDOWS) for (line in chunk.lineSequence()) if (line.isNotBlank()) wi.add(floats(line))
        val pb = ArrayList<Float>()
        for (chunk in MapData.PEBBLES) for (line in chunk.lineSequence()) if (line.isNotBlank()) for (v in floats(line)) pb.add(v)
        return MapDef(MapData.W, MapData.H, ps, gr, wi, pb.toFloatArray(), MapData.SPAWNS, MapData.PICKS)
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
            path.moveTo(pl.pts[0], pl.pts[1])
            var i = 2
            while (i < pl.pts.size) { path.lineTo(pl.pts[i], pl.pts[i + 1]); i += 2 }
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
        smoothGrid(g)
        return g
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
