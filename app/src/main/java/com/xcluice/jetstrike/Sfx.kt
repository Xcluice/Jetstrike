package com.xcluice.jetstrike

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Small synthesized sound effects, generated once at startup so the APK needs no audio assets. */
class Sfx(ctx: Context) {
    private val rate = 22050
    private val pool: SoundPool
    private val ids = HashMap<String, Int>()
    var muted = false

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build()
        try {
            add(ctx, "pistol", gen(.12f, .8f, 2600f, .35f, 260f, .4f, true))
            add(ctx, "rifle", gen(.09f, .8f, 3000f, .3f, 200f, .5f, true))
            add(ctx, "shotgun", gen(.3f, 1f, 1500f, .5f, 110f, .4f, false))
            add(ctx, "sniper", gen(.4f, 1f, 5000f, .4f, 420f, .2f, false))
            add(ctx, "hit", gen(.07f, .1f, 2000f, .8f, 520f, .5f, false))
            add(ctx, "die", gen(.45f, 1f, 900f, .4f, 180f, .3f, false))
            add(ctx, "pick", gen(.22f, 0f, 2000f, .8f, 700f, 1.6f, false))
            add(ctx, "reload", gen(.1f, .2f, 2000f, .7f, 320f, 1.2f, true))
            add(ctx, "boom", gen(.6f, 1f, 600f, .6f, 85f, .3f, false))
            add(ctx, "jet", gen(.1f, 1f, 900f, 0f, 200f, 1f, false))
        } catch (e: Exception) {
            // sound is optional; the game runs without it
        }
    }

    private fun add(ctx: Context, name: String, samples: ShortArray) {
        val f = File(ctx.cacheDir, "sfx_$name.wav")
        f.writeBytes(wav(samples))
        ids[name] = pool.load(f.absolutePath, 1)
    }

    fun play(name: String, vol: Float) {
        if (muted) return
        val id = ids[name] ?: return
        val v = vol.coerceIn(0f, 1f) * (if (name == "jet") .25f else 1f)
        pool.play(id, v, v, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }

    private fun gen(dur: Float, noiseAmt: Float, lp: Float, toneAmt: Float, f0: Float, slide: Float, square: Boolean): ShortArray {
        val n = (dur * rate).toInt()
        val out = ShortArray(n)
        var phase = 0.0
        var lpState = 0f
        val alpha = (lp / (lp + rate / 6.2831855f)).coerceIn(0.01f, 1f)
        for (i in 0 until n) {
            val tt = i.toFloat() / rate
            val env = exp(-5f * tt / dur)
            val noise = (Random.nextFloat() * 2f - 1f)
            lpState += alpha * (noise - lpState)
            val f = f0 * slide.pow(tt / dur)
            phase += 2.0 * PI * f / rate
            val w = if (square) (if (sin(phase) >= 0.0) 1f else -1f) else sin(phase).toFloat()
            val s = env * (noiseAmt * lpState * 1.6f + toneAmt * w * .6f)
            out[i] = (s.coerceIn(-1f, 1f) * 30000f).toInt().toShort()
        }
        return out
    }

    private fun wav(s: ShortArray): ByteArray {
        val dataLen = s.size * 2
        val b = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + dataLen); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(1)
        b.putInt(rate); b.putInt(rate * 2); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(dataLen)
        for (v in s) b.putShort(v)
        return b.array()
    }
}
