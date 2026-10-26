package com.xcluice.jetstrike

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.InputFilter
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.EditText
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class Btn(val id: String, val l: Float, val t: Float, val r: Float, val b: Float)

class GameView(ctx: Context) : SurfaceView(ctx), SurfaceHolder.Callback, Runnable {

    private val game = Game()
    private val sfx = Sfx(ctx)
    private val prefs = ctx.getSharedPreferences("jetstrike", Context.MODE_PRIVATE)
    private val actions = ConcurrentLinkedQueue<Runnable>()
    @Volatile private var running = false
    private var thread: Thread? = null

    private val d = resources.displayMetrics.density
    private var W = 1f
    private var H = 1f
    private var scale = 1f
    private var baseScale = 1f
    private var insetL = 0f
    private var insetR = 0f

    // paints
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val gp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeJoin = Paint.Join.ROUND }
    private val skyPaint = Paint()
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vigPaint = Paint()
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val playPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val fDisplay: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val body: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    private val OUTLINE = 0xFF06141C.toInt()
    private val TEAL = 0xFF19E3C8.toInt()
    private val AMBER = 0xFFFFB347.toInt()
    private val WHITE = 0xFFEAF8FB.toInt()
    private val MUTED = 0xFFA3C8D1.toInt()
    private val RED = 0xFFFF4F6D.toInt()

    private val platBody = PLATS.map { pl ->
        Paint().apply {
            shader = LinearGradient(
                0f, pl.y, 0f, pl.y + min(pl.h, 140f),
                if (pl.wall) 0xFF4A7A90.toInt() else 0xFF2F5A6E.toInt(),
                if (pl.wall) 0xFF1A3846.toInt() else 0xFF0E222C.toInt(),
                Shader.TileMode.CLAMP
            )
        }
    }
    private val platLip = PLATS.map { pl ->
        Paint().apply {
            shader = LinearGradient(pl.x, 0f, pl.x + pl.w, 0f, TEAL, 0xFFFFD23F.toInt(), Shader.TileMode.CLAMP)
        }
    }

    // touch state
    private var lId = -1
    private var rId = -1
    private var panId = -1
    private var uiPtr = -1
    private var btnPtr = -1
    private var btnKind = ""
    private var panLx = 0f
    private var panLy = 0f
    private var lkx = 0f
    private var lky = 0f
    private var rkx = 0f
    private var rky = 0f
    private var pressedId: String? = null

    // hud geometry (screen px), refreshed every frame
    private val pauseR = RectF()
    private val muteR = RectF()
    private val grenR = RectF()
    private val reloadR = RectF()
    private val slotR = arrayOf(RectF(), RectF())
    private val bGren = RectF()
    private val bScope = RectF()
    private val bPick = RectF()
    private val pickR = RectF()
    private var pickCx = 0f
    private var pickCy = 0f
    private val scopeR = RectF()
    private var scopeCx = 0f
    private var scopeCy = 0f
    private val scopePath = Path()
    private val bReload = RectF()
    private val bSlot = arrayOf(RectF(), RectF())
    private var lcx = 0f
    private var lcy = 0f
    private var rcx = 0f
    private var rcy = 0f
    private var lR = 0f
    private var rR = 0f
    private var grenCx = 0f
    private var grenCy = 0f
    private var barCx = 0f
    private var barCy = 0f

    // customisable control layout (position as screen fractions, plus a size multiplier)
    private class Ctl(var fx: Float, var fy: Float, var s: Float)
    private val ctl = HashMap<String, Ctl>()
    private val CTL_KEYS = listOf("move", "aim", "gren", "scope", "pick", "weap")
    private val CTL_NAMES = mapOf("move" to "MOVE STICK", "aim" to "AIM / FIRE STICK", "gren" to "GRENADE BUTTON", "scope" to "RANGE BUTTON", "pick" to "PICK BUTTON", "weap" to "WEAPON BAR")
    private val CTL_MIN = .6f
    private val CTL_MAX = 1.6f
    private var ctlLoaded = false
    @Volatile private var editing = false
    private var editSel = "move"
    private var editPtr = -1
    private var dragging = false
    private var sliderDrag = false
    private var dragDx = 0f
    private var dragDy = 0f
    private val sliderR = RectF()
    private val editDoneR = RectF()
    private val editResetR = RectF()
    private val tmpR = RectF()
    private val previewEnt = Ent(99, SKINS[0], 0)

    private var hostList: List<HostInfo> = emptyList()
    private var ipCache = ""
    private var ipAt = -10f

    // overlay layout in a 480x300 virtual card
    private var uiK = 1f
    private var uiOx = 0f
    private var uiOy = 0f
    private val backBtn = Btn("back", 372f, 18f, 448f, 52f)
    private val menuBtns = listOf(
        Btn("team", 204f, 82f, 328f, 112f), Btn("solo", 334f, 82f, 460f, 112f),
        Btn("easy", 204f, 134f, 284f, 160f), Btn("normal", 290f, 134f, 374f, 160f), Btn("hard", 380f, 134f, 460f, 160f),
        Btn("offline", 204f, 172f, 460f, 218f),
        Btn("host", 204f, 228f, 284f, 266f), Btn("join", 292f, 228f, 372f, 266f), Btn("ctl", 380f, 228f, 460f, 266f),
        Btn("prev", 24f, 128f, 50f, 164f), Btn("next", 158f, 128f, 184f, 164f),
        Btn("name", 20f, 238f, 188f, 278f)
    )
    private val hostBtns = listOf(
        backBtn,
        Btn("team", 36f, 114f, 238f, 150f), Btn("solo", 242f, 114f, 444f, 150f),
        Btn("easy", 36f, 158f, 168f, 190f), Btn("normal", 174f, 158f, 306f, 190f), Btn("hard", 312f, 158f, 444f, 190f),
        Btn("start", 36f, 208f, 444f, 262f)
    )
    private val waitBtns = listOf(backBtn)
    private val pauseBtns = listOf(
        Btn("resume", 36f, 104f, 444f, 156f), Btn("quit", 36f, 170f, 444f, 222f)
    )
    private val overBtns = listOf(
        Btn("again", 36f, 214f, 236f, 266f), Btn("menu", 244f, 214f, 444f, 266f)
    )
    private val overClientBtns = listOf(Btn("menu", 36f, 214f, 444f, 266f))

    init {
        game.net = Net(ctx)
        game.diff = prefs.getString("diff", "normal") ?: "normal"
        if (!DIFFS.containsKey(game.diff)) game.diff = "normal"
        game.menuRules = if (prefs.getInt("rules", 0) == 1) Rules.SOLO else Rules.TEAM
        game.sfx = { n, v -> sfx.play(n, v) }
        game.mySkin = prefs.getInt("skin", 0).coerceIn(0, SKINS.size - 1)
        game.myName = (prefs.getString("name", "Player") ?: "Player").take(12)
        titlePaint.shader = LinearGradient(68f, 0f, 270f, 0f,
            intArrayOf(TEAL, 0xFFFFD23F.toInt(), 0xFFFF8A3D.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        playPaint.shader = LinearGradient(36f, 0f, 444f, 0f,
            intArrayOf(TEAL, 0xFFFFD23F.toInt(), 0xFFFF8A3D.toInt()), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        logoPaint.shader = LinearGradient(36f, 22f, 100f, 86f,
            intArrayOf(TEAL, 0xFFFFD23F.toInt(), 0xFFFF7A3D.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        selPaint.shader = LinearGradient(0f, 0f, 480f, 0f,
            intArrayOf(TEAL, 0xFFFFB347.toInt()), null, Shader.TileMode.CLAMP)
        holder.addCallback(this)
        isFocusable = true
    }

    /* ---------- lifecycle ---------- */
    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        W = width.toFloat(); H = height.toFloat()
        baseScale = min(W / 700f, H / 430f)
        scale = baseScale * game.zoomCur / 4f
        game.vw = W / scale; game.vh = H / scale
        uiK = min(W * .94f / 480f, H * .94f / 300f)
        uiOx = (W - 480f * uiK) / 2f
        uiOy = (H - 300f * uiK) / 2f
        if (Build.VERSION.SDK_INT >= 28) {
            val cut = rootWindowInsets?.displayCutout
            if (cut != null) { insetL = cut.safeInsetLeft.toFloat(); insetR = cut.safeInsetRight.toFloat() }
        }
        skyPaint.shader = LinearGradient(0f, 0f, 0f, H,
            intArrayOf(0xFF052A40.toInt(), 0xFF0F7C86.toInt(), 0xFFF2B266.toInt(), 0xFFFFCF8A.toInt()),
            floatArrayOf(0f, .5f, .85f, 1f), Shader.TileMode.CLAMP)
        val sr = 150f * scale
        sunPaint.shader = RadialGradient(W * .72f, H * .3f, sr,
            intArrayOf(0xF2FFF8D2.toInt(), 0x8CFFD678.toInt(), 0x00FFAA50),
            floatArrayOf(0f, .18f, 1f), Shader.TileMode.CLAMP)
        vigPaint.shader = RadialGradient(W / 2f, H / 2f, hypot(W, H) / 2f,
            intArrayOf(0x00FF3C50, 0x00FF3C50, 0x8CFF3C50.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        layoutHud()
        if (!running) {
            running = true
            thread = Thread(this, "jetstrike-loop").also { it.start() }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try { thread?.join(500) } catch (e: InterruptedException) { }
        thread = null
    }

    fun autoPause() {
        game.pause()
    }

    fun release() {
        game.net?.shutdown()
        sfx.release()
    }

    fun handleBack(): Boolean {
        if (editing) { actions.add(Runnable { finishEdit() }); return true }
        val m = game.mode
        if (m == Mode.MENU) return false
        if (m == Mode.PLAY) {
            if (game.paused) actions.add(Runnable { game.resume() }) else actions.add(Runnable { game.pause() })
        } else {
            actions.add(Runnable { game.showMenu() })
        }
        return true
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            var dt = (now - last) / 1e9f
            last = now
            if (dt > .033f) dt = .033f
            while (true) {
                val a = actions.poll() ?: break
                a.run()
            }
            val ns = baseScale * game.zoomCur / 4f
            if (abs(ns - scale) > 1e-5f) { scale = ns; game.vw = W / scale; game.vh = H / scale }
            game.update(dt)
            val c: Canvas? = try {
                if (Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas()
            } catch (e: Exception) { null }
            if (c != null) {
                try { render(c) } catch (e: Exception) { }
                try { holder.unlockCanvasAndPost(c) } catch (e: Exception) { }
            } else {
                try { Thread.sleep(8) } catch (e: InterruptedException) { }
            }
        }
    }

    /* ---------- drawing helpers ---------- */
    private fun dp(v: Float): Float = v * d

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, col: Int) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = col
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, rad, rad, p)
    }

    /** Rounded rect with a dark outline, used for the characters so they read against any backdrop. */
    private fun rro(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, col: Int) {
        sp.shader = null; sp.strokeWidth = 4.2f; sp.color = OUTLINE
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, rad, rad, sp)
        rr(c, l, t, r, b, rad, col)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = col
        c.drawCircle(x, y, r, p)
    }

    private fun circo(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        sp.shader = null; sp.strokeWidth = 4.2f; sp.color = OUTLINE
        c.drawCircle(x, y, r, sp)
        circle(c, x, y, r, col)
    }

    private fun oval(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, col: Int) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = col
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(rect, p)
    }

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, col: Int) {
        sp.shader = null; sp.strokeWidth = w; sp.color = col
        c.drawLine(x1, y1, x2, y2, sp)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int, align: Paint.Align, face: Typeface, spacing: Float = 0f) {
        tp.shader = null; tp.style = Paint.Style.FILL
        tp.typeface = face; tp.textSize = size; tp.color = col; tp.textAlign = align; tp.letterSpacing = spacing
        c.drawText(s, x, y, tp)
    }

    private fun glass(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, sw: Float) {
        rr(c, l, t, r, b, rad, 0x66062030)
        gp.style = Paint.Style.FILL
        gp.shader = LinearGradient(0f, t, 0f, b, 0x4DFFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP)
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, rad, rad, gp)
        sp.shader = null; sp.strokeWidth = sw; sp.color = 0x66FFFFFF
        c.drawRoundRect(rect, rad, rad, sp)
    }

    /* ---------- world ---------- */
    private fun mountains(c: Canvas, par: Float, base: Float, amp: Float, col: Int, f1: Float, f2: Float) {
        path.reset()
        path.moveTo(0f, H)
        var x = 0f
        while (x <= W + 20f) {
            val wx = x + game.camX * scale * par
            path.lineTo(x, base + sin(wx * f1) * amp + sin(wx * f2 + 1.7f) * amp * .5f)
            x += 20f
        }
        path.lineTo(W + 20f, H)
        path.close()
        p.shader = null; p.style = Paint.Style.FILL; p.color = col
        c.drawPath(path, p)
    }

    private fun drawBackground(c: Canvas) {
        c.drawRect(0f, 0f, W, H, skyPaint)
        c.drawCircle(W * .72f, H * .3f, 150f * scale, sunPaint)
        val horizon = (GROUND_Y - game.camY) * scale
        for (i in 0 until 6) {
            var cx = ((i * 330f + game.t * (8f + i * 2f)) * scale * .6f - game.camX * scale * .08f) % (W + 400f)
            if (cx < 0f) cx += W + 400f
            cx -= 200f
            val cy = H * .12f + ((i * 97) % 160) * scale * .6f
            oval(c, cx, cy, 70f * scale, 16f * scale, 0x24FFFFFF)
            oval(c, cx + 40f * scale, cy - 8f * scale, 48f * scale, 13f * scale, 0x24FFFFFF)
        }
        mountains(c, .15f, horizon - 170f * scale, 40f * scale, 0x8C084658.toInt(), .004f, .011f)
        mountains(c, .3f, horizon - 90f * scale, 34f * scale, 0xBF053042.toInt(), .006f, .017f)
        mountains(c, .5f, horizon - 30f * scale, 18f * scale, 0xE603202E.toInt(), .01f, .026f)
    }

    private fun drawPlatforms(c: Canvas) {
        val left = game.camX - 20f
        val right = game.camX + game.vw + 20f
        for (i in PLATS.indices) {
            val pl = PLATS[i]
            if (pl.x + pl.w < left || pl.x > right) continue
            // soft drop shadow under floating platforms
            if (!pl.wall && pl.y < GROUND_Y - 1f) rr(c, pl.x + 6f, pl.y + pl.h, pl.x + pl.w - 6f, pl.y + pl.h + 12f, 6f, 0x22000000)
            c.drawRect(pl.x, pl.y, pl.x + pl.w, pl.y + pl.h, platBody[i])
            rr(c, pl.x, pl.y, pl.x + pl.w, pl.y + 2f, 0f, 0x26FFFFFF)
            c.drawRect(pl.x, pl.y, pl.x + pl.w, pl.y + 5f, platLip[i])
            if (!pl.wall && pl.h <= 20f) {
                var x = pl.x + 16f
                while (x < pl.x + pl.w - 8f) {
                    circle(c, x, pl.y + 13f, 2f, 0x40000000)
                    x += 44f
                }
            } else if (!pl.wall) {
                var x = pl.x + 40f
                while (x < pl.x + pl.w) {
                    rr(c, x, pl.y + 30f, x + 3f, pl.y + 120f, 1f, 0x14FFFFFF)
                    x += 90f
                }
            }
        }
    }

    private fun drawPicks(c: Canvas) {
        for ((i, pk) in game.picks.withIndex()) {
            if (pk.cd > 0f) continue
            val by = pk.y + sin(game.t * 3f + i) * 3f
            val hp = pk.t == "health"
            val gr = pk.t == "gren"
            val tint = if (hp) 0xFF5EE06B.toInt() else if (gr) 0xFFFF6F59.toInt() else AMBER
            circle(c, pk.x, by, 29f, (0x22 shl 24) or (tint and 0x00FFFFFF))
            circle(c, pk.x, by, 21f, (0x33 shl 24) or (tint and 0x00FFFFFF))
            rr(c, pk.x - 15f, by - 15f, pk.x + 15f, by + 15f, 9f, 0x77FFFFFF)
            sp.shader = null; sp.strokeWidth = 1.6f; sp.color = 0xCCFFFFFF.toInt()
            rect.set(pk.x - 15f, by - 15f, pk.x + 15f, by + 15f)
            c.drawRoundRect(rect, 9f, 9f, sp)
            if (hp) {
                rr(c, pk.x - 2.5f, by - 8f, pk.x + 2.5f, by + 8f, 1f, 0xFF3FC95A.toInt())
                rr(c, pk.x - 8f, by - 2.5f, pk.x + 8f, by + 2.5f, 1f, 0xFF3FC95A.toInt())
            } else if (gr) {
                circle(c, pk.x, by + 2f, 7.5f, 0xFF2B3A42.toInt())
                rr(c, pk.x - 2.5f, by - 8f, pk.x + 2.5f, by - 4f, 1f, 0xFF8DA4AD.toInt())
                circle(c, pk.x + 3f, by - 1f, 1.8f, 0xFFFF6F59.toInt())
            } else {
                val s = when (pk.t) { "shotgun" -> "SG"; "sniper" -> "SN"; "smg" -> "SM"; "rocket" -> "RK"; "flame" -> "FL"; "laser" -> "LS"; else -> "AR" }
                text(c, s, pk.x, by + 4.5f, 13f, 0xFF04141C.toInt(), Paint.Align.CENTER, body)
            }
        }
    }

    private fun drawGrenades(c: Canvas) {
        for (g in game.grenades) {
            oval(c, g.x, g.y + 7f, 7f, 2.2f, 0x33000000)
            circo(c, g.x, g.y, 6.5f, 0xFF3A4A52.toInt())
            rr(c, g.x - 2f, g.y - 10f, g.x + 2f, g.y - 5f, 1f, 0xFF8DA4AD.toInt())
            val on = floor(game.t * (if (g.fuse < .7f) 14f else 6f)).toInt() % 2 == 0
            circle(c, g.x + 2f, g.y - 1f, 2.2f, if (on) 0xFFFF4F4F.toInt() else 0xFF661A1A.toInt())
        }
    }

    private fun leg(c: Canvas, e: Ent, ph: Float, col: Int, back: Boolean) {
        val hx = if (back) -2f else 2f
        val hy = -17f
        val moving = e.onG && abs(e.vx) > 20f
        val fx: Float
        val fy: Float
        if (e.onG && !moving) {
            fx = hx + (if (back) -3f else 3f); fy = 0f
        } else if (e.onG) {
            fx = sin(ph) * 9f; fy = -max(0f, cos(ph)) * 6f
        } else if (e.jetting) {
            fx = hx + (if (back) -3f else 2f) + sin(game.t * 14f + (if (back) 1f else 0f)) * 1.5f; fy = -3f
        } else {
            fx = hx + (if (back) -5f else 4f); fy = -4f + sin(game.t * 6f + (if (back) 2f else 0f)) * 1.5f
        }
        val kx = (hx + fx) / 2f + 4f
        val ky = (hy + fy) / 2f
        path.reset()
        path.moveTo(hx, hy)
        path.quadTo(kx, ky, fx, fy)
        sp.shader = null; sp.color = OUTLINE; sp.strokeWidth = 10.6f
        c.drawPath(path, sp)
        sp.color = col; sp.strokeWidth = 7.6f
        c.drawPath(path, sp)
        rro(c, fx - 4f, fy - 3f, fx + 6f, fy + 2f, 2.5f, 0xFF1B2C34.toInt())
    }

    private fun fin(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, col: Int) {
        path.reset()
        path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
        sp.shader = null; sp.strokeWidth = 2.6f; sp.color = OUTLINE
        c.drawPath(path, sp)
        p.shader = null; p.style = Paint.Style.FILL; p.color = col
        c.drawPath(path, p)
    }

    private fun accessory(c: Canvas, sk: Skin, dark: Int, light: Int) {
        when (sk.acc) {
            0 -> {
                line(c, -2f, -48f, -6f, -58f, 3.6f, OUTLINE); line(c, -2f, -48f, -6f, -58f, 1.4f, light)
                circo(c, -6f, -59f, 2.6f, light)
            }
            1 -> fin(c, -7f, -46f, -3f, -62f, 3f, -48f, dark)
            2 -> for (k in 0..2) {
                val x = -4f + k * 4.5f
                fin(c, x - 2.6f, -48f, x, -57f - (if (k == 1) 3f else 0f), x + 2.6f, -48f, light)
            }
            3 -> {
                rro(c, -8.5f, -45f, 10.5f, -41.5f, 1.5f, dark)
                val w = sin(game.t * 9f) * 2f
                line(c, -8f, -43f, -19f, -40f + w, 4f, OUTLINE); line(c, -8f, -43f, -19f, -40f + w, 2f, light)
                line(c, -8f, -43f, -17f, -46f - w, 4f, OUTLINE); line(c, -8f, -43f, -17f, -46f - w, 2f, light)
            }
            4 -> {
                rect.set(-6f, -60f, 8f, -54f)
                sp.shader = null; sp.strokeWidth = 4f; sp.color = OUTLINE
                c.drawOval(rect, sp)
                sp.strokeWidth = 2f; sp.color = light
                c.drawOval(rect, sp)
            }
            5 -> {
                fin(c, -7f, -47f, -5.5f, -59f, -1f, -49f, dark)
                fin(c, 2f, -49f, 5.5f, -59f, 8f, -46f, dark)
            }
            6 -> {
                line(c, -3f, -48f, -9f, -58f, 3.6f, OUTLINE); line(c, -3f, -48f, -9f, -58f, 1.4f, light)
                line(c, 3f, -48f, 8f, -58f, 3.6f, OUTLINE); line(c, 3f, -48f, 8f, -58f, 1.4f, light)
                circle(c, -9f, -58.5f, 2f, light); circle(c, 8f, -58.5f, 2f, light)
            }
        }
    }

    private fun cape(c: Canvas, dark: Int) {
        path.reset()
        path.moveTo(-6f, -33f)
        path.quadTo(-24f, -22f, -22f + sin(game.t * 4f) * 2f, -4f)
        path.lineTo(-10f, -12f)
        path.close()
        sp.shader = null; sp.strokeWidth = 2.8f; sp.color = OUTLINE
        c.drawPath(path, sp)
        p.shader = null; p.style = Paint.Style.FILL; p.color = dark
        c.drawPath(path, p)
    }

    private fun drawGun(c: Canvas, e: Ent) {
        val w = WEAPONS.getValue(e.wp)
        rro(c, 5f, -3f, 5f + w.len, 3f, 2f, 0xFF2B3A42.toInt())
        rr(c, 5f + w.len * .35f, -3f, 5f + w.len * .75f, -1f, 0f, 0xFF8DA4AD.toInt())
        when (e.wp) {
            "sniper" -> rr(c, 10f, -6f, 19f, -3f, 1f, TEAL)
            "shotgun" -> rr(c, 2f, -3f, 12f, 4f, 2f, 0xFF8A5A2B.toInt())
            "rifle" -> rr(c, 12f, 2f, 17f, 9f, 1f, 0xFF1C262B.toInt())
            "smg" -> rr(c, 9f, 2f, 13f, 10f, 1f, 0xFF1C262B.toInt())
            "rocket" -> {
                rro(c, 0f, -6f, 5f + w.len, 6f, 3f, 0xFF59693F.toInt())
                rr(c, 5f + w.len - 4f, -7f, 5f + w.len + 2f, 7f, 2f, 0xFF2B3A42.toInt())
            }
            "flame" -> {
                rro(c, 2f, -8f, 13f, 5f, 3f, 0xFFD9553A.toInt())
                rr(c, 5f + w.len - 2f, -4.5f, 5f + w.len + 3f, 4.5f, 2f, 0xFF2B3A42.toInt())
            }
            "laser" -> {
                rr(c, 10f, -5f, 5f + w.len - 4f, -2.5f, 1f, 0xFF00E5FF.toInt())
                rr(c, 6f, 2f, 11f, 8f, 1f, 0xFF1C262B.toInt())
            }
        }
        if (e.flash > 0f) {
            val fx = 5f + w.len + 3f
            path.reset()
            path.moveTo(fx, 0f); path.lineTo(fx + 11f, -6f); path.lineTo(fx + 7f, 0f); path.lineTo(fx + 11f, 6f); path.close()
            p.shader = null; p.style = Paint.Style.FILL; p.color = 0xFFFFF3B0.toInt()
            c.drawPath(path, p)
            circle(c, fx + 2f, 0f, 6f, 0xCCFFAA3C.toInt())
        }
    }

    private fun drawSoldier(c: Canvas, e: Ent, tags: Boolean = true) {
        val sk = e.skin
        val hurt = e.hurt > 0f
        val bodyC = if (hurt) 0xFFFFFFFF.toInt() else sk.c
        val dark = if (hurt) 0xFFE6E6E6.toInt() else sk.d
        val light = if (hurt) 0xFFFFFFFF.toInt() else sk.l
        val dir = if (cos(e.aim) >= 0f) 1f else -1f
        val la = if (dir > 0f) e.aim else PI_F - e.aim
        val moving = e.onG && abs(e.vx) > 20f
        val bob = if (moving) abs(sin(e.run)) * 1.6f else 0f
        val blink = e.inv > 0f && floor(game.t * 14f).toInt() % 2 == 1
        val team = game.rules == Rules.TEAM
        val ally = e.team == game.player.team
        val teamCol = if (ally) TEAL else RED

        if (!blink) {
            c.save()
            c.translate(e.x, e.y)
            if (e.onG) oval(c, 0f, 1f, 12f, 3f, 0x38000000)
            if (team && e.onG) {
                sp.shader = null; sp.strokeWidth = 2f; sp.color = (0xAA shl 24) or (teamCol and 0x00FFFFFF)
                rect.set(-15f, -2.5f, 15f, 4.5f)
                c.drawOval(rect, sp)
            }
            c.scale(dir, 1f)
            c.translate(0f, -bob)
            // jetpack
            rro(c, -17f, -32f, -7f, -11f, 3f, 0xFF3F525C.toInt())
            rr(c, -16f, -12f, -12.5f, -8f, 1f, 0xFF1B2830.toInt())
            rr(c, -10.5f, -12f, -7f, -8f, 1f, 0xFF1B2830.toInt())
            rr(c, -16f, -28f, -8f, -25.5f, 1f, if (e.jetting) 0xFFFFD35C.toInt() else 0xFFFF6F59.toInt())
            if (e.jetting) {
                val fl = 14f + Math.random().toFloat() * 10f
                path.reset()
                path.moveTo(-17f, -9f); path.lineTo(-7f, -9f); path.lineTo(-12f, -9f + fl); path.close()
                p.shader = null; p.style = Paint.Style.FILL; p.color = 0xE6FF9B3D.toInt()
                c.drawPath(path, p)
                path.reset()
                path.moveTo(-14.5f, -9f); path.lineTo(-9.5f, -9f); path.lineTo(-12f, -9f + fl * .6f); path.close()
                p.color = 0xF2FFF0AA.toInt()
                c.drawPath(path, p)
            }
            // back leg, back arm, front leg
            leg(c, e, e.run + PI_F, dark, true)
            line(c, 0f, -27f, 3f + cos(la) * 12f, -27f + sin(la) * 12f, 10f, OUTLINE)
            line(c, 0f, -27f, 3f + cos(la) * 12f, -27f + sin(la) * 12f, 6.6f, dark)
            leg(c, e, e.run, bodyC, false)
            if (sk.acc == 7) cape(c, dark)
            // torso
            rro(c, -9.5f, -35f, 9.5f, -14f, 6f, bodyC)
            rr(c, -9.5f, -21f, 9.5f, -17f, 0f, dark)
            rr(c, -6f, -33f, 2f, -25f, 3f, (light and 0x00FFFFFF) or 0x99000000.toInt())
            // head
            circo(c, 1f, -42f, 12.5f, bodyC)
            circle(c, -2.5f, -47f, 6.5f, (light and 0x00FFFFFF) or 0x99000000.toInt())
            oval(c, 6.5f, -42f, 7.5f, 5.6f, 0xFF2B8CA3.toInt())
            oval(c, 6.5f, -41f, 6.4f, 4.1f, 0xFF8FE3F5.toInt())
            oval(c, 4.5f, -43.8f, 3.4f, 1.6f, 0xBFFFFFFF.toInt())
            c.save(); c.translate(0f, -3.5f); accessory(c, sk, dark, light); c.restore()
            // front arm and gun
            c.save()
            c.translate(1f, -27f)
            c.rotate(la * 180f / PI_F)
            c.scale(1.12f, 1.12f)
            line(c, 0f, 0f, 9f, 0f, 10f, OUTLINE)
            line(c, 0f, 0f, 9f, 0f, 6.6f, bodyC)
            drawGun(c, e)
            circle(c, 9f, 0f, 2.8f, light)
            c.restore()
            c.restore()
        }
        if (!tags) return
        // marker: your name and an arrow, name and health for everyone else
        c.save()
        c.translate(e.x, e.y)
        if (game.isMe(e)) {
            text(c, game.nameOf(e.id), 0f, -70f, 12f, TEAL, Paint.Align.CENTER, body)
            val yy = -80f + sin(game.t * 4f) * 2f
            path.reset()
            path.moveTo(0f, yy); path.lineTo(-7f, yy - 10f); path.lineTo(7f, yy - 10f); path.close()
            p.shader = null; p.style = Paint.Style.FILL; p.color = TEAL
            c.drawPath(path, p)
        } else {
            val nameCol = if (team) teamCol else 0xE6FFFFFF.toInt()
            text(c, game.nameOf(e.id), 0f, -74f, 12f, nameCol, Paint.Align.CENTER, body)
            rr(c, -18f, -70f, 18f, -65f, 2.5f, 0x80000000.toInt())
            val hpCol = if (e.hp > 50f) 0xFF5EE06B.toInt() else if (e.hp > 25f) 0xFFFFD23F.toInt() else RED
            rr(c, -18f, -70f, -18f + max(0f, 36f * e.hp / 100f), -65f, 2.5f, hpCol)
        }
        c.restore()
        if (e.reload > 0f) {
            val w = WEAPONS.getValue(e.wp)
            rr(c, e.x - 14f, e.y - 62f, e.x + 14f, e.y - 59f, 1.5f, 0x33FFFFFF)
            rr(c, e.x - 14f, e.y - 62f, e.x - 14f + 28f * (1f - e.reload / w.rel), e.y - 59f, 1.5f, AMBER)
        }
    }

    private fun drawBullets(c: Canvas) {
        for (b in game.bullets) {
            when (b.kind) {
                1 -> {
                    c.save()
                    c.translate(b.x, b.y)
                    c.rotate(atan2(b.vy, b.vx) * 180f / PI_F)
                    path.reset()
                    path.moveTo(-9f, -3f); path.lineTo(-20f - Math.random().toFloat() * 8f, 0f); path.lineTo(-9f, 3f); path.close()
                    p.shader = null; p.style = Paint.Style.FILL; p.color = 0xE6FF9B3D.toInt()
                    c.drawPath(path, p)
                    rro(c, -9f, -3.2f, 8f, 3.2f, 2f, 0xFFC9D4D8.toInt())
                    path.reset()
                    path.moveTo(8f, -3.2f); path.lineTo(15f, 0f); path.lineTo(8f, 3.2f); path.close()
                    p.color = 0xFFFF5A5A.toInt()
                    c.drawPath(path, p)
                    c.restore()
                }
                2 -> {
                    circle(c, b.x, b.y, 9f, 0x44FF6A2D)
                    circle(c, b.x, b.y, 5.5f, 0x99FFA63D.toInt())
                    circle(c, b.x, b.y, 2.8f, 0xCCFFF0AA.toInt())
                }
                3 -> {
                    val tx = b.x - b.vx * .03f
                    val ty = b.y - b.vy * .03f
                    line(c, tx, ty, b.x, b.y, 9f, 0x4400E5FF)
                    line(c, tx, ty, b.x, b.y, 3f, 0xFFB8F8FF.toInt())
                }
                else -> {
                    val tx = b.x - b.vx * b.tr
                    val ty = b.y - b.vy * b.tr
                    line(c, tx, ty, b.x, b.y, if (b.big) 7f else 5.5f, 0x59FFC85A)
                    line(c, tx, ty, b.x, b.y, if (b.big) 3f else 2f, 0xFFFFFBE0.toInt())
                }
            }
        }
    }

    private fun drawParts(c: Canvas) {
        for (pt in game.parts) {
            val a = max(0f, pt.l / pt.m)
            val col = ((a * 255f).toInt() shl 24) or (pt.c and 0x00FFFFFF)
            val s = if (pt.glow) pt.s * (.4f + a * .6f) else pt.s
            circle(c, pt.x, pt.y, s, col)
        }
    }

    /** Floating damage numbers: pop in large, drift up, fade. */
    private fun drawDamageTexts(c: Canvas) {
        for (dt in game.texts) {
            val a = max(0f, min(1f, dt.l * 1.7f))
            val sz = 19f * (1f + max(0f, dt.l - .82f) * 2.6f)
            tp.shader = null
            tp.typeface = fDisplay; tp.textSize = sz; tp.textAlign = Paint.Align.CENTER; tp.letterSpacing = 0f
            tp.style = Paint.Style.STROKE; tp.strokeWidth = sz * .22f
            tp.color = ((a * 210f).toInt() shl 24) or 0x061018
            c.drawText(dt.text, dt.x, dt.y, tp)
            tp.style = Paint.Style.FILL
            tp.color = ((a * 255f).toInt() shl 24) or (dt.col and 0x00FFFFFF)
            c.drawText(dt.text, dt.x, dt.y, tp)
        }
    }

    private fun drawAim(c: Canvas) {
        val pl = game.player
        if (!pl.alive || !game.isMe(pl)) return
        val sx = pl.x + cos(pl.aim) * 86f
        val sy = pl.y - 28f + sin(pl.aim) * 86f
        sp.shader = null; sp.strokeWidth = 1.8f; sp.color = 0xBFFFFFFF.toInt()
        c.drawCircle(sx, sy, 6f, sp)
        c.drawLine(sx - 10f, sy, sx - 3f, sy, sp); c.drawLine(sx + 3f, sy, sx + 10f, sy, sp)
        c.drawLine(sx, sy - 10f, sx, sy - 3f, sp); c.drawLine(sx, sy + 3f, sx, sy + 10f, sp)
    }

    /* ---------- HUD ---------- */
    private fun defCtl(k: String): Ctl {
        val edge = max(dp(84f), W * .13f)
        return when (k) {
            "move" -> Ctl((edge + insetL) / W, (H - dp(88f)) / H, 1f)
            "aim" -> Ctl((W - edge - insetR) / W, (H - dp(88f)) / H, 1f)
            "gren" -> Ctl((W - edge - insetR) / W, (H - dp(88f) - dp(112f)) / H, 1f)
            "scope" -> Ctl((W - edge - insetR - dp(88f)) / W, (H - dp(88f) - dp(34f)) / H, 1f)
            "pick" -> Ctl((W - edge - insetR - dp(100f)) / W, (H - dp(88f) - dp(112f)) / H, 1f)
            else -> Ctl(.5f, (H - dp(12f) - dp(26f)) / H, 1f)
        }
    }

    private fun loadCtl() {
        for (k in CTL_KEYS) {
            val df = defCtl(k)
            ctl[k] = Ctl(prefs.getFloat("c_${k}_x", df.fx), prefs.getFloat("c_${k}_y", df.fy),
                prefs.getFloat("c_${k}_s", 1f).coerceIn(CTL_MIN, CTL_MAX))
        }
    }

    private fun saveCtl() {
        val ed = prefs.edit()
        for ((k, v) in ctl) { ed.putFloat("c_${k}_x", v.fx); ed.putFloat("c_${k}_y", v.fy); ed.putFloat("c_${k}_s", v.s) }
        ed.apply()
    }

    private fun resetCtl() {
        val ed = prefs.edit()
        for (k in CTL_KEYS) { ed.remove("c_${k}_x"); ed.remove("c_${k}_y"); ed.remove("c_${k}_s") }
        ed.apply()
        ctl.clear(); loadCtl(); layoutHud()
    }

    private fun finishEdit() {
        saveCtl()
        editing = false
        editPtr = -1; dragging = false; sliderDrag = false
    }

    private fun scaleRect(r: RectF, cx: Float, cy: Float, k: Float, out: RectF) {
        out.set(cx + (r.left - cx) * k, cy + (r.top - cy) * k, cx + (r.right - cx) * k, cy + (r.bottom - cy) * k)
    }

    private fun layoutHud() {
        if (!ctlLoaded || ctl.isEmpty()) { loadCtl(); ctlLoaded = true }
        val mr = dp(12f) + insetR
        val top = dp(10f)
        val bs = dp(42f)
        pauseR.set(W - mr - bs, top, W - mr, top + bs)
        muteR.set(W - mr - bs * 2f - dp(8f), top, W - mr - bs - dp(8f), top + bs)
        val m = ctl.getValue("move")
        lcx = m.fx * W; lcy = m.fy * H; lR = dp(56f) * m.s
        val a = ctl.getValue("aim")
        rcx = a.fx * W; rcy = a.fy * H; rR = dp(56f) * a.s
        val g = ctl.getValue("gren")
        grenCx = g.fx * W; grenCy = g.fy * H
        val gs = dp(30f)
        bGren.set(grenCx - gs, grenCy - gs, grenCx + gs, grenCy + gs)
        scaleRect(bGren, grenCx, grenCy, g.s, grenR)
        val sc = ctl.getValue("scope")
        scopeCx = sc.fx * W; scopeCy = sc.fy * H
        bScope.set(scopeCx - gs, scopeCy - gs, scopeCx + gs, scopeCy + gs)
        scaleRect(bScope, scopeCx, scopeCy, sc.s, scopeR)
        val pk = ctl.getValue("pick")
        pickCx = pk.fx * W; pickCy = pk.fy * H
        bPick.set(pickCx - gs, pickCy - gs, pickCx + gs, pickCy + gs)
        scaleRect(bPick, pickCx, pickCy, pk.s, pickR)
        // weapon bar: two slots and a reload button
        val w = ctl.getValue("weap")
        barCx = w.fx * W; barCy = w.fy * H
        val sw = dp(112f)
        val sh = dp(52f)
        val gap = dp(6f)
        val rs = dp(46f)
        val total = sw * 2f + gap * 2f + rs
        val x0 = barCx - total / 2f
        val y0 = barCy - sh / 2f
        bSlot[0].set(x0, y0, x0 + sw, y0 + sh)
        bSlot[1].set(x0 + sw + gap, y0, x0 + sw * 2f + gap, y0 + sh)
        bReload.set(x0 + sw * 2f + gap * 2f, y0 + (sh - rs) / 2f, x0 + total, y0 + (sh + rs) / 2f)
        scaleRect(bSlot[0], barCx, barCy, w.s, slotR[0])
        scaleRect(bSlot[1], barCx, barCy, w.s, slotR[1])
        scaleRect(bReload, barCx, barCy, w.s, reloadR)
    }

    private fun statRow(c: Canvas, label: String, x: Float, y: Float, w: Float, frac: Float, col: Int, num: String) {
        text(c, label, x, y + dp(4f), dp(11f), MUTED, Paint.Align.LEFT, body, .08f)
        val bx = x + dp(34f)
        val bw = w - dp(34f) - dp(32f)
        rr(c, bx, y - dp(4f), bx + bw, y + dp(4f), dp(4f), 0x59000000)
        val f = max(0f, min(1f, frac))
        if (f > 0.01f) rr(c, bx, y - dp(4f), bx + max(dp(8f), bw * f), y + dp(4f), dp(4f), col)
        text(c, num, x + w, y + dp(4f), dp(12f), WHITE, Paint.Align.RIGHT, body)
    }

    private fun drawStick(c: Canvas, cx: Float, cy: Float, kx: Float, ky: Float, active: Boolean, label: String, warm: Boolean, k: Float) {
        val r = dp(56f) * k
        circle(c, cx, cy, r, if (active) 0x40FFFFFF else 0x1CFFFFFF)
        sp.shader = null; sp.strokeWidth = dp(1.5f); sp.color = if (active) 0x99FFFFFF.toInt() else 0x55FFFFFF
        c.drawCircle(cx, cy, r, sp)
        sp.strokeWidth = dp(1f); sp.color = 0x22FFFFFF
        c.drawCircle(cx, cy, r * .5f, sp)
        val knob = if (warm) (if (active) 0xDDFFB347.toInt() else 0x66FFB347) else (if (active) 0xCCFFFFFF.toInt() else 0x66FFFFFF)
        circle(c, cx + kx * r, cy + ky * r, dp(26f) * k, knob)
        text(c, label, cx, cy + r + dp(15f), dp(10f), 0x99FFFFFF.toInt(), Paint.Align.CENTER, body, .15f)
    }

    private fun roundBtn(c: Canvas, r: RectF, active: Boolean) {
        glass(c, r.left, r.top, r.right, r.bottom, r.height() / 2f, dp(1f))
        if (active) {
            sp.shader = null; sp.strokeWidth = dp(2f); sp.color = AMBER
            rect.set(r)
            c.drawRoundRect(rect, r.height() / 2f, r.height() / 2f, sp)
        }
    }

    private fun drawWeaponBar(c: Canvas, pl: Ent) {
        for (i in 0..1) {
            val r = bSlot[i]
            val key = pl.wps[i]
            val sel = pl.cur == i
            glass(c, r.left, r.top, r.right, r.bottom, dp(14f), dp(1f))
            if (sel) {
                sp.shader = null; sp.strokeWidth = dp(2.2f)
                sp.shader = LinearGradient(r.left, 0f, r.right, 0f, TEAL, AMBER, Shader.TileMode.CLAMP)
                rect.set(r)
                c.drawRoundRect(rect, dp(14f), dp(14f), sp)
                sp.shader = null
            }
            if (key == null) {
                text(c, "EMPTY", r.centerX(), r.centerY() + dp(4f), dp(11f), 0x66FFFFFF, Paint.Align.CENTER, body, .15f)
                text(c, "grab a gun", r.centerX(), r.centerY() + dp(18f), dp(9f), 0x44FFFFFF, Paint.Align.CENTER, body)
                continue
            }
            val w = WEAPONS.getValue(key)
            c.save()
            c.translate(r.left + dp(10f), r.centerY() - dp(2f))
            c.scale(d * .95f, d * .95f)
            gunIcon(c, key, w.len * .55f, 0f, 1f, if (sel) WHITE else 0xAAFFFFFF.toInt())
            c.restore()
            text(c, w.n.uppercase(), r.left + dp(8f), r.top + dp(15f), dp(9.5f), if (sel) WHITE else MUTED, Paint.Align.LEFT, body, .06f)
            val ammo = pl.ammos[i]
            val low = ammo <= max(1, w.mag / 5)
            text(c, ammo.toString(), r.right - dp(9f), r.bottom - dp(11f), dp(21f),
                if (low) RED else AMBER, Paint.Align.RIGHT, fDisplay)
            text(c, "/" + w.mag, r.right - dp(9f), r.top + dp(15f), dp(9.5f), 0x88FFFFFF.toInt(), Paint.Align.RIGHT, body)
            if (sel && pl.reload > 0f) {
                rr(c, r.left + dp(10f), r.bottom - dp(8f), r.right - dp(10f), r.bottom - dp(5f), dp(2f), 0x33FFFFFF)
                rr(c, r.left + dp(10f), r.bottom - dp(8f), r.left + dp(10f) + (r.width() - dp(20f)) * (1f - pl.reload / w.rel), r.bottom - dp(5f), dp(2f), AMBER)
            }
        }
        roundBtn(c, bReload, pl.reload > 0f)
        val rcx2 = bReload.centerX()
        val rcy2 = bReload.centerY()
        sp.shader = null; sp.strokeWidth = dp(2.4f); sp.color = WHITE
        rect.set(rcx2 - dp(8f), rcy2 - dp(8f), rcx2 + dp(8f), rcy2 + dp(8f))
        c.drawArc(rect, -40f, 270f, false, sp)
        path.reset()
        path.moveTo(rcx2 + dp(9f), rcy2 - dp(11f)); path.lineTo(rcx2 + dp(9f), rcy2 - dp(2f)); path.lineTo(rcx2 + dp(1f), rcy2 - dp(5f)); path.close()
        p.shader = null; p.style = Paint.Style.FILL; p.color = WHITE
        c.drawPath(path, p)
    }

    private fun gunIcon(c: Canvas, key: String, len: Float, cy: Float, k: Float, col: Int) {
        val x0 = 0f
        rr(c, x0, cy - 3f * k, x0 + len, cy + 3f * k, 2f, col)
        when (key) {
            "sniper" -> rr(c, x0 + len * .3f, cy - 7f * k, x0 + len * .55f, cy - 3f * k, 1f, TEAL)
            "shotgun" -> rr(c, x0, cy - 3f * k, x0 + len * .3f, cy + 5f * k, 2f, 0xFFC98A4B.toInt())
            "rifle" -> rr(c, x0 + len * .35f, cy + 3f * k, x0 + len * .5f, cy + 10f * k, 1f, col)
            "smg" -> rr(c, x0 + len * .35f, cy + 3f * k, x0 + len * .5f, cy + 9f * k, 1f, col)
            "rocket" -> rr(c, x0, cy - 5f * k, x0 + len, cy + 5f * k, 3f, col)
            "flame" -> rr(c, x0 + len * .1f, cy - 7f * k, x0 + len * .45f, cy + 4f * k, 2f, 0xFFFF8A3D.toInt())
            "laser" -> rr(c, x0 + len * .3f, cy - 5f * k, x0 + len * .75f, cy - 3f * k, 1f, 0xFF00E5FF.toInt())
            else -> rr(c, x0 + len * .1f, cy + 3f * k, x0 + len * .3f, cy + 9f * k, 2f, col)
        }
    }

    /** The scope button now sets view range: lower zoom = more of the map on screen. */
    private fun zoomLabel(z: Float): String = String.format("%.1f", 4f / z) + "x"

    private fun drawScopeButton(c: Canvas) {
        val z = game.zoomLevel()
        val on = abs(z - 4f) > .1f
        glass(c, bScope.left, bScope.top, bScope.right, bScope.bottom, bScope.height() / 2f, dp(1f))
        if (on) {
            sp.shader = null; sp.strokeWidth = dp(2f); sp.color = AMBER
            rect.set(bScope)
            c.drawRoundRect(rect, bScope.height() / 2f, bScope.height() / 2f, sp)
        }
        val cx = bScope.centerX()
        val cy = bScope.centerY() - dp(5f)
        sp.shader = null; sp.strokeWidth = dp(1.8f); sp.color = WHITE
        c.drawCircle(cx, cy, dp(9f), sp)
        c.drawLine(cx - dp(13f), cy, cx - dp(4f), cy, sp); c.drawLine(cx + dp(4f), cy, cx + dp(13f), cy, sp)
        c.drawLine(cx, cy - dp(13f), cx, cy - dp(4f), sp); c.drawLine(cx, cy + dp(4f), cx, cy + dp(13f), sp)
        text(c, zoomLabel(z), cx, bScope.centerY() + dp(19f), dp(11f), if (on) AMBER else WHITE, Paint.Align.CENTER, fDisplay)
    }

    /** Scope overlay when zoomed in or out; every weapon has its own reticle. */
    private fun drawScope(c: Canvas) {
        val g = game
        val pl = g.player
        if (g.mode != Mode.PLAY || g.paused || !pl.alive || !g.isMe(pl)) return
        if (abs(g.zoomCur - 4f) < .2f) return
        val w = WEAPONS.getValue(pl.wp)
        val sx = (pl.x + cos(pl.aim) * 86f - g.camX) * scale
        val sy = (pl.y - 28f + sin(pl.aim) * 86f - g.camY) * scale
        val big = min(W, H)
        when (w.reticle) {
            4 -> {
                val r = dp(64f)
                sp.shader = null; sp.strokeWidth = dp(2f); sp.color = 0xCCFFFFFF.toInt()
                c.drawCircle(sx, sy, r, sp)
                sp.strokeWidth = dp(1f); sp.color = 0x99FFFFFF.toInt()
                c.drawLine(sx - r, sy, sx - dp(10f), sy, sp); c.drawLine(sx + dp(10f), sy, sx + r, sy, sp)
                c.drawLine(sx, sy - r, sx, sy - dp(10f), sp); c.drawLine(sx, sy + dp(10f), sx, sy + r, sp)
                for (i in 1..2) {
                    val o = dp(26f) * i
                    c.drawLine(sx + o, sy - dp(4f), sx + o, sy + dp(4f), sp); c.drawLine(sx - o, sy - dp(4f), sx - o, sy + dp(4f), sp)
                    c.drawLine(sx - dp(4f), sy + o, sx + dp(4f), sy + o, sp)
                }
                circle(c, sx, sy, dp(2f), RED)
            }
            1 -> {
                sp.shader = null; sp.strokeWidth = dp(1.8f); sp.color = 0xCCFF5A5A.toInt()
                c.drawCircle(sx, sy, dp(30f), sp)
                circle(c, sx, sy, dp(3f), 0xFFFF5A5A.toInt())
                c.drawLine(sx - dp(42f), sy, sx - dp(30f), sy, sp); c.drawLine(sx + dp(30f), sy, sx + dp(42f), sy, sp)
            }
            2 -> {
                sp.shader = null; sp.strokeWidth = dp(1.6f); sp.color = 0xAAFFFFFF.toInt()
                c.drawCircle(sx, sy, dp(46f), sp)
                for (a in 0 until 4) {
                    val ax = cos(a * PI_F / 2f); val ay = sin(a * PI_F / 2f)
                    c.drawLine(sx + ax * dp(46f), sy + ay * dp(46f), sx + ax * dp(58f), sy + ay * dp(58f), sp)
                }
                circle(c, sx, sy, dp(2f), 0xFFFFFFFF.toInt())
            }
            3 -> {
                sp.shader = null; sp.strokeWidth = dp(2f); sp.color = 0xCCFFB347.toInt()
                val k = dp(34f); val l = dp(12f)
                for (ix in intArrayOf(-1, 1)) for (iy in intArrayOf(-1, 1)) {
                    c.drawLine(sx + ix * k, sy + iy * k, sx + ix * (k - l), sy + iy * k, sp)
                    c.drawLine(sx + ix * k, sy + iy * k, sx + ix * k, sy + iy * (k - l), sp)
                }
                circle(c, sx, sy, dp(2f), 0xFFFFB347.toInt())
            }
            else -> {
                sp.shader = null; sp.strokeWidth = dp(1.4f); sp.color = 0xCCFFFFFF.toInt()
                c.drawCircle(sx, sy, dp(14f), sp)
                c.drawLine(sx - dp(24f), sy, sx - dp(14f), sy, sp); c.drawLine(sx + dp(14f), sy, sx + dp(24f), sy, sp)
                circle(c, sx, sy, dp(2f), 0xFFFFFFFF.toInt())
            }
        }
    }

    private fun drawGrenadeButton(c: Canvas, pl: Ent) {
        val ready = pl.grenades > 0 && pl.alive
        roundBtn(c, bGren, false)
        val cx = bGren.centerX()
        val cy = bGren.centerY()
        val a = if (ready) 0xFF else 0x66
        circle(c, cx, cy + dp(2f), dp(10f), (a shl 24) or 0x3A4A52)
        rr(c, cx - dp(3f), cy - dp(12f), cx + dp(3f), cy - dp(6f), dp(1.5f), (a shl 24) or 0x8DA4AD)
        circle(c, cx + dp(4f), cy - dp(2f), dp(2.2f), (a shl 24) or 0xFF6F59)
        val bx = bGren.right - dp(6f)
        val by = bGren.top + dp(8f)
        circle(c, bx, by, dp(9f), if (ready) AMBER else 0x66FFB347)
        text(c, pl.grenades.toString(), bx, by + dp(4.5f), dp(12f), 0xFF04141C.toInt(), Paint.Align.CENTER, fDisplay)
    }

    private fun drawPickButton(c: Canvas, pl: Ent) {
        val pk = if (pl.alive) game.gunNear(pl) else null
        val near = pk != null
        roundBtn(c, bPick, near)
        val cx = bPick.centerX()
        val cy = bPick.centerY() - dp(5f)
        val a = if (near) 0xFF else 0x77
        val col = (a shl 24) or 0xFFFFFF
        rr(c, cx - dp(14f), cy - dp(6f), cx + dp(12f), cy + dp(1f), dp(2f), col)
        rr(c, cx - dp(14f), cy - dp(9f), cx - dp(4f), cy - dp(5f), dp(1.5f), col)
        rr(c, cx - dp(9f), cy + dp(1f), cx - dp(3f), cy + dp(10f), dp(1.5f), col)
        text(c, "PICK", cx, bPick.centerY() + dp(19f), dp(11f), if (near) AMBER else WHITE, Paint.Align.CENTER, fDisplay)
        if (pk != null) {
            text(c, WEAPONS.getValue(pk.t).n.uppercase(), cx, bPick.top - dp(6f), dp(10f), AMBER, Paint.Align.CENTER, fDisplay, .06f)
        }
    }

    private fun drawScoreboard(c: Canvas, top: Float) {
        val g = game
        if (g.rules == Rules.TEAM) {
            val mine = g.player.team
            val a = g.teamKills(mine)
            val b = g.teamKills(1 - mine)
            val w = dp(150f)
            val x0 = (W - w) / 2f
            glass(c, x0, top, x0 + w, top + dp(32f), dp(16f), dp(1f))
            circle(c, x0 + dp(16f), top + dp(16f), dp(5f), TEAL)
            text(c, a.toString(), x0 + dp(28f), top + dp(22f), dp(17f), WHITE, Paint.Align.LEFT, fDisplay)
            text(c, "VS", W / 2f, top + dp(20f), dp(10f), MUTED, Paint.Align.CENTER, body, .2f)
            circle(c, x0 + w - dp(16f), top + dp(16f), dp(5f), RED)
            text(c, b.toString(), x0 + w - dp(28f), top + dp(22f), dp(17f), WHITE, Paint.Align.RIGHT, fDisplay)
            text(c, "FIRST TO $GOAL_TEAM", W / 2f, top + dp(46f), dp(10f), 0xB3A3C8D1.toInt(), Paint.Align.CENTER, body, .2f)
        } else {
            val list = g.ents.sortedByDescending { it.kills }
            val cw = dp(46f)
            val gap = dp(4f)
            val total = cw * list.size + gap * (list.size - 1) + dp(12f)
            val sx = (W - total) / 2f
            glass(c, sx, top, sx + total, top + dp(30f), dp(15f), dp(1f))
            for ((i, e) in list.withIndex()) {
                val x0 = sx + dp(6f) + i * (cw + gap)
                if (g.isMe(e)) rr(c, x0, top + dp(4f), x0 + cw, top + dp(26f), dp(11f), 0x4D19E3C8)
                circle(c, x0 + dp(10f), top + dp(15f), dp(5f), e.skin.c)
                text(c, e.kills.toString(), x0 + dp(22f), top + dp(20f), dp(14f), WHITE, Paint.Align.LEFT, body)
            }
            text(c, "FIRST TO $GOAL_SOLO", W / 2f, top + dp(44f), dp(10f), 0xB3A3C8D1.toInt(), Paint.Align.CENTER, body, .2f)
        }
    }

    private fun drawHud(c: Canvas) {
        val g = game
        val pl = g.player
        val ml = dp(12f) + insetL
        val mr = dp(12f) + insetR
        val top = dp(10f)

        // stats
        val pw = dp(188f)
        val ph = dp(60f)
        glass(c, ml, top, ml + pw, top + ph, dp(16f), dp(1f))
        val ix = ml + dp(12f)
        val iw = pw - dp(24f)
        statRow(c, "HP", ix, top + dp(20f), iw,
            pl.hp / 100f, if (pl.hp > 50f) 0xFF5EE06B.toInt() else if (pl.hp > 25f) 0xFFFFD23F.toInt() else RED,
            max(0, Math.ceil(pl.hp.toDouble()).toInt()).toString())
        statRow(c, "FUEL", ix, top + dp(42f), iw, pl.fuel / 100f,
            if (pl.empty) 0xFFFF8A3D.toInt() else TEAL, Math.round(pl.fuel).toString())

        // tools
        glass(c, pauseR.left, pauseR.top, pauseR.right, pauseR.bottom, dp(14f), dp(1f))
        glass(c, muteR.left, muteR.top, muteR.right, muteR.bottom, dp(14f), dp(1f))
        val pcx = pauseR.centerX()
        val pcy = pauseR.centerY()
        line(c, pcx - dp(4f), pcy - dp(7f), pcx - dp(4f), pcy + dp(7f), dp(3f), WHITE)
        line(c, pcx + dp(4f), pcy - dp(7f), pcx + dp(4f), pcy + dp(7f), dp(3f), WHITE)
        val mcx = muteR.centerX()
        val mcy = muteR.centerY()
        path.reset()
        path.moveTo(mcx - dp(8f), mcy - dp(3f)); path.lineTo(mcx - dp(4f), mcy - dp(3f)); path.lineTo(mcx + dp(1f), mcy - dp(7f))
        path.lineTo(mcx + dp(1f), mcy + dp(7f)); path.lineTo(mcx - dp(4f), mcy + dp(3f)); path.lineTo(mcx - dp(8f), mcy + dp(3f)); path.close()
        p.shader = null; p.style = Paint.Style.FILL; p.color = WHITE
        c.drawPath(path, p)
        if (sfx.muted) {
            line(c, mcx + dp(4f), mcy - dp(4f), mcx + dp(10f), mcy + dp(4f), dp(2f), WHITE)
            line(c, mcx + dp(10f), mcy - dp(4f), mcx + dp(4f), mcy + dp(4f), dp(2f), WHITE)
        } else {
            sp.shader = null; sp.strokeWidth = dp(2f); sp.color = WHITE
            rect.set(mcx - dp(2f), mcy - dp(5f), mcx + dp(8f), mcy + dp(5f))
            c.drawArc(rect, -50f, 100f, false, sp)
        }

        drawScoreboard(c, top)

        // kill feed
        var fy = pauseR.bottom + dp(10f)
        for (f in g.feed) {
            val a = min(1f, f.t / .5f)
            val alpha = (a * 255f).toInt()
            tp.typeface = body; tp.textSize = dp(13f); tp.letterSpacing = 0f
            val t2 = " took out "
            val wA = tp.measureText(f.a)
            val wM = tp.measureText(t2)
            val wB = tp.measureText(f.b)
            val tw = wA + wM + wB + dp(20f)
            val x0 = W - mr - tw
            rr(c, x0, fy, x0 + tw, fy + dp(24f), dp(12f), ((alpha * .45f).toInt() shl 24) or 0x062030)
            var tx = x0 + dp(10f)
            text(c, f.a, tx, fy + dp(16f), dp(13f), (alpha shl 24) or (f.ac and 0x00FFFFFF), Paint.Align.LEFT, body)
            tx += wA
            text(c, t2, tx, fy + dp(16f), dp(13f), ((alpha * .7f).toInt() shl 24) or 0xEAF8FB, Paint.Align.LEFT, body)
            tx += wM
            text(c, f.b, tx, fy + dp(16f), dp(13f), (alpha shl 24) or (f.bc and 0x00FFFFFF), Paint.Align.LEFT, body)
            fy += dp(28f)
        }

        // respawn
        if (!pl.alive && g.mode == Mode.PLAY) {
            val bw = dp(180f)
            val bh = dp(64f)
            glass(c, (W - bw) / 2f, H * .3f, (W + bw) / 2f, H * .3f + bh, dp(18f), dp(1f))
            text(c, "RESPAWNING", W / 2f, H * .3f + dp(26f), dp(16f), WHITE, Paint.Align.CENTER, fDisplay, .04f)
            text(c, max(1, Math.ceil(pl.respawn.toDouble()).toInt()).toString(), W / 2f, H * .3f + dp(52f), dp(22f), AMBER, Paint.Align.CENTER, fDisplay)
        }

        drawControls(c, pl)
    }

    private fun drawControls(c: Canvas, pl: Ent) {
        drawStick(c, lcx, lcy, lkx, lky, lId != -1, "MOVE / FLY", false, ctl.getValue("move").s)
        drawStick(c, rcx, rcy, rkx, rky, rId != -1, "AIM / FIRE", true, ctl.getValue("aim").s)
        val g = ctl.getValue("gren")
        c.save(); c.translate(grenCx, grenCy); c.scale(g.s, g.s); c.translate(-grenCx, -grenCy)
        drawGrenadeButton(c, pl)
        c.restore()
        val sc = ctl.getValue("scope")
        c.save(); c.translate(scopeCx, scopeCy); c.scale(sc.s, sc.s); c.translate(-scopeCx, -scopeCy)
        drawScopeButton(c)
        c.restore()
        val pkc = ctl.getValue("pick")
        c.save(); c.translate(pickCx, pickCy); c.scale(pkc.s, pkc.s); c.translate(-pickCx, -pickCy)
        drawPickButton(c, pl)
        c.restore()
        val w = ctl.getValue("weap")
        c.save(); c.translate(barCx, barCy); c.scale(w.s, w.s); c.translate(-barCx, -barCy)
        drawWeaponBar(c, pl)
        c.restore()
    }

    /* ---------- control editor ---------- */
    private fun ctlBounds(k: String, out: RectF) {
        when (k) {
            "move" -> out.set(lcx - lR, lcy - lR, lcx + lR, lcy + lR)
            "aim" -> out.set(rcx - rR, rcy - rR, rcx + rR, rcy + rR)
            "gren" -> out.set(grenR)
            "scope" -> out.set(scopeR)
            "pick" -> out.set(pickR)
            else -> out.set(slotR[0].left, min(slotR[0].top, reloadR.top), reloadR.right, max(slotR[1].bottom, reloadR.bottom))
        }
    }

    private fun drawEdit(c: Canvas) {
        c.drawRect(0f, 0f, W, H, p.apply { shader = null; style = Paint.Style.FILL; color = 0x88041420.toInt() })
        drawControls(c, game.player)
        for (k in CTL_KEYS) {
            ctlBounds(k, tmpR)
            tmpR.inset(-dp(6f), -dp(6f))
            sp.shader = null
            sp.strokeWidth = if (k == editSel) dp(2.4f) else dp(1.2f)
            sp.color = if (k == editSel) AMBER else 0x66FFFFFF
            c.drawRoundRect(tmpR, dp(16f), dp(16f), sp)
        }
        val pw = min(dp(470f), W - dp(24f))
        val pl = (W - pw) / 2f
        val top = dp(10f)
        glass(c, pl, top, pl + pw, top + dp(54f), dp(18f), dp(1f))
        text(c, CTL_NAMES[editSel] ?: "", pl + dp(14f), top + dp(23f), dp(12f), AMBER, Paint.Align.LEFT, body, .08f)
        text(c, "drag it to move it", pl + dp(14f), top + dp(40f), dp(10f), MUTED, Paint.Align.LEFT, body)
        val sx0 = pl + dp(150f)
        val sx1 = pl + dp(290f)
        val sy = top + dp(33f)
        sliderR.set(sx0, sy - dp(3f), sx1, sy + dp(3f))
        text(c, "SIZE", sx0, top + dp(20f), dp(10f), MUTED, Paint.Align.LEFT, body, .15f)
        rr(c, sx0, sy - dp(3f), sx1, sy + dp(3f), dp(3f), 0x55FFFFFF)
        val f = (ctl.getValue(editSel).s - CTL_MIN) / (CTL_MAX - CTL_MIN)
        rr(c, sx0, sy - dp(3f), sx0 + (sx1 - sx0) * f, sy + dp(3f), dp(3f), AMBER)
        circle(c, sx0 + (sx1 - sx0) * f, sy, dp(9f), WHITE)
        editResetR.set(pl + dp(306f), top + dp(12f), pl + dp(376f), top + dp(42f))
        editDoneR.set(pl + dp(384f), top + dp(12f), pl + dp(456f), top + dp(42f))
        rr(c, editResetR.left, editResetR.top, editResetR.right, editResetR.bottom, dp(15f), 0x33FFFFFF)
        text(c, "RESET", editResetR.centerX(), editResetR.centerY() + dp(4.5f), dp(12f), WHITE, Paint.Align.CENTER, body, .1f)
        rr(c, editDoneR.left, editDoneR.top, editDoneR.right, editDoneR.bottom, dp(15f), AMBER)
        text(c, "DONE", editDoneR.centerX(), editDoneR.centerY() + dp(4.5f), dp(12f), 0xFF04141C.toInt(), Paint.Align.CENTER, fDisplay, .1f)
    }

    private fun setSize(x: Float) {
        val f = ((x - sliderR.left) / max(1f, sliderR.width())).coerceIn(0f, 1f)
        ctl.getValue(editSel).s = CTL_MIN + f * (CTL_MAX - CTL_MIN)
        layoutHud()
    }

    private fun editDown(x: Float, y: Float) {
        if (editDoneR.contains(x, y)) { finishEdit(); return }
        if (editResetR.contains(x, y)) { resetCtl(); return }
        if (x >= sliderR.left - dp(14f) && x <= sliderR.right + dp(14f) && abs(y - sliderR.centerY()) <= dp(18f)) {
            sliderDrag = true; setSize(x); return
        }
        for (k in listOf("weap", "pick", "gren", "scope", "aim", "move")) {
            ctlBounds(k, tmpR)
            if (tmpR.contains(x, y)) {
                editSel = k
                dragging = true
                val cc = ctl.getValue(k)
                dragDx = cc.fx * W - x
                dragDy = cc.fy * H - y
                return
            }
        }
    }

    private fun editMove(x: Float, y: Float) {
        if (sliderDrag) { setSize(x); return }
        if (dragging) {
            val cc = ctl.getValue(editSel)
            cc.fx = ((x + dragDx) / W).coerceIn(.04f, .96f)
            cc.fy = ((y + dragDy) / H).coerceIn(.06f, .96f)
            layoutHud()
        }
    }

    private fun askName() {
        val act = context as? Activity ?: return
        act.runOnUiThread {
            val et = EditText(act)
            et.setText(game.myName)
            et.setSingleLine()
            et.filters = arrayOf(InputFilter.LengthFilter(12))
            et.setSelection(et.text.length)
            AlertDialog.Builder(act)
                .setTitle("Your name")
                .setView(et)
                .setPositiveButton("Save") { _, _ ->
                    val n = et.text.toString().trim().ifEmpty { "Player" }
                    prefs.edit().putString("name", n).apply()
                    actions.add(Runnable { game.myName = n })
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    /* ---------- overlays ---------- */
    private fun btnShape(c: Canvas, b: Btn, primary: Boolean, label: String, selected: Boolean) {
        if (primary) {
            playPaint.style = Paint.Style.FILL
            rect.set(b.l, b.t, b.r, b.b)
            c.drawRoundRect(rect, 16f, 16f, playPaint)
            text(c, label, (b.l + b.r) / 2f, (b.t + b.b) / 2f + 7f, 21f, 0xFF04141C.toInt(), Paint.Align.CENTER, fDisplay, .06f)
        } else if (selected) {
            selPaint.style = Paint.Style.FILL
            rect.set(b.l, b.t, b.r, b.b)
            c.drawRoundRect(rect, 12f, 12f, selPaint)
            text(c, label, (b.l + b.r) / 2f, (b.t + b.b) / 2f + 5f, 15f, 0xFF04141C.toInt(), Paint.Align.CENTER, body, .12f)
        } else {
            rr(c, b.l, b.t, b.r, b.b, 12f, 0x40000000)
            text(c, label, (b.l + b.r) / 2f, (b.t + b.b) / 2f + 5f, 15f, MUTED, Paint.Align.CENTER, body, .12f)
        }
    }

    private fun ghostBtn(c: Canvas, b: Btn, label: String) {
        rr(c, b.l, b.t, b.r, b.b, 14f, 0x24FFFFFF)
        sp.shader = null; sp.strokeWidth = 1.2f; sp.color = 0x59FFFFFF
        rect.set(b.l, b.t, b.r, b.b)
        c.drawRoundRect(rect, 14f, 14f, sp)
        text(c, label, (b.l + b.r) / 2f, (b.t + b.b) / 2f + 6f, 17f, WHITE, Paint.Align.CENTER, body, .12f)
    }

    private fun smallGhost(c: Canvas, b: Btn, label: String) {
        rr(c, b.l, b.t, b.r, b.b, 12f, 0x24FFFFFF)
        sp.shader = null; sp.strokeWidth = 1.2f; sp.color = 0x59FFFFFF
        rect.set(b.l, b.t, b.r, b.b)
        c.drawRoundRect(rect, 12f, 12f, sp)
        text(c, label, (b.l + b.r) / 2f, (b.t + b.b) / 2f + 5f, 13f, WHITE, Paint.Align.CENTER, body, .12f)
    }

    private fun drawLogo(c: Canvas) {
        gp.style = Paint.Style.FILL
        rect.set(36f, 22f, 100f, 86f)
        c.drawRoundRect(rect, 18f, 18f, logoPaint)
        path.reset()
        path.moveTo(61f, 66f); path.lineTo(75f, 66f); path.lineTo(68f, 84f); path.close()
        p.shader = null; p.style = Paint.Style.FILL; p.color = 0xE6FFFFFF.toInt()
        c.drawPath(path, p)
        circle(c, 68f, 48f, 17f, 0xFFFFFFFF.toInt())
        oval(c, 72f, 48f, 11f, 7.5f, 0xFF0A2A38.toInt())
        oval(c, 70f, 45f, 5f, 2.2f, 0xD98FF3FF.toInt())
    }

    private fun rulesText(r: Rules): String = if (r == Rules.TEAM) "TEAM VS BOTS" else "SOLO"

    private fun rulesRow(c: Canvas, tb: Btn, sb: Btn) {
        btnShape(c, tb, false, "TEAM VS BOTS", game.menuRules == Rules.TEAM)
        btnShape(c, sb, false, "SOLO", game.menuRules == Rules.SOLO)
    }

    private fun diffRow(c: Canvas, list: List<Btn>) {
        for (b in list) if (b.id == "easy" || b.id == "normal" || b.id == "hard") btnShape(c, b, false, b.id.uppercase(), game.diff == b.id)
    }

    private fun byId(list: List<Btn>, id: String): Btn = list.first { it.id == id }

    private fun dots(): String = ".".repeat(1 + (game.t * 2f).toInt() % 3)

    private fun drawOverlay(c: Canvas) {
        c.drawRect(0f, 0f, W, H, p.apply { shader = null; style = Paint.Style.FILL; color = 0x66041420 })
        c.save()
        c.translate(uiOx, uiOy)
        c.scale(uiK, uiK)
        glass(c, 0f, 0f, 480f, 300f, 28f, 1.4f)
        val g = game
        if (g.mode == Mode.PLAY && g.paused) {
            text(c, "PAUSED", 240f, 70f, 34f, WHITE, Paint.Align.CENTER, fDisplay, .04f)
            if (g.online) text(c, "The match keeps running for everyone else.", 240f, 92f, 12f, MUTED, Paint.Align.CENTER, body)
            btnShape(c, pauseBtns[0], true, "RESUME", false)
            ghostBtn(c, pauseBtns[1], if (g.online) "LEAVE MATCH" else "QUIT TO MENU")
        } else when (g.mode) {
            Mode.MENU -> {
                c.save(); c.translate(20f, 12f); c.scale(.6f, .6f); c.translate(-36f, -22f); drawLogo(c); c.restore()
                tp.typeface = fDisplay; tp.textSize = 28f; tp.textAlign = Paint.Align.LEFT; tp.letterSpacing = .02f
                tp.style = Paint.Style.FILL
                tp.shader = titlePaint.shader
                c.drawText("JETSTRIKE", 68f, 42f, tp)
                tp.shader = null
                text(c, "Jetpack deathmatch", 68f, 58f, 11f, MUTED, Paint.Align.LEFT, body, .06f)
                // character preview
                rr(c, 20f, 72f, 188f, 228f, 16f, 0x33000000)
                sp.shader = null; sp.strokeWidth = 1.2f; sp.color = 0x44FFFFFF
                rect.set(20f, 72f, 188f, 228f)
                c.drawRoundRect(rect, 16f, 16f, sp)
                previewEnt.skin = SKINS[g.mySkin]
                previewEnt.aim = sin(g.t * .9f) * .3f
                previewEnt.alive = true; previewEnt.onG = true; previewEnt.x = 0f; previewEnt.y = 0f
                c.save(); c.translate(108f, 204f); c.scale(1.9f, 1.9f)
                drawSoldier(c, previewEnt, false)
                c.restore()
                text(c, SKINS[g.mySkin].name.uppercase(), 104f, 221f, 12f, AMBER, Paint.Align.CENTER, body, .15f)
                rr(c, 24f, 128f, 50f, 164f, 10f, 0x33000000)
                rr(c, 158f, 128f, 184f, 164f, 10f, 0x33000000)
                line(c, 41f, 138f, 33f, 146f, 3f, WHITE); line(c, 33f, 146f, 41f, 154f, 3f, WHITE)
                line(c, 167f, 138f, 175f, 146f, 3f, WHITE); line(c, 175f, 146f, 167f, 154f, 3f, WHITE)
                rr(c, 20f, 238f, 188f, 278f, 12f, 0x40000000)
                text(c, "YOUR NAME", 32f, 252f, 9f, MUTED, Paint.Align.LEFT, body, .2f)
                text(c, g.myName, 32f, 270f, 15f, WHITE, Paint.Align.LEFT, body, .04f)
                text(c, "EDIT", 176f, 270f, 11f, AMBER, Paint.Align.RIGHT, body, .12f)
                // play options
                text(c, "MODE", 204f, 76f, 10f, MUTED, Paint.Align.LEFT, body, .2f)
                rulesRow(c, byId(menuBtns, "team"), byId(menuBtns, "solo"))
                text(c, "BOT SKILL", 204f, 128f, 10f, MUTED, Paint.Align.LEFT, body, .2f)
                diffRow(c, menuBtns)
                btnShape(c, byId(menuBtns, "offline"), true, "PLAY OFFLINE", false)
                smallGhost(c, byId(menuBtns, "host"), "HOST LAN")
                smallGhost(c, byId(menuBtns, "join"), "JOIN LAN")
                smallGhost(c, byId(menuBtns, "ctl"), "CONTROLS")
                text(c, "Left stick moves and flies  |  Right stick aims and fires  |  Drag the screen to look", 240f, 291f, 9.5f, MUTED, Paint.Align.CENTER, body, .01f)
            }
            Mode.HOST -> {
                val n = (g.net?.activeClients()?.size ?: 0) + 1
                val cap = if (g.menuRules == Rules.TEAM) 3 else 4
                text(c, "HOSTING ON LAN", 36f, 52f, 26f, WHITE, Paint.Align.LEFT, fDisplay, .03f)
                smallGhost(c, backBtn, "BACK")
                if (g.t - ipAt > 2f) { ipAt = g.t; ipCache = g.net?.localIp() ?: "" }
                val ip = if (ipCache.isEmpty()) "no Wi-Fi found" else ipCache
                text(c, "Your address: $ip", 36f, 78f, 13f, MUTED, Paint.Align.LEFT, body)
                text(c, "Friends tap JOIN LAN on the same Wi-Fi or hotspot.", 36f, 96f, 12f, MUTED, Paint.Align.LEFT, body)
                text(c, "$n/$cap", 444f, 96f, 22f, AMBER, Paint.Align.RIGHT, fDisplay)
                rulesRow(c, byId(hostBtns, "team"), byId(hostBtns, "solo"))
                diffRow(c, hostBtns)
                btnShape(c, byId(hostBtns, "start"), true, if (n > 1) "START MATCH" else "START (WAITING FOR PLAYERS)", false)
                text(c, if (g.menuRules == Rules.TEAM) "Humans team up against 3 bots (bots fill the empty seats)." else "Free for all: every player and bot for themselves.",
                    240f, 284f, 11f, MUTED, Paint.Align.CENTER, body)
            }
            Mode.JOIN -> {
                text(c, "JOIN LAN GAME", 36f, 52f, 26f, WHITE, Paint.Align.LEFT, fDisplay, .03f)
                smallGhost(c, backBtn, "BACK")
                if (hostList.isEmpty()) {
                    text(c, "Looking for games on your Wi-Fi" + dots(), 240f, 150f, 16f, WHITE, Paint.Align.CENTER, body)
                    text(c, "Ask a friend to tap HOST LAN. Both phones need the same Wi-Fi or hotspot.", 240f, 176f, 11.5f, MUTED, Paint.Align.CENTER, body)
                } else {
                    for ((i, h) in hostList.take(3).withIndex()) {
                        val b = Btn("h$i", 36f, 84f + i * 56f, 444f, 130f + i * 56f)
                        rr(c, b.l, b.t, b.r, b.b, 14f, 0x2AFFFFFF)
                        sp.shader = null; sp.strokeWidth = 1.2f; sp.color = 0x66FFFFFF
                        rect.set(b.l, b.t, b.r, b.b)
                        c.drawRoundRect(rect, 14f, 14f, sp)
                        text(c, h.ip, b.l + 16f, b.t + 20f, 15f, WHITE, Paint.Align.LEFT, body)
                        text(c, rulesText(if (h.rules == 0) Rules.TEAM else Rules.SOLO), b.l + 16f, b.t + 37f, 11f, MUTED, Paint.Align.LEFT, body, .1f)
                        text(c, "${h.players}/${h.max}   JOIN", b.r - 16f, b.t + 30f, 16f, AMBER, Paint.Align.RIGHT, fDisplay)
                    }
                }
            }
            Mode.WAIT -> {
                text(c, "CONNECTED", 36f, 52f, 26f, WHITE, Paint.Align.LEFT, fDisplay, .03f)
                smallGhost(c, backBtn, "LEAVE")
                text(c, "Players in lobby: ${g.lobbyHumans}", 240f, 140f, 20f, AMBER, Paint.Align.CENTER, fDisplay)
                text(c, "Mode: " + rulesText(g.menuRules), 240f, 166f, 14f, MUTED, Paint.Align.CENTER, body, .1f)
                text(c, "Waiting for the host to start" + dots(), 240f, 204f, 16f, WHITE, Paint.Align.CENTER, body)
            }
            Mode.OVER -> {
                val won = if (g.rules == Rules.TEAM) g.winTeam == g.player.team else g.winId == g.localId
                val title = if (g.rules == Rules.TEAM) (if (won) "YOUR TEAM WINS" else "BOTS WIN")
                else (if (won) "VICTORY" else g.nameOf(g.winId).uppercase() + " WINS")
                text(c, title, 240f, 66f, 32f, if (won) 0xFFFFD23F.toInt() else WHITE, Paint.Align.CENTER, fDisplay, .04f)
                val goal = if (g.rules == Rules.TEAM) GOAL_TEAM else GOAL_SOLO
                text(c, if (g.rules == Rules.TEAM) "First team to $goal kills." else "First to $goal kills.", 240f, 92f, 14f, MUTED, Paint.Align.CENTER, body)
                val m = (g.matchTime / 60f).toInt()
                val s = (g.matchTime % 60f).toInt()
                val vals = arrayOf(g.player.kills.toString(), g.player.deaths.toString(), "$m:${if (s < 10) "0" else ""}$s")
                val labs = arrayOf("KILLS", "DEATHS", "TIME")
                for (i in 0 until 3) {
                    val x0 = 36f + i * 138f
                    rr(c, x0, 116f, x0 + 132f, 188f, 14f, 0x40000000)
                    text(c, vals[i], x0 + 66f, 158f, 28f, WHITE, Paint.Align.CENTER, fDisplay)
                    text(c, labs[i], x0 + 66f, 178f, 11f, MUTED, Paint.Align.CENTER, body, .18f)
                }
                if (g.role == Role.CLIENT) {
                    ghostBtn(c, overClientBtns[0], "LEAVE (HOST CAN START A REMATCH)")
                } else {
                    btnShape(c, overBtns[0], true, "PLAY AGAIN", false)
                    ghostBtn(c, overBtns[1], "MENU")
                }
            }
            else -> {}
        }
        c.restore()
    }

    private fun drawNotice(c: Canvas) {
        val g = game
        if (g.noticeT <= 0f || g.notice.isEmpty()) return
        val a = min(1f, g.noticeT / .5f)
        tp.typeface = body; tp.textSize = dp(13f); tp.letterSpacing = 0f
        val tw = tp.measureText(g.notice) + dp(28f)
        val x0 = (W - tw) / 2f
        val y0 = H - dp(86f)
        rr(c, x0, y0, x0 + tw, y0 + dp(30f), dp(15f), ((a * 190f).toInt() shl 24) or 0x062030)
        text(c, g.notice, W / 2f, y0 + dp(20f), dp(13f), ((a * 255f).toInt() shl 24) or 0xEAF8FB, Paint.Align.CENTER, body)
    }

    /* ---------- frame ---------- */
    private fun render(c: Canvas) {
        hostList = if (game.mode == Mode.JOIN) (game.net?.liveHosts() ?: emptyList()) else emptyList()
        c.drawColor(0xFF04141C.toInt())
        drawBackground(c)
        c.save()
        c.scale(scale, scale)
        val sh = game.shake
        val ox = if (sh > .1f) rnd(-sh, sh) else 0f
        val oy = if (sh > .1f) rnd(-sh, sh) else 0f
        c.translate(-game.camX + ox, -game.camY + oy)
        drawPlatforms(c)
        drawPicks(c)
        drawGrenades(c)
        for (e in game.ents) if (e.alive) drawSoldier(c, e)
        drawBullets(c)
        drawParts(c)
        drawDamageTexts(c)
        if (game.mode == Mode.PLAY && !game.paused && abs(game.zoomCur - 4f) < .2f) drawAim(c)
        c.restore()
        drawScope(c)
        if (game.mode == Mode.PLAY || game.mode == Mode.OVER) {
            if (game.vigT > 0f) {
                vigPaint.alpha = (min(1f, game.vigT) * 255f).toInt()
                c.drawRect(0f, 0f, W, H, vigPaint)
            }
            drawHud(c)
        }
        if (editing) drawEdit(c) else if (game.mode != Mode.PLAY || game.paused) drawOverlay(c)
        drawNotice(c)
    }

    /* ---------- touch ---------- */
    private fun currentBtns(): List<Btn> {
        val g = game
        if (g.mode == Mode.PLAY) return if (g.paused) pauseBtns else emptyList()
        return when (g.mode) {
            Mode.MENU -> menuBtns
            Mode.HOST -> hostBtns
            Mode.JOIN -> {
                val l = ArrayList<Btn>()
                l.add(backBtn)
                for (i in 0 until min(3, hostList.size)) l.add(Btn("h$i", 36f, 84f + i * 56f, 444f, 130f + i * 56f))
                l
            }
            Mode.WAIT -> waitBtns
            Mode.OVER -> if (g.role == Role.CLIENT) overClientBtns else overBtns
            else -> emptyList()
        }
    }

    private fun hitBtn(x: Float, y: Float): Btn? {
        val ux = (x - uiOx) / uiK
        val uy = (y - uiOy) / uiK
        for (b in currentBtns()) if (ux >= b.l && ux <= b.r && uy >= b.t && uy <= b.b) return b
        return null
    }

    private fun dispatch(id: String) {
        actions.add(Runnable {
            val g = game
            when (id) {
                "team", "solo" -> {
                    val r = if (id == "team") Rules.TEAM else Rules.SOLO
                    val n = g.net
                    if (g.mode == Mode.HOST && n != null) {
                        if (r == Rules.TEAM && n.activeClients().size + 1 > 3) {
                            g.note("Team mode fits 3 players. Someone has to leave first.")
                        } else {
                            g.menuRules = r
                            n.hostRules = r.ordinal
                            n.broadcastLobby()
                        }
                    } else {
                        g.menuRules = r
                    }
                    prefs.edit().putInt("rules", g.menuRules.ordinal).apply()
                }
                "easy", "normal", "hard" -> {
                    g.diff = id
                    prefs.edit().putString("diff", id).apply()
                    val n = g.net
                    if (g.mode == Mode.HOST && n != null) {
                        n.hostDiff = g.diffIndex()
                        n.broadcastLobby()
                    }
                }
                "offline" -> g.startOffline()
                "prev", "next" -> {
                    val n = SKINS.size
                    g.mySkin = (g.mySkin + (if (id == "next") 1 else n - 1)) % n
                    prefs.edit().putInt("skin", g.mySkin).apply()
                }
                "name" -> askName()
                "ctl" -> { editing = true; editSel = "move" }
                "host" -> g.hostOpen()
                "join" -> g.joinOpen()
                "start" -> g.hostBegin()
                "back" -> g.showMenu()
                "again" -> g.restartMatch()
                "resume" -> g.resume()
                "quit", "menu" -> g.showMenu()
                else -> {
                    if (id.length == 2 && id[0] == 'h') {
                        val i = id[1] - '0'
                        val h = hostList.getOrNull(i)
                        if (h != null) g.joinConnect(h.ip)
                    }
                }
            }
        })
    }

    private fun inStick(x: Float, y: Float, cx: Float, cy: Float, r: Float): Boolean = hypot(x - cx, y - cy) <= r * 1.5f

    private fun onDown(id: Int, x: Float, y: Float) {
        val g = game
        if (editing) { if (editPtr == -1) { editPtr = id; editDown(x, y) }; return }
        if (g.mode == Mode.PLAY && !g.paused) {
            // on-screen buttons first
            if (pauseR.contains(x, y)) { btnPtr = id; btnKind = "pause"; return }
            if (muteR.contains(x, y)) { btnPtr = id; btnKind = "mute"; return }
            if (grenR.contains(x, y)) { btnPtr = id; btnKind = "gren"; return }
            if (reloadR.contains(x, y)) { btnPtr = id; btnKind = "reload"; return }
            if (scopeR.contains(x, y)) { btnPtr = id; btnKind = "scope"; return }
            if (pickR.contains(x, y)) { btnPtr = id; btnKind = "pick"; return }
            if (slotR[0].contains(x, y)) { btnPtr = id; btnKind = "slot0"; return }
            if (slotR[1].contains(x, y)) { btnPtr = id; btnKind = "slot1"; return }
            // the sticks only respond on their own pads
            if (lId == -1 && inStick(x, y, lcx, lcy, lR)) { lId = id; moveStick(true, x, y); g.lOn = true; return }
            if (rId == -1 && inStick(x, y, rcx, rcy, rR)) { rId = id; moveStick(false, x, y); g.rOn = true; return }
            // anywhere else: drag to look around
            if (panId == -1) { panId = id; panLx = x; panLy = y; g.panning = true }
        } else if (uiPtr == -1) {
            uiPtr = id
            pressedId = hitBtn(x, y)?.id
        }
    }

    private fun moveStick(left: Boolean, x: Float, y: Float) {
        val cx = if (left) lcx else rcx
        val cy = if (left) lcy else rcy
        val sr = if (left) lR else rR
        var dx = (x - cx) / sr
        var dy = (y - cy) / sr
        val m = hypot(dx, dy)
        if (m > 1f) { dx /= m; dy /= m }
        if (left) { lkx = dx; lky = dy; game.lx = dx; game.ly = dy } else { rkx = dx; rky = dy; game.rx = dx; game.ry = dy }
    }

    private fun onMove(id: Int, x: Float, y: Float) {
        if (editing) { if (id == editPtr) editMove(x, y); return }
        if (id == lId) moveStick(true, x, y)
        else if (id == rId) moveStick(false, x, y)
        else if (id == panId) {
            val g = game
            g.panX = (g.panX - (x - panLx) / scale).coerceIn(-420f, 420f)
            g.panY = (g.panY - (y - panLy) / scale).coerceIn(-260f, 260f)
            panLx = x; panLy = y
        }
    }

    private fun onUp(id: Int, x: Float, y: Float) {
        val g = game
        if (editing) { if (id == editPtr) { editPtr = -1; dragging = false; sliderDrag = false }; return }
        if (id == lId) {
            lId = -1; lkx = 0f; lky = 0f; g.lOn = false; g.lx = 0f; g.ly = 0f
        } else if (id == rId) {
            rId = -1; rkx = 0f; rky = 0f; g.rOn = false; g.rx = 0f; g.ry = 0f
        } else if (id == panId) {
            panId = -1; g.panning = false
        } else if (id == btnPtr) {
            val kind = btnKind
            btnPtr = -1; btnKind = ""
            val hit = when (kind) {
                "pause" -> pauseR.contains(x, y)
                "mute" -> muteR.contains(x, y)
                "gren" -> grenR.contains(x, y)
                "reload" -> reloadR.contains(x, y)
                "scope" -> scopeR.contains(x, y)
                "pick" -> pickR.contains(x, y)
                "slot0" -> slotR[0].contains(x, y)
                "slot1" -> slotR[1].contains(x, y)
                else -> false
            }
            if (hit) when (kind) {
                "pause" -> actions.add(Runnable { g.pause() })
                "mute" -> sfx.muted = !sfx.muted
                "gren" -> actions.add(Runnable { g.reqG++ })
                "reload" -> actions.add(Runnable { g.reqR++ })
                "scope" -> actions.add(Runnable { g.scopeStep() })
                "pick" -> actions.add(Runnable { g.reqP++ })
                "slot0" -> actions.add(Runnable { g.reqSV = 0; g.reqS++ })
                "slot1" -> actions.add(Runnable { g.reqSV = 1; g.reqS++ })
            }
        } else if (id == uiPtr) {
            val b = hitBtn(x, y)
            val pid = pressedId
            if (b != null && b.id == pid) dispatch(b.id)
            pressedId = null
            uiPtr = -1
        }
    }

    private fun cancelTouches() {
        val g = game
        lId = -1; rId = -1; panId = -1; uiPtr = -1; btnPtr = -1; btnKind = ""; pressedId = null
        editPtr = -1; dragging = false; sliderDrag = false
        lkx = 0f; lky = 0f; rkx = 0f; rky = 0f
        g.lOn = false; g.rOn = false; g.lx = 0f; g.ly = 0f; g.rx = 0f; g.ry = 0f; g.panning = false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                onDown(ev.getPointerId(i), ev.getX(i), ev.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) onMove(ev.getPointerId(i), ev.getX(i), ev.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = ev.actionIndex
                onUp(ev.getPointerId(i), ev.getX(i), ev.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> cancelTouches()
        }
        return true
    }
}
