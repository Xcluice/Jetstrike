package com.xcluice.jetstrike

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

class TPoly(val cls: Int, val pts: FloatArray)

/**
 * The level: polygons traced from the real map layout (see MapData), rasterised once into a
 * collision grid. Classes: 1 rock, 2 concrete, 3 wood (all solid) and 4 background cave (not solid).
 */
object Terrain {
    private const val CELL = 2f
    private const val INV = 1f / CELL

    /** Flip to true if the dark cave areas should block movement too. */
    var darkSolid = false

    val polys = ArrayList<TPoly>()
    val grass = ArrayList<FloatArray>()
    val windows = ArrayList<FloatArray>()
    val pebbles: FloatArray

    private val gw = (MapData.W / CELL).toInt()
    private val gh = (MapData.H / CELL).toInt()
    private val grid = ByteArray(gw * gh)

    private fun floats(line: String): FloatArray {
        val t = line.trim().split(' ')
        return FloatArray(t.size) { t[it].toFloat() }
    }

    init {
        for (chunk in MapData.POLYS) for (line in chunk.lineSequence()) {
            if (line.isBlank()) continue
            val f = floats(line)
            polys.add(TPoly(f[0].toInt(), f.copyOfRange(1, f.size)))
        }
        for (chunk in MapData.GRASS) for (line in chunk.lineSequence()) if (line.isNotBlank()) grass.add(floats(line))
        for (chunk in MapData.WINDOWS) for (line in chunk.lineSequence()) if (line.isNotBlank()) windows.add(floats(line))
        val pb = ArrayList<Float>()
        for (chunk in MapData.PEBBLES) for (line in chunk.lineSequence()) if (line.isNotBlank()) for (v in floats(line)) pb.add(v)
        pebbles = pb.toFloatArray()
        rasterize()
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

    private fun rasterize() {
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
            for (x in 0 until gw) grid[o + x] = if ((row[x] ushr 24) > 100) 1 else 0
        }
        bmp.recycle()
    }

    fun solid(x: Float, y: Float): Boolean {
        if (y >= MapData.H) return true
        if (y < 0f) return false
        if (x < 0f || x >= MapData.W) return true
        return grid[(y * INV).toInt() * gw + (x * INV).toInt()].toInt() != 0
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
