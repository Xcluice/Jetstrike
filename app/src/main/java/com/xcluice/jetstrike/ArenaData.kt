package com.xcluice.jetstrike

/** The boss arena: a wide steel floor with ramps, cover and floating platforms. Built from simple shapes. */
object ArenaData {
    const val W = 2400f
    const val H = 1300f
    private const val FLOOR = 1040f

    private fun rect(cls: Int, x0: Float, y0: Float, x1: Float, y1: Float) =
        TPoly(cls, floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1))

    private fun tri(cls: Int, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) =
        TPoly(cls, floatArrayOf(ax, ay, bx, by, cx, cy))

    fun build(): MapDef {
        val polys = listOf(
            rect(1, 0f, FLOOR, W, H),                              // floor
            tri(1, 0f, FLOOR, 0f, 880f, 240f, FLOOR),               // left ramp
            tri(1, W, FLOOR, W, 880f, W - 240f, FLOOR),             // right ramp
            rect(2, 1120f, 960f, 1280f, FLOOR),                     // centre cover block
            rect(2, 260f, 860f, 640f, 892f),                        // low platforms
            rect(2, 1760f, 860f, 2140f, 892f),
            rect(2, 980f, 720f, 1420f, 752f),                       // centre platform
            rect(2, 520f, 620f, 820f, 652f),                        // mid platforms
            rect(2, 1580f, 620f, 1880f, 652f),
            rect(2, 1060f, 500f, 1340f, 532f)                       // top platform
        )
        val spawns = ArrayList<FloatArray>()
        for (x in floatArrayOf(330f, 520f, 700f, 900f, 1500f, 1700f, 1880f, 2070f)) spawns.add(floatArrayOf(x, FLOOR - 14f))
        spawns.add(floatArrayOf(450f, 846f)); spawns.add(floatArrayOf(1950f, 846f))
        spawns.add(floatArrayOf(1200f, 706f)); spawns.add(floatArrayOf(670f, 606f))
        spawns.add(floatArrayOf(1730f, 606f)); spawns.add(floatArrayOf(1200f, 486f))
        val picks = listOf(
            Triple("health", 1200f, 960f - 18f), Triple("health", 450f, 860f - 18f), Triple("health", 1950f, 860f - 18f),
            Triple("health", 1200f, 500f - 18f),
            Triple("rocket", 1200f, 720f - 18f), Triple("sniper", 1280f, 500f - 18f),
            Triple("shotgun", 670f, 620f - 18f), Triple("laser", 1730f, 620f - 18f),
            Triple("rifle", 700f, FLOOR - 18f), Triple("rifle", 1700f, FLOOR - 18f),
            Triple("smg", 330f, FLOOR - 18f), Triple("smg", 2070f, FLOOR - 18f),
            Triple("gren", 900f, FLOOR - 18f), Triple("gren", 1500f, FLOOR - 18f)
        )
        return MapDef(W, H, polys, emptyList(), emptyList(), FloatArray(0), spawns, picks)
    }
}
