package com.xcluice.jetstrike

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
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
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
    private var lcx = 0f
    private var lcy = 0f
    private var rcx = 0f
    private var rcy = 0f
    private var stickR = 0f

    private var hostList: List<HostInfo> = emptyList()
    private var ipCache = ""
    private var ipAt = -10f

    // overlay layout in a 480x300 virtual card
    private var uiK = 1f
    private var uiOx = 0f
    private var uiOy = 0f
    private val backBtn = Btn("back", 372f, 18f, 448f, 52f)
    private val menuBtns = listOf(
        Btn("team", 36f, 106f, 238f, 144f), Btn("solo", 242f, 106f, 444f, 144f),
        Btn("easy", 36f, 152f, 168f, 188f), Btn("normal", 174f, 152f, 306f, 188f), Btn("hard", 312f, 152f, 444f, 188f),
        Btn("offline", 36f, 200f, 214f, 252f), Btn("host", 220f, 200f, 330f, 252f), Btn("join", 336f, 200f, 444f, 252f)
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
        titlePaint.shader = LinearGradient(116f, 0f, 400f, 0f,
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
        scale = min(W / 700f, H / 430f)
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
        sp.shader = null; sp.strokeWidth = 3.2f; sp.color = OUTLINE
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, rad, rad, sp)
        rr(c, l, t, r, b, rad, col)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = col
        c.drawCircle(x, y, r, p)
    }

    private fun circo(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        sp.shader = null; sp.strokeWidth = 3.2f; sp.color = OUTLINE
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
                val s = when (pk.t) { "shotgun" -> "SG"; "sniper" -> "SN"; else -> "AR" }
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
        sp.shader = null; sp.color = OUTLINE; sp.strokeWidth = 8.6f
        c.drawPath(path, sp)
        sp.color = col; sp.strokeWidth = 6f
        c.drawPath(path, sp)
        rro(c, fx - 4f, fy - 3f, fx + 6f, fy + 2f, 2.5f, 0xFF1B2C34.toInt())
    }

    private fun drawGun(c: Canvas, e: Ent) {
        val w = WEAPONS.getValue(e.wp)
        rro(c, 5f, -3f, 5f + w.len, 3f, 2f, 0xFF2B3A42.toInt())
        rr(c, 5f + w.len * .35f, -3f, 5f + w.len * .75f, -1f, 0f, 0xFF8DA4AD.toInt())
        when (e.wp) {
            "sniper" -> rr(c, 10f, -6f, 19f, -3f, 1f, TEAL)
            "shotgun" -> rr(c, 2f, -3f, 12f, 4f, 2f, 0xFF8A5A2B.toInt())
            "rifle" -> rr(c, 12f, 2f, 17f, 9f, 1f, 0xFF1C262B.toInt())
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

    private fun drawSoldier(c: Canvas, e: Ent) {
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
            line(c, 0f, -27f, 3f + cos(la) * 12f, -27f + sin(la) * 12f, 8f, OUTLINE)
            line(c, 0f, -27f, 3f + cos(la) * 12f, -27f + sin(la) * 12f, 5f, dark)
            leg(c, e, e.run, bodyC, false)
            // torso
            rro(c, -8f, -34f, 8f, -14f, 5f, bodyC)
            rr(c, -8f, -20f, 8f, -17f, 0f, dark)
            rr(c, -6f, -33f, 2f, -25f, 3f, (light and 0x00FFFFFF) or 0x99000000.toInt())
            // head
            circo(c, 1f, -40f, 9f, bodyC)
            circle(c, -1.5f, -43f, 5f, (light and 0x00FFFFFF) or 0x99000000.toInt())
            oval(c, 5f, -40f, 5.5f, 4f, 0xFF2B8CA3.toInt())
            oval(c, 5f, -39f, 4.5f, 2.8f, 0xFF8FE3F5.toInt())
            oval(c, 3.5f, -41.5f, 2.5f, 1.2f, 0xBFFFFFFF.toInt())
            // front arm and gun
            c.save()
            c.translate(1f, -27f)
            c.rotate(la * 180f / PI_F)
            line(c, 0f, 0f, 9f, 0f, 8f, OUTLINE)
            line(c, 0f, 0f, 9f, 0f, 5f, bodyC)
            drawGun(c, e)
            circle(c, 9f, 0f, 2.8f, light)
            c.restore()
            c.restore()
        }
        // marker: arrow for you, name and health for everyone else
        c.save()
        c.translate(e.x, e.y)
        if (game.isMe(e)) {
            val yy = -62f + sin(game.t * 4f) * 2f
            path.reset()
            path.moveTo(0f, yy); path.lineTo(-7f, yy - 10f); path.lineTo(7f, yy - 10f); path.close()
            p.shader = null; p.style = Paint.Style.FILL; p.color = TEAL
            c.drawPath(path, p)
        } else {
            val nameCol = if (team) teamCol else 0xE6FFFFFF.toInt()
            text(c, game.nameOf(e.id), 0f, -66f, 12f, nameCol, Paint.Align.CENTER, body)
            rr(c, -18f, -62f, 18f, -57f, 2.5f, 0x80000000.toInt())
            val hpCol = if (e.hp > 50f) 0xFF5EE06B.toInt() else if (e.hp > 25f) 0xFFFFD23F.toInt() else RED
            rr(c, -18f, -62f, -18f + max(0f, 36f * e.hp / 100f), -57f, 2.5f, hpCol)
        }
        c.restore()
        if (e.reload > 0f) {
            val w = WEAPONS.getValue(e.wp)
            rr(c, e.x - 14f, e.y - 56f, e.x + 14f, e.y - 53f, 1.5f, 0x33FFFFFF)
            rr(c, e.x - 14f, e.y - 56f, e.x - 14f + 28f * (1f - e.reload / w.rel), e.y - 53f, 1.5f, AMBER)
        }
    }

    private fun drawBullets(c: Canvas) {
        for (b in game.bullets) {
            val tx = b.x - b.vx * b.tr
            val ty = b.y - b.vy * b.tr
            line(c, tx, ty, b.x, b.y, if (b.big) 7f else 5.5f, 0x59FFC85A)
            line(c, tx, ty, b.x, b.y, if (b.big) 3f else 2f, 0xFFFFFBE0.toInt())
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
    private fun layoutHud() {
        val mr = dp(12f) + insetR
        val top = dp(10f)
        val bs = dp(42f)
        stickR = dp(56f)
        val edge = max(dp(84f), W * .13f)
        lcx = edge + insetL; lcy = H - dp(88f)
        rcx = W - edge - insetR; rcy = H - dp(88f)
        pauseR.set(W - mr - bs, top, W - mr, top + bs)
        muteR.set(W - mr - bs * 2f - dp(8f), top, W - mr - bs - dp(8f), top + bs)
        val gs = dp(30f)
        val gcy = rcy - dp(112f)
        grenR.set(rcx - gs, gcy - gs, rcx + gs, gcy + gs)
        // weapon bar: two slots and a reload button, bottom centre
        val sw = dp(112f)
        val sh = dp(52f)
        val gap = dp(6f)
        val rs = dp(46f)
        val total = sw * 2f + gap * 2f + rs
        val x0 = (W - total) / 2f
        val y0 = H - sh - dp(12f)
        slotR[0].set(x0, y0, x0 + sw, y0 + sh)
        slotR[1].set(x0 + sw + gap, y0, x0 + sw * 2f + gap, y0 + sh)
        reloadR.set(x0 + sw * 2f + gap * 2f, y0 + (sh - rs) / 2f, x0 + total, y0 + (sh + rs) / 2f)
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

    private fun drawStick(c: Canvas, cx: Float, cy: Float, kx: Float, ky: Float, active: Boolean, label: String, warm: Boolean) {
        val r = stickR
        circle(c, cx, cy, r, if (active) 0x40FFFFFF else 0x1CFFFFFF)
        sp.shader = null; sp.strokeWidth = dp(1.5f); sp.color = if (active) 0x99FFFFFF.toInt() else 0x55FFFFFF
        c.drawCircle(cx, cy, r, sp)
        sp.strokeWidth = dp(1f); sp.color = 0x22FFFFFF
        c.drawCircle(cx, cy, r * .5f, sp)
        val knob = if (warm) (if (active) 0xDDFFB347.toInt() else 0x66FFB347) else (if (active) 0xCCFFFFFF.toInt() else 0x66FFFFFF)
        circle(c, cx + kx * r, cy + ky * r, dp(26f), knob)
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
            val r = slotR[i]
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
        roundBtn(c, reloadR, pl.reload > 0f)
        val rcx2 = reloadR.centerX()
        val rcy2 = reloadR.centerY()
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
            else -> rr(c, x0 + len * .1f, cy + 3f * k, x0 + len * .3f, cy + 9f * k, 2f, col)
        }
    }

    private fun drawGrenadeButton(c: Canvas, pl: Ent) {
        val ready = pl.grenades > 0 && pl.alive
        roundBtn(c, grenR, false)
        val cx = grenR.centerX()
        val cy = grenR.centerY()
        val a = if (ready) 0xFF else 0x66
        circle(c, cx, cy + dp(2f), dp(10f), (a shl 24) or 0x3A4A52)
        rr(c, cx - dp(3f), cy - dp(12f), cx + dp(3f), cy - dp(6f), dp(1.5f), (a shl 24) or 0x8DA4AD)
        circle(c, cx + dp(4f), cy - dp(2f), dp(2.2f), (a shl 24) or 0xFF6F59)
        val bx = grenR.right - dp(6f)
        val by = grenR.top + dp(8f)
        circle(c, bx, by, dp(9f), if (ready) AMBER else 0x66FFB347)
        text(c, pl.grenades.toString(), bx, by + dp(4.5f), dp(12f), 0xFF04141C.toInt(), Paint.Align.CENTER, fDisplay)
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

        // controls: fixed sticks, grenade, weapon bar
        drawStick(c, lcx, lcy, lkx, lky, lId != -1, "MOVE / FLY", false)
        drawStick(c, rcx, rcy, rkx, rky, rId != -1, "AIM / FIRE", true)
        drawGrenadeButton(c, pl)
        drawWeaponBar(c, pl)
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
                drawLogo(c)
                tp.typeface = fDisplay; tp.textSize = 40f; tp.textAlign = Paint.Align.LEFT; tp.letterSpacing = .02f
                tp.style = Paint.Style.FILL
                tp.shader = titlePaint.shader
                c.drawText("JETSTRIKE", 116f, 68f, tp)
                tp.shader = null
                text(c, "Jetpack deathmatch. Play offline or on your Wi-Fi.", 116f, 92f, 14f, MUTED, Paint.Align.LEFT, body, .02f)
                rulesRow(c, byId(menuBtns, "team"), byId(menuBtns, "solo"))
                diffRow(c, menuBtns)
                btnShape(c, byId(menuBtns, "offline"), true, "PLAY OFFLINE", false)
                smallGhost(c, byId(menuBtns, "host"), "HOST LAN")
                smallGhost(c, byId(menuBtns, "join"), "JOIN LAN")
                text(c, "Left stick: move, push up to fly   |   Right stick: aim and shoot   |   Drag the screen to look around",
                    240f, 278f, 10.5f, MUTED, Paint.Align.CENTER, body, .01f)
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
        if (game.mode == Mode.PLAY && !game.paused) drawAim(c)
        c.restore()
        if (game.mode == Mode.PLAY || game.mode == Mode.OVER) {
            if (game.vigT > 0f) {
                vigPaint.alpha = (min(1f, game.vigT) * 255f).toInt()
                c.drawRect(0f, 0f, W, H, vigPaint)
            }
            drawHud(c)
        }
        if (game.mode != Mode.PLAY || game.paused) drawOverlay(c)
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

    private fun inStick(x: Float, y: Float, cx: Float, cy: Float): Boolean = hypot(x - cx, y - cy) <= stickR * 1.5f

    private fun onDown(id: Int, x: Float, y: Float) {
        val g = game
        if (g.mode == Mode.PLAY && !g.paused) {
            // on-screen buttons first
            if (pauseR.contains(x, y)) { btnPtr = id; btnKind = "pause"; return }
            if (muteR.contains(x, y)) { btnPtr = id; btnKind = "mute"; return }
            if (grenR.contains(x, y)) { btnPtr = id; btnKind = "gren"; return }
            if (reloadR.contains(x, y)) { btnPtr = id; btnKind = "reload"; return }
            if (slotR[0].contains(x, y)) { btnPtr = id; btnKind = "slot0"; return }
            if (slotR[1].contains(x, y)) { btnPtr = id; btnKind = "slot1"; return }
            // the sticks only respond on their own pads
            if (lId == -1 && inStick(x, y, lcx, lcy)) { lId = id; moveStick(true, x, y); g.lOn = true; return }
            if (rId == -1 && inStick(x, y, rcx, rcy)) { rId = id; moveStick(false, x, y); g.rOn = true; return }
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
        var dx = (x - cx) / stickR
        var dy = (y - cy) / stickR
        val m = hypot(dx, dy)
        if (m > 1f) { dx /= m; dy /= m }
        if (left) { lkx = dx; lky = dy; game.lx = dx; game.ly = dy } else { rkx = dx; rky = dy; game.rx = dx; game.ry = dy }
    }

    private fun onMove(id: Int, x: Float, y: Float) {
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
                "slot0" -> slotR[0].contains(x, y)
                "slot1" -> slotR[1].contains(x, y)
                else -> false
            }
            if (hit) when (kind) {
                "pause" -> actions.add(Runnable { g.pause() })
                "mute" -> sfx.muted = !sfx.muted
                "gren" -> actions.add(Runnable { g.reqG++ })
                "reload" -> actions.add(Runnable { g.reqR++ })
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
