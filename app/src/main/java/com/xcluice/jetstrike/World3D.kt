package com.xcluice.jetstrike

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/* ---------- what the game thread hands over to the GL thread, once per frame ---------- */

class SoldierSnap(
    val id: Int, val skin: Skin, val x: Float, val y: Float, val vx: Float, val onG: Boolean, val run: Float,
    val aim: Float, val jetting: Boolean, val hurt: Boolean, val hidden: Boolean, val sc: Float,
    val flash: Float, val wp: String, val boss: Boolean
)

class PickSnap(val t: String, val x: Float, val y: Float, val on: Boolean)
class GrenSnap(val x: Float, val y: Float, val fuse: Float)

class Snap(
    val camX: Float, val camY: Float, val vw: Float, val vh: Float, val t: Float,
    val mapId: Int, val version: Int, val polys: List<TPoly>, val windows: List<FloatArray>,
    val worldW: Float, val worldH: Float,
    val soldiers: List<SoldierSnap>, val picks: List<PickSnap>, val grens: List<GrenSnap>
)

/**
 * The 3D layer. It sits under the transparent Canvas layer: sky, mountains, the extruded level, pickups and every
 * soldier with his gun are drawn here with a perspective camera whose z = 0 plane lines up with the 2D game plane,
 * so bullets, hit boxes and the HUD drawn on top stay exactly where they were.
 */
class World3D : GLSurfaceView.Renderer {
    @Volatile private var snap: Snap? = null
    var view: GLSurfaceView? = null

    fun submit(s: Snap) {
        snap = s
        view?.requestRender()
    }

    /* ---------- GL state ---------- */
    private var prog = 0
    private var aP = 0; private var aN = 0; private var aT = 0; private var aC = 0
    private var uVP = 0; private var uM = 0; private var uN = 0; private var uMode = 0; private var uTint = 0
    private var uFlash = 0; private var uFog = 0; private var uFogR = 0; private var uSky = 0; private var uGnd = 0
    private var uSun = 0; private var uL = 0; private var uTex = 0; private var uGlow = 0
    private var texNoise = 0
    private var texGlow = 0
    private var sw = 1
    private var sh = 1

    private val registry = ArrayList<Mesh3>()
    private val ident = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val vp = FloatArray(16)
    private val proj = FloatArray(16)
    private val viewM = FloatArray(16)
    private val nm = FloatArray(9)

    private var terr: TerrainMesh.Result? = null
    private var terrVer = -1
    private var lastT = 0f

    private class HeroGfx(val m: HeroModel, val segs: List<Mesh3>) {
        val arms = HashMap<String, Mesh3>()
    }
    private val heroes = HashMap<Skin, HeroGfx>()
    private val guns = HashMap<String, GunModel>()
    private val yaws = HashMap<Int, Float>()

    private lateinit var quad: Mesh3
    private lateinit var skyMesh: Mesh3
    private lateinit var healthMesh: Mesh3
    private lateinit var flameOut: Mesh3
    private lateinit var flameIn: Mesh3

    private fun reg(m: Mesh3): Mesh3 { registry.add(m); return m }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        return s
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // a new context: everything uploaded before is gone
        for (m in registry) m.vbo = 0
        registry.clear()
        heroes.clear(); guns.clear(); terr = null; terrVer = -1

        val vs = """
            uniform mat4 uVP; uniform mat4 uM; uniform mat3 uN;
            attribute vec3 aP; attribute vec3 aN; attribute vec2 aT; attribute vec3 aC;
            varying vec3 vN; varying vec2 vT; varying vec3 vC; varying float vD;
            void main() {
                vec4 p = uVP * (uM * vec4(aP, 1.0));
                gl_Position = p;
                vN = uN * aN; vT = aT; vC = aC; vD = p.w;
            }
        """.trimIndent()
        val fs = """
            precision mediump float;
            uniform float uMode; uniform vec4 uTint; uniform float uFlash;
            uniform vec3 uFog; uniform vec2 uFogR; uniform vec3 uSky; uniform vec3 uGnd; uniform vec3 uSun; uniform vec3 uL;
            uniform sampler2D uTex; uniform sampler2D uGlow;
            varying vec3 vN; varying vec2 vT; varying vec3 vC; varying float vD;
            void main() {
                vec3 col = vC;
                float a = 1.0;
                if (uMode < 0.5 || (uMode > 0.5 && uMode < 1.5)) {
                    vec3 n = normalize(vN);
                    float d = max(dot(n, uL), 0.0);
                    vec3 amb = mix(uGnd, uSky, n.y * 0.5 + 0.5);
                    float rim = pow(1.0 - abs(n.z), 3.0) * 0.12;
                    if (uMode > 0.5) col *= texture2D(uTex, vT).rgb;
                    col = col * (amb + uSun * d + rim);
                    col = mix(col, vec3(1.0), uFlash);
                    a = uTint.a;
                } else if (uMode < 2.5) {
                    col = vC * uTint.rgb;
                    a = uTint.a;
                } else {
                    col = uTint.rgb;
                    a = texture2D(uGlow, vT).a * uTint.a;
                }
                if (uMode < 2.5) {
                    float f = smoothstep(uFogR.x, uFogR.y, vD);
                    col = mix(col, uFog, f * 0.88);
                }
                gl_FragColor = vec4(col, a);
            }
        """.trimIndent()
        prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(prog, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(prog)
        aP = GLES20.glGetAttribLocation(prog, "aP"); aN = GLES20.glGetAttribLocation(prog, "aN")
        aT = GLES20.glGetAttribLocation(prog, "aT"); aC = GLES20.glGetAttribLocation(prog, "aC")
        uVP = GLES20.glGetUniformLocation(prog, "uVP"); uM = GLES20.glGetUniformLocation(prog, "uM")
        uN = GLES20.glGetUniformLocation(prog, "uN"); uMode = GLES20.glGetUniformLocation(prog, "uMode")
        uTint = GLES20.glGetUniformLocation(prog, "uTint"); uFlash = GLES20.glGetUniformLocation(prog, "uFlash")
        uFog = GLES20.glGetUniformLocation(prog, "uFog"); uFogR = GLES20.glGetUniformLocation(prog, "uFogR")
        uSky = GLES20.glGetUniformLocation(prog, "uSky"); uGnd = GLES20.glGetUniformLocation(prog, "uGnd")
        uSun = GLES20.glGetUniformLocation(prog, "uSun"); uL = GLES20.glGetUniformLocation(prog, "uL")
        uTex = GLES20.glGetUniformLocation(prog, "uTex"); uGlow = GLES20.glGetUniformLocation(prog, "uGlow")

        texNoise = makeNoiseTex()
        texGlow = makeGlowTex()

        val q = MeshB()
        q.v(-.5f, -.5f, 0f, 0f, 0f, 1f, 0f, 0f); q.v(.5f, -.5f, 0f, 0f, 0f, 1f, 1f, 0f); q.v(.5f, .5f, 0f, 0f, 0f, 1f, 1f, 1f)
        q.v(-.5f, -.5f, 0f, 0f, 0f, 1f, 0f, 0f); q.v(.5f, .5f, 0f, 0f, 0f, 1f, 1f, 1f); q.v(-.5f, .5f, 0f, 0f, 0f, 1f, 0f, 1f)
        quad = reg(q.build())

        val h = MeshB()
        h.color(0xFFEAF8EC.toInt()); h.rrExtrude(-15f, -15f, 15f, 15f, 8f, -4f, 4f, 3)
        h.color(0xFF3FC95A.toInt()); h.push(); h.translate(0f, 0f, 4f); h.box(5f, 16f, 2f); h.box(16f, 5f, 2f); h.pop()
        healthMesh = reg(h.build())

        val fo = MeshB(); fo.color(0xFFFF9B3D.toInt()); fo.cone(1f, 1f, 8)
        flameOut = reg(fo.build())
        val fi = MeshB(); fi.color(0xFFFFF0AA.toInt()); fi.cone(1f, 1f, 8)
        flameIn = reg(fi.build())

        // sky is built per frame from the map palette (see drawSky); a dummy keeps the lateinit valid
        skyMesh = reg(MeshB().build())
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        sw = max(1, width); sh = max(1, height)
        GLES20.glViewport(0, 0, sw, sh)
    }

    /* ---------- resources ---------- */

    private fun makeNoiseTex(): Int {
        val n = 128
        val px = IntArray(n * n)
        fun h(x: Int, y: Int, s: Int): Float {
            var v = x * 374761393 + y * 668265263 + s * 1274126177
            v = (v xor (v ushr 13)) * 1274126177
            v = v xor (v ushr 16)
            return (v and 0xFFFF) / 65535f
        }
        fun vn(x: Float, y: Float, cell: Int, s: Int): Float {
            val gx = x / cell; val gy = y / cell
            val x0 = floor(gx).toInt(); val y0 = floor(gy).toInt()
            val fx = gx - x0; val fy = gy - y0
            val m = n / cell
            fun g(a: Int, b: Int) = h(((a % m) + m) % m, ((b % m) + m) % m, s)
            val u = fx * fx * (3f - 2f * fx); val w = fy * fy * (3f - 2f * fy)
            return (g(x0, y0) * (1f - u) + g(x0 + 1, y0) * u) * (1f - w) + (g(x0, y0 + 1) * (1f - u) + g(x0 + 1, y0 + 1) * u) * w
        }
        for (y in 0 until n) for (x in 0 until n) {
            val v = vn(x.toFloat(), y.toFloat(), 32, 1) * .5f + vn(x.toFloat(), y.toFloat(), 16, 2) * .3f + vn(x.toFloat(), y.toFloat(), 4, 3) * .2f
            val b = (0.72f + v * 0.36f).coerceIn(0f, 1f)
            val c = (b * 255f).toInt()
            px[y * n + x] = (0xFF shl 24) or (c shl 16) or (c shl 8) or c
        }
        val bmp = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        val id = IntArray(1)
        GLES20.glGenTextures(1, id, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        bmp.recycle()
        return id[0]
    }

    private fun makeGlowTex(): Int {
        val n = 64
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + .5f) / n * 2f - 1f
            val dy = (y + .5f) / n * 2f - 1f
            val r = min(1f, sqrt(dx * dx + dy * dy))
            val a = (1f - r) * (1f - r)
            px[y * n + x] = ((a * 255f).toInt() shl 24) or 0x00FFFFFF
        }
        val bmp = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        val id = IntArray(1)
        GLES20.glGenTextures(1, id, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        return id[0]
    }

    private fun upload(m: Mesh3) {
        val id = IntArray(1)
        GLES20.glGenBuffers(1, id, 0)
        m.vbo = id[0]
        val bb = ByteBuffer.allocateDirect(m.count * VSTRIDE * 4).order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(m.data, 0, m.count * VSTRIDE)
        fb.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, m.vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, m.count * VSTRIDE * 4, fb, GLES20.GL_STATIC_DRAW)
    }

    private fun gun(key: String): GunModel = guns.getOrPut(key) {
        val g = buildGunModel(key)
        registry.add(g.mesh)
        g
    }

    private fun hero(s: Skin): HeroGfx = heroes.getOrPut(s) {
        val hm = buildHeroModel(s)
        registry.add(hm.body)
        for (l in hm.legs) registry.add(l)
        val segs = hm.cloths.map { reg(buildClothSeg(it.w, it.h / 4f, it.color)) }
        HeroGfx(hm, segs)
    }

    /* ---------- matrix helpers (small and allocation-happy, but only a few hundred per frame) ---------- */
    private fun tr(m: FloatArray, x: Float, y: Float, z: Float): FloatArray { val r = m.copyOf(); Matrix.translateM(r, 0, x, y, z); return r }
    private fun rz(m: FloatArray, a: Float): FloatArray { val r = m.copyOf(); Matrix.rotateM(r, 0, a * 180f / PI.toFloat(), 0f, 0f, 1f); return r }
    private fun ry(m: FloatArray, a: Float): FloatArray { val r = m.copyOf(); Matrix.rotateM(r, 0, a * 180f / PI.toFloat(), 0f, 1f, 0f); return r }
    private fun rx(m: FloatArray, a: Float): FloatArray { val r = m.copyOf(); Matrix.rotateM(r, 0, a * 180f / PI.toFloat(), 1f, 0f, 0f); return r }
    private fun sc(m: FloatArray, x: Float, y: Float, z: Float): FloatArray { val r = m.copyOf(); Matrix.scaleM(r, 0, x, y, z); return r }

    private fun draw(m: Mesh3, model: FloatArray, mode: Int, r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f, flash: Float = 0f) {
        if (m.count == 0) return
        if (m.vbo == 0) upload(m)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, m.vbo)
        val st = VSTRIDE * 4
        GLES20.glEnableVertexAttribArray(aP); GLES20.glVertexAttribPointer(aP, 3, GLES20.GL_FLOAT, false, st, 0)
        GLES20.glEnableVertexAttribArray(aN); GLES20.glVertexAttribPointer(aN, 3, GLES20.GL_FLOAT, false, st, 12)
        GLES20.glEnableVertexAttribArray(aT); GLES20.glVertexAttribPointer(aT, 2, GLES20.GL_FLOAT, false, st, 24)
        GLES20.glEnableVertexAttribArray(aC); GLES20.glVertexAttribPointer(aC, 3, GLES20.GL_FLOAT, false, st, 32)
        GLES20.glUniformMatrix4fv(uM, 1, false, model, 0)
        nm[0] = model[0]; nm[1] = model[1]; nm[2] = model[2]
        nm[3] = model[4]; nm[4] = model[5]; nm[5] = model[6]
        nm[6] = model[8]; nm[7] = model[9]; nm[8] = model[10]
        GLES20.glUniformMatrix3fv(uN, 1, false, nm, 0)
        GLES20.glUniform1f(uMode, mode.toFloat())
        GLES20.glUniform4f(uTint, r, g, b, a)
        GLES20.glUniform1f(uFlash, flash)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, m.count)
    }

    private fun cr(c: Int) = ((c shr 16) and 255) / 255f
    private fun cg(c: Int) = ((c shr 8) and 255) / 255f
    private fun cb(c: Int) = (c and 255) / 255f

    /* ---------- frame ---------- */

    override fun onDrawFrame(gl: GL10?) {
        val s = snap
        val arena = s != null && s.mapId == 1
        val fog = if (arena) 0xFF2A1013.toInt() else 0xFFD3E0E2.toInt()
        GLES20.glClearColor(cr(fog), cg(fog), cb(fog), 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (s == null || prog == 0) return

        GLES20.glUseProgram(prog)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texNoise)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texGlow)
        GLES20.glUniform1i(uTex, 0); GLES20.glUniform1i(uGlow, 1)
        GLES20.glUniform3f(uFog, cr(fog), cg(fog), cb(fog))
        GLES20.glUniform2f(uFogR, 350f, 2600f)
        if (arena) {
            GLES20.glUniform3f(uSky, .42f, .36f, .52f); GLES20.glUniform3f(uGnd, .26f, .14f, .17f)
            GLES20.glUniform3f(uSun, .55f, .32f, .34f)
        } else {
            GLES20.glUniform3f(uSky, .66f, .72f, .80f); GLES20.glUniform3f(uGnd, .42f, .39f, .35f)
            GLES20.glUniform3f(uSun, .62f, .56f, .46f)
        }
        val ln = sqrt(.35f * .35f + .75f * .75f + .55f * .55f)
        GLES20.glUniform3f(uL, -.35f / ln, .75f / ln, .55f / ln)

        // level geometry for this map
        if (terr == null || terrVer != s.version) {
            terr?.let { t -> registry.remove(t.solid); registry.remove(t.back); registry.remove(t.glass); registry.removeAll(t.ridges.toSet()) }
            val tm = try { TerrainMesh.build(s.polys, s.windows, s.worldW, s.worldH, arena) } catch (e: Exception) { null }
            if (tm != null) {
                registry.add(tm.solid); registry.add(tm.back); registry.add(tm.glass); registry.addAll(tm.ridges)
            }
            terr = tm
            terrVer = s.version
        }

        // camera: the z = 0 plane maps exactly onto the 2D transform
        val fov = 38f
        val d = (s.vh / 2f) / tan(Math.toRadians(fov / 2.0).toFloat())
        val cx = s.camX + s.vw / 2f
        val cy = -(s.camY + s.vh / 2f)
        Matrix.setLookAtM(viewM, 0, cx, cy, d, cx, cy, 0f, 0f, 1f, 0f)
        Matrix.perspectiveM(proj, 0, fov, sw.toFloat() / sh.toFloat(), 40f, 7000f)
        Matrix.multiplyMM(vp, 0, proj, 0, viewM, 0)

        val dt = if (lastT > 0f && s.t >= lastT) min(.1f, s.t - lastT) else 0f
        lastT = s.t

        // sky and clouds (screen space, no depth)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUniformMatrix4fv(uVP, 1, false, ident, 0)
        drawSky(arena)
        GLES20.glUniformMatrix4fv(uVP, 1, false, vp, 0)
        drawClouds(s, arena)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        val tm = terr
        if (tm != null) {
            for (r in tm.ridges) draw(r, ident, 2, 1f, 1f, 1f, 1f)
            draw(tm.back, ident, 1)
            draw(tm.solid, ident, 1)
            draw(tm.glass, ident, 0)
        }

        // opaque props
        val blended = ArrayList<Runnable>()
        for ((i, pk) in s.picks.withIndex()) {
            if (!pk.on) continue
            val by = pk.y + sin(s.t * 3f + i) * 3f
            val base = tr(ident, pk.x, -by, 0f)
            val tintC = when (pk.t) { "health" -> 0xFF5EE06B.toInt(); "gren" -> 0xFFFF6F59.toInt(); else -> 0xFFFFB347.toInt() }
            blended.add(Runnable {
                draw(quad, sc(base, 62f, 62f, 1f), 3, cr(tintC), cg(tintC), cb(tintC), .38f)
                draw(quad, sc(base, 40f, 40f, 1f), 3, cr(tintC), cg(tintC), cb(tintC), .35f)
            })
            when (pk.t) {
                "health" -> draw(healthMesh, ry(base, s.t * 1.5f), 0)
                "gren" -> {
                    val gm = gun("grenade")
                    val k = 24f / gm.h
                    draw(gm.mesh, tr(sc(ry(base, s.t * 1.5f), k, k, k), -gm.w / 2f, -gm.h / 2f, 0f), 0)
                }
                else -> {
                    val gm = gun(pk.t)
                    val k = (GUN_LEN[pk.t] ?: 40f) * 1.1f / gm.w
                    draw(gm.mesh, tr(sc(ry(base, s.t * 1.2f), k, k, k), -gm.w / 2f, -gm.h / 2f, 0f), 0)
                }
            }
        }
        for (g in s.grens) {
            val gm = gun("grenade")
            val k = 15f / gm.h
            val base = tr(ident, g.x, -g.y, 0f)
            draw(gm.mesh, tr(sc(rz(ry(base, s.t * 5f), s.t * 4f), k, k, k), -gm.w / 2f, -gm.h / 2f, 0f), 0)
            val on = floor(s.t * (if (g.fuse < .7f) 14f else 6f)).toInt() % 2 == 0
            blended.add(Runnable {
                draw(quad, sc(tr(base, 3f, 0f, 8f), 12f, 12f, 1f), 3, 1f, .15f, .1f, if (on) .95f else .25f)
                draw(quad, sc(tr(ident, g.x, -g.y - 7f, 0f), 15f, 5f, 1f), 3, 0f, 0f, 0f, .3f)
            })
        }
        for (e in s.soldiers) drawSoldier(s, e, dt, blended)

        // transparent bits last, without writing depth
        GLES20.glDepthMask(false)
        for (r in blended) r.run()
        GLES20.glDepthMask(true)
        GLES20.glDisableVertexAttribArray(aP); GLES20.glDisableVertexAttribArray(aN)
        GLES20.glDisableVertexAttribArray(aT); GLES20.glDisableVertexAttribArray(aC)
    }

    private fun drawSky(arena: Boolean) {
        val top: Int; val mid: Int; val hor: Int
        if (arena) { top = 0xFF080506.toInt(); mid = 0xFF2A1018.toInt(); hor = 0xFF6A2A2E.toInt() }
        else { top = 0xFF6F9BC2.toInt(); mid = 0xFFAEC7D4.toInt(); hor = 0xFFDFE9E8.toInt() }
        if (skyMesh.count == 0 || skyArena != arena) {
            val b = MeshB()
            fun band(y0: Float, c0: Int, y1: Float, c1: Int) {
                b.color(c0); b.v(-1f, y0, 0f, 0f, 0f, 1f); b.v(1f, y0, 0f, 0f, 0f, 1f)
                b.color(c1); b.v(1f, y1, 0f, 0f, 0f, 1f)
                b.color(c0); b.v(-1f, y0, 0f, 0f, 0f, 1f)
                b.color(c1); b.v(1f, y1, 0f, 0f, 0f, 1f); b.v(-1f, y1, 0f, 0f, 0f, 1f)
            }
            band(-1f, hor, .05f, mid)
            band(.05f, mid, 1f, top)
            val m = b.build()
                registry.remove(skyMesh)
                skyMesh = reg(m)
                skyArena = arena

        }
        draw(skyMesh, ident, 2)
        // sun
        val sun = sc(tr(ident, .55f, .45f, 0f), .55f * (sh.toFloat() / sw.toFloat()) * 2.2f, 1.1f, 1f)
        if (!arena) draw(quad, sun, 3, 1f, .96f, .82f, .75f) else draw(quad, sun, 3, 1f, .4f, .35f, .35f)
    }

    private var skyArena = false

    private fun drawClouds(s: Snap, arena: Boolean) {
        val n = 9
        for (i in 0 until n) {
            val z = -1100f - (i % 4) * 260f
            val span = s.worldW + 3200f
            var x = (((i * 977f + s.t * (6f + (i % 3) * 3f)) % span) + span) % span - 1400f
            val y = -(s.worldH * .5f) + 380f + (i * 83 % 200) + (i % 3) * 120f
            val w = 520f + (i * 61 % 5) * 90f
            val m = sc(tr(ident, x, y, z), w, w * .28f, 1f)
            if (arena) draw(quad, m, 3, .6f, .2f, .22f, .22f) else draw(quad, m, 3, 1f, 1f, 1f, .5f)
        }
    }

    private fun drawSoldier(s: Snap, e: SoldierSnap, dt: Float, blended: ArrayList<Runnable>) {
        val sk = e.skin
        val h = sk.h
        val gfx = hero(sk)
        val hm = gfx.m
        val dir = if (cos(e.aim) >= 0f) 1f else -1f
        val la = if (dir > 0f) e.aim else PI.toFloat() - e.aim
        val target = if (dir > 0f) -0.45f else PI.toFloat() - 0.45f
        val cur = yaws[e.id] ?: target
        val ny = cur + (target - cur) * min(1f, dt * 11f)
        yaws[e.id] = ny
        val moving = e.onG && abs(e.vx) > 20f
        val bob = if (moving) abs(sin(e.run)) * 1.6f else 0f
        val k = e.sc * h.scale
        val flash = if (e.hurt) .85f else 0f

        // ground shadow
        if (e.onG) blended.add(Runnable { draw(quad, sc(tr(ident, e.x, -e.y + 1f, 2f), 30f * k, 8f * k, 1f), 3, 0f, 0f, 0f, .38f) })
        if (e.hidden) return

        val m0 = ry(sc(tr(ident, e.x, -e.y + bob * k, 0f), k, k, k), ny)
        draw(hm.body, m0, 0, flash = flash)

        // legs swing on their hip pivots
        val stride = h.hip / 17f
        for (i in 0 until 2) {
            val back = i == 1
            val ph = if (back) e.run + PI.toFloat() else e.run
            val ang: Float = if (e.onG && !moving) (if (back) -0.12f else 0.12f)
            else if (e.onG) atan2(sin(ph) * 9f * stride, hm.legPivot[i][1]) * 1.15f
            else if (e.jetting) (if (back) -0.12f else 0.1f) + sin(s.t * 14f + (if (back) 1f else 0f)) * 0.05f
            else (if (back) -0.25f else 0.2f) + sin(s.t * 6f + (if (back) 2f else 0f)) * 0.05f
            val lm = rz(tr(m0, if (back) -2f else 2f, hm.legPivot[i][1], hm.legPivot[i][2]), ang)
            draw(hm.legs[i], lm, 0, flash = flash)
        }

        // arm and gun, rotated by the aim elevation
        val gm = gun(e.wp)
        val arms = gfx.arms.getOrPut(e.wp) { reg(buildArms(hm, gm, e.wp)) }
        val aimM = rz(tr(m0, 1f, hm.shY, 0f), -la)
        draw(arms, aimM, 0, flash = flash)
        val sg = (GUN_LEN[e.wp] ?: 40f) / gm.w * 1.12f
        val gripX = 10f
        val gunM = sc(tr(aimM, gripX - gm.gx * sg, -(gm.h - gm.gy) * sg, 0f), sg, sg, sg)
        draw(gm.mesh, gunM, 0, flash = flash)
        if (e.flash > 0f && gm.kind != 0) {
            val mx = gripX + (gm.mx - gm.gx) * sg
            val my = (gm.gy - gm.my) * sg
            val a = min(1f, e.flash * 8f)
            val size = 20f + 18f * a
            val fm = tr(aimM, mx + 4f, my, 3f)
            val c = when (gm.kind) { 3 -> 0xFF40F0FF.toInt(); 2 -> 0xFFFF9B3D.toInt(); else -> 0xFFFFE08A.toInt() }
            blended.add(Runnable { draw(quad, sc(fm, size, size, 1f), 3, cr(c), cg(c), cb(c), .9f * a) })
        }

        // cloth: scarves and capes trail behind
        if (hm.cloths.isNotEmpty()) {
            val wind = (e.vx * dir / 260f).coerceIn(-1f, 1f)
            for ((ci, cl) in hm.cloths.withIndex()) {
                var cm = tr(m0, cl.x, cl.y, cl.z)
                for (j in 0 until 4) {
                    val ang = cl.rz + sin(s.t * cl.speed * .5f + j * .8f + ci) * cl.amp * (.4f + .3f * j) - wind * (.22f + .16f * j)
                    cm = rz(cm, ang)
                    draw(gfx.segs[ci], cm, 0, flash = flash)
                    cm = tr(cm, 0f, -cl.h / 4f, 0f)
                }
            }
        }

        // jet flames
        if (e.jetting) {
            for (f in hm.flames) {
                val fl = (14f + (sin(s.t * 40f + f[2]) * .5f + .5f) * 10f) * (.8f + h.pack * .2f)
                val fm = tr(m0, f[0], f[1] - fl / 2f, f[2])
                val r = 3.4f * f[3]
                blended.add(Runnable {
                    val o = sc(rx(fm, PI.toFloat()), r, fl, r)
                    draw(flameOut, o, 2, 1f, 1f, 1f, .88f)
                    val i2 = sc(rx(tr(fm, 0f, fl * .1f, 0f), PI.toFloat()), r * .5f, fl * .6f, r * .5f)
                    draw(flameIn, i2, 2, 1f, 1f, 1f, .95f)
                    draw(quad, sc(tr(m0, f[0], f[1] - fl * .5f, f[2] + 2f), 26f, 26f, 1f), 3, 1f, .6f, .2f, .35f)
                })
            }
        }
    }
}
