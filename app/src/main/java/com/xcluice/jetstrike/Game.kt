package com.xcluice.jetstrike

import android.graphics.Color
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
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

const val WORLD_W = 4200f
const val WORLD_H = 1300f
const val GROUND_Y = 1250f
const val GRAV = 1500f
const val SPEED = 250f
const val GOAL_SOLO = 15
const val GOAL_TEAM = 25
const val PI_F = 3.1415927f
const val GREN_R = 150f

const val EV_DMG = 1
const val EV_DIE = 2
const val EV_SHOT = 3
const val EV_EXPL = 4
const val EV_PICK = 5
const val EV_FEED = 6
const val EV_HEAL = 7
const val EV_SPARK = 8
const val EV_THROW = 9

fun rnd(a: Float, b: Float): Float = a + Random.nextFloat() * (b - a)

fun angDiff(a: Float, b: Float): Float {
    var d = b - a
    while (d > PI_F) d -= 2f * PI_F
    while (d < -PI_F) d += 2f * PI_F
    return d
}

class Plat(val x: Float, val y: Float, val w: Float, val h: Float, val wall: Boolean = false)

class Skin(val name: String, hex: String, hexD: String, hexL: String, val acc: Int = 0) {
    val c: Int = Color.parseColor(hex)
    val d: Int = Color.parseColor(hexD)
    val l: Int = Color.parseColor(hexL)
}

class Weapon(
    val key: String, val n: String, val rate: Float, val dmg: Float, val spd: Float, val spr: Float,
    val mag: Int, val rel: Float, val pel: Int, val life: Float, val len: Float, val rec: Float, val tr: Float,
    val scopes: FloatArray, val reticle: Int, val kind: Int
)

class Diff(val err: Float, val speed: Float)

class Pick(val t: String, val x: Float, val y: Float) {
    var cd = 0f
}

class Ent(val id: Int, var skin: Skin, val team: Int) {
    var name = ""
    var human = false
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
    val wps = arrayOf<String?>("pistol", null)
    val ammos = intArrayOf(12, 0)
    var cur = 0
    val wp: String
        get() = wps[cur] ?: "pistol"
    var ammo: Int
        get() = ammos[cur]
        set(v) { ammos[cur] = v }
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
    var grenades = 3
    var gCool = 0f
    var gDone = 0
    var sDone = 0
    var rDone = 0
    // network interpolation targets
    var tx = 0f
    var ty = 0f
    var tvx = 0f
    var tvy = 0f
    var taim = 0f
    // bot brain
    var aiT = 0f
    var strafe = if (Random.nextBoolean()) 1f else -1f
    var stuck = 0f
    var bump = 0f
    var force = 0f
    var boost = 0f
    var aiMx = 0f
    var aiJet = false
    var jetT = 0f
    var restT = 0f
    var seenT = 0f
    var tg: Ent? = null
    var dist = 999f
    var los = false
    var goal: Pick? = null
    var ph = Random.nextFloat() * 10f
}

class Bullet(
    var x: Float, var y: Float, val vx: Float, val vy: Float, val dmg: Float,
    var l: Float, val o: Ent?, val team: Int, val tr: Float, val big: Boolean, val kind: Int = 0
)

class Grenade(var x: Float, var y: Float, var vx: Float, var vy: Float, var fuse: Float, val oid: Int, val team: Int)

class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float, var l: Float,
    val m: Float, val s: Float, val c: Int, val g: Float, val glow: Boolean
)

class DmgText(var x: Float, var y: Float, val text: String, val col: Int) {
    var l = 1.0f
}

class FeedItem(val a: String, val ac: Int, val b: String, val bc: Int) {
    var t = 4f
}

class Ev(val typ: Int, val x: Float, val y: Float, val a: Float, val b: Float)

enum class Mode { MENU, PLAY, OVER, HOST, JOIN, WAIT }
enum class Rules { TEAM, SOLO }
enum class Role { OFFLINE, HOST, CLIENT }

val WEAPON_KEYS: List<String> = listOf("pistol", "rifle", "shotgun", "sniper", "smg", "rocket", "flame", "laser")
val DIFF_KEYS: List<String> = listOf("easy", "normal", "hard")
val BOT_NAMES: List<String> = listOf("Rook", "Vex", "Blitz", "Echo", "Flux", "Zane", "Mako", "Juno")

fun wIdx(k: String?): Int = if (k == null) -1 else WEAPON_KEYS.indexOf(k)

private val HALF: List<FloatArray> = listOf(
    floatArrayOf(150f, 1110f, 280f), floatArrayOf(620f, 1110f, 320f), floatArrayOf(1350f, 1110f, 420f),
    floatArrayOf(380f, 960f, 260f), floatArrayOf(960f, 960f, 300f), floatArrayOf(1650f, 960f, 300f),
    floatArrayOf(120f, 810f, 260f), floatArrayOf(700f, 810f, 260f), floatArrayOf(1300f, 810f, 320f),
    floatArrayOf(420f, 660f, 280f), floatArrayOf(1000f, 660f, 260f), floatArrayOf(1600f, 660f, 260f),
    floatArrayOf(150f, 510f, 240f), floatArrayOf(760f, 510f, 300f), floatArrayOf(1380f, 510f, 260f),
    floatArrayOf(480f, 360f, 260f), floatArrayOf(1080f, 360f, 280f)
)

private val CENTER: List<FloatArray> = listOf(
    floatArrayOf(1850f, 1110f, 500f), floatArrayOf(1900f, 810f, 400f),
    floatArrayOf(1850f, 510f, 500f), floatArrayOf(1750f, 230f, 700f)
)

val PLATS: List<Plat> = buildList {
    add(Plat(0f, GROUND_Y, WORLD_W, 600f))
    for (h in HALF) {
        add(Plat(h[0], h[1], h[2], 20f))
        add(Plat(WORLD_W - h[0] - h[2], h[1], h[2], 20f))
    }
    for (h in CENTER) add(Plat(h[0], h[1], h[2], 20f))
    add(Plat(1000f, GROUND_Y - 90f, 24f, 90f, true))
    add(Plat(WORLD_W - 1024f, GROUND_Y - 90f, 24f, 90f, true))
    add(Plat(2088f, GROUND_Y - 120f, 24f, 120f, true))
}

val SPAWNS: List<FloatArray> = buildList {
    for (i in 1 until PLATS.size) {
        val pl = PLATS[i]
        if (!pl.wall) add(floatArrayOf(pl.x + pl.w / 2f, pl.y))
    }
    for (x in listOf(300f, 900f, 1500f, 2700f, 3300f, 3900f)) add(floatArrayOf(x, GROUND_Y))
}

val PICK_DEF: List<Triple<String, Float, Float>> = buildList {
    val guns = listOf("rifle", "shotgun", "smg", "laser", "rocket", "flame", "sniper", "smg")
    var gi = 0
    for (i in 1 until PLATS.size) {
        val pl = PLATS[i]
        if (pl.wall || pl.y <= 230f) continue
        val cx = pl.x + pl.w / 2f
        when (i % 4) {
            0 -> add(Triple("health", cx, pl.y - 18f))
            1 -> add(Triple("gren", cx, pl.y - 18f))
            2 -> { add(Triple(guns[gi % guns.size], cx, pl.y - 18f)); gi++ }
        }
    }
    add(Triple("sniper", 2100f, 230f - 18f))
    add(Triple("health", 2100f, GROUND_Y - 18f))
    add(Triple("rifle", 600f, GROUND_Y - 18f)); add(Triple("rifle", 3600f, GROUND_Y - 18f))
    add(Triple("shotgun", 1500f, GROUND_Y - 18f)); add(Triple("shotgun", 2700f, GROUND_Y - 18f))
    add(Triple("gren", 1100f, GROUND_Y - 18f)); add(Triple("gren", 3100f, GROUND_Y - 18f))
}

val SKINS: List<Skin> = listOf(
    Skin("Nova", "#19e3c8", "#0b8f86", "#a6fff1", 0),
    Skin("Blaze", "#ff8a3d", "#b34a12", "#ffd3ae", 1),
    Skin("Frost", "#7cc8ff", "#2a6fae", "#dff2ff", 2),
    Skin("Viper", "#7ee05c", "#2f8a22", "#d6ffc2", 3),
    Skin("Sol", "#ffd23f", "#a67c00", "#fff2ae", 4),
    Skin("Bloom", "#ff6b9a", "#a3305a", "#ffc7da", 5),
    Skin("Onyx", "#6f8296", "#2f3d4b", "#c4d3e0", 6),
    Skin("Rust", "#c97b4a", "#6a3a1a", "#f0c9a6", 7)
)

val WEAPONS: Map<String, Weapon> = mapOf(
    "pistol" to Weapon("pistol", "Pistol", .26f, 16f, 1000f, .025f, 12, 1.0f, 1, .75f, 15f, 30f, .02f, floatArrayOf(4f, 5f), 0, 0),
    "smg" to Weapon("smg", "SMG", .06f, 6f, 1100f, .09f, 40, 1.4f, 1, .7f, 17f, 10f, .02f, floatArrayOf(4f, 5f), 0, 0),
    "rifle" to Weapon("rifle", "Assault Rifle", .095f, 9f, 1150f, .05f, 30, 1.5f, 1, .8f, 26f, 22f, .025f, floatArrayOf(4f, 6f), 1, 0),
    "shotgun" to Weapon("shotgun", "Shotgun", .75f, 9f, 900f, .2f, 6, 1.7f, 7, .34f, 23f, 170f, .02f, floatArrayOf(4f, 2f), 2, 0),
    "rocket" to Weapon("rocket", "Rocket Launcher", 1.2f, 0f, 650f, .01f, 3, 2.4f, 1, 2.0f, 30f, 160f, .03f, floatArrayOf(4f, 3f), 3, 1),
    "flame" to Weapon("flame", "Flamethrower", .06f, 5f, 520f, .22f, 100, 2.2f, 1, .38f, 20f, 6f, .01f, floatArrayOf(4f, 3f), 0, 2),
    "laser" to Weapon("laser", "Laser Rifle", .42f, 24f, 3200f, 0f, 12, 1.8f, 1, .5f, 28f, 40f, .03f, floatArrayOf(4f, 6f), 1, 3),
    "sniper" to Weapon("sniper", "Sniper", 1.05f, 62f, 2100f, .004f, 5, 2.0f, 1, .9f, 34f, 140f, .05f, floatArrayOf(4f, 7f, 6f, 5f), 4, 0)
)

val DIFFS: Map<String, Diff> = mapOf(
    "easy" to Diff(.32f, 3.5f),
    "normal" to Diff(.17f, 6.5f),
    "hard" to Diff(.08f, 11f)
)

class Game {
    var role = Role.OFFLINE
    var mySkin = 0
    var myName = "Player"
    var rules = Rules.SOLO
    var menuRules = Rules.TEAM
    var mode = Mode.MENU
    var paused = false
    var diff = "normal"
    var net: Net? = null

    val ents = ArrayList<Ent>()
    val bullets = ArrayList<Bullet>()
    val grenades = ArrayList<Grenade>()
    val parts = ArrayList<Particle>()
    val texts = ArrayList<DmgText>()
    val picks = ArrayList<Pick>()
    val feed = ArrayList<FeedItem>()
    lateinit var player: Ent
    var localId = 0
    var humans = 0
    var winId = -1
    var winTeam = -1

    var t = 0f
    var matchTime = 0f
    var shake = 0f
    var vigT = 0f
    var camX = 800f
    var camY = 300f
    var vw = 800f
    var vh = 450f
    var panX = 0f
    var panY = 0f
    var panning = false
    var scopeIdx = 0
    var zoomCur = 4f
    private var lastWp = "pistol"

    // touch sticks and button requests, set by the view
    var lx = 0f
    var ly = 0f
    var lOn = false
    var rx = 0f
    var ry = 0f
    var rOn = false
    var reqG = 0
    var reqS = 0
    var reqSV = 0
    var reqR = 0

    // online
    val remotes = arrayOfNulls<RemoteIn>(8)
    private val outEvents = ArrayList<Ev>()
    private var snapAcc = 0f
    private var inAcc = 0f
    var lobbyHumans = 1
    var notice = ""
    var noticeT = 0f

    // (sound name, volume 0..1)
    var sfx: (String, Float) -> Unit = { _, _ -> }

    init {
        buildEnts(Rules.SOLO, 0, 0)
    }

    val online: Boolean
        get() = role != Role.OFFLINE

    fun scopes(): FloatArray = WEAPONS.getValue(player.wp).scopes

    fun zoomLevel(): Float {
        val l = scopes()
        return l[scopeIdx.coerceIn(0, l.size - 1)]
    }

    fun scopeStep() {
        scopeIdx = (scopeIdx + 1) % scopes().size
    }

    fun diffIndex(): Int = max(0, DIFF_KEYS.indexOf(diff))

    fun nameOf(id: Int): String = ents.getOrNull(id)?.name ?: "?"

    fun teamKills(team: Int): Int {
        var s = 0
        for (e in ents) if (e.team == team) s += e.kills
        return s
    }

    fun isMe(e: Ent): Boolean = e.human && e.id == localId

    fun note(s: String) {
        notice = s
        noticeT = 4f
    }

    /* ---------- setup ---------- */
    private fun buildEnts(r: Rules, humanCount: Int, local: Int, pSkins: IntArray? = null, pNames: List<String>? = null) {
        rules = r
        humans = humanCount
        localId = local
        ents.clear(); bullets.clear(); grenades.clear(); parts.clear(); texts.clear(); picks.clear(); feed.clear()
        outEvents.clear()
        val n = if (r == Rules.TEAM) 8 else max(7, humanCount)
        val used = HashSet<Int>()
        for (i in 0 until humanCount) used.add((pSkins?.getOrNull(i) ?: mySkin).coerceIn(0, SKINS.size - 1))
        var bs = 0
        for (i in 0 until n) {
            val human = i < humanCount
            val si: Int
            if (human) {
                si = (pSkins?.getOrNull(i) ?: mySkin).coerceIn(0, SKINS.size - 1)
            } else {
                var v = bs % SKINS.size
                var guard = 0
                while (used.contains(v) && guard < SKINS.size) { bs++; v = bs % SKINS.size; guard++ }
                used.add(v); bs++
                si = v
            }
            val e = Ent(i, SKINS[si], if (r == Rules.TEAM) i / 4 else i)
            e.human = human
            e.bot = !human
            e.name = if (human) (pNames?.getOrNull(i) ?: (if (i == local) myName else "P${i + 1}")) else BOT_NAMES[i % BOT_NAMES.size]
            ents.add(e)
        }
        player = ents[min(local, ents.size - 1)]
        for (e in ents) spawn(e)
        for (d in PICK_DEF) picks.add(Pick(d.first, d.second, d.third))
        matchTime = 0f; shake = 0f; vigT = 0f; winId = -1; winTeam = -1
        panX = 0f; panY = 0f
        camX = player.x - 300f; camY = player.y - 300f
    }

    fun showMenu() {
        net?.shutdown()
        role = Role.OFFLINE
        mode = Mode.MENU
        paused = false
        buildEnts(Rules.SOLO, 0, 0)
        updateCam(0f, true)
    }

    fun startOffline() {
        net?.shutdown()
        role = Role.OFFLINE
        buildEnts(menuRules, 1, 0, intArrayOf(mySkin), listOf(myName))
        mode = Mode.PLAY
        paused = false
        updateCam(0f, true)
    }

    fun restartMatch() {
        if (role == Role.HOST) hostBegin() else startOffline()
    }

    fun pause() {
        if (mode == Mode.PLAY) {
            paused = true
            lOn = false; rOn = false; lx = 0f; ly = 0f; rx = 0f; ry = 0f
        }
    }

    fun resume() {
        paused = false
    }

    fun hostOpen() {
        val n = net ?: return
        buildEnts(Rules.SOLO, 0, 0)
        if (n.startHosting(menuRules.ordinal, diffIndex())) {
            mode = Mode.HOST
        } else {
            note("Could not open a LAN game. Check your Wi-Fi.")
        }
    }

    fun hostBegin() {
        val n = net ?: return
        val cl = n.activeClients()
        val hc = 1 + cl.size
        role = Role.HOST
        for (i in remotes.indices) remotes[i] = null
        val skins = IntArray(hc)
        val names = ArrayList<String>()
        skins[0] = mySkin; names.add(myName)
        for ((i, c) in cl.withIndex()) { c.playerId = i + 1; skins[i + 1] = c.skin; names.add(c.name) }
        buildEnts(menuRules, hc, 0, skins, names)
        for ((i, c) in cl.withIndex()) {
            remotes[i + 1] = c.rin
            n.sendStart(c, menuRules.ordinal, diffIndex(), c.playerId, hc, skins, names)
        }
        n.started = true
        mode = Mode.PLAY
        paused = false
        snapAcc = 0f
        updateCam(0f, true)
    }

    fun joinOpen() {
        val n = net ?: return
        buildEnts(Rules.SOLO, 0, 0)
        mode = Mode.JOIN
        n.startListening()
    }

    fun joinConnect(ip: String) {
        val n = net ?: return
        n.connect(ip, mySkin, myName)
        mode = Mode.WAIT
        lobbyHumans = 0
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
        e.tx = e.x; e.ty = e.y; e.tvx = 0f; e.tvy = 0f
        e.hp = 100f; e.fuel = 100f; e.empty = false; e.alive = true; e.inv = 2f
        e.wps[0] = "pistol"; e.wps[1] = null; e.ammos[0] = WEAPONS.getValue("pistol").mag; e.ammos[1] = 0; e.cur = 0
        e.reload = 0f; e.cool = .3f; e.hurt = 0f; e.grenades = 3; e.gCool = 0f
        e.aim = if (e.x < WORLD_W / 2f) 0f else PI_F
        e.taim = e.aim
    }

    /* ---------- main update ---------- */
    fun update(dt: Float) {
        t += dt
        if (noticeT > 0f) noticeT -= dt
        if (player.wp != lastWp) { lastWp = player.wp; scopeIdx = 0 }
        if (!player.alive) scopeIdx = 0
        val zt = if (mode == Mode.PLAY || mode == Mode.OVER) zoomLevel() else 4f
        zoomCur += (zt - zoomCur) * (1f - exp(-8f * dt))
        pollNet()
        if (role == Role.CLIENT && (mode == Mode.PLAY || mode == Mode.OVER)) {
            clientUpdate(dt)
        } else if (mode == Mode.PLAY) {
            if (!paused || online) simulate(dt)
        } else if (mode == Mode.OVER) {
            stepParts(dt); stepTexts(dt)
            shake = max(0f, shake - dt * 30f)
            updateCam(dt, false)
            if (role == Role.HOST) netTick(dt)
        } else {
            simulate(dt)
        }
        vigT = max(0f, vigT - dt * 1.6f)
        var i = feed.size - 1
        while (i >= 0) {
            feed[i].t -= dt
            if (feed[i].t <= 0f) feed.removeAt(i)
            i--
        }
    }

    private fun simulate(dt: Float) {
        val playing = mode == Mode.PLAY
        if (playing) {
            matchTime += dt
            playerInput()
            if (!paused) consumeReqs(player, reqG, reqS, reqSV, reqR)
            if (role == Role.HOST) {
                for (e in ents) if (e.human && e.id != localId) pullRemote(e)
            }
        }
        for (e in ents) stepEnt(e, dt)
        for (p in picks) if (p.cd > 0f) p.cd -= dt
        stepBullets(dt)
        stepGrenades(dt)
        stepParts(dt)
        stepTexts(dt)
        shake = max(0f, shake - dt * 30f)
        updateCam(dt, false)
        if (!playing) {
            for (e in ents) if (e.kills >= GOAL_SOLO) { for (q in ents) q.kills = 0; break }
        }
        if (role == Role.HOST) netTick(dt)
    }

    fun playerInput() {
        val p = player
        if (!p.alive || paused || !p.human) { p.mx = 0f; p.jet = false; p.fire = false; return }
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

    private fun pullRemote(e: Ent) {
        val r = remotes[e.id]
        if (r == null || !e.alive) { e.mx = 0f; e.jet = false; e.fire = false; return }
        e.mx = r.mx.coerceIn(-1f, 1f); e.jet = r.jet; e.fire = r.fire; e.aim = r.aim
        consumeReqs(e, r.gCnt, r.sCnt, r.sVal, r.rCnt)
    }

    private fun consumeReqs(e: Ent, g: Int, s: Int, sv: Int, r: Int) {
        if (g != e.gDone) { e.gDone = g; throwGrenade(e) }
        if (s != e.sDone) { e.sDone = s; switchTo(e, sv) }
        if (r != e.rDone) { e.rDone = r; startReload(e) }
    }

    private fun switchTo(e: Ent, slot: Int) {
        if (!e.alive || slot < 0 || slot > 1 || slot == e.cur || e.wps[slot] == null) return
        e.cur = slot
        e.reload = 0f
        e.cool = .2f
    }

    /* ---------- events ---------- */
    private fun emit(typ: Int, x: Float, y: Float, a: Float, b: Float) {
        applyEvent(typ, x, y, a, b)
        if (role == Role.HOST && outEvents.size < 80) outEvents.add(Ev(typ, x, y, a, b))
    }

    private fun snd(name: String, x: Float, y: Float) {
        if (mode != Mode.PLAY && mode != Mode.OVER) return
        val d = hypot(x - player.x, y - player.y)
        val v = max(0f, 1f - d / 1100f)
        if (v > 0.02f) sfx(name, v)
    }

    private fun applyEvent(typ: Int, x: Float, y: Float, a: Float, b: Float) {
        when (typ) {
            EV_DMG -> {
                val v = ents.getOrNull(b.toInt())
                val mine = v != null && isMe(v)
                val col = if (mine) 0xFFFF5A5A.toInt() else 0xFFFFE08A.toInt()
                texts.add(DmgText(x + rnd(-8f, 8f), y, a.toInt().toString(), col))
                if (v != null) {
                    v.hurt = .12f
                    burst(v.x, v.y - 26f, v.skin.c, 5, 220f, 500f)
                    snd("hit", v.x, v.y)
                }
                if (mine && mode == Mode.PLAY) { vigT = 1f; shake = max(shake, 5f) }
            }
            EV_HEAL -> {
                texts.add(DmgText(x, y, "+" + a.toInt().toString(), 0xFF5EE06B.toInt()))
            }
            EV_DIE -> {
                val v = ents.getOrNull(a.toInt())
                val c1 = v?.skin?.c ?: 0xFFFFFFFF.toInt()
                val c2 = v?.skin?.l ?: 0xFFFFFFFF.toInt()
                burst(x, y, c1, 26, 420f, 700f)
                burst(x, y, c2, 10, 300f, 700f)
                for (i in 0 until 8) part(x, y, rnd(-200f, 200f), rnd(-300f, -60f), rnd(.5f, 1f), rnd(4f, 8f), 0xFFFF9B3D.toInt(), 500f, true)
                snd("die", x, y)
            }
            EV_SHOT -> {
                val e = ents.getOrNull(a.toInt())
                if (e != null) {
                    e.flash = .06f
                    val w = WEAPONS.getValue(WEAPON_KEYS[b.toInt().coerceIn(0, WEAPON_KEYS.size - 1)])
                    val mxp = e.x + cos(e.aim) * (w.len + 8f)
                    val myp = e.y - 28f + sin(e.aim) * (w.len + 8f)
                    for (k in 0 until 3) {
                        part(mxp, myp, cos(e.aim) * rnd(80f, 260f) + rnd(-60f, 60f), sin(e.aim) * rnd(80f, 260f) + rnd(-60f, 60f),
                            .14f, rnd(2f, 4f), 0xFFFFE08A.toInt(), 0f, true)
                    }
                    if (isMe(e)) shake = max(shake, if (w.key == "shotgun") 7f else if (w.key == "sniper") 9f else if (w.key == "rocket") 8f else if (w.key == "flame" || w.key == "smg") 1f else 2f)
                    snd(w.key, e.x, e.y)
                }
            }
            EV_EXPL -> {
                for (i in 0 until 40) {
                    val an = rnd(0f, 6.283f)
                    val s = rnd(80f, 520f)
                    part(x, y, cos(an) * s, sin(an) * s, rnd(.3f, .8f), rnd(4f, 10f),
                        if (i % 3 == 0) 0xFFFFE08A.toInt() else if (i % 3 == 1) 0xFFFF8A3D.toInt() else 0xFFE04A2E.toInt(), 200f, true)
                }
                for (i in 0 until 16) {
                    val an = rnd(0f, 6.283f)
                    val s = rnd(40f, 220f)
                    part(x, y, cos(an) * s, sin(an) * s - 60f, rnd(.6f, 1.2f), rnd(6f, 12f), 0x99445560.toInt(), -40f, false)
                }
                if (hypot(x - player.x, y - player.y) < 500f) shake = max(shake, 11f)
                snd("boom", x, y)
            }
            EV_PICK -> {
                burst(x, y, 0xFFFFFFFF.toInt(), 10, 200f, 200f)
                snd("pick", x, y)
            }
            EV_FEED -> {
                val ka = ents.getOrNull(a.toInt())
                val kb = ents.getOrNull(b.toInt())
                if (ka != null && kb != null) {
                    feed.add(FeedItem(nameOf(ka.id), ka.skin.c, nameOf(kb.id), kb.skin.c))
                    while (feed.size > 4) feed.removeAt(0)
                }
            }
            EV_SPARK -> burst(x, y, 0xFFFFE08A.toInt(), 5, 200f, 300f)
            EV_THROW -> snd("reload", x, y)
        }
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

    /** Movement, jetpack and gravity. Shared by the host simulation and client-side prediction. */
    private fun movePhysics(e: Ent, dt: Float) {
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
        } else {
            e.fuel = min(100f, e.fuel + (if (e.onG) 32f else 6f) * dt)
        }
        e.vy += GRAV * dt
        if (e.vy > 900f) e.vy = 900f
        moveEnt(e, dt)
        if (e.onG && abs(e.vx) > 20f) e.run += abs(e.vx) * dt * .055f else if (!e.onG) e.run += dt * 4f
    }

    private fun jetFx(e: Ent) {
        val dirj = if (cos(e.aim) >= 0f) 1f else -1f
        for (k in 0 until 2) {
            part(e.x - dirj * 10f + rnd(-2f, 2f), e.y - 12f, rnd(-40f, 40f), rnd(180f, 320f) + e.vy * .1f,
                rnd(.22f, .4f), rnd(3f, 6.5f), if (k == 1) 0xFFFFD35C.toInt() else 0xFFFF8A3D.toInt(), 0f, true)
        }
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

    /* ---------- particles and texts ---------- */
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

    private fun stepTexts(dt: Float) {
        var i = texts.size - 1
        while (i >= 0) {
            val d = texts[i]
            d.l -= dt * 1.1f
            d.y -= 60f * dt
            if (d.l <= 0f) texts.removeAt(i)
            i--
        }
    }

    /* ---------- combat ---------- */
    private fun startReload(e: Ent) {
        val w = WEAPONS.getValue(e.wp)
        if (!e.alive || e.reload > 0f || e.ammo >= w.mag) return
        e.reload = w.rel
        snd("reload", e.x, e.y)
    }

    private fun tryFire(e: Ent) {
        val w = WEAPONS.getValue(e.wp)
        if (e.reload > 0f || e.cool > 0f) return
        if (e.ammo <= 0) { startReload(e); return }
        e.cool = w.rate * (if (e.bot) 1.4f else 1f); e.ammo--
        val sx = e.x
        val sy = e.y - 28f
        val mxp = sx + cos(e.aim) * (w.len + 8f)
        val myp = sy + sin(e.aim) * (w.len + 8f)
        for (i in 0 until w.pel) {
            val a = e.aim + (Random.nextFloat() - .5f) * 2f * w.spr
            val sp = w.spd * (if (w.pel > 1) rnd(.85f, 1.05f) else 1f)
            bullets.add(Bullet(mxp, myp, cos(a) * sp, sin(a) * sp, w.dmg, w.life, e, e.team, w.tr, w.key == "sniper", w.kind))
        }
        e.vx -= cos(e.aim) * w.rec * .5f
        e.vy -= sin(e.aim) * w.rec * .15f
        emit(EV_SHOT, e.x, e.y, e.id.toFloat(), wIdx(w.key).toFloat())
        if (e.ammo <= 0) startReload(e)
    }

    private fun throwGrenade(e: Ent) {
        if (!e.alive || !e.human || e.grenades <= 0 || e.gCool > 0f) return
        e.grenades--
        e.gCool = .6f
        val sp = 540f
        val gx = e.x + cos(e.aim) * 14f
        val gy = e.y - 28f + sin(e.aim) * 14f
        grenades.add(Grenade(gx, gy, cos(e.aim) * sp + e.vx * .5f, sin(e.aim) * sp - 150f + e.vy * .3f, 2f, e.id, e.team))
        emit(EV_THROW, e.x, e.y, 0f, 0f)
    }

    private fun stepGrenades(dt: Float) {
        var i = grenades.size - 1
        while (i >= 0) {
            val g = grenades[i]
            val steps = 2
            val sdt = dt / steps
            for (s in 0 until steps) {
                g.vy += 1100f * sdt
                g.x += g.vx * sdt
                for (p in PLATS) {
                    if (g.x + 6f > p.x && g.x - 6f < p.x + p.w && g.y + 6f > p.y && g.y - 6f < p.y + p.h) {
                        g.x -= g.vx * sdt; g.vx = -g.vx * .5f
                        break
                    }
                }
                g.y += g.vy * sdt
                for (p in PLATS) {
                    if (g.x + 6f > p.x && g.x - 6f < p.x + p.w && g.y + 6f > p.y && g.y - 6f < p.y + p.h) {
                        g.y -= g.vy * sdt
                        g.vy = if (abs(g.vy) < 40f) 0f else -g.vy * .45f
                        g.vx *= .85f
                        break
                    }
                }
            }
            g.fuse -= dt
            if (g.fuse <= 0f) {
                explode(g)
                grenades.removeAt(i)
            }
            i--
        }
    }

    private fun explode(g: Grenade) {
        explodeAt(g.x, g.y, g.oid, 95f, GREN_R)
    }

    private fun explodeAt(x: Float, y: Float, oid: Int, maxDmg: Float, radius: Float) {
        emit(EV_EXPL, x, y, 0f, 0f)
        val owner = ents.getOrNull(oid)
        for (e in ents) {
            if (!e.alive) continue
            val dx = e.x - x
            val dy = (e.y - 22f) - y
            val d = hypot(dx, dy)
            if (d < radius) {
                val f = 1f - d / radius
                if (owner != null) damage(e, maxDmg * f + 8f, owner, atan2(dy, dx))
                e.vx += (dx / max(d, 1f)) * 520f * f
                e.vy += (dy / max(d, 1f)) * 520f * f - 120f * f
            }
        }
    }

    private fun damage(e: Ent, dmg: Float, by: Ent, ang: Float) {
        if (!e.alive || e.inv > 0f || by.team == e.team) return
        e.hp -= dmg
        e.vx += cos(ang) * dmg * 2.2f
        emit(EV_DMG, e.x, e.y - 52f, dmg, e.id.toFloat())
        if (e.hp <= 0f) die(e, by)
    }

    private fun die(e: Ent, by: Ent) {
        e.alive = false; e.respawn = 3f; e.deaths++; e.hp = 0f
        emit(EV_DIE, e.x, e.y - 22f, e.id.toFloat(), 0f)
        if (by !== e) {
            by.kills++
            if (mode == Mode.PLAY) emit(EV_FEED, 0f, 0f, by.id.toFloat(), e.id.toFloat())
        }
        if (mode == Mode.PLAY && by !== e) {
            if (rules == Rules.TEAM) {
                if (teamKills(by.team) >= GOAL_TEAM) endGame(by.id, by.team)
            } else if (by.kills >= GOAL_SOLO) endGame(by.id, by.team)
        }
    }

    private fun endGame(id: Int, team: Int) {
        mode = Mode.OVER
        winId = id
        winTeam = team
        paused = false
    }

    private fun givePrimary(e: Ent, key: String) {
        val mag = WEAPONS.getValue(key).mag
        for (i in 0..1) {
            if (e.wps[i] == key) { e.ammos[i] = mag; e.cur = i; e.reload = 0f; return }
        }
        val empty = if (e.wps[1] == null) 1 else if (e.wps[0] == null) 0 else -1
        val slot = if (empty >= 0) empty else e.cur
        e.wps[slot] = key; e.ammos[slot] = mag; e.cur = slot; e.reload = 0f; e.cool = .15f
    }

    private fun nearPick(e: Ent, healthOnly: Boolean): Pick? {
        var best: Pick? = null
        var bd = 1e9f
        for (p in picks) {
            if (p.cd > 0f || p.t == "gren") continue
            if (healthOnly && p.t != "health") continue
            if (!healthOnly && p.t == "health") continue
            val d = hypot(p.x - e.x, p.y - e.y)
            if (d < bd) { bd = d; best = p }
        }
        return best
    }

    /* ---------- bots ---------- */
    private fun botThink(e: Ent, dt: Float) {
        val dd = DIFFS.getValue(diff)
        e.aiT -= dt; e.boost -= dt; e.force -= dt
        if (e.aiT <= 0f) {
            e.aiT = .12f + Random.nextFloat() * .1f
            var tg: Ent? = null
            var bd = 1e9f
            for (o in ents) {
                if (o === e || !o.alive || o.team == e.team) continue
                val d = hypot(o.x - e.x, o.y - e.y)
                if (d < bd) { bd = d; tg = o }
            }
            e.tg = tg; e.dist = bd
            e.los = if (tg != null) los(e.x, e.y - 28f, tg.x, tg.y - 24f) else false
            e.goal = null
            if (e.hp < 50f) e.goal = nearPick(e, true)
            else if (e.wp == "pistol" && e.wps[1] == null && (tg == null || bd > 260f || !e.los)) e.goal = nearPick(e, false)
            var mx = 0f
            var jet = false
            val g = e.goal
            if (g != null) {
                val gx = g.x - e.x
                val gy = g.y - e.y
                mx = if (abs(gx) > 14f) sign(gx) else 0f
                jet = gy < -70f || (gy < -30f && abs(gx) < 40f)
            } else if (tg != null) {
                val dx = tg.x - e.x
                val dy = tg.y - e.y
                val ideal = when (e.wp) { "shotgun" -> 170f; "sniper" -> 520f; "flame" -> 120f; "rocket" -> 420f; "laser" -> 380f; "smg" -> 260f; else -> 320f }
                if (bd > ideal + 70f) mx = sign(dx)
                else if (bd < ideal - 70f) mx = -sign(dx)
                else {
                    if (Random.nextFloat() < .25f) e.strafe *= -1f
                    mx = e.strafe * .7f
                }
                jet = dy < -110f
            }
            if (Random.nextFloat() < .02f && e.fuel > 55f) e.boost = .2f + Random.nextFloat() * .2f
            if (e.fuel < 30f) jet = false
            e.aiMx = mx; e.aiJet = jet
        }
        if (e.onG && abs(e.vx) < 25f && abs(e.aiMx) > .3f) e.stuck += dt else e.stuck = 0f
        if (e.stuck > .3f) { e.boost = .5f; e.stuck = 0f; e.strafe *= -1f }
        if (!e.onG && (e.aiJet || e.boost > 0f) && e.vy > -40f) e.bump += dt else e.bump = 0f
        if (e.bump > .35f) { e.force = .5f; e.strafe = if (Random.nextBoolean()) 1f else -1f; e.bump = 0f }
        e.mx = if (e.force > 0f) e.strafe else e.aiMx
        e.jet = (e.aiJet || e.boost > 0f) && !e.empty
        // bots fly in short bursts with a rest in between, so they can't hover over you forever
        if (e.jet) {
            e.jetT += dt
            if (e.jetT > .8f) { e.restT = when (diff) { "easy" -> 1.4f; "hard" -> .55f; else -> .9f }; e.jetT = 0f }
        } else e.jetT = 0f
        if (e.restT > 0f) { e.restT -= dt; e.jet = false }
        if (e.tg != null && e.los) e.seenT += dt else e.seenT = 0f
        var des = e.aim
        val tg2 = e.tg
        if (tg2 != null && tg2.alive && e.los) {
            val tx = tg2.x + tg2.vx * .12f
            val ty = tg2.y - 24f + tg2.vy * .12f
            des = atan2(ty - (e.y - 28f), tx - e.x) + sin(t * 1.9f + e.ph) * dd.err
        } else if (e.mx != 0f) {
            des = if (e.mx > 0f) 0f else PI_F
        }
        e.aim += angDiff(e.aim, des) * min(1f, dd.speed * dt)
        e.fire = tg2 != null && tg2.alive && e.los &&
            e.dist < (when (e.wp) { "sniper" -> 800f; "flame" -> 190f; "shotgun" -> 320f; else -> 520f }) && abs(angDiff(e.aim, des)) < .2f &&
            e.seenT > (when (diff) { "easy" -> .75f; "hard" -> .3f; else -> .5f })
    }

    private fun stepEnt(e: Ent, dt: Float) {
        if (!e.alive) {
            e.respawn -= dt
            if (e.respawn <= 0f) spawn(e)
            return
        }
        e.inv = max(0f, e.inv - dt); e.hurt = max(0f, e.hurt - dt); e.cool -= dt; e.flash = max(0f, e.flash - dt)
        e.gCool -= dt
        if (e.bot) botThink(e, dt)
        movePhysics(e, dt)
        if (e.jetting) {
            jetFx(e)
            e.jetSnd -= dt
            if (e.jetSnd <= 0f) { e.jetSnd = .12f; if (isMe(e)) snd("jet", e.x, e.y) }
        }
        if (e.reload > 0f) {
            e.reload -= dt
            if (e.reload <= 0f) { e.reload = 0f; e.ammo = WEAPONS.getValue(e.wp).mag }
        }
        if (e.fire) tryFire(e)
        for (p in picks) {
            if (p.cd > 0f) continue
            if (abs(e.x - p.x) < 26f && p.y > e.y - 50f && p.y < e.y + 10f) {
                var took = true
                if (p.t == "health") {
                    if (e.hp >= 100f) took = false
                    else {
                        val h = min(45f, 100f - e.hp)
                        e.hp += h
                        emit(EV_HEAL, e.x, e.y - 52f, h, e.id.toFloat())
                    }
                } else if (p.t == "gren") {
                    if (!e.human || e.grenades >= 5) took = false else e.grenades = min(5, e.grenades + 2)
                } else {
                    givePrimary(e, p.t)
                }
                if (took) {
                    p.cd = 14f
                    emit(EV_PICK, p.x, p.y, 0f, 0f)
                }
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
                if (b.kind == 1 && s % 2 == 0) part(b.x, b.y, rnd(-20f, 20f), rnd(-20f, 20f), .5f, rnd(3f, 6f), 0x99AAAAAA.toInt(), -20f, false)
                for (p in PLATS) {
                    if (b.x > p.x && b.x < p.x + p.w && b.y > p.y && b.y < p.y + p.h) {
                        dead = true
                        emit(EV_SPARK, b.x, b.y, 0f, 0f)
                        break
                    }
                }
                if (!dead) {
                    for (e in ents) {
                        if (!e.alive || e.team == b.team) continue
                        if (abs(b.x - e.x) < 14f && b.y > e.y - 54f && b.y < e.y + 2f) {
                            if (e.inv > 0f) continue
                            val o = b.o
                            if (o != null && b.kind != 1) damage(e, b.dmg, o, atan2(b.vy, b.vx))
                            dead = true
                            break
                        }
                    }
                }
                s++
            }
            b.l -= dt
            if (b.kind == 1 && (dead || b.l <= 0f)) explodeAt(b.x, b.y, b.o?.id ?: -1, 85f, 135f)
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
        if (humans == 0) {
            val f = ents[floor(t / 6f).toInt() % ents.size]
            return if (f.alive) f else player
        }
        return player
    }

    fun updateCam(dt: Float, snap: Boolean) {
        val f = focusEnt()
        val zk = if (zoomCur > 4f) 1f + (zoomCur - 4f) * .9f else 1f
        val lead = if (f.alive) cos(f.aim) * 60f * zk else 0f
        val leadY = if (f.alive) sin(f.aim) * 40f * zk else 0f
        if (!panning) {
            val k = exp(-4f * dt)
            panX *= k; panY *= k
        }
        var tx = f.x + lead - vw / 2f + panX
        var ty = f.y - 110f - vh / 2f + panY + leadY
        tx = if (vw >= WORLD_W) (WORLD_W - vw) / 2f else max(0f, min(WORLD_W - vw, tx))
        ty = if (vh >= WORLD_H) (WORLD_H - vh) / 2f else max(0f, min(WORLD_H - vh, ty))
        val k2 = if (snap) 1f else 1f - exp(-6f * dt)
        camX += (tx - camX) * k2
        camY += (ty - camY) * k2
    }

    /* ---------- networking ---------- */
    private fun netTick(dt: Float) {
        snapAcc += dt
        if (snapAcc >= 1f / 30f) {
            snapAcc = 0f
            val n = net ?: return
            if (n.activeClients().isEmpty()) { outEvents.clear(); return }
            n.sendSnapshot(frame(MSG_SNAP) { writeSnapshot(it) })
            outEvents.clear()
        }
    }

    private fun writeSnapshot(o: DataOutputStream) {
        o.writeByte(if (mode == Mode.OVER) 1 else 0)
        o.writeByte(winId)
        o.writeByte(winTeam)
        o.writeFloat(matchTime)
        o.writeByte(ents.size)
        for (e in ents) {
            o.writeByte(e.id)
            o.writeFloat(e.x); o.writeFloat(e.y); o.writeFloat(e.vx); o.writeFloat(e.vy); o.writeFloat(e.aim)
            o.writeShort(e.hp.toInt()); o.writeShort(e.fuel.toInt())
            val fl = (if (e.alive) 1 else 0) or (if (e.onG) 2 else 0) or (if (e.jetting) 4 else 0) or (if (e.empty) 8 else 0)
            o.writeByte(fl)
            o.writeFloat(e.inv)
            o.writeByte(e.cur)
            o.writeByte(wIdx(e.wps[0]) + 1); o.writeByte(wIdx(e.wps[1]) + 1)
            o.writeShort(e.ammos[0]); o.writeShort(e.ammos[1])
            o.writeFloat(e.reload)
            o.writeShort(e.kills); o.writeShort(e.deaths)
            o.writeByte(e.grenades)
            o.writeFloat(e.respawn)
        }
        o.writeByte(picks.size)
        for (p in picks) o.writeFloat(p.cd)
        val nb = min(bullets.size, 150)
        o.writeShort(nb)
        for (i in 0 until nb) {
            val b = bullets[i]
            o.writeFloat(b.x); o.writeFloat(b.y); o.writeFloat(b.vx); o.writeFloat(b.vy)
            o.writeByte(b.o?.id ?: 0); o.writeByte(b.team); o.writeByte(b.kind * 2 + (if (b.big) 1 else 0))
        }
        o.writeByte(min(grenades.size, 40))
        for (i in 0 until min(grenades.size, 40)) {
            val g = grenades[i]
            o.writeFloat(g.x); o.writeFloat(g.y); o.writeFloat(g.vx); o.writeFloat(g.vy); o.writeFloat(g.fuse)
            o.writeByte(g.oid)
        }
        o.writeByte(outEvents.size)
        for (ev in outEvents) {
            o.writeByte(ev.typ)
            o.writeFloat(ev.x); o.writeFloat(ev.y); o.writeFloat(ev.a); o.writeFloat(ev.b)
        }
    }

    private fun applySnapshot(data: ByteArray) {
        val d = DataInputStream(ByteArrayInputStream(data))
        val state = d.readUnsignedByte()
        val wid = d.readByte().toInt()
        val wt = d.readByte().toInt()
        matchTime = d.readFloat()
        val n = d.readUnsignedByte()
        for (k in 0 until n) {
            val id = d.readUnsignedByte()
            val sx = d.readFloat(); val sy = d.readFloat(); val svx = d.readFloat(); val svy = d.readFloat(); val sa = d.readFloat()
            val hp = d.readShort().toFloat(); val fuel = d.readShort().toFloat()
            val fl = d.readUnsignedByte()
            val inv = d.readFloat()
            val cur = d.readUnsignedByte()
            val w0 = d.readUnsignedByte() - 1; val w1 = d.readUnsignedByte() - 1
            val a0 = d.readShort().toInt(); val a1 = d.readShort().toInt()
            val rel = d.readFloat()
            val kills = d.readShort().toInt(); val deaths = d.readShort().toInt()
            val gr = d.readUnsignedByte()
            val resp = d.readFloat()
            val e = ents.getOrNull(id) ?: continue
            val wasAlive = e.alive
            e.alive = (fl and 1) != 0
            e.hp = hp; e.inv = inv; e.cur = cur.coerceIn(0, 1)
            e.wps[0] = if (w0 >= 0) WEAPON_KEYS.getOrNull(w0) else null
            e.wps[1] = if (w1 >= 0) WEAPON_KEYS.getOrNull(w1) else null
            e.ammos[0] = a0; e.ammos[1] = a1
            e.reload = rel; e.kills = kills; e.deaths = deaths; e.grenades = gr; e.respawn = resp
            e.empty = (fl and 8) != 0
            if (isMe(e)) {
                e.fuel = fuel
                val dx = sx - e.x
                val dy = sy - e.y
                if (!wasAlive || abs(dx) + abs(dy) > 120f) {
                    e.x = sx; e.y = sy; e.vx = svx; e.vy = svy
                } else {
                    e.x += dx * .35f; e.y += dy * .35f
                    e.vx += (svx - e.vx) * .2f; e.vy += (svy - e.vy) * .2f
                }
            } else {
                e.fuel = fuel
                if (!wasAlive && e.alive) { e.x = sx; e.y = sy }
                e.tx = sx; e.ty = sy; e.tvx = svx; e.tvy = svy; e.taim = sa
                e.onG = (fl and 2) != 0
                e.jetting = (fl and 4) != 0
            }
        }
        val np = d.readUnsignedByte()
        for (k in 0 until np) {
            val cd = d.readFloat()
            if (k < picks.size) picks[k].cd = cd
        }
        bullets.clear()
        val nb = d.readUnsignedShort()
        for (k in 0 until nb) {
            val bx = d.readFloat(); val by = d.readFloat(); val bvx = d.readFloat(); val bvy = d.readFloat()
            val oid = d.readUnsignedByte(); val team = d.readUnsignedByte(); val kv = d.readUnsignedByte()
            val big = (kv and 1) == 1
            val kind = kv shr 1
            bullets.add(Bullet(bx, by, bvx, bvy, 0f, 1f, ents.getOrNull(oid), team, if (kind == 3) .03f else .02f, big, kind))
        }
        grenades.clear()
        val ng = d.readUnsignedByte()
        for (k in 0 until ng) {
            val gx = d.readFloat(); val gy = d.readFloat(); val gvx = d.readFloat(); val gvy = d.readFloat(); val gf = d.readFloat()
            val oid = d.readUnsignedByte()
            grenades.add(Grenade(gx, gy, gvx, gvy, gf, oid, ents.getOrNull(oid)?.team ?: 0))
        }
        val ne = d.readUnsignedByte()
        for (k in 0 until ne) {
            val typ = d.readUnsignedByte()
            val ex = d.readFloat(); val ey = d.readFloat(); val ea = d.readFloat(); val eb = d.readFloat()
            applyEvent(typ, ex, ey, ea, eb)
        }
        if (state == 1 && mode == Mode.PLAY) {
            mode = Mode.OVER
            winId = wid
            winTeam = wt
            paused = false
        }
    }

    private fun clientUpdate(dt: Float) {
        val me = player
        if (mode == Mode.PLAY) playerInput() else { me.mx = 0f; me.jet = false; me.fire = false }
        for (e in ents) {
            e.hurt = max(0f, e.hurt - dt); e.flash = max(0f, e.flash - dt); e.inv = max(0f, e.inv - dt)
            if (!e.alive) continue
            if (e === me) {
                movePhysics(e, dt)
                if (e.jetting) jetFx(e)
            } else {
                e.tx += e.tvx * dt; e.ty += e.tvy * dt
                val k = min(1f, dt * 18f)
                e.x += (e.tx - e.x) * k
                e.y += (e.ty - e.y) * k
                e.vx = e.tvx; e.vy = e.tvy
                e.aim += angDiff(e.aim, e.taim) * min(1f, dt * 25f)
                if (e.onG && abs(e.vx) > 20f) e.run += abs(e.vx) * dt * .055f else if (!e.onG) e.run += dt * 4f
                if (e.jetting) jetFx(e)
            }
        }
        for (b in bullets) {
            b.x += b.vx * dt; b.y += b.vy * dt
            if (b.kind == 1 && Random.nextFloat() < .6f) part(b.x, b.y, rnd(-20f, 20f), rnd(-20f, 20f), .5f, rnd(3f, 6f), 0x99AAAAAA.toInt(), -20f, false)
        }
        for (g in grenades) { g.vy += 1100f * dt; g.x += g.vx * dt; g.y += g.vy * dt }
        stepParts(dt); stepTexts(dt)
        shake = max(0f, shake - dt * 30f)
        updateCam(dt, false)
        sendInput(dt)
    }

    private fun sendInput(dt: Float) {
        inAcc += dt
        if (inAcc < 1f / 30f) return
        inAcc = 0f
        val p = player
        val f = frame(MSG_INPUT) {
            it.writeFloat(p.mx)
            it.writeByte((if (p.jet) 1 else 0) or (if (p.fire) 2 else 0))
            it.writeFloat(p.aim)
            it.writeInt(reqG); it.writeInt(reqS); it.writeByte(reqSV); it.writeInt(reqR)
        }
        net?.sendToHost(f)
    }

    private fun pollNet() {
        val n = net ?: return
        while (true) {
            val m = n.inbox.poll() ?: break
            try {
                val d = DataInputStream(ByteArrayInputStream(m.data))
                when (m.type) {
                    MSG_WELCOME -> {
                        lobbyHumans = d.readUnsignedByte()
                        val r = d.readUnsignedByte()
                        menuRules = if (r == 0) Rules.TEAM else Rules.SOLO
                        diff = DIFF_KEYS.getOrElse(d.readUnsignedByte()) { "normal" }
                        mode = Mode.WAIT
                    }
                    MSG_LOBBY -> {
                        lobbyHumans = d.readUnsignedByte()
                        val r = d.readUnsignedByte()
                        menuRules = if (r == 0) Rules.TEAM else Rules.SOLO
                        diff = DIFF_KEYS.getOrElse(d.readUnsignedByte()) { "normal" }
                    }
                    MSG_START -> {
                        val r = d.readUnsignedByte()
                        diff = DIFF_KEYS.getOrElse(d.readUnsignedByte()) { "normal" }
                        val id = d.readUnsignedByte()
                        val hc = d.readUnsignedByte()
                        role = Role.CLIENT
                        val sk = IntArray(hc)
                        val nm = ArrayList<String>()
                        for (k in 0 until hc) { sk[k] = d.readUnsignedByte(); nm.add(d.readUTF()) }
                        buildEnts(if (r == 0) Rules.TEAM else Rules.SOLO, hc, id, sk, nm)
                        mode = Mode.PLAY
                        paused = false
                        reqG = 0; reqS = 0; reqSV = 0; reqR = 0
                        updateCam(0f, true)
                    }
                    MSG_SNAP -> if (role == Role.CLIENT) applySnapshot(m.data)
                    MSG_REJECT -> {
                        note("That game is full or already started.")
                        showMenu()
                    }
                    MSG_DISCONNECTED -> {
                        if (role == Role.CLIENT || mode == Mode.WAIT) {
                            note("Disconnected from the host.")
                            showMenu()
                        }
                    }
                    MSG_CLIENT_LEFT -> {
                        val id = m.data[0].toInt()
                        remotes[id] = null
                        val e = ents.getOrNull(id)
                        if (e != null && role == Role.HOST) { e.human = false; e.bot = true }
                    }
                }
            } catch (ex: Exception) {
                // ignore a malformed packet
            }
        }
    }
}
