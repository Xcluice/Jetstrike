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
import com.xcluice.jetstrike.Terrain

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

/** GL-side scene: just the extruded map. The camera is driven entirely by [View3D] so it matches the 2D layer exactly. */
class Game3DLogic : GameLogic {
    private var terrain: Node? = null
    private var builtVer = -1

    override fun onCreate(e: Engine) {
        val sc = e.scene
        sc.sunDir = floatArrayOf(-0.5f, 0.55f, 0.65f)
        sc.fogStart = 80f; sc.fogEnd = 260f
        e.camera.smooth = 10000f
    }

    override fun onUpdate(e: Engine, dt: Float) {
        if (!View3D.active) return
        if (terrain == null || builtVer != Terrain.version) {
            terrain?.let { e.scene.root.remove(it) }
            val t = Terrain3D.build()
            t.children.firstOrNull { it.name == "backdrop" }?.let { t.remove(it) }
            e.scene.add(t)
            terrain = t
            builtVer = Terrain.version
        }
        val c = e.camera
        c.smooth = 10000f
        c.yaw = View3D.yaw; c.pitch = View3D.pitch
        c.goalX = View3D.tx; c.goalY = View3D.ty; c.goalZ = 0f; c.goalDistance = View3D.dist
        c.snap()
    }
}
