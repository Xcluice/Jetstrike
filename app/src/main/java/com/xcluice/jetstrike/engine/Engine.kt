package com.xcluice.jetstrike.engine

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.tan

/** Game code plugs in here. All callbacks run on the GL thread; use [Engine3DView.queueEvent] from the UI thread. */
interface GameLogic {
    /** Build meshes and scene content. Called once, after the first GL context exists. */
    fun onCreate(e: Engine)
    /** Called every frame before rendering. [dt] is in seconds and clamped to 50 ms. */
    fun onUpdate(e: Engine, dt: Float)
    fun onResize(e: Engine, w: Int, h: Int) {}
}

/** The engine: owns the scene and camera and exposes frame statistics. */
class Engine(val logic: GameLogic) {
    val scene = Scene()
    val camera = Camera()
    var width = 1
        internal set
    var height = 1
        internal set
    /** Seconds since start. */
    var time = 0f
        internal set
    var fps = 0
        internal set
    var drawCalls = 0
        internal set
    var visibleNodes = 0
        internal set
    val aspect: Float get() = width.toFloat() / maxOf(height, 1)
}

class Engine3DView(context: Context, val engine: Engine) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setPreserveEGLContextOnPause(false)
        setRenderer(EngineRenderer(engine))
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}

class EngineRenderer(private val engine: Engine) : GLSurfaceView.Renderer {
    private val lit = Program(Shaders.LIT_VS, Shaders.LIT_FS)
    private val pts = Program(Shaders.POINT_VS, Shaders.POINT_FS)
    private val sky = Program(Shaders.SKY_VS, Shaders.SKY_FS)
    private val bgProg = Program(Shaders.BG_VS, Shaders.BG_FS)
    private var bgTex = 0
    private var bgTexEpoch = -1
    private var bgTexVer = -1
    private val skyBuf = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().also {
        it.put(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)).position(0)
    }

    private val opaque = ArrayList<Node>(256)
    private val trans = ArrayList<Node>(32)
    private val planes = FloatArray(24)
    private val mvp = FloatArray(16)
    private val nrm = FloatArray(9)

    private var created = false
    private var last = 0L
    private var fpsAcc = 0f
    private var fpsFrames = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GL.epoch++
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        if (!created) {
            created = true
            engine.logic.onCreate(engine)
        }
        last = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        engine.width = w; engine.height = h
        engine.logic.onResize(engine, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = SystemClock.elapsedRealtimeNanos()
        val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(0.0005f, 0.05f)
        last = now
        engine.time += dt
        fpsAcc += dt; fpsFrames++
        if (fpsAcc >= 1f) {
            engine.fps = fpsFrames; fpsAcc = 0f; fpsFrames = 0
            Log.i(GL.TAG, "fps=${engine.fps} calls=${engine.drawCalls} visible=${engine.visibleNodes} particles=${engine.scene.particles.count + engine.scene.glow.count}")
        }

        engine.logic.onUpdate(engine, dt)
        val sc = engine.scene
        val cam = engine.camera
        sc.particles.update(dt)
        sc.glow.update(dt)
        cam.update(dt, engine.aspect)
        sc.root.updateWorld(null)
        extractPlanes(cam.viewProj)
        opaque.clear(); trans.clear()
        collect(sc.root, cam)
        engine.visibleNodes = opaque.size + trans.size
        engine.drawCalls = 0

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        drawSky(sc)
        drawScene(sc, cam)
        drawParticles(sc, cam)
        GL.check("frame")
    }

    private fun resetAttribs() {
        for (i in 0 until 8) GLES20.glDisableVertexAttribArray(i)
    }

    private fun drawBgTexture(): Boolean {
        val bmp = View3D.bg ?: return false
        if (!View3D.active) return false
        if (bgTex == 0 || bgTexEpoch != GL.epoch) {
            val t = IntArray(1)
            GLES20.glGenTextures(1, t, 0)
            bgTex = t[0]; bgTexEpoch = GL.epoch; bgTexVer = -1
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTex)
        val v = View3D.bgVer
        if (v != bgTexVer) {
            bgTexVer = v
            try { android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0) } catch (e: Exception) { return false }
        }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
        bgProg.use()
        resetAttribs()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(bgProg.u("uTex"), 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        val a = bgProg.a("aPos")
        GLES20.glEnableVertexAttribArray(a)
        skyBuf.position(0)
        GLES20.glVertexAttribPointer(a, 2, GLES20.GL_FLOAT, false, 0, skyBuf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        return true
    }

    private fun drawSky(sc: Scene) {
        if (drawBgTexture()) return
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
        sky.use()
        resetAttribs()
        GLES20.glUniform3fv(sky.u("uTop"), 1, sc.skyTop, 0)
        GLES20.glUniform3fv(sky.u("uBottom"), 1, sc.skyBottom, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        val a = sky.a("aPos")
        GLES20.glEnableVertexAttribArray(a)
        skyBuf.position(0)
        GLES20.glVertexAttribPointer(a, 2, GLES20.GL_FLOAT, false, 0, skyBuf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
    }

    private fun drawScene(sc: Scene, cam: Camera) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        lit.use()
        resetAttribs()
        GLES20.glUniform3fv(lit.u("uSunDir"), 1, sc.sunDir, 0)
        GLES20.glUniform3fv(lit.u("uSunColor"), 1, sc.sunColor, 0)
        GLES20.glUniform3fv(lit.u("uSky"), 1, sc.ambientSky, 0)
        GLES20.glUniform3fv(lit.u("uGround"), 1, sc.ambientGround, 0)
        GLES20.glUniform3fv(lit.u("uFogColor"), 1, sc.fogColor, 0)
        GLES20.glUniform2f(lit.u("uFog"), sc.fogStart, sc.fogEnd)
        GLES20.glUniform3f(lit.u("uCam"), cam.ex, cam.ey, cam.ez)

        for (i in 0 until opaque.size) drawNode(opaque[i])

        if (trans.isNotEmpty()) {
            for (n in trans) {
                val dx = n.world[12] - cam.ex; val dy = n.world[13] - cam.ey; val dz = n.world[14] - cam.ez
                n.sortKey = dx * dx + dy * dy + dz * dz
            }
            trans.sortByDescending { it.sortKey }
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glDepthMask(false)
            for (i in 0 until trans.size) drawNode(trans[i])
            GLES20.glDepthMask(true)
            GLES20.glDisable(GLES20.GL_BLEND)
        }
    }

    private fun drawNode(n: Node) {
        val m = n.mesh ?: return
        val w = n.world
        Matrix.multiplyMM(mvp, 0, engine.camera.viewProj, 0, w, 0)
        GLES20.glUniformMatrix4fv(lit.u("uMVP"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(lit.u("uModel"), 1, false, w, 0)
        normalMatrix(w)
        GLES20.glUniformMatrix3fv(lit.u("uNormal"), 1, false, nrm, 0)
        GLES20.glUniform4f(lit.u("uTint"), n.r, n.g, n.b, n.a)
        GLES20.glUniform1f(lit.u("uEmissive"), n.emissive)
        m.draw(lit)
        engine.drawCalls++
    }

    /** Upper 3x3 of the world matrix with each axis normalized (correct for rotation + uniform scale). */
    private fun normalMatrix(w: FloatArray) {
        for (c in 0 until 3) {
            val x = w[c * 4]; val y = w[c * 4 + 1]; val z = w[c * 4 + 2]
            val l = kotlin.math.sqrt(x * x + y * y + z * z).let { if (it < 1e-8f) 1f else it }
            nrm[c * 3] = x / l; nrm[c * 3 + 1] = y / l; nrm[c * 3 + 2] = z / l
        }
    }

    private fun drawParticles(sc: Scene, cam: Camera) {
        if (sc.particles.count == 0 && sc.glow.count == 0) return
        pts.use()
        resetAttribs()
        GLES20.glUniformMatrix4fv(pts.u("uVP"), 1, false, cam.viewProj, 0)
        // converts world-space size to pixels: viewport height / (2 tan(fov / 2))
        val scale = engine.height / (2f * tan(Math.toRadians(cam.fov / 2.0)).toFloat())
        GLES20.glUniform1f(pts.u("uScale"), scale)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glDepthMask(false)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        sc.particles.draw(pts)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        sc.glow.draw(pts)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        resetAttribs()
    }

    private fun collect(n: Node, cam: Camera) {
        if (!n.visible) return
        val m = n.mesh
        if (m != null && m.vertexCount > 0 && (!n.cull || inFrustum(n, m))) {
            if (n.a < 0.999f) trans.add(n) else opaque.add(n)
        }
        for (i in 0 until n.children.size) collect(n.children[i], cam)
    }

    private fun inFrustum(n: Node, m: Mesh): Boolean {
        val w = n.world
        val x = w[0] * m.cx + w[4] * m.cy + w[8] * m.cz + w[12]
        val y = w[1] * m.cx + w[5] * m.cy + w[9] * m.cz + w[13]
        val z = w[2] * m.cx + w[6] * m.cy + w[10] * m.cz + w[14]
        val sc = maxOf(
            kotlin.math.sqrt(w[0] * w[0] + w[1] * w[1] + w[2] * w[2]),
            kotlin.math.sqrt(w[4] * w[4] + w[5] * w[5] + w[6] * w[6]),
            kotlin.math.sqrt(w[8] * w[8] + w[9] * w[9] + w[10] * w[10])
        )
        val r = m.radius * sc
        for (i in 0 until 6) {
            val o = i * 4
            if (planes[o] * x + planes[o + 1] * y + planes[o + 2] * z + planes[o + 3] < -r) return false
        }
        return true
    }

    /** Gribb-Hartmann frustum plane extraction from a column-major view-projection matrix. */
    private fun extractPlanes(m: FloatArray) {
        for (i in 0 until 6) {
            val row = i / 2
            val sign = if (i % 2 == 0) 1f else -1f
            val o = i * 4
            for (k in 0 until 4) planes[o + k] = m[k * 4 + 3] + sign * m[k * 4 + row]
            val l = kotlin.math.sqrt(planes[o] * planes[o] + planes[o + 1] * planes[o + 1] + planes[o + 2] * planes[o + 2])
            if (l > 1e-8f) for (k in 0 until 4) planes[o + k] /= l
        }
    }
}
