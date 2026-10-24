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
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
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

    private val platBody = PLATS.map { pl ->
        Paint().apply {
            shader = LinearGradient(
                0f, pl.y, 0f, pl.y + min(pl.h, 120f),
                if (pl.wall) 0xFF3B6376.toInt() else 0xFF2F5A6E.toInt(),
                if (pl.wall) 0xFF1A3846.toInt() else 0xFF10252F.toInt(),
                Shader.TileMode.CLAMP
            )
        }
    }
    private val platLip = PLATS.map { pl ->
        Paint().apply {
            shader = LinearGradient(pl.x, 0f, pl.x + pl.w, 0f, 0xFF19E3C8.toInt(), 0xFFFFD23F.toInt(), Shader.TileMode.CLAMP)
        }
    }

    // touch state
    private var lId = -1
    private var rId = -1
    private var lox = 0f
    private var loy = 0f
    private var rox = 0f
    private var roy = 0f
    private var lkx = 0f
    private var lky = 0f
    private var rkx = 0f
    private var rky = 0f
    private var uiPtr = -1
    private var pressedId: String? = null
    private val pauseR = RectF()
    private val muteR = RectF()

    // overlay layout in a 480x300 virtual card
    private var uiK = 1f
    private var uiOx = 0f
    private var uiOy = 0f
    private val menuBtns = listOf(
        Btn("easy", 36f, 138f, 168f, 178f), Btn("normal", 174f, 138f, 306f, 178f), Btn("hard", 312f, 138f, 444f, 178f),
        Btn("play", 36f, 198f, 444f, 250f)
    )
    private val pauseBtns = listOf(
        Btn("resume", 36f, 104f, 444f, 156f), Btn("quit", 36f, 170f, 444f, 222f)
    )
    private val overBtns = listOf(
        Btn("again", 36f, 214f, 236f, 266f), Btn("menu", 244f, 214f, 444f, 266f)
    )

    init {
        game.diff = prefs.getString("diff", "normal") ?: "normal"
        if (!DIFFS.containsKey(game.diff)) game.diff = "normal"
        game.sfx = { n, v -> sfx.play(n, v) }
        titlePaint.shader = LinearGradient(116f, 0f, 400f, 0f,
            intArrayOf(0xFF19E3C8.toInt(), 0xFFFFD23F.toInt(), 0xFFFF8A3D.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        playPaint.shader = LinearGradient(36f, 0f, 444f, 0f,
            intArrayOf(0xFF19E3C8.toInt(), 0xFFFFD23F.toInt(), 0xFFFF8A3D.toInt()), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        logoPaint.shader = LinearGradient(36f, 22f, 100f, 86f,
            intArrayOf(0xFF19E3C8.toInt(), 0xFFFFD23F.toInt(), 0xFFFF7A3D.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        selPaint.shader = LinearGradient(0f, 0f, 480f, 0f,
            intArrayOf(0xFF19E3C8.toInt(), 0xFFFFB347.toInt()), null, Shader.TileMode.CLAMP)
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
        sfx.release()
    }

    fun handleBack(): Boolean {
        return when (game.mode) {
            Mode.PLAY -> { actions.add(Runnable { game.pause() }); true }
            Mode.PAUSE -> { actions.add(Runnable { game.resume() }); true }
            Mode.OVER -> { actions.add(Runnable { game.showMenu() }); true }
            Mode.MENU -> false
        }
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

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = col
        c.drawCircle(x, y, r, p)
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
        tp.shader = null
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
            c.drawRect(pl.x, pl.y, pl.x + pl.w, pl.y + pl.h, platBody[i])
            rr(c, pl.x, pl.y, pl.x + pl.w, pl.y + 2f, 0f, 0x1AFFFFFF)
            c.drawRect(pl.x, pl.y, pl.x + pl.w, pl.y + 4f, platLip[i])
            if (!pl.wall && pl.h <= 20f) {
                var x = pl.x + 16f
                while (x < pl.x + pl.w - 8f) {
                    circle(c, x, pl.y + 12f, 2f, 0x40000000)
                    x += 44f
                }
            }
        }
    }

    private fun drawPicks(c: Canvas) {
        for ((i, pk) in game.picks.withIndex()) {
            if (pk.cd > 0f) continue
            val by = pk.y + sin(game.t * 3f + i) * 3f
            val hp = pk.t == "health"
            circle(c, pk.x, by, 27f, if (hp) 0x225EE06B else 0x22FFB347)
            circle(c, pk.x, by, 20f, if (hp) 0x335EE06B else 0x33FFB347)
            rr(c, pk.x - 14f, by - 14f, pk.x + 14f, by + 14f, 8f, 0x66FFFFFF)
            sp.shader = null; sp.strokeWidth = 1.5f; sp.color = 0xCCFFFFFF.toInt()
            rect.set(pk.x - 14f, by - 14f, pk.x + 14f, by + 14f)
            c.drawRoundRect(rect, 8f, 8f, sp)
            if (hp) {
                rr(c, pk.x - 2.5f, by - 8f, pk.x + 2.5f, by + 8f, 1f, 0xFF3FC95A.toInt())
                rr(c, pk.x - 8f, by - 2.5f, pk.x + 8f, by + 2.5f, 1f, 0xFF3FC95A.toInt())
            } else {
                val s = when (pk.t) { "shotgun" -> "SG"; "sniper" -> "SN"; else -> "AR" }
                text(c, s, pk.x, by + 4f, 12f, 0xFF04141C.toInt(), Paint.Align.CENTER, body)
            }
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
        sp.shader = null; sp.color = col; sp.strokeWidth = 6f
        c.drawPath(path, sp)
        rr(c, fx - 4f, fy - 3f, fx + 6f, fy + 2f, 2.5f, 0xFF1B2C34.toInt())
    }

    private fun drawGun(c: Canvas, e: Ent) {
        val w = WEAPONS.getValue(e.wp)
        rr(c, 5f, -3f, 5f + w.len, 3f, 2f, 0xFF2B3A42.toInt())
        rr(c, 5f + w.len * .35f, -3f, 5f + w.len * .75f, -1f, 0f, 0xFF8DA4AD.toInt())
        when (e.wp) {
            "sniper" -> rr(c, 10f, -6f, 19f, -3f, 1f, 0xFF19E3C8.toInt())
            "shotgun" -> rr(c, 2f, -3f, 12f, 4f, 2f, 0xFF8A5A2B.toInt())
            "rifle" -> rr(c, 12f, 2f, 17f, 9f, 1f, 0xFF1C262B.toInt())
        }
        if (e.flash > 0f) {
            val fx = 5f + w.len + 3f
            path.reset()
            path.moveTo(fx, 0f); path.lineTo(fx + 9f, -5f); path.lineTo(fx + 6f, 0f); path.lineTo(fx + 9f, 5f); path.close()
            p.shader = null; p.style = Paint.Style.FILL; p.color = 0xFFFFF3B0.toInt()
            c.drawPath(path, p)
            circle(c, fx + 2f, 0f, 5f, 0xCCFFAA3C.toInt())
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

        if (!blink) {
            c.save()
            c.translate(e.x, e.y)
            if (e.onG) oval(c, 0f, 1f, 12f, 3f, 0x38000000)
            c.scale(dir, 1f)
            c.translate(0f, -bob)
            // jetpack
            rr(c, -17f, -32f, -7f, -11f, 3f, 0xFF3F525C.toInt())
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
            line(c, 0f, -27f, 3f + cos(la) * 12f, -27f + sin(la) * 12f, 5f, dark)
            leg(c, e, e.run, bodyC, false)
            // torso
            rr(c, -8f, -34f, 8f, -14f, 5f, bodyC)
            rr(c, -8f, -20f, 8f, -17f, 0f, dark)
            rr(c, -6f, -33f, 2f, -25f, 3f, (light and 0x00FFFFFF) or 0x99000000.toInt())
            // head
            circle(c, 1f, -40f, 9f, bodyC)
            circle(c, -1.5f, -43f, 5f, (light and 0x00FFFFFF) or 0x99000000.toInt())
            oval(c, 5f, -40f, 5.5f, 4f, 0xFF2B8CA3.toInt())
            oval(c, 5f, -39f, 4.5f, 2.8f, 0xFF8FE3F5.toInt())
            oval(c, 3.5f, -41.5f, 2.5f, 1.2f, 0xBFFFFFFF.toInt())
            // front arm and gun
            c.save()
            c.translate(1f, -27f)
            c.rotate(la * 180f / PI_F)
            line(c, 0f, 0f, 9f, 0f, 5f, bodyC)
            drawGun(c, e)
            circle(c, 9f, 0f, 2.8f, light)
            c.restore()
            c.restore()
        }
        // name tag or player marker
        c.save()
        c.translate(e.x, e.y)
        if (e === game.player && !e.bot) {
            val yy = -62f + sin(game.t * 4f) * 2f
            path.reset()
            path.moveTo(0f, yy); path.lineTo(-6f, yy - 9f); path.lineTo(6f, yy - 9f); path.close()
            p.shader = null; p.style = Paint.Style.FILL; p.color = 0xFF19E3C8.toInt()
            c.drawPath(path, p)
        } else {
            text(c, e.skin.name, 0f, -66f, 12f, 0xE6FFFFFF.toInt(), Paint.Align.CENTER, body)
            rr(c, -18f, -62f, 18f, -57f, 2.5f, 0x66000000)
            val hpCol = if (e.hp > 50f) 0xFF5EE06B.toInt() else if (e.hp > 25f) 0xFFFFD23F.toInt() else 0xFFFF4F6D.toInt()
            rr(c, -18f, -62f, -18f + max(0f, 36f * e.hp / 100f), -57f, 2.5f, hpCol)
        }
        c.restore()
        if (e.reload > 0f) {
            val w = WEAPONS.getValue(e.wp)
            rr(c, e.x - 14f, e.y - 56f, e.x + 14f, e.y - 53f, 1.5f, 0x33FFFFFF)
            rr(c, e.x - 14f, e.y - 56f, e.x - 14f + 28f * (1f - e.reload / w.rel), e.y - 53f, 1.5f, 0xFFFFB347.toInt())
        }
    }

    private fun drawBullets(c: Canvas) {
        for (b in game.bullets) {
            val tx = b.x - b.vx * b.tr
            val ty = b.y - b.vy * b.tr
            line(c, tx, ty, b.x, b.y, if (b.big) 6f else 5f, 0x59FFC85A)
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

    private fun drawAim(c: Canvas) {
        val pl = game.player
        if (!pl.alive || pl.bot) return
        val sx = pl.x + cos(pl.aim) * 86f
        val sy = pl.y - 28f + sin(pl.aim) * 86f
        sp.shader = null; sp.strokeWidth = 1.6f; sp.color = 0xBFFFFFFF.toInt()
        c.drawCircle(sx, sy, 6f, sp)
        c.drawLine(sx - 10f, sy, sx - 3f, sy, sp); c.drawLine(sx + 3f, sy, sx + 10f, sy, sp)
        c.drawLine(sx, sy - 10f, sx, sy - 3f, sp); c.drawLine(sx, sy + 3f, sx, sy + 10f, sp)
    }

    /* ---------- HUD ---------- */
    private fun statRow(c: Canvas, label: String, x: Float, y: Float, w: Float, frac: Float, col: Int, num: String) {
        text(c, label, x, y + dp(4f), dp(11f), 0xFFA3C8D1.toInt(), Paint.Align.LEFT, body, .08f)
        val bx = x + dp(34f)
        val bw = w - dp(34f) - dp(32f)
        rr(c, bx, y - dp(4f), bx + bw, y + dp(4f), dp(4f), 0x59000000)
        val f = max(0f, min(1f, frac))
        if (f > 0.01f) rr(c, bx, y - dp(4f), bx + max(dp(8f), bw * f), y + dp(4f), dp(4f), col)
        text(c, num, x + w, y + dp(4f), dp(12f), 0xFFEAF8FB.toInt(), Paint.Align.RIGHT, body)
    }

    private fun drawStick(c: Canvas, cx: Float, cy: Float, kx: Float, ky: Float, active: Boolean, label: String, warm: Boolean) {
        val r = dp(56f)
        circle(c, cx, cy, r, if (active) 0x40FFFFFF else 0x1CFFFFFF)
        sp.shader = null; sp.strokeWidth = dp(1.5f); sp.color = if (active) 0x99FFFFFF.toInt() else 0x55FFFFFF
        c.drawCircle(cx, cy, r, sp)
        val knob = if (warm) (if (active) 0xDDFFB347.toInt() else 0x66FFB347) else (if (active) 0xCCFFFFFF.toInt() else 0x66FFFFFF)
        circle(c, cx + kx * r, cy + ky * r, dp(26f), knob)
        if (!active) text(c, label, cx, cy + r + dp(16f), dp(11f), 0xB3FFFFFF.toInt(), Paint.Align.CENTER, body, .15f)
    }

    private fun drawHud(c: Canvas) {
        val g = game
        val pl = g.player
        val ml = dp(12f) + insetL
        val mr = dp(12f) + insetR
        val top = dp(10f)

        // stats
        val pw = dp(206f)
        val ph = dp(88f)
        glass(c, ml, top, ml + pw, top + ph, dp(16f), dp(1f))
        val ix = ml + dp(12f)
        val iw = pw - dp(24f)
        statRow(c, "HP", ix, top + dp(18f), iw,
            pl.hp / 100f, if (pl.hp > 50f) 0xFF5EE06B.toInt() else if (pl.hp > 25f) 0xFFFFD23F.toInt() else 0xFFFF4F6D.toInt(),
            max(0, Math.ceil(pl.hp.toDouble()).toInt()).toString())
        statRow(c, "FUEL", ix, top + dp(38f), iw, pl.fuel / 100f,
            if (pl.empty) 0xFFFF8A3D.toInt() else 0xFF19E3C8.toInt(), Math.round(pl.fuel).toString())
        val w = WEAPONS.getValue(pl.wp)
        text(c, w.n.uppercase(), ix, top + dp(70f), dp(13f), 0xFFEAF8FB.toInt(), Paint.Align.LEFT, body, .06f)
        val tag = if (pl.reload > 0f) "RELOADING" else if (pl.empty) "FUEL LOW" else ""
        if (tag.isNotEmpty()) text(c, tag, ix + iw * .62f, top + dp(70f), dp(10f), 0xFFA3C8D1.toInt(), Paint.Align.CENTER, body, .1f)
        text(c, pl.ammo.toString(), ix + iw, top + dp(74f), dp(20f), 0xFFFFB347.toInt(), Paint.Align.RIGHT, fDisplay)

        // tools
        val bs = dp(42f)
        pauseR.set(W - mr - bs, top, W - mr, top + bs)
        muteR.set(W - mr - bs * 2f - dp(8f), top, W - mr - bs - dp(8f), top + bs)
        glass(c, pauseR.left, pauseR.top, pauseR.right, pauseR.bottom, dp(14f), dp(1f))
        glass(c, muteR.left, muteR.top, muteR.right, muteR.bottom, dp(14f), dp(1f))
        val pcx = pauseR.centerX()
        val pcy = pauseR.centerY()
        line(c, pcx - dp(4f), pcy - dp(7f), pcx - dp(4f), pcy + dp(7f), dp(3f), 0xFFEAF8FB.toInt())
        line(c, pcx + dp(4f), pcy - dp(7f), pcx + dp(4f), pcy + dp(7f), dp(3f), 0xFFEAF8FB.toInt())
        val mcx = muteR.centerX()
        val mcy = muteR.centerY()
        path.reset()
        path.moveTo(mcx - dp(8f), mcy - dp(3f)); path.lineTo(mcx - dp(4f), mcy - dp(3f)); path.lineTo(mcx + dp(1f), mcy - dp(7f))
        path.lineTo(mcx + dp(1f), mcy + dp(7f)); path.lineTo(mcx - dp(4f), mcy + dp(3f)); path.lineTo(mcx - dp(8f), mcy + dp(3f)); path.close()
        p.shader = null; p.style = Paint.Style.FILL; p.color = 0xFFEAF8FB.toInt()
        c.drawPath(path, p)
        if (sfx.muted) {
            line(c, mcx + dp(4f), mcy - dp(4f), mcx + dp(10f), mcy + dp(4f), dp(2f), 0xFFEAF8FB.toInt())
            line(c, mcx + dp(10f), mcy - dp(4f), mcx + dp(4f), mcy + dp(4f), dp(2f), 0xFFEAF8FB.toInt())
        } else {
            sp.shader = null; sp.strokeWidth = dp(2f); sp.color = 0xFFEAF8FB.toInt()
            rect.set(mcx - dp(2f), mcy - dp(5f), mcx + dp(8f), mcy + dp(5f))
            c.drawArc(rect, -50f, 100f, false, sp)
        }

        // scoreboard: compact colored chips
        val list = g.ents.sortedByDescending { it.kills }
        val cw = dp(46f)
        val gap = dp(4f)
        val total = cw * list.size + gap * (list.size - 1) + dp(12f)
        val sx = (W - total) / 2f
        val sy = top
        glass(c, sx, sy, sx + total, sy + dp(30f), dp(15f), dp(1f))
        for ((i, e) in list.withIndex()) {
            val x0 = sx + dp(6f) + i * (cw + gap)
            if (e === g.player) rr(c, x0, sy + dp(4f), x0 + cw, sy + dp(26f), dp(11f), 0x4D19E3C8)
            circle(c, x0 + dp(10f), sy + dp(15f), dp(5f), e.skin.c)
            text(c, e.kills.toString(), x0 + dp(22f), sy + dp(20f), dp(14f), 0xFFEAF8FB.toInt(), Paint.Align.LEFT, body)
        }
        text(c, "FIRST TO $GOAL", W / 2f, sy + dp(44f), dp(10f), 0xB3A3C8D1.toInt(), Paint.Align.CENTER, body, .2f)

        // kill feed
        var fy = top + bs + dp(12f)
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
            text(c, "RESPAWNING", W / 2f, H * .3f + dp(26f), dp(16f), 0xFFEAF8FB.toInt(), Paint.Align.CENTER, fDisplay, .04f)
            text(c, max(1, Math.ceil(pl.respawn.toDouble()).toInt()).toString(), W / 2f, H * .3f + dp(52f), dp(22f), 0xFFFFB347.toInt(), Paint.Align.CENTER, fDisplay)
        }

        // sticks
        val ldx = if (lId == -1) max(dp(80f), W * .14f) else lox
        val ldy = if (lId == -1) H - dp(84f) else loy
        val rdx = if (rId == -1) W - max(dp(80f), W * .14f) else rox
        val rdy = if (rId == -1) H - dp(84f) else roy
        drawStick(c, ldx, ldy, lkx, lky, lId != -1, "MOVE / FLY", false)
        drawStick(c, rdx, rdy, rkx, rky, rId != -1, "AIM / FIRE", true)
    }

    /* ---------- overlays ---------- */
    private fun btnShape(c: Canvas, b: Btn, primary: Boolean, label: String, selected: Boolean) {
        if (primary) {
            gp.style = Paint.Style.FILL
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
            text(c, label, (b.l + b.r) / 2f, (b.t + b.b) / 2f + 5f, 15f, 0xFFA3C8D1.toInt(), Paint.Align.CENTER, body, .12f)
        }
        if (pressedId == b.id) rr(c, b.l, b.t, b.r, b.b, 14f, 0x40FFFFFF)
    }

    private fun ghostBtn(c: Canvas, b: Btn, label: String) {
        rr(c, b.l, b.t, b.r, b.b, 14f, 0x24FFFFFF)
        sp.shader = null; sp.strokeWidth = 1.2f; sp.color = 0x59FFFFFF
        rect.set(b.l, b.t, b.r, b.b)
        c.drawRoundRect(rect, 14f, 14f, sp)
        text(c, label, (b.l + b.r) / 2f, (b.t + b.b) / 2f + 6f, 17f, 0xFFEAF8FB.toInt(), Paint.Align.CENTER, body, .12f)
        if (pressedId == b.id) rr(c, b.l, b.t, b.r, b.b, 14f, 0x40FFFFFF)
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

    private fun drawOverlay(c: Canvas) {
        c.drawRect(0f, 0f, W, H, p.apply { shader = null; style = Paint.Style.FILL; color = 0x66041420 })
        c.save()
        c.translate(uiOx, uiOy)
        c.scale(uiK, uiK)
        glass(c, 0f, 0f, 480f, 300f, 28f, 1.4f)
        when (game.mode) {
            Mode.MENU -> {
                drawLogo(c)
                tp.typeface = fDisplay; tp.textSize = 40f; tp.textAlign = Paint.Align.LEFT; tp.letterSpacing = .02f
                tp.shader = titlePaint.shader
                c.drawText("JETSTRIKE", 116f, 68f, tp)
                tp.shader = null
                text(c, "Jetpack deathmatch. You against four bots.", 116f, 92f, 14f, 0xFFA3C8D1.toInt(), Paint.Align.LEFT, body, .02f)
                text(c, "BOT SKILL", 36f, 128f, 11f, 0xFFA3C8D1.toInt(), Paint.Align.LEFT, body, .2f)
                rr(c, 32f, 134f, 448f, 182f, 16f, 0x33000000)
                for (b in menuBtns) {
                    when (b.id) {
                        "play" -> btnShape(c, b, true, "PLAY", false)
                        else -> btnShape(c, b, false, b.id.uppercase(), game.diff == b.id)
                    }
                }
                text(c, "Left stick: move, push up to fly   |   Right stick: aim and shoot", 240f, 282f, 12f, 0xFFA3C8D1.toInt(), Paint.Align.CENTER, body, .02f)
            }
            Mode.PAUSE -> {
                text(c, "PAUSED", 240f, 70f, 34f, 0xFFEAF8FB.toInt(), Paint.Align.CENTER, fDisplay, .04f)
                btnShape(c, pauseBtns[0], true, "RESUME", false)
                ghostBtn(c, pauseBtns[1], "QUIT TO MENU")
            }
            Mode.OVER -> {
                val w = game.winner
                val won = w === game.player
                text(c, if (won) "VICTORY" else (w?.skin?.name ?: "Bot").uppercase() + " WINS", 240f, 66f, 32f,
                    if (won) 0xFFFFD23F.toInt() else 0xFFEAF8FB.toInt(), Paint.Align.CENTER, fDisplay, .04f)
                text(c, if (won) "You reached $GOAL kills first." else "They reached $GOAL kills first.", 240f, 92f, 14f,
                    0xFFA3C8D1.toInt(), Paint.Align.CENTER, body)
                val m = (game.matchTime / 60f).toInt()
                val s = (game.matchTime % 60f).toInt()
                val vals = arrayOf(game.player.kills.toString(), game.player.deaths.toString(), "$m:${if (s < 10) "0" else ""}$s")
                val labs = arrayOf("KILLS", "DEATHS", "TIME")
                for (i in 0 until 3) {
                    val x0 = 36f + i * 138f
                    rr(c, x0, 116f, x0 + 132f, 188f, 14f, 0x40000000)
                    text(c, vals[i], x0 + 66f, 158f, 28f, 0xFFEAF8FB.toInt(), Paint.Align.CENTER, fDisplay)
                    text(c, labs[i], x0 + 66f, 178f, 11f, 0xFFA3C8D1.toInt(), Paint.Align.CENTER, body, .18f)
                }
                btnShape(c, overBtns[0], true, "PLAY AGAIN", false)
                ghostBtn(c, overBtns[1], "MENU")
            }
            else -> {}
        }
        c.restore()
    }

    /* ---------- frame ---------- */
    private fun render(c: Canvas) {
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
        for (e in game.ents) if (e.alive) drawSoldier(c, e)
        drawBullets(c)
        drawParts(c)
        if (game.mode == Mode.PLAY) drawAim(c)
        c.restore()
        if (game.mode == Mode.PLAY || game.mode == Mode.PAUSE || game.mode == Mode.OVER) {
            if (game.vigT > 0f) {
                vigPaint.alpha = (min(1f, game.vigT) * 255f).toInt()
                c.drawRect(0f, 0f, W, H, vigPaint)
            }
            drawHud(c)
        }
        if (game.mode != Mode.PLAY) drawOverlay(c)
    }

    /* ---------- touch ---------- */
    private fun currentBtns(): List<Btn> = when (game.mode) {
        Mode.MENU -> menuBtns
        Mode.PAUSE -> pauseBtns
        Mode.OVER -> overBtns
        else -> emptyList()
    }

    private fun hitBtn(x: Float, y: Float): Btn? {
        val ux = (x - uiOx) / uiK
        val uy = (y - uiOy) / uiK
        for (b in currentBtns()) if (ux >= b.l && ux <= b.r && uy >= b.t && uy <= b.b) return b
        return null
    }

    private fun dispatch(id: String) {
        actions.add(Runnable {
            when (id) {
                "easy", "normal", "hard" -> {
                    game.diff = id
                    prefs.edit().putString("diff", id).apply()
                }
                "play", "again" -> game.startGame()
                "resume" -> game.resume()
                "quit", "menu" -> game.showMenu()
            }
        })
    }

    private fun onDown(id: Int, x: Float, y: Float) {
        if (game.mode == Mode.PLAY) {
            if (pauseR.contains(x, y)) { actions.add(Runnable { game.pause() }); return }
            if (muteR.contains(x, y)) { sfx.muted = !sfx.muted; return }
            if (x < W / 2f) {
                if (lId == -1) { lId = id; lox = x; loy = y; lkx = 0f; lky = 0f; game.lx = 0f; game.ly = 0f; game.lOn = true }
            } else {
                if (rId == -1) { rId = id; rox = x; roy = y; rkx = 0f; rky = 0f; game.rx = 0f; game.ry = 0f; game.rOn = true }
            }
        } else if (uiPtr == -1) {
            uiPtr = id
            pressedId = hitBtn(x, y)?.id
        }
    }

    private fun onMove(id: Int, x: Float, y: Float) {
        val r = dp(56f)
        if (id == lId) {
            var dx = (x - lox) / r
            var dy = (y - loy) / r
            val m = hypot(dx, dy)
            if (m > 1f) { dx /= m; dy /= m }
            lkx = dx; lky = dy; game.lx = dx; game.ly = dy
        } else if (id == rId) {
            var dx = (x - rox) / r
            var dy = (y - roy) / r
            val m = hypot(dx, dy)
            if (m > 1f) { dx /= m; dy /= m }
            rkx = dx; rky = dy; game.rx = dx; game.ry = dy
        }
    }

    private fun onUp(id: Int, x: Float, y: Float) {
        if (id == lId) {
            lId = -1; lkx = 0f; lky = 0f; game.lOn = false; game.lx = 0f; game.ly = 0f
        } else if (id == rId) {
            rId = -1; rkx = 0f; rky = 0f; game.rOn = false; game.rx = 0f; game.ry = 0f
        } else if (id == uiPtr) {
            val b = hitBtn(x, y)
            val pid = pressedId
            if (b != null && b.id == pid) dispatch(b.id)
            pressedId = null
            uiPtr = -1
        }
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
            MotionEvent.ACTION_CANCEL -> {
                lId = -1; rId = -1; uiPtr = -1; pressedId = null
                lkx = 0f; lky = 0f; rkx = 0f; rky = 0f
                game.lOn = false; game.rOn = false; game.lx = 0f; game.ly = 0f; game.rx = 0f; game.ry = 0f
            }
        }
        return true
    }
}
