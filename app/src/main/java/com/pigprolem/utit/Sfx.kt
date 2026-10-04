package com.pigprolem.utit

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.*

object Sfx {
    private const val SR = 22050
    private val TWO_PI = (2 * PI).toFloat()

    private fun bp(x: FloatArray, fc: Float, q: Float): FloatArray {
        val w = TWO_PI * fc / SR
        val al = sin(w) / (2 * q)
        val a0 = 1 + al; val a1 = -2 * cos(w); val a2 = 1 - al
        val y = FloatArray(x.size)
        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
        for (i in x.indices) {
            val v = (al * x[i] - al * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1; x1 = x[i]; y2 = y1; y1 = v; y[i] = v
        }
        return y
    }

    private fun grunt(f0: Float, f1: Float, dur: Float, vol: Float): FloatArray {
        val n = (dur * SR).toInt()
        val x = FloatArray(n)
        var ph = 0f
        for (i in 0 until n) {
            val t = i / n.toFloat()
            ph += (f0 + (f1 - f0) * t) / SR
            val saw = 2 * (ph % 1f) - 1f
            val env = min(1f, i / (0.02f * SR)) * (1f - t)
            x[i] = saw * env * (1f + 0.3f * sin(TWO_PI * 30f * i / SR))
        }
        val a = bp(x, 700f, 3f); val b = bp(x, 1600f, 4f)
        return FloatArray(n) { (a[it] * 0.9f + b[it] * 0.7f) * vol * 3f }
    }

    private fun gap(d: Float) = FloatArray((d * SR).toInt())

    private fun tone(f: Float, dur: Float, vol: Float, vib: Float = 0f): FloatArray {
        val n = (dur * SR).toInt()
        var ph = 0f
        return FloatArray(n) { i ->
            val t = i / n.toFloat()
            ph += (f + vib * sin(TWO_PI * 9f * i / SR)) / SR
            sin(TWO_PI * ph) * vol * min(1f, i / (0.01f * SR)) * (1f - t)
        }
    }

    private fun play(x: FloatArray) {
        Thread {
            try {
                val s = ShortArray(x.size) { (x[it].coerceIn(-1f, 1f) * 30000).toInt().toShort() }
                val t = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(s.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC).build()
                t.write(s, 0, s.size); t.play()
                Thread.sleep(x.size * 1000L / SR + 200)
                t.release()
            } catch (e: Exception) { }
        }.start()
    }

    fun oink() = play(grunt(240f, 180f, 0.13f, 0.5f) + gap(0.05f) + grunt(320f, 200f, 0.2f, 0.5f))
    fun whine() = play(FloatArray((0.5f * SR).toInt()).also { out ->
        var ph = 0f
        for (i in out.indices) {
            val t = i / out.size.toFloat()
            ph += (560f - 230f * t + 18f * sin(TWO_PI * 9f * i / SR)) / SR
            out[i] = sin(TWO_PI * ph) * 0.25f * min(1f, i / (0.05f * SR)) * (1f - t)
        }
    })
    fun crunch() {
        val r = java.util.Random()
        var out = FloatArray(0)
        repeat(3) {
            val n = (0.05f * SR).toInt()
            out += FloatArray(n) { i -> (r.nextFloat() * 2 - 1) * (1 - i / n.toFloat()) * 0.35f }
            out += gap(0.07f)
        }
        play(out)
    }
    fun cheer() = play(tone(523f, 0.12f, 0.3f) + tone(659f, 0.12f, 0.3f) + tone(784f, 0.12f, 0.3f) + tone(1047f, 0.25f, 0.3f))
}
