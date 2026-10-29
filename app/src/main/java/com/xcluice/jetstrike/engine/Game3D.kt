package com.xcluice.jetstrike.engine

import android.app.Activity
import android.graphics.PixelFormat
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import com.xcluice.jetstrike.GameView
import com.xcluice.jetstrike.Skin
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Jetstrike 3D. The real game view (same menu, characters, controls, soldiers, map art and HUD) is drawn on a
 * transparent layer, perspective-warped onto the gameplay plane of a shared camera. Underneath, the OpenGL layer draws
 * the game's own background plus the map extruded into real depth, so the camera can lean and the world shows its thickness.
 */
class Game3DActivity : Activity() {
    private lateinit var gl: Engine3DView
    private lateinit var gv: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val engine = Engine(Game3DLogic())
        gl = Engine3DView(this, engine)
        gv = GameView(this)
        gv.mode3d = true
        gv.setZOrderOnTop(true)
        gv.holder.setFormat(PixelFormat.TRANSLUCENT)
        val root = FrameLayout(this)
        root.addView(gl, FrameLayout.LayoutParams(-1, -1))
        root.addView(gv, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    override fun onResume() { super.onResume(); gl.onResume(); hideSystemUi() }
    override fun onPause() { gv.autoPause(); gl.onPause(); super.onPause() }
    override fun onDestroy() { gv.release(); super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!gv.handleBack()) super.onBackPressed()
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

/**
 * GL-side world for Jetstrike 3D. Everything physical is drawn here in real 3D from [Snap3D] (a copy of the game state
 * made after each Game.update): the extruded map, one rigged model per fighter, tracers, grenades, pickup crates and
 * the sparks. The camera is driven by [View3D], so the 2D HUD, name tags and aim marker stay glued to the gameplay plane.
 */
class Game3DLogic : GameLogic {
    private var terrain: Node? = null
    private var builtVer = -1
    private val fighters = HashMap<Int, Prefabs.Soldier>()
    private val fighterSkin = HashMap<Int, Skin>()
    private val tracers = ArrayList<Node>()
    private val nades = ArrayList<Node>()
    private val crates = ArrayList<Node>()
    private val crateTypes = ArrayList<String>()
    private val crateMeshes = HashMap<String, Mesh>()
    private val nadeMesh = MeshBuilder().color(0xFF3B4A2E.toInt()).sphere(0f, 0f, 0f, 0.12f, 8, 6).build()
    private var jetFx = 0f

    override fun onCreate(e: Engine) {
        val sc = e.scene
        sc.sunDir = floatArrayOf(-0.5f, 0.7f, 0.6f)
        sc.sunColor = Scene.rgb(0xFFFFE2B8.toInt(), 1.0f)
        sc.ambientSky = Scene.rgb(0xFF9DB9E6.toInt(), 0.62f)
        sc.ambientGround = Scene.rgb(0xFF6A4A3A.toInt(), 0.5f)
        sc.fogStart = 90f; sc.fogEnd = 300f
        e.camera.smooth = 10000f
    }

    override fun onUpdate(e: Engine, dt: Float) {
        if (!View3D.active) return
        val sn = Snap3D
        if (terrain == null || builtVer != sn.mapVer) {
            terrain?.let { e.scene.root.remove(it) }
            val t = Terrain3D.build()
            t.children.firstOrNull { it.name == "backdrop" }?.let { t.remove(it) }
            // slab sits behind the gameplay plane (z = 0) with only a thin lip in front, so fighters are never hidden
            t.z = 0.5f - Terrain3D.DEPTH / 2f
            e.scene.add(t)
            terrain = t
            builtVer = sn.mapVer
        }
        val c = e.camera
        c.smooth = 10000f
        c.yaw = View3D.yaw; c.pitch = View3D.pitch
        c.goalX = View3D.tx; c.goalY = View3D.ty; c.goalZ = 0f; c.goalDistance = View3D.dist
        c.snap()

        synchronized(sn.lock) {
            syncFighters(e, sn, dt)
            syncBullets(e, sn)
            syncGrenades(e, sn)
            syncPickups(e, sn)
            syncParticles(e, sn)
        }
    }

    private fun syncFighters(e: Engine, sn: Snap3D, dt: Float) {
        val seen = HashSet<Int>()
        for (i in 0 until sn.nEnt) {
            val en = sn.ents[i]
            val sk = en.skin ?: continue
            var f = fighters[en.id]
            if (f == null || fighterSkin[en.id] !== sk) {
                f?.let { e.scene.root.remove(it.root) }
                f = Prefabs.hero(sk, en.boss)
                e.scene.add(f.root)
                fighters[en.id] = f
                fighterSkin[en.id] = sk
            }
            seen.add(en.id)
            f.root.visible = en.alive
            if (!en.alive) continue
            val wx = Terrain3D.wx(en.x); val wy = Terrain3D.wy(en.y)
            val k = (if (en.sc <= 0f) 1f else en.sc) * sk.h.scale
            f.root.setPos(wx, wy, 0f)
            f.root.sx = k; f.root.sy = k; f.root.sz = k
            val right = cos(en.aim) >= 0f
            f.face(right)
            f.arm.rz = if (right) atan2(-sin(en.aim), cos(en.aim)) else atan2(-sin(en.aim), -cos(en.aim))
            f.setGun(en.wp)
            val moving = en.onG && abs(en.vx) > 20f
            val swing = if (moving) sin(en.run) * 0.8f else if (!en.onG) 0.35f else 0f
            f.legL.rz = swing; f.legR.rz = -swing
            f.flash(if (en.hurt > 0f) 0.85f else 0f)
            f.flame.visible = en.jetting
            if (en.jetting) {
                f.flame.sy = 0.8f + 0.4f * sin(e.time * 60f)
                val back = if (right) -1f else 1f
                val px = wx + back * 0.3f * k; val py = wy + 0.4f * k
                e.scene.glow.jet(px, py, 0f, 0f, -1f, 0f, 1, 3f, 0.5f, 0.3f, 0.2f * k, 0.02f, 0xFFFFD080.toInt(), 0x00FF3300)
                jetFx += dt
                if (jetFx > 0.03f) {
                    jetFx = 0f
                    e.scene.particles.jet(px, py - 0.05f, 0f, 0f, -1f, 0f, 1, 2f, 0.8f, 0.8f, 0.12f * k, 0.4f, 0x66AAAAAA, 0x00555555)
                }
            }
        }
        if (fighters.size > seen.size) {
            val gone = fighters.keys.filter { it !in seen }
            for (g in gone) { fighters.remove(g)?.let { e.scene.root.remove(it.root) }; fighterSkin.remove(g) }
        }
    }

    private fun pool(e: Engine, pool: ArrayList<Node>, need: Int, make: () -> Node) {
        while (pool.size < need) { val n = make(); pool.add(n); e.scene.add(n) }
    }

    private fun syncBullets(e: Engine, sn: Snap3D) {
        pool(e, tracers, sn.nBul) { Prefabs.tracer(0xFFFFFFFF.toInt()) }
        for (i in 0 until sn.nBul) {
            val o = i * 6
            val n = tracers[i]
            n.visible = true
            n.setPos(Terrain3D.wx(sn.bul[o]), Terrain3D.wy(sn.bul[o + 1]), 0f)
            n.rz = atan2(-sn.bul[o + 3], sn.bul[o + 2])
            val big = sn.bul[o + 5] > 0.5f
            when (sn.bul[o + 4].toInt()) {
                1 -> { n.sx = 1.8f; n.sy = 3f; n.sz = 3f; n.tint(0xFFFF9A3D.toInt()) }
                2 -> { n.sx = 1.1f; n.sy = 4f; n.sz = 4f; n.tint(0xFFFF6A2A.toInt()) }
                3 -> { n.sx = 2.2f; n.sy = 1.6f; n.sz = 1.6f; n.tint(0xFF6AF0FF.toInt()) }
                else -> { n.sx = if (big) 2.2f else 1f; n.sy = if (big) 2f else 1f; n.sz = n.sy; n.tint(0xFFFFE27A.toInt()) }
            }
        }
        for (i in sn.nBul until tracers.size) tracers[i].visible = false
    }

    private fun syncGrenades(e: Engine, sn: Snap3D) {
        pool(e, nades, sn.nGre) { Node(nadeMesh) }
        for (i in 0 until sn.nGre) {
            nades[i].visible = true
            nades[i].setPos(Terrain3D.wx(sn.gre[i * 2]), Terrain3D.wy(sn.gre[i * 2 + 1]), 0f)
        }
        for (i in sn.nGre until nades.size) nades[i].visible = false
    }

    private fun crateFor(t: String): Node {
        val col = when (t) {
            "health" -> 0xFFE8465A.toInt()
            "gren" -> 0xFF5C7A3A.toInt()
            else -> 0xFFE0B040.toInt()
        }
        return Node(crateMeshes.getOrPut(t) { MeshBuilder().color(col).box(0f, 0.2f, 0f, 0.4f, 0.4f, 0.4f).color(0xFFFFFFFF.toInt()).box(0f, 0.2f, 0f, 0.1f, 0.42f, 0.1f).build() })
    }

    private fun syncPickups(e: Engine, sn: Snap3D) {
        var rebuild = crates.size != sn.nPick
        if (!rebuild) for (i in 0 until sn.nPick) if (crateTypes[i] != (sn.pickT[i] ?: "")) { rebuild = true; break }
        if (rebuild) {
            for (c in crates) e.scene.root.remove(c)
            crates.clear(); crateTypes.clear()
            for (i in 0 until sn.nPick) {
                val t = sn.pickT[i] ?: ""
                val c = crateFor(t); crates.add(c); crateTypes.add(t); e.scene.add(c)
            }
        }
        for (i in 0 until sn.nPick) {
            val c = crates[i]
            c.visible = sn.pick[i * 3 + 2] <= 0f
            c.setPos(Terrain3D.wx(sn.pick[i * 3]), Terrain3D.wy(sn.pick[i * 3 + 1]) + 0.06f + 0.04f * sin(e.time * 3f + i), 0f)
            c.ry = e.time * 1.2f + i
        }
    }

    /** The game's sparks, smoke and blasts become one-frame engine particles. */
    private fun syncParticles(e: Engine, sn: Snap3D) {
        for (i in 0 until sn.nPart) {
            val o = i * 5
            val a = (sn.part[o + 2].coerceIn(0f, 1f) * 235f).toInt()
            val glow = sn.part[o + 4] > 0.5f
            val size = (sn.part[o + 3] * Terrain3D.S * (if (glow) 2.4f else 1.8f)).coerceAtLeast(0.06f)
            val sys = if (glow) e.scene.glow else e.scene.particles
            sys.emit(Terrain3D.wx(sn.part[o]), Terrain3D.wy(sn.part[o + 1]), 0.05f, 0f, 0f, 0f, 0.05f, size, size, (a shl 24) or (sn.partC[i] and 0x00FFFFFF), 0x00000000)
        }
    }
}
