package com.xcluice.jetstrike

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

const val WORLD_W = 2400f
const val WORLD_H = 900f
const val GROUND_Y = 850f
const val GRAV = 1500f
const val SPEED = 250f
const val GOAL = 12
const val PI_F = 3.1415927f

fun rnd(a: Float, b: Float): Float = a + Random.nextFloat() * (b - a)

fun angDiff(a: Float, b: Float): Float {
    var d = b - a
    while (d > PI_F) d -= 2f * PI_F
    while (d < -PI_F) d += 2f * PI_F
    return d
}

class Plat(val x: Float, val y: Float, val w: Float, val h: Float, val wall: Boolean = false)

class Skin(val name: String, hex: String, hexD: String, hexL: String) {
    val c: Int = Color.parseColor(hex)
    val d: Int = Color.parseColor(hexD)
    val l: Int = Color.parseColor(hexL)
}

class Weapon(
    val key: String, val n: String, val rate: Float, val dmg: Float, val spd: Float, val spr: Float,
    val mag: Int, val rel: Float, val pel: Int, val life: Float, val len: Float, val rec: Float, val tr: Float
)

class Diff(val err: Float, val speed: Float)

class Pick(val t: String, val x: Float, val y: Float) {
    var cd = 0f
}

class Ent(val id: Int, val skin: Skin) {
    var bot = true
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var hp = 100f
    var fuel = 100f
    var empty = false
    var alive = false
    var respawn = 0f
    var inv = 0f
    var aim = 0f
    var onG = false
    var run = Random.nextFloat() * 6f
    var wp = "pistol"
    var ammo = 12
    var reload = 0f
    var cool = 0f
    var kills = 0
    var deaths = 0
    var hurt = 0f
    var flash = 0f
    var jetting = false
    var jetSnd = 0f
    var mx = 0f
    var jet = false
    var fire = false
    // bot brain
    var aiT = 0f
    var strafe = if (Random.nextBoolean()) 1f else -1f
    var stuck = 0f
    var bump = 0f
    var force = 0f
    var boost = 0f
    var aiMx = 0f
    var aiJet = false
    var tg: Ent? = null
    var dist = 999f
    var los = false
    var goal: Pick? = null
    var ph = Random.nextFloat() * 10f
}

class Bullet(
    var x: Float, var y: Float, val vx: Float, val vy: Float, val dmg: Float,
    var l: Float, val o: Ent, val tr: Float, val big: Boolean
)

class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float, var l: Float,
    val m: Float, val s: Float, val c: Int, val g: Float, val glow: Boolean
)

class FeedItem(val a: String, val ac: Int, val b: String, val bc: Int) {
    var t = 4f
}

enum class Mode { MENU, PLAY, PAUSE, OVER }

val PLATS: List<Plat> = listOf(
    Plat(0f, 850f, 2400f, 600f),
    Plat(100f, 720f, 280f, 20f), Plat(1000f, 720f, 400f, 20f), Plat(2020f, 720f, 280f, 20f),
    Plat(520f, 580f, 260f, 20f), Plat(1620f, 580f, 260f, 20f),
    Plat(250f, 430f, 240f, 20f), Plat(980f, 430f, 440f, 20f), Plat(1900f, 430f, 240f, 20f),
    Plat(620f, 300f, 220f, 20f), Plat(1560f, 300f, 220f, 20f),
    Plat(1080f, 200f, 240f, 20f),
    Plat(700f, 760f, 24f, 90f, true), Plat(1676f, 760f, 24f, 90f, true)
)

val SPAWNS: List<FloatArray> = listOf(
    floatArrayOf(200f, 720f), floatArrayOf(1200f, 720f), floatArrayOf(2150f, 720f),
    floatArrayOf(650f, 580f), floatArrayOf(1750f, 580f),
    floatArrayOf(360f, 430f), floatArrayOf(1200f, 430f), floatArrayOf(2020f, 430f),
    floatArrayOf(730f, 300f), floatArrayOf(1670f, 300f),
    floatArrayOf(300f, 850f), floatArrayOf(2100f, 850f), floatArrayOf(1200f, 850f),
    floatArrayOf(1200f, 200f)
)

val PICK_DEF: List<Triple<String, Float, Float>> = listOf(
    Triple("sniper", 1200f, 182f), Triple("shotgun", 650f, 562f), Triple("shotgun", 1750f, 562f),
    Triple("rifle", 230f, 702f), Triple("rifle", 2150f, 702f),
    Triple("health", 360f, 412f), Triple("health", 2020f, 412f), Triple("health", 1200f, 702f),
    Triple("health", 1670f, 282f), Triple("health", 730f, 282f)
)

val SKINS: List<Skin> = listOf(
    Skin("You", "#19e3c8", "#0b8f86", "#a6fff1"),
    Skin("Rook", "#ff8a3d", "#b34a12", "#ffd3ae"),
    Skin("Vex", "#ffd23f", "#a67c00", "#fff2ae"),
    Skin("Blitz", "#5ee06b", "#238a30", "#ccffd0"),
    Skin("Nova", "#ff4f6d", "#a3173a", "#ffc3ce")
)

val WEAPONS: Map<String, Weapon> = mapOf(
    "pistol" to Weapon("pistol", "Pistol", .26f, 16f, 1000f, .025f, 12, 1.0f, 1, .75f, 15f, 30f, .02f),
    "rifle" to Weapon("rifle", "Assault Rifle", .095f, 9f, 1150f, .05f, 30, 1.5f, 1, .8f, 26f, 22f, .025f),
    "shotgun" to Weapon("shotgun", "Shotgun", .75f, 9f, 900f, .2f, 6, 1.7f, 7, .34f, 23f, 170f, .02f),
    "sniper" to Weapon("sniper", "Sniper", 1.05f, 62f, 2100f, .004f, 5, 2.0f, 1, .9f, 34f, 140f, .05f)
)

val DIFFS: Map<String, Diff> = mapOf(
    "easy" to Diff(.24f, 6f),
    "normal" to Diff(.11f, 10f),
    "hard" to Diff(.045f, 16f)
)

class Game {
    var mode = Mode.MENU
    var diff = "normal"
    val ents = ArrayList<Ent>()
    val bullets = ArrayList<Bullet>()
    val parts = ArrayList<Particle>()
    val picks = ArrayList<Pick>()
    val feed = ArrayList<FeedItem>()
    lateinit var player: Ent
    var winner: Ent? = null

    var t = 0f
    var matchTime = 0f
    var shake = 0f
    var vigT = 0f
    var camX = 800f
    var camY = 300f
    var vw = 800f
    var vh = 450f

    // touch sticks, set by the view
    var lx = 0f
    var ly = 0f
    var lOn = false
    var rx = 0f
    var ry = 0f
    var rOn = false

    // (sound name, volume 0..1)
    var sfx: (String, Float) -> Unit = { _, _ -> }

    init {
        newGame(true)
    }

    private fun snd(name: String, x: Float, y: Float) {
        if (mode != Mode.PLAY) return
        val d = hypot(x - player.x, y - player.y)
        val v = max(0f, 1f - d / 1000f)
        if (v > 0.02f) sfx(name, v)
    }

    fun newGame(attract: Boolean) {
        ents.clear(); bullets.clear(); parts.clear(); picks.clear(); feed.clear()
        for (i in 0 until 5) ents.add(Ent(i, SKINS[i]))
        player = ents[0]
        player.bot = attract
        for (e in ents) spawn(e)
        for (d in PICK_DEF) picks.add(Pick(d.first, d.second, d.third))
        matchTime = 0f; shake = 0f; vigT = 0f; winner = null
        camX = player.x - 300f; camY = player.y - 300f
    }

    fun showMenu() {
        mode = Mode.MENU
        newGame(true)
        updateCam(0f, true)
    }

    fun startGame() {
        newGame(false)
        mode = Mode.PLAY
        updateCam(0f, true)
    }

    fun pause() {
        if (mode == Mode.PLAY) {
            mode = Mode.PAUSE
            lOn = false; rOn = false; lx = 0f; ly = 0f; rx = 0f; ry = 0f
        }
    }

    fun resume() {
        if (mode == Mode.PAUSE) mode = Mode.PLAY
    }

    private fun endGame(w: Ent) {
        mode = Mode.OVER
        winner = w
    }

    private fun spawn(e: Ent) {
        var best = SPAWNS[0]
        var bs = -1f
        for (s in SPAWNS) {
            var md = 1e9f
            for (o in ents) {
                if (o !== e && o.alive) md = min(md, hypot(o.x - s[0], o.y - s[1]))
            }
            md += Random.nextFloat() * 150f
            if (md > bs) { bs = md; best = s }
        }
        e.x = best[0]; e.y = best[1]; e.vx = 0f; e.vy = 0f
        e.hp = 100f; e.fuel = 100f; e.empty = false; e.alive = true; e.inv = 2f
        e.wp = "pistol"; e.ammo = WEAPONS.getValue("pistol").mag; e.reload = 0f; e.cool = .3f; e.hurt = 0f
        e.aim = if (e.x < WORLD_W / 2f) 0f else PI_F
    }

    /* ---------- main update ---------- */
    fun update(dt: Float) {
        t += dt
        if (mode == Mode.PLAY || mode == Mode.MENU) {
            if (mode == Mode.PLAY) {
                matchTime += dt
                playerInput()
            }
            for (e in ents) stepEnt(e, dt)
            for (p in picks) if (p.cd > 0f) p.cd -= dt
            stepBullets(dt)
            stepParts(dt)
            shake = max(0f, shake - dt * 30f)
            updateCam(dt, false)
            if (mode == Mode.MENU) {
                for (e in ents) if (e.kills >= GOAL) { for (q in ents) q.kills = 0; break }
            }
        }
        vigT = max(0f, vigT - dt * 1.6f)
        var i = feed.size - 1
        while (i >= 0) {
            feed[i].t -= dt
            if (feed[i].t <= 0f) feed.removeAt(i)
            i--
        }
    }

    private fun playerInput() {
        val p = player
        if (!p.alive || p.bot) { p.mx = 0f; p.jet = false; p.fire = false; return }
        var mx = 0f
        var jet = false
        var fire = false
        val dz = .18f
        if (lOn) {
            if (abs(lx) > dz) mx = sign(lx) * min(1f, (abs(lx) - dz) / (1f - dz) * 1.35f)
            jet = ly < -.45f
        }
        val rm = hypot(rx, ry)
        if (rOn && rm > .12f) {
            p.aim = atan2(ry, rx)
            fire = rm > .4f
        } else if (mx != 0f && !rOn) {
            p.aim = if (mx > 0f) 0f else PI_F
        }
        p.mx = mx; p.jet = jet; p.fire = fire
    }

    /* ---------- physics ---------- */
    private fun hitRect(e: Ent, p: Plat): Boolean =
        e.x - 11f < p.x + p.w && e.x + 11f > p.x && e.y - 44f < p.y + p.h && e.y > p.y

    private fun moveEnt(e: Ent, dt: Float) {
        e.x += e.vx * dt
        for (p in PLATS) {
            if (hitRect(e, p)) {
                if (e.vx > 0f) e.x = p.x - 11f
                else if (e.vx < 0f) e.x = p.x + p.w + 11f
                else e.x += if (e.x < p.x + p.w / 2f) -1f else 1f
                e.vx = 0f
            }
        }
        e.x = max(14f, min(WORLD_W - 14f, e.x))
        e.y += e.vy * dt
        e.onG = false
        for (p in PLATS) {
            if (hitRect(e, p)) {
                if (e.vy >= 0f) { e.y = p.y; e.onG = true } else { e.y = p.y + p.h + 44f }
                e.vy = 0f
            }
        }
        if (e.y - 44f < 0f) { e.y = 44f; if (e.vy < 0f) e.vy = 0f }
    }

    private fun los(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val n = ceil(hypot(x2 - x1, y2 - y1) / 18f).toInt()
        for (i in 1 until n) {
            val x = x1 + (x2 - x1) * i / n
            val y = y1 + (y2 - y1) * i / n
            for (p in PLATS) {
                if (x > p.x && x < p.x + p.w && y > p.y && y < p.y + p.h) return false
            }
        }
        return true
    }

    /* ---------- particles ---------- */
    private fun part(x: Float, y: Float, vx: Float, vy: Float, life: Float, size: Float, color: Int, grav: Float, glow: Boolean) {
        if (parts.size > 700) return
        parts.add(Particle(x, y, vx, vy, life, life, size, color, grav, glow))
    }

    private fun burst(x: Float, y: Float, color: Int, n: Int, sp: Float, grav: Float) {
        for (i in 0 until n) {
            val a = rnd(0f, 6.283f)
            val s = rnd(.3f, 1f) * sp
            part(x, y, cos(a) * s, sin(a) * s, rnd(.3f, .7f), rnd(2f, 4.5f), color, grav, false)
        }
    }

    private fun stepParts(dt: Float) {
        var i = parts.size - 1
        while (i >= 0) {
            val p = parts[i]
            p.vy += p.g * dt; p.x += p.vx * dt; p.y += p.vy * dt; p.l -= dt
            if (p.l <= 0f) {
                val last = parts.size - 1
                parts[i] = parts[last]
                parts.removeAt(last)
            }
            i--
        }
    }

    /* ---------- combat ---------- */
    private fun startReload(e: Ent) {
        val w = WEAPONS.getValue(e.wp)
        if (e.reload > 0f || e.ammo >= w.mag) return
        e.reload = w.rel
        snd("reload", e.x, e.y)
    }

    private fun tryFire(e: Ent) {
        val w = WEAPONS.getValue(e.wp)
        if (e.reload > 0f || e.cool > 0f) return
        if (e.ammo <= 0) { startReload(e); return }
        e.cool = w.rate; e.ammo--; e.flash = .06f
        val sx = e.x
        val sy = e.y - 28f
        val mxp = sx + cos(e.aim) * (w.len + 8f)
        val myp = sy + sin(e.aim) * (w.len + 8f)
        for (i in 0 until w.pel) {
            val a = e.aim + (Random.nextFloat() - .5f) * 2f * w.spr
            val sp = w.spd * (if (w.pel > 1) rnd(.85f, 1.05f) else 1f)
            bullets.add(Bullet(mxp, myp, cos(a) * sp, sin(a) * sp, w.dmg, w.life, e, w.tr, w.key == "sniper"))
        }
        e.vx -= cos(e.aim) * w.rec * .5f
        e.vy -= sin(e.aim) * w.rec * .15f
        for (k in 0 until 3) {
            part(mxp, myp, cos(e.aim) * rnd(80f, 260f) + rnd(-60f, 60f), sin(e.aim) * rnd(80f, 260f) + rnd(-60f, 60f),
                .14f, rnd(2f, 4f), 0xFFFFE08A.toInt(), 0f, true)
        }
        if (e === player && !e.bot) shake = max(shake, if (e.wp == "shotgun") 7f else if (e.wp == "sniper") 9f else 2f)
        snd(e.wp, e.x, e.y)
        if (e.ammo <= 0) startReload(e)
    }

    private fun damage(e: Ent, dmg: Float, by: Ent, ang: Float) {
        if (!e.alive || e.inv > 0f) return
        e.hp -= dmg; e.hurt = .12f
        e.vx += cos(ang) * dmg * 2.2f
        burst(e.x, e.y - 26f, e.skin.c, 5, 220f, 500f)
        snd("hit", e.x, e.y)
        if (e === player && mode == Mode.PLAY) { vigT = 1f; shake = max(shake, 5f) }
        if (e.hp <= 0f) die(e, by)
    }

    private fun die(e: Ent, by: Ent) {
        e.alive = false; e.respawn = 3f; e.deaths++; e.hp = 0f
        burst(e.x, e.y - 22f, e.skin.c, 26, 420f, 700f)
        burst(e.x, e.y - 22f, e.skin.l, 10, 300f, 700f)
        for (i in 0 until 8) part(e.x, e.y - 22f, rnd(-200f, 200f), rnd(-300f, -60f), rnd(.5f, 1f), rnd(4f, 8f), 0xFFFF9B3D.toInt(), 500f, true)
        snd("die", e.x, e.y)
        if (by !== e) {
            by.kills++
            if (mode == Mode.PLAY) {
                feed.add(FeedItem(by.skin.name, by.skin.c, e.skin.name, e.skin.c))
                while (feed.size > 4) feed.removeAt(0)
            }
        }
        if (mode == Mode.PLAY && by !== e && by.kills >= GOAL) endGame(by)
    }

    private fun nearPick(e: Ent, healthOnly: Boolean): Pick? {
        var best: Pick? = null
        var bd = 1e9f
        for (p in picks) {
            if (p.cd > 0f) continue
            if (healthOnly && p.t != "health") continue
            if (!healthOnly && p.t == "health") continue
            val d = hypot(p.x - e.x, p.y - e.y)
            if (d < bd) { bd = d; best = p }
        }
        return best
    }

    /* ---------- bots ---------- */
    private fun botThink(e: Ent, dt: Float) {
        val D = DIFFS.getValue(diff)
        e.aiT -= dt; e.boost -= dt; e.force -= dt
        if (e.aiT <= 0f) {
            e.aiT = .12f + Random.nextFloat() * .1f
            var tg: Ent? = null
            var bd = 1e9f
            for (o in ents) {
                if (o === e || !o.alive) continue
                val d = hypot(o.x - e.x, o.y - e.y)
                if (d < bd) { bd = d; tg = o }
            }
            e.tg = tg; e.dist = bd
            e.los = if (tg != null) los(e.x, e.y - 28f, tg.x, tg.y - 24f) else false
            e.goal = null
            if (e.hp < 50f) e.goal = nearPick(e, true)
            else if (e.wp == "pistol" && (tg == null || bd > 260f || !e.los)) e.goal = nearPick(e, false)
            var mx = 0f
            var jet = false
            val g = e.goal
            if (g != null) {
                val gx = g.x - e.x
                val gy = g.y - e.y
                mx = if (abs(gx) > 14f) sign(gx) else 0f
                jet = gy < -50f || (gy < -20f && abs(gx) < 40f)
            } else if (tg != null) {
                val dx = tg.x - e.x
                val dy = tg.y - e.y
                val ideal = if (e.wp == "shotgun") 170f else if (e.wp == "sniper") 520f else 320f
                if (bd > ideal + 70f) mx = sign(dx)
                else if (bd < ideal - 70f) mx = -sign(dx)
                else {
                    if (Random.nextFloat() < .25f) e.strafe *= -1f
                    mx = e.strafe * .7f
                }
                jet = dy < -60f
            }
            if (Random.nextFloat() < .07f) e.boost = .25f + Random.nextFloat() * .3f
            e.aiMx = mx; e.aiJet = jet
        }
        if (e.onG && abs(e.vx) < 25f && abs(e.aiMx) > .3f) e.stuck += dt else e.stuck = 0f
        if (e.stuck > .3f) { e.boost = .5f; e.stuck = 0f; e.strafe *= -1f }
        if (!e.onG && (e.aiJet || e.boost > 0f) && e.vy > -40f) e.bump += dt else e.bump = 0f
        if (e.bump > .35f) { e.force = .5f; e.strafe = if (Random.nextBoolean()) 1f else -1f; e.bump = 0f }
        e.mx = if (e.force > 0f) e.strafe else e.aiMx
        e.jet = (e.aiJet || e.boost > 0f) && !e.empty
        var des = e.aim
        val tg2 = e.tg
        if (tg2 != null && tg2.alive && e.los) {
            val tx = tg2.x + tg2.vx * .12f
            val ty = tg2.y - 24f + tg2.vy * .12f
            des = atan2(ty - (e.y - 28f), tx - e.x) + sin(t * 1.9f + e.ph) * D.err
        } else if (e.mx != 0f) {
            des = if (e.mx > 0f) 0f else PI_F
        }
        e.aim += angDiff(e.aim, des) * min(1f, D.speed * dt)
        e.fire = tg2 != null && tg2.alive && e.los &&
            e.dist < (if (e.wp == "sniper") 950f else 620f) && abs(angDiff(e.aim, des)) < .22f
    }

    private fun stepEnt(e: Ent, dt: Float) {
        if (!e.alive) {
            e.respawn -= dt
            if (e.respawn <= 0f) spawn(e)
            return
        }
        e.inv = max(0f, e.inv - dt); e.hurt = max(0f, e.hurt - dt); e.cool -= dt; e.flash = max(0f, e.flash - dt)
        if (e.bot) botThink(e, dt)
        val tv = e.mx * SPEED
        val acc = if (e.onG) 2600f else 1200f
        if (e.vx < tv) e.vx = min(tv, e.vx + acc * dt) else if (e.vx > tv) e.vx = max(tv, e.vx - acc * dt)
        if (e.fuel <= 0f) e.empty = true
        if (e.empty && e.fuel > 14f) e.empty = false
        e.jetting = false
        if (e.jet && !e.empty) {
            e.jetting = true
            if (e.onG && e.vy >= 0f) e.vy = -170f
            e.vy -= 2900f * dt
            if (e.vy < -340f) e.vy = -340f
            e.fuel = max(0f, e.fuel - 30f * dt)
            val dirj = if (cos(e.aim) >= 0f) 1f else -1f
            for (k in 0 until 2) {
                part(e.x - dirj * 10f + rnd(-2f, 2f), e.y - 12f, rnd(-40f, 40f), rnd(180f, 320f) + e.vy * .1f,
                    rnd(.22f, .4f), rnd(3f, 6.5f), if (k == 1) 0xFFFFD35C.toInt() else 0xFFFF8A3D.toInt(), 0f, true)
            }
            e.jetSnd -= dt
            if (e.jetSnd <= 0f) { e.jetSnd = .12f; if (e === player) snd("jet", e.x, e.y) }
        } else {
            e.fuel = min(100f, e.fuel + (if (e.onG) 32f else 6f) * dt)
        }
        e.vy += GRAV * dt
        if (e.vy > 900f) e.vy = 900f
        moveEnt(e, dt)
        if (e.onG && abs(e.vx) > 20f) e.run += abs(e.vx) * dt * .055f else if (!e.onG) e.run += dt * 4f
        if (e.reload > 0f) {
            e.reload -= dt
            if (e.reload <= 0f) { e.reload = 0f; e.ammo = WEAPONS.getValue(e.wp).mag }
        }
        if (e.fire) tryFire(e)
        for (p in picks) {
            if (p.cd > 0f) continue
            if (abs(e.x - p.x) < 26f && p.y > e.y - 50f && p.y < e.y + 10f) {
                if (p.t == "health") {
                    if (e.hp >= 100f) continue
                    e.hp = min(100f, e.hp + 45f)
                } else {
                    e.wp = p.t; e.ammo = WEAPONS.getValue(p.t).mag; e.reload = 0f; e.cool = .15f
                }
                p.cd = 14f
                snd("pick", p.x, p.y)
                burst(p.x, p.y, 0xFFFFFFFF.toInt(), 10, 200f, 200f)
            }
        }
    }

    private fun stepBullets(dt: Float) {
        var i = bullets.size - 1
        while (i >= 0) {
            val b = bullets[i]
            val steps = max(1, ceil(hypot(b.vx, b.vy) * dt / 10f).toInt())
            val sdt = dt / steps
            var dead = false
            var s = 0
            while (s < steps && !dead) {
                b.x += b.vx * sdt; b.y += b.vy * sdt
                for (p in PLATS) {
                    if (b.x > p.x && b.x < p.x + p.w && b.y > p.y && b.y < p.y + p.h) {
                        dead = true
                        burst(b.x, b.y, 0xFFFFE08A.toInt(), 5, 200f, 300f)
                        break
                    }
                }
                if (!dead) {
                    for (e in ents) {
                        if (!e.alive || e === b.o) continue
                        if (abs(b.x - e.x) < 12f && b.y > e.y - 48f && b.y < e.y + 2f) {
                            if (e.inv > 0f) continue
                            damage(e, b.dmg, b.o, atan2(b.vy, b.vx))
                            dead = true
                            break
                        }
                    }
                }
                s++
            }
            b.l -= dt
            if (dead || b.l <= 0f || b.x < -50f || b.x > WORLD_W + 50f || b.y < -200f || b.y > WORLD_H + 100f) {
                val last = bullets.size - 1
                bullets[i] = bullets[last]
                bullets.removeAt(last)
            }
            i--
        }
    }

    /* ---------- camera ---------- */
    private fun focusEnt(): Ent {
        if (mode == Mode.MENU) {
            val f = ents[floor(t / 6f).toInt() % ents.size]
            return if (f.alive) f else player
        }
        return player
    }

    fun updateCam(dt: Float, snap: Boolean) {
        val f = focusEnt()
        val lead = if (f.alive) cos(f.aim) * 60f else 0f
        var tx = f.x + lead - vw / 2f
        var ty = f.y - 110f - vh / 2f
        tx = if (vw >= WORLD_W) (WORLD_W - vw) / 2f else max(0f, min(WORLD_W - vw, tx))
        ty = if (vh >= WORLD_H) (WORLD_H - vh) / 2f else max(0f, min(WORLD_H - vh, ty))
        val k = if (snap) 1f else 1f - exp(-5f * dt)
        camX += (tx - camX) * k
        camY += (ty - camY) * k
    }
}
