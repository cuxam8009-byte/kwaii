package com.zyqo.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

private val SECRET_RE = Regex(
    "(sk-[A-Za-z0-9_\\-]{8,}|gsk_[A-Za-z0-9]{8,}|AIza[0-9A-Za-z_\\-]{8,}|AQ\\.[0-9A-Za-z_\\-]{8,}|tvly-[A-Za-z0-9_\\-]{6,}|Bearer\\s+\\S+)"
)

fun scrub(s: String): String = SECRET_RE.replace(s, "[khóa ẩn]")

object DebugLog {
    private const val MAX = 300
    private val lines = ConcurrentLinkedDeque<String>()

    fun log(kind: String, msg: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        lines.addLast(stamp + " [" + kind + "] " + scrub(msg).take(400))
        while (lines.size > MAX) lines.pollFirst()
    }

    fun export(): String = lines.joinToString("\n")

    fun clear() {
        lines.clear()
    }
}
