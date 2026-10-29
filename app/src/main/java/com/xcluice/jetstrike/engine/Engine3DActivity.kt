package com.xcluice.jetstrike.engine

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.xcluice.jetstrike.Terrain
import kotlin.math.sin

/**
 * Engine test scene (second launcher icon "Jetstrike 3D"). A soldier patrols the extruded map using the real
 * Terrain collision, with jetpack fire and muzzle flashes, so you can check lighting, camera and particles on a phone.
 *
 * Drag = orbit the camera. Pinch = range (zoom). Double tap = switch between the Mini Outpost map and the boss arena.
 */
class Engine3DActivity : Activity() {
    private lateinit var view: Engine3DView
    private lateinit var engine: Engine
    private val logic = DemoLogic()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        engine = Engine(logic)
        view = Engine3DView(this, engine)
        setContentView(view)

        val scale = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val f = 1f / d.scaleFactor
                view.queueEvent { engine.camera.zoomRange(f) }
                return true
            }
        })
        val taps = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean { logic.requestMapSwap = true; return true }
        })
        var lx = 0f; var ly = 0f
        view.setOnTouchListener { _, ev ->
            scale.onTouchEvent(ev)
            taps.onTouchEvent(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lx = ev.x; ly = ev.y }
                MotionEvent.ACTION_MOVE -> if (ev.pointerCount == 1 && !scale.isInProgress) {
                    val dx = ev.x - lx; val dy = ev.y - ly
                    lx = ev.x; ly = ev.y
                    view.queueEvent { engine.camera.orbit(-dx * 0.006f, dy * 0.006f) }
                } else { lx = ev.x; ly = ev.y }
            }
            true
        }
    }

    override fun onResume() { super.onResume(); view.onResume(); hideSystemUi() }
    override fun onPause() { view.onPause(); super.onPause() }

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

class DemoLogic : GameLogic {
    @Volatile var requestMapSwap = false

    private var terrain: Node? = null
    private var soldier: Prefabs.Soldier? = null
    // soldier position in map pixels (feet), same space as Terrain
    private var px = 0f
    private var py = 0f
    private var vy = 0f
    private var dir = 1f
    private var t = 0f
    private var jetT = 0f
    private var shotT = 0f

    override fun onCreate(e: Engine) {
        e.camera.distance = 18f; e.camera.goalDistance = 18f
        e.scene.fogStart = 60f; e.scene.fogEnd = 220f
        soldier = Prefabs.soldier(0xFF3D7BD9.toInt(), 0xFF22406E.toInt(), 0xFF9FE3FF.toInt()).also { e.scene.add(it.root) }
        loadMap(e)
    }

    private fun loadMap(e: Engine) {
        terrain?.let { e.scene.root.remove(it) }
        terrain = Terrain3D.build().also { e.scene.add(it) }
        val sp = Terrain.spawns
        if (sp.isNotEmpty() && sp[0].size >= 2) { px = sp[0][0]; py = sp[0][1] } else { px = Terrain.worldW / 2; py = Terrain.worldH / 2 }
        vy = 0f
        e.camera.follow(Terrain3D.wx(px), Terrain3D.wy(py) + 1f)
        e.camera.snap()
    }

    override fun onUpdate(e: Engine, dt: Float) {
        if (requestMapSwap) {
            requestMapSwap = false
            Terrain.load(if (Terrain.mapId == 0) 1 else 0)
            loadMap(e)
        }
        val s = soldier ?: return
        t += dt

        // walk with step-up and wall turn-around, fall with gravity (same Terrain queries the real game uses)
        val nx = px + dir * 110f * dt
        if (!Terrain.boxHit(nx, py - 1f)) px = nx
        else if (!Terrain.boxHit(nx, py - 15f)) { px = nx; py -= 14f }
        else dir = -dir
        if (px < 40f || px > Terrain.worldW - 40f) dir = -dir

        jetT = (jetT + dt) % 6f
        val jetting = jetT < 1.1f
        val grounded = Terrain.boxHit(px, py + 1.5f)
        if (jetting) { vy = -260f; py += vy * dt }
        else if (!grounded) {
            vy = minOf(vy + 1500f * dt, 900f)
            val ny = py + vy * dt
            if (Terrain.boxHit(px, ny)) vy = 0f else py = ny
        } else vy = 0f
        py = py.coerceIn(0f, Terrain.worldH - 1f)

        val wx = Terrain3D.wx(px); val wy = Terrain3D.wy(py)
        s.root.setPos(wx, wy, 0f)
        s.face(dir > 0f)
        val swing = if (grounded && !jetting) sin(t * 11f) * 0.7f else 0.25f
        s.legL.rz = swing; s.legR.rz = -swing
        s.arm.rz = 0.1f * sin(t * 2f)
        s.flame.visible = jetting
        s.flame.sy = 0.8f + 0.4f * sin(t * 60f)

        val back = -dir
        if (jetting) {
            e.scene.glow.jet(wx + back * 0.27f, wy + 0.6f, 0f, 0f, -1f, 0f, 3, 3f, 0.5f, 0.35f, 0.22f, 0.02f, 0xFFFFD080.toInt(), 0x00FF3300)
            e.scene.particles.jet(wx + back * 0.27f, wy + 0.5f, 0f, 0f, -1f, 0f, 1, 2f, 0.8f, 0.9f, 0.12f, 0.4f, 0x66AAAAAA, 0x00555555)
        }
        shotT -= dt
        if (shotT <= 0f) {
            shotT = 1.2f
            val mx = wx + dir * 0.95f; val my = wy + 0.95f
            e.scene.glow.burst(mx, my, 0.18f, 10, 5f, 0.12f, 0.3f, 0xFFFFF2B0.toInt(), 0x00FF8800)
            e.scene.glow.jet(mx, my, 0.18f, dir, 0.05f, 0f, 6, 16f, 0.15f, 0.25f, 0.08f, 0.02f, 0xFFFFE9A0.toInt(), 0x00FF6600, 20f)
            e.camera.shake(0.12f)
        }

        e.camera.follow(wx, wy + 1.2f, 0f)
    }
}
