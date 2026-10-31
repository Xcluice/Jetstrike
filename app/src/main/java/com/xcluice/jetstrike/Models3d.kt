package com.xcluice.jetstrike

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

/**
 * 3D versions of every gun and fighter. Guns are traced rectangle by rectangle from the sprites (pixel coordinates,
 * y down, then flipped). Fighters follow the Hero body numbers and the per-fighter gear of the 2D renderer.
 * Everything faces +X with Y up; one unit is one game unit.
 */

private const val DEG = (PI / 180.0).toFloat()

private val GREY = 0xFF9097A0.toInt()
private val GREY_L = 0xFFB4BAC1.toInt()
private val DARK = 0xFF25282C.toInt()
private val BLACK = 0xFF15171A.toInt()
private val CHAR = 0xFF3A3C42.toInt()
private val OLIVE = 0xFF6C7152.toInt()
private val OLIVE_L = 0xFF80875F.toInt()
private val OLIVE_D = 0xFF4A5139.toInt()
private val WOOD = 0xFF8A4F26.toInt()
private val WOOD_L = 0xFFA8672F.toInt()
private val YELLOW = 0xFFE9E24C.toInt()
private val GLASS = 0xFF5FD0E8.toInt()
private val RED = 0xFFE0443A.toInt()
private val CREAM = 0xFFF2E3C4.toInt()

/** Game-unit length of each gun when held or lying on the ground (same table as the 2D sprites). */
val GUN_LEN: Map<String, Float> = mapOf(
    "pistol" to 26f, "smg" to 38f, "rifle" to 44f, "shotgun" to 46f,
    "rocket" to 50f, "flame" to 42f, "laser" to 40f, "sniper" to 58f, "grenade" to 14f
)

/** kind: 0 none, 1 bullet muzzle flash, 2 flame, 3 laser. Positions are in sprite pixels (y down). */
class GunModel(val mesh: Mesh3, val w: Float, val h: Float, val gx: Float, val gy: Float,
               val mx: Float, val my: Float, val fx: Float, val fy: Float, val kind: Int)

private class GB(val hgt: Float) {
    val b = MeshB()

    fun r(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, c: Int, rot: Float = 0f, dz: Float = 0f, rr: Float = 0.28f) {
        b.color(c); b.push()
        b.translate((x0 + x1) / 2f, hgt - (y0 + y1) / 2f, dz)
        b.rotZ(-rot * DEG)
        val w = x1 - x0
        val h = y1 - y0
        b.rrExtrude(-w / 2f, -h / 2f, w / 2f, h / 2f, min(w, min(h, z)) * rr, -z / 2f, z / 2f, 2)
        b.pop()
    }

    /** Cylinder along x from x0 to x1 at height yc; radius rb at the rear end and rf at the front. */
    fun t(x0: Float, x1: Float, yc: Float, rb: Float, c: Int, rf: Float = rb, sz: Float = 1f, dz: Float = 0f) {
        b.color(c); b.push()
        b.translate((x0 + x1) / 2f, hgt - yc, dz)
        b.rotZ(-(PI / 2.0).toFloat())
        b.scale(1f, 1f, sz)
        b.cylY(rb, rf, x1 - x0, 14)
        b.pop()
    }

    fun sp(cx: Float, cy: Float, rad: Float, c: Int, sx: Float = 1f, sy: Float = 1f, sz: Float = 1f) {
        b.color(c); b.push()
        b.translate(cx, hgt - cy, 0f)
        b.scale(sx, sy, sz)
        b.sphere(rad, 14, 10)
        b.pop()
    }

    fun tor(cx: Float, cy: Float, rad: Float, tube: Float, c: Int, dz: Float = 0f) {
        b.color(c); b.push()
        b.translate(cx, hgt - cy, dz)
        b.torus(rad, tube, 18, 6)
        b.pop()
    }

    fun fin(w: Float, gx: Float, gy: Float, mx: Float, my: Float, fx: Float, fy: Float, kind: Int) =
        GunModel(b.build(), w, hgt, gx, gy, mx, my, fx, fy, kind)
}

fun buildGunModel(key: String): GunModel = when (key) {
    "pistol" -> { // revolver 264x124
        val g = GB(124f)
        g.t(130f, 258f, 32f, 10f, GREY); g.r(130f, 17f, 255f, 26f, 12f, GREY_L); g.r(238f, 10f, 251f, 24f, 10f, DARK)
        g.r(55f, 24f, 150f, 80f, 30f, GREY, rr = 0.35f)
        g.t(82f, 132f, 52f, 21f, GREY_L)
        for (i in 0 until 5) g.r(90f + i * 9f, 36f, 94f + i * 9f, 68f, 46f, CHAR, rr = 0.1f)
        g.r(50f, 18f, 66f, 36f, 8f, DARK, rot = -15f)
        g.r(30f, 66f, 60f, 118f, 26f, BLACK, rot = 25f, rr = 0.4f)
        for (i in 0 until 4) g.r(14f + i * 3f, 96f + i * 5f, 32f + i * 3f, 99f + i * 5f, 28f, CHAR, rot = 25f, rr = 0.1f)
        g.tor(88f, 88f, 13f, 2.2f, GREY); g.r(86f, 72f, 91f, 88f, 5f, DARK)
        g.fin(264f, 48f, 80f, 258f, 32f, 60f, 96f, 1)
    }
    "rifle" -> { // 284x133
        val g = GB(133f)
        g.r(0f, 38f, 54f, 90f, 20f, OLIVE, rr = 0.3f); g.r(0f, 36f, 8f, 94f, 24f, BLACK)
        g.r(45f, 28f, 190f, 78f, 28f, OLIVE, rr = 0.35f); g.r(180f, 42f, 256f, 78f, 30f, OLIVE_L, rr = 0.3f)
        g.r(165f, 64f, 256f, 82f, 28f, CHAR, rot = 1.5f)
        for (i in 0 until 8) g.r(172f + i * 10f, 66f, 176f + i * 10f, 80f, 30f, DARK, rr = 0.1f)
        g.r(92f, 6f, 152f, 28f, 26f, DARK); g.r(98f, 10f, 106f, 24f, 27f, RED); g.r(70f, 18f, 190f, 28f, 20f, DARK)
        g.t(250f, 284f, 51f, 4.5f, DARK); g.t(268f, 286f, 51f, 7f, BLACK)
        g.r(112f, 76f, 150f, 130f, 20f, DARK, rot = 9f); g.r(84f, 76f, 114f, 114f, 22f, DARK, rot = 15f, rr = 0.4f)
        g.tor(124f, 80f, 12f, 2f, DARK)
        g.fin(286f, 100f, 88f, 286f, 51f, 210f, 60f, 1)
    }
    "shotgun" -> { // pump 404x149
        val g = GB(149f)
        g.r(55f, 26f, 158f, 70f, 26f, 0xFF3B3D42.toInt(), rr = 0.3f); g.r(95f, 38f, 128f, 46f, 27f, GREY)
        g.r(5f, 28f, 66f, 70f, 26f, DARK, rr = 0.4f)
        g.t(156f, 304f, 48f, 25f, 0xFF232428.toInt(), sz = 0.78f)
        for (i in 0 until 9) g.t(168f + i * 15f, 173f + i * 15f, 48f, 26f, BLACK, sz = 0.8f)
        g.t(300f, 404f, 38f, 9f, 0xFF3A3C40.toInt()); g.t(300f, 392f, 60f, 7f, 0xFF303236.toInt()); g.t(392f, 404f, 38f, 11f, BLACK)
        g.r(372f, 10f, 388f, 30f, 10f, DARK); g.r(28f, 12f, 60f, 30f, 12f, DARK)
        g.r(18f, 72f, 50f, 146f, 28f, BLACK, rot = 22f, rr = 0.4f)
        g.tor(100f, 92f, 17f, 2.4f, CHAR); g.r(96f, 72f, 101f, 90f, 5f, DARK)
        g.fin(404f, 40f, 90f, 404f, 38f, 235f, 48f, 1)
    }
    "sniper" -> { // 284x100
        val g = GB(100f)
        g.r(0f, 36f, 64f, 80f, 20f, 0xFF6A6F4E.toInt(), rr = 0.3f); g.r(0f, 30f, 50f, 40f, 20f, DARK); g.r(0f, 36f, 7f, 80f, 24f, BLACK)
        g.r(58f, 34f, 140f, 72f, 26f, 0xFF6F7378.toInt(), rr = 0.3f); g.r(100f, 40f, 176f, 68f, 28f, OLIVE, rr = 0.35f)
        g.t(72f, 148f, 16f, 8f, 0xFF26282C.toInt()); g.t(66f, 82f, 16f, 11f, DARK); g.t(136f, 152f, 16f, 11f, DARK)
        g.t(150f, 153f, 16f, 9.5f, GLASS)
        g.r(88f, 22f, 96f, 38f, 12f, DARK); g.r(125f, 22f, 133f, 38f, 12f, DARK)
        g.t(140f, 250f, 46f, 3.2f, 0xFF303236.toInt()); g.t(236f, 284f, 41f, 9f, 0xFF8A8F94.toInt())
        g.r(102f, 66f, 128f, 94f, 20f, DARK); g.r(60f, 66f, 86f, 96f, 22f, BLACK, rot = 20f, rr = 0.4f)
        for (dz in floatArrayOf(-20f, 20f)) { g.r(140f, 54f, 146f, 98f, 5f, DARK, rot = -24f, dz = dz); g.r(158f, 54f, 164f, 98f, 5f, DARK, rot = 18f, dz = dz) }
        g.fin(284f, 72f, 66f, 284f, 41f, 140f, 56f, 1)
    }
    "smg" -> { // AK 284x122
        val g = GB(122f)
        g.r(0f, 34f, 80f, 92f, 22f, WOOD, rot = -8f, rr = 0.3f)
        g.r(75f, 14f, 166f, 60f, 26f, 0xFF1C1D20.toInt(), rr = 0.3f); g.r(82f, 10f, 160f, 22f, 25f, 0xFF2A2B2F.toInt())
        g.r(155f, 38f, 238f, 62f, 30f, WOOD, rr = 0.3f); g.r(150f, 24f, 240f, 36f, 26f, WOOD_L)
        g.t(160f, 246f, 22f, 3.5f, DARK); g.t(235f, 282f, 45f, 3.5f, DARK)
        g.r(262f, 14f, 272f, 42f, 8f, DARK); g.t(272f, 286f, 45f, 5f, BLACK)
        g.r(122f, 54f, 158f, 86f, 18f, DARK, rot = -10f); g.r(131f, 80f, 167f, 108f, 18f, DARK, rot = -18f); g.r(142f, 102f, 178f, 124f, 18f, DARK, rot = -27f)
        g.r(62f, 72f, 94f, 116f, 22f, WOOD_L, rot = 15f, rr = 0.4f); g.tor(112f, 76f, 12f, 2f, DARK)
        g.fin(286f, 82f, 80f, 286f, 45f, 200f, 52f, 1)
    }
    "rocket" -> { // 284x115
        val g = GB(115f)
        g.t(20f, 250f, 56f, 18.5f, 0xFF7A6B3F.toInt()); g.t(0f, 32f, 55f, 27f, BLACK, rf = 24f); g.t(232f, 284f, 56f, 22f, BLACK)
        g.t(88f, 99f, 56f, 19.5f, 0xFFF2D21A.toInt())
        g.t(158f, 214f, 17f, 9f, BLACK); g.t(212f, 216f, 17f, 7.5f, GLASS); g.r(170f, 22f, 200f, 40f, 18f, BLACK)
        g.r(140f, 60f, 218f, 80f, 28f, BLACK, rr = 0.3f); g.r(172f, 76f, 214f, 112f, 26f, BLACK, rr = 0.35f)
        g.r(128f, 64f, 135f, 112f, 5f, BLACK, rot = -12f)
        g.fin(284f, 192f, 92f, 284f, 56f, 100f, 56f, 1)
    }
    "flame" -> { // 284x108
        val g = GB(108f)
        g.t(55f, 160f, 36f, 24f, YELLOW); g.t(61f, 70f, 36f, 25.5f, DARK); g.t(141f, 150f, 36f, 25.5f, DARK)
        g.r(38f, 50f, 172f, 74f, 24f, 0xFF2E2F33.toInt(), rr = 0.35f); g.r(20f, 60f, 62f, 108f, 24f, CHAR, rot = 20f, rr = 0.4f)
        g.t(160f, 228f, 52f, 8f, DARK); g.r(160f, 24f, 178f, 100f, 20f, CHAR)
        g.sp(252f, 30f, 26f, CHAR); g.t(266f, 286f, 30f, 10f, 0xFF8A8D92.toInt(), rf = 14f)
        g.tor(22f, 40f, 14f, 2.2f, DARK); g.tor(262f, 40f, 18f, 2.2f, DARK)
        g.fin(288f, 40f, 72f, 288f, 30f, 172f, 62f, 2)
    }
    "laser" -> { // 284x109
        val g = GB(109f)
        g.r(40f, 34f, 250f, 84f, 34f, 0xFF5F6B45.toInt(), rr = 0.4f); g.r(105f, 18f, 282f, 50f, 30f, 0xFF73805A.toInt(), rr = 0.3f)
        for (i in 0 until 9) g.r(122f + i * 17f, 29f, 133f + i * 17f, 39f, 32f, 0xFF1A1D16.toInt(), rr = 0.1f)
        g.r(55f, 5f, 115f, 34f, 28f, OLIVE, rot = -10f); g.r(78f, 0f, 102f, 20f, 20f, OLIVE_D, rot = -28f)
        g.r(120f, 56f, 262f, 90f, 30f, OLIVE_D, rr = 0.35f); g.r(62f, 68f, 98f, 108f, 24f, BLACK, rot = 12f, rr = 0.4f)
        g.t(262f, 286f, 34f, 8f, 0xFF00E5FF.toInt())
        g.fin(286f, 80f, 74f, 286f, 34f, 180f, 60f, 3)
    }
    else -> { // grenade 73x99
        val g = GB(99f)
        g.sp(36f, 62f, 30f, 0xFF5A6A3C.toInt(), sy = 1.22f)
        for (y in floatArrayOf(48f, 62f, 76f)) {
            g.b.color(0xFF2C3620.toInt()); g.b.push(); g.b.translate(36f, 99f - y, 0f); g.b.rotX((PI / 2.0).toFloat())
            g.b.torus(if (y == 62f) 30f else 26f, 1.6f, 18, 5); g.b.pop()
        }
        g.t(30f, 44f, 77f, 8f, GREY, rf = 8f); g.r(28f, 3f, 46f, 12f, 20f, GREY); g.r(40f, 2f, 70f, 10f, 10f, GREY_L, rot = 14f); g.tor(68f, 22f, 6f, 1.4f, GREY_L)
        g.fin(73f, 36f, 52f, 36f, 52f, 36f, 52f, 0)
    }
}

/* ------------------------------------------------------------------ fighters */

class ClothSpec(val x: Float, val y: Float, val z: Float, val rz: Float, val w: Float, val h: Float,
                val color: Int, val amp: Float, val speed: Float)

class HeroModel(
    val body: Mesh3, val legs: Array<Mesh3>, val legPivot: Array<FloatArray>,
    val cloths: List<ClothSpec>, val shY: Float, val tz: Float, val limb: Float,
    val flames: Array<FloatArray>, val cC: Int, val cD: Int, val cL: Int
)

private fun mixC(a: Int, b: Int, t: Float): Int {
    fun ch(s: Int) = (((a shr s) and 255) * (1f - t) + ((b shr s) and 255) * t).toInt()
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

fun buildHeroModel(skin: Skin): HeroModel {
    val h = skin.h
    val b = MeshB()
    val cC = skin.c; val cD = skin.d; val cL = skin.l
    val tw = h.tw; val th = h.th; val hr = h.hr; val limb = h.limb; val pk = h.pack
    val tBot = h.hip - 3f
    val tTop = tBot + th
    val shY = tTop - 8f
    val hcy = tTop + hr * 0.56f
    val hTop = hcy + hr
    val tx = tw * 0.95f
    val tz = tw * 1.15f
    val hx = 1f
    val chestY = tBot + th * 0.55f
    val cloths = ArrayList<ClothSpec>()

    fun sph(x: Float, y: Float, z: Float, r: Float, c: Int, sx: Float = 1f, sy: Float = 1f, sz: Float = 1f, seg: Int = 12) {
        b.color(c); b.push(); b.translate(x, y, z); b.scale(sx, sy, sz); b.sphere(r, seg, seg / 2 + 1); b.pop()
    }
    fun cone(x: Float, y: Float, z: Float, r: Float, hh: Float, c: Int, rx: Float = 0f, rz: Float = 0f, sx: Float = 1f, sz: Float = 1f) {
        b.color(c); b.push(); b.translate(x, y, z); b.rotZ(rz); b.rotX(rx); b.scale(sx, 1f, sz); b.cone(r, hh, 8); b.pop()
    }
    fun bx(x: Float, y: Float, z: Float, w: Float, hh: Float, d: Float, c: Int, rx: Float = 0f) {
        b.color(c); b.push(); b.translate(x, y, z); b.rotX(rx); b.box(w, hh, d); b.pop()
    }
    fun ring(y: Float, r: Float, tube: Float, c: Int) {
        b.color(c); b.push(); b.translate(hx, y, 0f); b.rotX((PI / 2.0).toFloat()); b.torus(r, tube, 24, 6); b.pop()
    }

    // torso, belt, chest panel
    b.color(cC); b.rrExtrude(-tx, tBot, tx, tTop, min(h.rad, tx * 0.9f), -tz, tz, 3)
    b.color(cD); b.rrExtrude(-tx - 0.3f, tBot, tx + 0.3f, tBot + 4f, 0.8f, -tz - 0.3f, tz + 0.3f, 2)
    bx(tx - 0.1f, tTop - th * 0.28f, 0f, 1.0f, th * 0.4f, tz * 1.5f, mixC(cC, cL, 0.5f))

    // head and visor
    if (h.squareHead) {
        b.color(cC); b.rrExtrude(hx - hr * 0.95f, hcy - hr * 0.92f, hx + hr * 0.95f, hcy + hr * 0.92f, hr * 0.42f, -hr * 0.95f, hr * 0.95f, 3)
    } else {
        sph(hx, hcy, 0f, hr, cC, seg = 18)
    }
    sph(hx + hr * 0.62f, hcy, 0f, hr * 0.62f, 0xFF2B8CA3.toInt(), 0.42f, 0.62f, 0.95f)
    sph(hx + hr * 0.7f, hcy + hr * 0.03f, 0f, hr * 0.56f, 0xFF8FE3F5.toInt(), 0.4f, 0.52f, 0.86f)

    // jetpack
    val px = -(tx + 4f * pk)
    bx(px, tBot + th * 0.5f, 0f, 9f * pk, th * 0.92f, tz * 1.5f, 0xFF3F525C.toInt())
    bx(px + 5f * pk, tTop - 5.5f, 0f, 1.2f, 2.8f, tz * 1.3f, 0xFFFF6F59.toInt())
    val flames = arrayOf(floatArrayOf(px, tBot - 3.5f, tz * 0.55f * min(pk, 1.3f), pk), floatArrayOf(px, tBot - 3.5f, -tz * 0.55f * min(pk, 1.3f), pk))
    for (f in flames) {
        b.color(0xFF1B2830.toInt()); b.push(); b.translate(f[0], tBot - 1f, f[2]); b.cylY(4.2f * pk, 3.4f * pk, 5f, 12); b.pop()
    }

    // fighter specific gear
    when (skin.acc) {
        0 -> {
            b.color(cL); b.rod(-2f, hTop - 2f, 0f, -7.5f, hTop + 9f, 0f, 0.7f)
            sph(-7.8f, hTop + 10f, 0f, 2.6f, cL); sph(tx + 0.2f, chestY, 2f, 2.4f, cL)
        }
        1 -> {
            cone(-3.5f, hTop + 2f, 0f, 5f, 17f, cD, rz = 0.35f, sz = 0.35f)
            ring(hcy + hr * 0.45f, hr * 0.86f, 1.4f, cD)
            for (s in floatArrayOf(1f, -1f)) {
                cone(0.5f, tTop + 5.5f, s * (tz + 3f), 3.4f, 10f, cD)
                cone(-4f, tTop + 2.5f, s * (tz + 4f), 2.4f, 7f, cD, rz = 0.7f)
            }
        }
        2 -> {
            for (k in 0 until 3) cone(-4f + k * 4.5f, hTop + 1.5f, 0f, 3f, 10f + (if (k == 1) 3.5f else 0f), cL, sz = 0.4f)
            bx(hx + hr * 0.78f, hcy + hr * 0.42f, 0f, 5f, 4.5f, hr * 1.5f, cD)
            bx(tx + 0.2f, tTop - th * 0.38f, 0f, 1.2f, th * 0.55f, tz * 1.5f, cD)
            bx(tx + 0.8f, tTop - 10f, 0f, 1.4f, 2.4f, tz * 1.2f, cL)
            for (s in floatArrayOf(1f, -1f)) sph(tx + 1f, tBot + th * 0.35f, s * tz * 0.7f, 1.6f, cL, seg = 8)
            sph(1f, tTop + 0.5f, tz + 5.5f, 9.5f, cD, 1f, 0.8f, 0.8f)
        }
        3 -> {
            ring(hcy - hr * 0.25f + 5f, hr * 0.9f, 1.2f, cD)
            cloths.add(ClothSpec(hx - hr * 0.9f, hcy - hr * 0.2f, 2.5f, -1.35f, 2f, 9.5f, cL, 0.25f, 9f))
            cloths.add(ClothSpec(hx - hr * 0.9f, hcy - hr * 0.2f, -2.5f, -1.35f, 2f, 9.5f, cL, 0.25f, 10f))
            cloths.add(ClothSpec(-2f, tTop - 1f, 0f, -1.25f, 7f, 22f, cL, 0.35f, 7f))
            bx(tx + 0.2f, tBot + th * 0.5f, 0f, 1.2f, th * 1.05f, 2.8f, cD, rx = 0.75f)
        }
        4 -> {
            ring(hTop + 7f, hr * 0.62f, 1.1f, cL)
            bx(tx + 0.2f, chestY + 3f, 0f, 1f, 3.4f, 15f, cL)
            bx(tx + 0.2f, chestY + 3f, 0f, 1f, 15f, 3.4f, cL)
            bx(0f, tBot + 8.5f, 0f, 2f * tx + 0.4f, 2f, 2f * tz + 0.4f, cL)
        }
        5 -> {
            for (s in floatArrayOf(1f, -1f)) {
                cone(-1.5f, hTop + 0.5f, s * hr * 0.5f, 4f, 12f, cD, rx = s * -0.3f, sx = 0.4f)
                bx(tx + 0.2f, tBot + th * 0.5f, s * tz * 0.7f, 1.2f, th * 0.9f, 2.4f, cD)
            }
            sph(tx + 1f, tBot + th * 0.5f, 0f, 2.6f, cL)
        }
        6 -> {
            b.color(cD); b.push(); b.translate(hx, hcy + hr * 0.04f, 0f); b.sphere(hr * 1.03f, 16, 6, (PI * 0.42).toFloat()); b.pop()
            for (s in floatArrayOf(1f, -1f)) {
                b.color(cL); b.rod(-3f, hTop - 2f, s * 3f, -9f, hTop + 9.5f, s * 8f, 0.6f)
                sph(-9.2f, hTop + 10f, s * 8.2f, 2f, cL, seg = 8)
                cone(-1f, tTop + 7f, s * (tz + 1.5f), 4f, 15f, cD, rz = s * 0.25f, sx = 0.5f)
            }
            sph(tx + 0.5f, chestY, 0f, 5.5f, cL, 0.3f, 1f, 0.8f, seg = 4)
        }
        7 -> {
            ring(hcy + hr * 0.3f, hr * 0.9f, 1.1f, cL)
            cloths.add(ClothSpec(-(tx + 9.5f * pk + 1f), tTop - 2f, 0f, 0f, tz * 1.7f, 36f, cD, 0.3f, 4f))
            bx(tx + 0.2f, tBot + th * 0.5f, 0f, 1.4f, th * 1.15f, 3.8f, cD, rx = -0.7f)
            for (k in 0 until 3) { b.color(cL); b.push(); b.translate(tx + 1.5f, tBot + th * (0.7f - k * 0.2f), (k - 1) * 5.5f); b.cylY(1.3f, 1.3f, 4f, 8); b.pop() }
            sph(1f, tTop + 1f, tz + 5.5f, 9f, cD, 1f, 0.85f, 0.85f)
        }
        8 -> {
            for (s in floatArrayOf(1f, -1f)) {
                cone(-1f, hTop + 3f, s * hr * 0.62f, 4.2f, 20f, CREAM, rx = s * -0.5f, rz = 0.2f)
                cone(0.5f, tTop + 6f, s * (tz + 3f), 4f, 13f, cD)
                cone(-5f, tTop + 3f, s * (tz + 4f), 3f, 10f, cD, rz = 0.7f)
            }
            cloths.add(ClothSpec(-(tx + 9.5f + 1f), tTop - 2f, 0f, 0f, tz * 1.7f, 40f, cD, 0.3f, 3f))
            bx(tx + 0.3f, chestY + 6f, 0f, 1.2f, 3f, tz * 1.3f, CREAM)
        }
    }
    val body = b.build()

    // legs, built around their hip pivot so they can swing
    val legLen = tBot + 1.5f - 3.2f
    val lr = 3.8f * limb
    val legs = Array(2) { i ->
        val lb = MeshB()
        val col = if (i == 0) cC else cD
        lb.color(col); lb.push(); lb.translate(0f, -legLen / 2f, 0f); lb.cylY(lr, lr * 0.92f, legLen, 10); lb.pop()
        lb.color(0xFF1B2C34.toInt())
        val bw = 6f * limb + 1f
        lb.rrExtrude(2.8f - bw, -legLen - 2.5f, 2.8f + bw, -legLen + 2.5f, 2f, -4.2f * limb, 4.2f * limb, 2)
        lb.build()
    }
    val pivots = arrayOf(floatArrayOf(0f, tBot + 1.5f, tz * 0.5f), floatArrayOf(0f, tBot + 1.5f, -tz * 0.5f))
    return HeroModel(body, legs, pivots, cloths, shY, tz, limb, flames, cC, cD, cL)
}

/** Arms and hands in aim space (origin at the shoulder pivot, +X along the barrel), for one gun. */
fun buildArms(m: HeroModel, g: GunModel, gunKey: String): Mesh3 {
    val b = MeshB()
    val s = (GUN_LEN[gunKey] ?: 40f) / g.w * 1.12f
    val gripX = 10f
    val fx = gripX + (g.fx - g.gx) * s
    val fy = (g.gy - g.fy) * s
    val r = 3f * m.limb
    val z = m.tz + 1.5f
    b.color(m.cC); b.rod(0f, 0f, z, gripX, 0f, 1.2f, r)
    b.color(m.cD); b.rod(0f, 0f, -z, fx, fy, -1.2f, r)
    b.color(m.cL)
    b.push(); b.translate(gripX, 0f, 1.2f); b.sphere(r * 1.25f, 10, 6); b.pop()
    b.push(); b.translate(fx, fy, -1.2f); b.sphere(r * 1.25f, 10, 6); b.pop()
    b.color(m.cC); b.push(); b.translate(0f, 0f, z); b.sphere(r * 1.2f, 10, 6); b.pop()
    b.color(m.cD); b.push(); b.translate(0f, 0f, -z); b.sphere(r * 1.2f, 10, 6); b.pop()
    return b.build()
}

/** One flat hanging cloth segment (width along z, hanging down from y = 0). */
fun buildClothSeg(w: Float, h: Float, color: Int): Mesh3 {
    val b = MeshB()
    b.color(color)
    b.quad(0f, 0f, -w / 2f, 0f, 0f, w / 2f, 0f, -h, w / 2f, 0f, -h, -w / 2f, 1f, 0f, 0f)
    return b.build()
}
