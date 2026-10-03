package com.zyqo.app

fun estimateTokens(text: String): Int {
    if (text.isEmpty()) return 0
    var ascii = 0
    var other = 0
    for (c in text) {
        if (c.code < 128) ascii++ else other++
    }
    return ((ascii / 3.6 + other / 1.8) * TokenMeter.factor).toInt() + 1
}

object TokenMeter {
    @Volatile
    var factor: Double = 1.0

    fun observe(estimated: Int, actual: Int) {
        if (estimated < 200 || actual < 50) return
        val r = actual.toDouble() / estimated
        factor = (factor * (0.7 + 0.3 * r)).coerceIn(0.5, 2.5)
    }
}

class RateInfo(val limit: Int, val used: Int, val requested: Int)

private val RATE_RE = Regex("Limit\\s+(\\d+),\\s*Used\\s+(\\d+),\\s*Requested\\s+(\\d+)", RegexOption.IGNORE_CASE)

fun parseRate(detail: String): RateInfo? {
    val m = RATE_RE.find(detail) ?: return null
    return RateInfo(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
}

fun humanizeError(fail: Fail, provider: String, retryAfterMs: Long, rate: RateInfo?): String {
    return when (fail) {
        Fail.RATE -> {
            val wait = if (retryAfterMs > 0) "thử lại sau ${(retryAfterMs + 999) / 1000} giây" else "thử lại sau ít phút"
            val extra = if (rate != null && rate.limit > 0) " (giới hạn ${rate.limit} token mỗi phút, yêu cầu này cần khoảng ${rate.requested})" else ""
            "$provider hết hạn mức trong phút này, $wait$extra."
        }
        Fail.OVERSIZE -> "Yêu cầu quá lớn so với giới hạn của $provider. Hãy hỏi gọn hơn hoặc mở dự án nhỏ hơn."
        Fail.AUTH -> "Khóa $provider bị từ chối. Kiểm tra lại khóa trong Cài đặt."
        Fail.BILLING -> "Tài khoản $provider hết số dư hoặc hết hạn mức."
        Fail.MODEL -> "$provider không tìm thấy model đã chọn."
        Fail.SERVER -> "$provider đang gặp sự cố, thử lại sau."
        Fail.NETWORK -> "Không kết nối được tới $provider. Kiểm tra mạng rồi thử lại."
        Fail.TOOLS -> "$provider không gọi công cụ ổn định ở model này, app chuyển sang chế độ văn bản."
        Fail.OTHER -> "Có lỗi không xác định từ $provider. Chi tiết nằm trong Nhật ký gỡ lỗi ở Cài đặt."
    }
}

class TpmGate(private val clock: () -> Long = { System.currentTimeMillis() }) {
    private val limits = HashMap<String, Int>()
    private val sent = HashMap<String, ArrayDeque<Pair<Long, Int>>>()

    @Synchronized
    fun learn(key: String, limit: Int) {
        if (limit > 0) limits[key] = limit
    }

    @Synchronized
    fun limitOf(key: String): Int = limits[key] ?: 0

    @Synchronized
    fun record(key: String, tokens: Int) {
        sent.getOrPut(key) { ArrayDeque() }.addLast(Pair(clock(), tokens))
    }

    @Synchronized
    fun undo(key: String) {
        sent[key]?.removeLastOrNull()
    }

    @Synchronized
    fun waitMs(key: String, tokens: Int): Long {
        val limit = limits[key] ?: return 0L
        val q = sent[key] ?: return 0L
        val now = clock()
        while (q.isNotEmpty() && now - q.first().first >= 60_000L) q.removeFirst()
        var used = 0
        for (e in q) used += e.second
        if (tokens > limit || used + tokens <= limit) return 0L
        var wait = 0L
        for (e in q) {
            used -= e.second
            wait = e.first + 60_000L - now
            if (used + tokens <= limit) break
        }
        return wait.coerceAtLeast(0L)
    }

    @Synchronized
    fun export(): String {
        val o = org.json.JSONObject()
        for ((k, v) in limits) o.put(k, v)
        return o.toString()
    }

    @Synchronized
    fun load(raw: String) {
        if (raw.isBlank()) return
        try {
            val o = org.json.JSONObject(raw)
            for (k in o.keys()) learn(k, o.optInt(k, 0))
        } catch (e: org.json.JSONException) {
        }
    }
}
