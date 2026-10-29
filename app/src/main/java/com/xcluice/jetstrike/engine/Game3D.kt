package com.xcluice.jetstrike.engine

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import com.xcluice.jetstrike.Ent
import com.xcluice.jetstrike.Game
import com.xcluice.jetstrike.Mode
import com.xcluice.jetstrike.Rules
import com.xcluice.jetstrike.Skin
import com.xcluice.jetstrike.Terrain
import com.xcluice.jetstrike.WEAPONS
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Jetstrike 3D: the real game simulation (Game.kt) drawn by the OpenGL engine. Soldiers are low-poly models,
 * the map is the extruded terrain, bullets are glowing streaks. Controls and HUD live in [Hud3D] on top.
 */
class Game3DActivity : Activity() {
    private lateinit var view: Engine3DView
    private lateinit var hud: Hud3D
    private lateinit var logic: Game3DLogic

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = getSharedPreferences("jetstrike", Context.MODE_PRIVATE)
        val game = Game()
        game.mySkin = prefs.getInt("skin", 0).coerceAtLeast(0)
        game.myName = (prefs.getString("name", "Player") ?: "Player").take(12)
        logic = Game3DLogic(game)
        val engine = Engine(logic)
        view = Engine3DView(this, engine)
        hud = Hud3D(this, game, logic, engine)
        val root = FrameLayout(this)
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        root.addView(hud, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    override fun onResume() { super.onResume(); view.onResume(); hideSystemUi() }
    override fun onPause() { logic.game.pause(); view.onPause(); super.onPause() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (logic.game.paused) finish() else logic.game.pause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }
}

class Game3DLogic(val game: Game) : GameLogic {
    @Volatile var wantStory = false
    @Volatile var wantRestart = false
    @Volatile var camPreset = 0
    @Volatile var orbitYaw = 0f
    @Volatile var orbitPitch = 0f

    private var terrain: Node? = null
    private var builtMap = -1
    private val soldiers = HashMap<Int, Prefabs.Soldier>()
    private val soldierSkin = HashMap<Int, Skin>()
    private val tracers = ArrayList<Node>()
    private val nades = ArrayList<Node>()
    private val crates = ArrayList<Node>()
    private val nadeMesh = MeshBuilder().color(0xFF3B4A2E.toInt()).sphere(0f, 0f, 0f, 0.12f, 8, 6).build()
    private val crateMeshes = HashMap<String, Mesh>()
    private var e: Engine? = null
    private var started = false
    private var jetFx = 0f

    private val presets = arrayOf(
        floatArrayOf(0f, 0.12f, 18f), floatArrayOf(0.5f, 0.32f, 16f),
        floatArrayOf(-0.5f, 0.32f, 16f), floatArrayOf(0f, 0.55f, 24f), floatArrayOf(0f, 0.05f, 11f)
    )

    override fun onCreate(e: Engine) {
        this.e = e
        val sc = e.scene
        // warm sunset over a sea of clouds (blue to orange, no purple)
        sc.skyTop = Scene.rgb(0xFF2F6DB5.toInt(), 1f)
        sc.skyBottom = Scene.rgb(0xFFFFB27A.toInt(), 1f)
        sc.fogColor = Scene.rgb(0xFFF3B98D.toInt(), 1f)
        sc.sunColor = Scene.rgb(0xFFFFC98A.toInt(), 1.0f)
        sc.ambientSky = Scene.rgb(0xFF9DB9E6.toInt(), 0.62f)
        sc.ambientGround = Scene.rgb(0xFF6A4A3A.toInt(), 0.5f)
        sc.sunDir = floatArrayOf(-0.55f, 0.5f, 0.65f)
        sc.fogStart = 60f; sc.fogEnd = 210f
        buildClouds(e)
        applyPreset(e)
        game.menuRules = Rules.TEAM
        startMatch(e, false)
    }

    private val clouds = ArrayList<Node>()
    private fun buildClouds(e: Engine) {
        val mb = MeshBuilder().color(0xFFFFFFFF.toInt())
        mb.sphere(0f, 0f, 0f, 1.0f, 10, 6).sphere(1.1f, -0.1f, 0.2f, 0.8f, 10, 6).sphere(-1.0f, -0.15f, -0.1f, 0.85f, 10, 6).sphere(0.3f, 0.45f, 0f, 0.7f, 10, 6)
        val mesh = mb.build()
        val rnd = kotlin.random.Random(11)
        for (i in 0 until 26) {
            val n = Node(mesh)
            val low = i % 2 == 0
            n.setPos(rnd.nextFloat() * 120f - 25f, if (low) rnd.nextFloat() * 5f - 4f else 14f + rnd.nextFloat() * 30f, -14f - rnd.nextFloat() * 70f)
            val s = 3f + rnd.nextFloat() * 6f
            n.sx = s * 1.6f; n.sy = s * 0.55f; n.sz = s
            n.tint(if (low) 0xE8FFF2E6.toInt() else 0xCCFFE2CC.toInt())
            n.emissive = 0.35f
            n.cull = false
            clouds.add(n)
            e.scene.add(n)
        }
    }

    private fun applyPreset(e: Engine) {
        val p = presets[camPreset % presets.size]
        e.camera.yaw = p[0] + orbitYaw; e.camera.pitch = p[1] + orbitPitch; e.camera.goalDistance = p[2]
    }

    fun nextView() { camPreset = (camPreset + 1) % presets.size; orbitYaw = 0f; orbitPitch = 0f; e?.let { applyPreset(it) } }
    fun orbit(dy: Float, dp: Float) {
        orbitYaw = (orbitYaw + dy).coerceIn(-1.2f, 1.2f)
        orbitPitch = (orbitPitch + dp).coerceIn(-0.2f, 0.9f)
        e?.let { applyPreset(it) }
    }

    private fun startMatch(e: Engine, story: Boolean) {
        game.paused = false
        if (story) { game.menuRules = Rules.STORY } else { game.menuRules = Rules.TEAM; Terrain.load(0) }
        game.startOffline()
        started = true
        builtMap = -1
        game.updateCam(0f, true)
        val p = game.player
        e.camera.follow(Terrain3D.wx(p.x), Terrain3D.wy(p.y) + 1.4f)
        e.camera.snap()
    }

    private fun crateFor(t: String): Node {
        val col = when (t) {
            "health" -> 0xFFE8465A.toInt()
            "gren" -> 0xFF5C7A3A.toInt()
            else -> 0xFFE0B040.toInt()
        }
        val m = crateMeshes.getOrPut(t) { MeshBuilder().color(col).box(0f, 0.2f, 0f, 0.4f, 0.4f, 0.4f).build() }
        return Node(m)
    }

    override fun onUpdate(e: Engine, dt: Float) {
        if (wantRestart || wantStory) {
            val story = wantStory
            wantRestart = false; wantStory = false
            startMatch(e, story)
        }
        if (game.mapId != builtMap || terrain == null) {
            terrain?.let { e.scene.root.remove(it) }
            terrain = Terrain3D.build().also { e.scene.add(it) }
            builtMap = game.mapId
        }
        game.update(dt)
        for (c in clouds) { c.x += dt * (0.25f + c.sz * 0.02f); if (c.x > 100f) c.x = -30f }

        // soldiers
        val seen = HashSet<Int>()
        for (en in game.ents) {
            val s = soldierFor(e, en)
            seen.add(en.id)
            s.root.visible = en.alive
            if (!en.alive) continue
            val wx = Terrain3D.wx(en.x); val wy = Terrain3D.wy(en.y)
            s.root.setPos(wx, wy, 0f)
            val sc = if (en.sc <= 0f) 1f else en.sc
            s.root.sx = sc; s.root.sy = sc; s.root.sz = sc
            val right = cos(en.aim) >= 0f
            s.face(right)
            s.arm.rz = if (right) -en.aim else en.aim - Math.PI.toFloat()
            val moving = en.onG && abs(en.vx) > 20f
            val swing = if (moving) sin(en.run) * 0.75f else if (!en.onG) 0.3f else 0f
            s.legL.rz = swing; s.legR.rz = -swing
            s.flash(if (en.hurt > 0f) 0.8f else 0f)
            s.flame.visible = en.jetting
            if (en.jetting) {
                s.flame.sy = 0.8f + 0.4f * sin(e.time * 60f)
                val back = if (right) -1f else 1f
                e.scene.glow.jet(wx + back * 0.33f * sc, wy + 0.45f * sc, 0f, 0f, -1f, 0f, 1, 3f, 0.5f, 0.3f, 0.2f * sc, 0.02f, 0xFFFFD080.toInt(), 0x00FF3300)
                jetFx += dt
                if (jetFx > 0.03f) {
                    jetFx = 0f
                    e.scene.particles.jet(wx + back * 0.33f * sc, wy + 0.4f * sc, 0f, 0f, -1f, 0f, 1, 2f, 0.8f, 0.8f, 0.12f * sc, 0.4f, 0x66AAAAAA, 0x00555555)
                }
            }
        }
        val dead = soldiers.keys.filter { it !in seen }
        for (k in dead) { soldiers.remove(k)?.let { e.scene.root.remove(it.root) }; soldierSkin.remove(k) }

        // bullets
        syncPool(e, tracers, game.bullets.size) { Prefabs.tracer(0xFFFFFFFF.toInt()) }
        for (i in game.bullets.indices) {
            val b = game.bullets[i]
            val n = tracers[i]
            n.visible = true
            n.setPos(Terrain3D.wx(b.x), Terrain3D.wy(b.y), 0f)
            n.rz = atan2(-b.vy, b.vx)
            when (b.kind) {
                1 -> { n.sx = 1.8f; n.sy = 3f; n.sz = 3f; n.tint(0xFFFF9A3D.toInt()) }
                2 -> { n.sx = 1.1f; n.sy = 4f; n.sz = 4f; n.tint(0xFFFF6A2A.toInt()) }
                3 -> { n.sx = 2.2f; n.sy = 1.6f; n.sz = 1.6f; n.tint(0xFF6AF0FF.toInt()) }
                else -> { n.sx = if (b.big) 2.2f else 1f; n.sy = 1f; n.sz = 1f; n.tint(0xFFFFE27A.toInt()) }
            }
        }
        for (i in game.bullets.size until tracers.size) tracers[i].visible = false

        // grenades
        syncPool(e, nades, game.grenades.size) { Node(nadeMesh) }
        for (i in game.grenades.indices) {
            val g = game.grenades[i]
            nades[i].visible = true
            nades[i].setPos(Terrain3D.wx(g.x), Terrain3D.wy(g.y), 0f)
        }
        for (i in game.grenades.size until nades.size) nades[i].visible = false

        // pickups
        if (crates.size != game.picks.size) {
            for (c in crates) e.scene.root.remove(c)
            crates.clear()
            for (p in game.picks) { val c = crateFor(p.t); crates.add(c); e.scene.add(c) }
        }
        for (i in game.picks.indices) {
            val p = game.picks[i]
            val c = crates[i]
            c.visible = p.cd <= 0f
            c.setPos(Terrain3D.wx(p.x), Terrain3D.wy(p.y) + 0.05f + 0.04f * sin(e.time * 3f + i), 0f)
            c.ry = e.time * 1.2f + i
        }

        // the 2D game's sparks and smoke become short-lived engine particles
        for (p in game.parts) {
            val a = ((p.l / p.m).coerceIn(0f, 1f) * 235f).toInt()
            val rgb = p.c and 0x00FFFFFF
            val size = (p.s * Terrain3D.S * (if (p.glow) 2.4f else 1.8f)).coerceAtLeast(0.06f)
            val sys = if (p.glow) e.scene.glow else e.scene.particles
            sys.emit(Terrain3D.wx(p.x), Terrain3D.wy(p.y), 0f, 0f, 0f, 0f, 0.05f, size, size, (a shl 24) or rgb, 0x00000000)
        }
        if (game.shake > 0.1f) e.camera.shake(min(game.shake, 14f) * 0.012f)

        val pl = game.player
        e.camera.follow(Terrain3D.wx(pl.x), Terrain3D.wy(pl.y) + 1.4f)
    }

    private fun syncPool(e: Engine, pool: ArrayList<Node>, need: Int, make: () -> Node) {
        while (pool.size < need) { val n = make(); pool.add(n); e.scene.add(n) }
    }

    private fun soldierFor(e: Engine, en: Ent): Prefabs.Soldier {
        var s = soldiers[en.id]
        if (s == null || soldierSkin[en.id] !== en.skin) {
            s?.let { e.scene.root.remove(it.root) }
            s = Prefabs.soldier(en.skin.c, en.skin.d, en.skin.l)
            e.scene.add(s.root)
            soldiers[en.id] = s
            soldierSkin[en.id] = en.skin
        }
        return s
    }
}

/** Transparent overlay: touch controls, health/ammo, name tags and the pause menu. */
class Hud3D(ctx: Context, private val game: Game, private val logic: Game3DLogic, private val engine: Engine) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val out = FloatArray(4)

    private class Btn(val id: String, var l: Float = 0f, var t: Float = 0f, var r: Float = 0f, var b: Float = 0f)
    private val bGren = Btn("gren"); private val bSwap = Btn("swap"); private val bReload = Btn("reload")
    private val bPick = Btn("pick"); private val bView = Btn("view"); private val bPause = Btn("pause")
    private val bResume = Btn("resume"); private val bRestart = Btn("restart"); private val bStory = Btn("story"); private val bExit = Btn("exit")
    private val bAgain = Btn("again")
    private val play = listOf(bGren, bSwap, bReload, bPick, bView, bPause)
    private val pauseBtns = listOf(bResume, bRestart, bStory, bExit)

    private var lId = -1; private var lcx = 0f; private var lcy = 0f
    private var rId = -1; private var rcx = 0f; private var rcy = 0f
    private var oId = -1; private var ox = 0f; private var oy = 0f
    private var lpx = 0f; private var lpy = 0f; private var rpx = 0f; private var rpy = 0f
    private val stickR get() = 52f * d

    init {
        setWillNotDraw(false)
        isHapticFeedbackEnabled = false
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val s = 50f * d; val g = 10f * d
        // action cluster, bottom right of the aim zone
        place(bGren, w - s - 14f * d, h - 2.2f * s - g - 10f * d, s)
        place(bReload, w - 2f * s - g - 14f * d, h - 2.2f * s - g - 10f * d, s)
        place(bSwap, w - s - 14f * d, h - 3.3f * s - 2f * g - 10f * d, s)
        place(bPick, w - 2f * s - g - 14f * d, h - 3.3f * s - 2f * g - 10f * d, s)
        place(bView, 14f * d, 14f * d, 40f * d)
        place(bPause, w - 54f * d, 14f * d, 40f * d)
        val bw = 220f * d; val bh = 46f * d
        var y = h / 2f - 2.2f * bh
        for (b in pauseBtns) { b.l = (w - bw) / 2f; b.r = b.l + bw; b.t = y; b.b = y + bh; y += bh + 10f * d }
        bAgain.l = (w - bw) / 2f; bAgain.r = bAgain.l + bw; bAgain.t = h / 2f + 30f * d; bAgain.b = bAgain.t + bh
    }

    private fun place(b: Btn, l: Float, t: Float, s: Float) { b.l = l; b.t = t; b.r = l + s; b.b = t + s }
    private fun hit(b: Btn, x: Float, y: Float) = x >= b.l && x <= b.r && y >= b.t && y <= b.b

    private fun project(wx: Float, wy: Float, wz: Float): Boolean {
        val m = engine.camera.viewProj
        val x = m[0] * wx + m[4] * wy + m[8] * wz + m[12]
        val y = m[1] * wx + m[5] * wy + m[9] * wz + m[13]
        val w = m[3] * wx + m[7] * wy + m[11] * wz + m[15]
        if (w <= 0.01f) return false
        out[0] = (x / w * 0.5f + 0.5f) * width
        out[1] = (1f - (y / w * 0.5f + 0.5f)) * height
        return true
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val paused = game.paused
        val over = game.mode == Mode.OVER
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                val id = ev.getPointerId(i); val x = ev.getX(i); val y = ev.getY(i)
                if (paused) {
                    for (b in pauseBtns) if (hit(b, x, y)) { onPress(b.id); return true }
                    return true
                }
                if (over) { if (hit(bAgain, x, y)) logic.wantRestart = true; return true }
                for (b in play) if (hit(b, x, y)) { onPress(b.id); return true }
                if (x < width * 0.4f && lId < 0) { lId = id; lcx = x; lcy = y; lpx = x; lpy = y; game.lOn = true }
                else if (x > width * 0.55f && rId < 0) { rId = id; rcx = x; rcy = y; rpx = x; rpy = y; game.rOn = true }
                else if (oId < 0) { oId = id; ox = x; oy = y }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val id = ev.getPointerId(i); val x = ev.getX(i); val y = ev.getY(i)
                    if (id == lId) { lpx = x; lpy = y; val v = vec(lcx, lcy, x, y); game.lx = v[0]; game.ly = v[1] }
                    else if (id == rId) { rpx = x; rpy = y; val v = vec(rcx, rcy, x, y); game.rx = v[0]; game.ry = v[1] }
                    else if (id == oId) { logic.orbit(-(x - ox) * 0.005f, (y - oy) * 0.004f); ox = x; oy = y }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val i = if (ev.actionMasked == MotionEvent.ACTION_CANCEL) -1 else ev.actionIndex
                val id = if (i >= 0) ev.getPointerId(i) else -99
                if (id == lId || i < 0) { lId = -1; game.lOn = false; game.lx = 0f; game.ly = 0f }
                if (id == rId || i < 0) { rId = -1; game.rOn = false; game.rx = 0f; game.ry = 0f }
                if (id == oId || i < 0) oId = -1
            }
        }
        invalidate()
        return true
    }

    private fun vec(cx: Float, cy: Float, x: Float, y: Float): FloatArray {
        var dx = (x - cx) / stickR; var dy = (y - cy) / stickR
        val l = hypot(dx, dy)
        if (l > 1f) { dx /= l; dy /= l }
        return floatArrayOf(dx, dy)
    }

    private fun onPress(id: String) {
        when (id) {
            "gren" -> game.reqG++
            "swap" -> { game.reqS++; game.reqSV = 1 - game.player.cur }
            "reload" -> game.reqR++
            "pick" -> game.reqP++
            "view" -> logic.nextView()
            "pause" -> game.pause()
            "resume" -> game.resume()
            "restart" -> { game.resume(); logic.wantRestart = true }
            "story" -> { game.resume(); logic.wantStory = true }
            "exit" -> (context as? Activity)?.finish()
        }
    }

    override fun onDraw(c: Canvas) {
        postInvalidateOnAnimation()
        // name tags and health bars
        for (e in game.ents) {
            if (!e.alive || e === game.player && false) continue
            val wx = Terrain3D.wx(e.x); val wy = Terrain3D.wy(e.y)
            val top = 1.75f * (if (e.sc <= 0f) 1f else e.sc)
            if (!project(wx, wy + top, 0f)) continue
            val x = out[0]; val y = out[1]
            val mine = e === game.player
            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.textSize = 11f * d
            p.color = if (mine) 0xFF5EE0E0.toInt() else if (e.team == game.player.team) 0xFF9FE3FF.toInt() else 0xFFFF8A8A.toInt()
            c.drawText(if (mine) "YOU" else e.name, x, y - 9f * d, p)
            val bw = 34f * d
            p.color = 0x88000000.toInt()
            rect.set(x - bw / 2, y - 6f * d, x + bw / 2, y - 1f * d); c.drawRoundRect(rect, 2f * d, 2f * d, p)
            val f = (e.hp / (if (e.maxHp > 0f) e.maxHp else 100f)).coerceIn(0f, 1f)
            p.color = if (f > .5f) 0xFF5EE06B.toInt() else if (f > .25f) 0xFFFFD23F.toInt() else 0xFFFF4F6D.toInt()
            rect.set(x - bw / 2, y - 6f * d, x - bw / 2 + bw * f, y - 1f * d); c.drawRoundRect(rect, 2f * d, 2f * d, p)
        }
        // damage numbers
        for (t in game.texts) {
            if (!project(Terrain3D.wx(t.x), Terrain3D.wy(t.y), 0f)) continue
            p.textAlign = Paint.Align.CENTER; p.textSize = 14f * d
            p.color = (t.col and 0x00FFFFFF) or ((t.l.coerceIn(0f, 1f) * 255f).toInt() shl 24)
            c.drawText(t.text, out[0], out[1], p)
        }

        drawSticks(c)
        for (b in play) drawBtn(c, b)
        val pl = game.player
        // self status
        p.textAlign = Paint.Align.LEFT; p.textSize = 12f * d; p.color = 0xFFEAF8FB.toInt()
        val w = WEAPONS[pl.wp]
        c.drawText((w?.n ?: pl.wp).uppercase() + "   " + pl.ammo + "/" + (w?.mag ?: 0) + (if (pl.reload > 0f) "  RELOADING" else ""), 64f * d, 30f * d, p)
        statBar(c, 64f * d, 38f * d, 120f * d, pl.hp / (if (pl.maxHp > 0f) pl.maxHp else 100f), 0xFFFF4F6D.toInt())
        statBar(c, 64f * d, 48f * d, 120f * d, pl.fuel / 100f, 0xFF4FC3F7.toInt())
        p.textAlign = Paint.Align.RIGHT; p.textSize = 12f * d
        c.drawText("GRENADES " + pl.grenades + "   KILLS " + pl.kills, width - 64f * d, 30f * d, p)
        if (game.rules == Rules.STORY && game.bannerT > 0f && game.banner.isNotEmpty()) {
            p.textAlign = Paint.Align.CENTER; p.textSize = 30f * d; p.color = 0xFFFFB347.toInt()
            c.drawText(game.banner, width / 2f, height * .3f, p)
        }
        if (game.rules == Rules.STORY) {
            p.textAlign = Paint.Align.CENTER; p.textSize = 12f * d; p.color = 0xFFEAF8FB.toInt()
            c.drawText((if (game.storyWave >= 4) "FINAL STAGE" else "WAVE " + (game.storyWave + 1) + " / 4") + "   " + game.enemiesLeft() + " LEFT", width / 2f, 24f * d, p)
        }

        if (game.paused) {
            p.style = Paint.Style.FILL; p.color = 0xAA06121A.toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
            p.textAlign = Paint.Align.CENTER; p.textSize = 26f * d; p.color = 0xFFEAF8FB.toInt()
            c.drawText("PAUSED", width / 2f, pauseBtns[0].t - 18f * d, p)
            panel(c, bResume, "RESUME", true); panel(c, bRestart, "NEW TEAM MATCH", false)
            panel(c, bStory, "STORY MODE", false); panel(c, bExit, "EXIT", false)
        } else if (game.mode == Mode.OVER) {
            p.style = Paint.Style.FILL; p.color = 0xAA06121A.toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
            p.textAlign = Paint.Align.CENTER; p.textSize = 28f * d; p.color = 0xFFFFD23F.toInt()
            val won = if (game.rules == Rules.STORY) true else if (game.rules == Rules.TEAM) game.winTeam == game.player.team else game.winId == game.localId
            c.drawText(if (game.rules == Rules.STORY) "STORY COMPLETE" else if (won) "YOUR TEAM WINS" else "BOTS WIN", width / 2f, height / 2f - 10f * d, p)
            panel(c, bAgain, "PLAY AGAIN", true)
        }
    }

    private fun statBar(c: Canvas, x: Float, y: Float, w: Float, f: Float, col: Int) {
        p.style = Paint.Style.FILL; p.color = 0x66000000
        rect.set(x, y, x + w, y + 6f * d); c.drawRoundRect(rect, 3f * d, 3f * d, p)
        p.color = col
        rect.set(x, y, x + w * f.coerceIn(0f, 1f), y + 6f * d); c.drawRoundRect(rect, 3f * d, 3f * d, p)
    }

    private fun drawSticks(c: Canvas) {
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d
        if (lId >= 0) stick(c, lcx, lcy, lpx, lpy, 0xFF5EE0E0.toInt())
        else { p.color = 0x33FFFFFF; p.style = Paint.Style.STROKE; c.drawCircle(width * 0.14f, height * 0.72f, stickR, p) }
        if (rId >= 0) stick(c, rcx, rcy, rpx, rpy, 0xFFFF8A5B.toInt())
        else { p.color = 0x33FFFFFF; p.style = Paint.Style.STROKE; c.drawCircle(width * 0.72f, height * 0.72f, stickR, p) }
    }

    private fun stick(c: Canvas, cx: Float, cy: Float, x: Float, y: Float, col: Int) {
        p.style = Paint.Style.STROKE; p.color = (col and 0x00FFFFFF) or 0x77000000; c.drawCircle(cx, cy, stickR, p)
        var dx = x - cx; var dy = y - cy
        val l = hypot(dx, dy)
        if (l > stickR) { dx = dx / l * stickR; dy = dy / l * stickR }
        p.style = Paint.Style.FILL; p.color = (col and 0x00FFFFFF) or 0xAA000000.toInt()
        c.drawCircle(cx + dx, cy + dy, 22f * d, p)
    }

    private fun drawBtn(c: Canvas, b: Btn) {
        val cx = (b.l + b.r) / 2f; val cy = (b.t + b.b) / 2f; val r = (b.r - b.l) / 2f
        p.style = Paint.Style.FILL; p.color = 0x55203848
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = 0x88FFFFFF.toInt()
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.textSize = 10f * d; p.color = 0xFFEAF8FB.toInt()
        val label = when (b.id) { "gren" -> "GREN"; "swap" -> "SWAP"; "reload" -> "RLD"; "pick" -> "PICK"; "view" -> "VIEW"; else -> "II" }
        c.drawText(label, cx, cy + 3.5f * d, p)
    }

    private fun panel(c: Canvas, b: Btn, label: String, primary: Boolean) {
        rect.set(b.l, b.t, b.r, b.b)
        p.style = Paint.Style.FILL; p.color = if (primary) 0xFFFFB347.toInt() else 0x66203848
        c.drawRoundRect(rect, 14f * d, 14f * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = 0x99FFFFFF.toInt()
        c.drawRoundRect(rect, 14f * d, 14f * d, p)
        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.textSize = 14f * d
        p.color = if (primary) 0xFF04141C.toInt() else 0xFFEAF8FB.toInt()
        c.drawText(label, (b.l + b.r) / 2f, (b.t + b.b) / 2f + 5f * d, p)
    }
}
