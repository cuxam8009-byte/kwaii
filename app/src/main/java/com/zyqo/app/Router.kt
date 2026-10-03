package com.zyqo.app

import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

fun trimTurns(turns: List<Turn>, maxChars: Int): List<Turn> {
    var total = 0
    val kept = ArrayList<Turn>()
    for (t in turns.asReversed()) {
        if (kept.isNotEmpty() && total + t.text.length > maxChars) break
        kept.add(t)
        total += t.text.length
    }
    kept.reverse()
    while (kept.size > 1 && kept.first().role == Role.ASSISTANT) kept.removeAt(0)
    return kept
}

fun mergeTurns(turns: List<Turn>): List<Turn> {
    val out = ArrayList<Turn>()
    for (t in turns) {
        val last = out.lastOrNull()
        if (last != null && last.role == t.role) out[out.lastIndex] = Turn(t.role, last.text + "\n\n" + t.text) else out.add(t)
    }
    return out
}

private enum class Outcome { DONE, NEXT_MODEL, NEXT_KEY }

class Attempt(val entry: KeyEntry, val model: String)

private const val WAIT_LIMIT_MS = 30_000L
private const val SWITCH_KEY_AFTER_MS = 3_000L

private fun tooBig(e: ApiError): Boolean {
    val r = e.rate ?: return false
    return r.limit > 0 && r.requested > r.limit
}

fun friendlyError(e: ApiError): ApiError {
    if (e.fail == Fail.OTHER && e.detail.isEmpty()) return e
    val label = e.provider?.label ?: "Nhà cung cấp"
    val out = ApiError(humanizeError(e.fail, label, e.retryAfterMs, e.rate), e.fail, e.retryAfterMs, e.rate, e.detail)
    out.provider = e.provider
    return out
}

class Router(private val llm: LlmApi, private val vault: () -> List<KeyEntry>) {
    private val cooldown = HashMap<String, Long>()
    private val dead = HashSet<String>()
    val gate = TpmGate()
    val caps = CapBook()

    @Volatile
    var lastModel: String = ""
        private set

    @Volatile
    var lastEstimate: Int = 0
        private set

    val lastOrigin: String get() = (lastProvider?.name ?: "") + "|" + lastModel

    @Volatile
    private var halted = false

    @Volatile
    var onEvent: (String) -> Unit = {}

    @Volatile
    var onWait: (String?) -> Unit = {}

    @Volatile
    var lastUsed: String = ""
        private set

    @Volatile
    var lastProvider: Provider? = null
        private set

    val truncated: Boolean get() = llm.truncated

    fun halt() {
        halted = true
        llm.cancel()
    }

    fun revive(id: String? = null) {
        if (id == null) dead.clear() else dead.remove(id)
        if (id == null) cooldown.clear() else cooldown.keys.removeAll { it.startsWith("$id|") }
    }

    fun isDead(id: String): Boolean = id in dead

    fun coolingMs(id: String): Long {
        val now = System.currentTimeMillis()
        return cooldown.entries.filter { it.key.startsWith("$id|") }.maxOfOrNull { it.value - now }?.coerceAtLeast(0L) ?: 0L
    }

    fun plan(): List<Attempt> {
        val now = System.currentTimeMillis()
        val all = vault().filter { it.enabled && it.id !in dead }
            .flatMap { e -> e.models.take(3).map { m -> Attempt(e, m) } }
        val ready = all.filter { (cooldown["${it.entry.id}|${it.model}"] ?: 0L) <= now }
        val cooling = all.filter { (cooldown["${it.entry.id}|${it.model}"] ?: 0L) > now }
            .sortedBy { cooldown["${it.entry.id}|${it.model}"] ?: 0L }
        return (ready + cooling).take(9)
    }

    private fun label(a: Attempt): String = a.entry.provider.label + " · " + a.model

    fun run(turns: List<Turn>, system: (Provider, Int) -> String): Flow<String> =
        drive(system, false, { b -> trimWithDigest(turns, b * 2).sumOf { estimateTokens(it.text) } }) { a, sys, b ->
            llm.stream(a.entry.provider, a.entry.key, a.model, sys, trimWithDigest(turns, b * 2))
        }

    fun runChat(msgs: List<CanonMsg>, system: (Provider, Int) -> String, tools: List<ToolDef>, force: Boolean): Flow<ChatEvent> =
        drive(system, true, { b -> estimateMsgs(maskObservations(trimMsgsDigest(msgs, b * 2))) + toolsTokens(tools) }) { a, sys, b ->
            val p = a.entry.provider
            if (caps.tools(p, a.model) == ToolSupport.NO) throw ToolFallback()
            val origin = p.name + "|" + a.model
            val trimmed = maskObservations(trimMsgsDigest(msgs, b * 2))
            val mixed = trimmed.any { m ->
                val o = m.opaque["origin"]
                o != null && o != origin
            }
            val use = if (mixed) flattenForFallback(trimmed).map { it.toCanon() } else trimmed
            llm.chat(p, a.entry.key, a.model, sys, use, pickTools(tools, p.budget <= 20_000), force).onEach {
                if (it is ChatEvent.Use) caps.learnTools(p, a.model, ToolSupport.YES)
            }
        }

    private fun <T> drive(
        system: (Provider, Int) -> String,
        native: Boolean,
        sizeOf: (Int) -> Int,
        open: (Attempt, String, Int) -> Flow<T>
    ): Flow<T> = flow {
        halted = false
        val attempts = plan()
        if (attempts.isEmpty()) throw ApiError("Chưa có khóa API khả dụng. Thêm khóa trong Cài đặt.", Fail.AUTH)
        val multiKey = attempts.map { it.entry.id }.toSet().size > 1
        var emitted = false
        var last: ApiError? = null
        var skipKey = ""
        for (a in attempts) {
            if (a.entry.id == skipKey) continue
            val wrapped = object : FlowCollector<T> {
                override suspend fun emit(value: T) {
                    emitted = true
                    this@flow.emit(value)
                }
            }
            DebugLog.log("router", "thử " + label(a) + " khóa " + maskKey(a.entry.key))
            val outcome = try {
                wrapped.attempt(a, system, sizeOf, open, multiKey)
            } catch (e: ApiError) {
                e.provider = a.entry.provider
                DebugLog.log("lỗi", label(a) + " " + e.fail + " chờ=" + e.retryAfterMs + "ms " + e.detail)
                if (emitted || halted) throw friendlyError(e)
                last = e
                react(a, e, native)
            } catch (e: IOException) {
                DebugLog.log("lỗi", label(a) + " mạng: " + (e.message ?: ""))
                if (emitted || halted) throw e
                last = ApiError("Không kết nối được mạng.", Fail.NETWORK)
                Outcome.NEXT_MODEL
            }
            if (outcome == Outcome.DONE) {
                lastUsed = label(a)
                lastProvider = a.entry.provider
                lastModel = a.model
                DebugLog.log("router", "xong với " + label(a))
                return@flow
            }
            if (outcome == Outcome.NEXT_KEY) skipKey = a.entry.id
            val next = attempts.drop(attempts.indexOf(a) + 1).firstOrNull { it.entry.id != skipKey }
            if (next != null) onEvent("Chuyển sang " + label(next))
        }
        val fin = last ?: ApiError("Không còn khóa nào dùng được.", Fail.AUTH)
        throw friendlyError(fin)
    }

    private suspend fun countdown(ms: Long, who: String) {
        var left = ms
        try {
            while (left > 0 && !halted) {
                onWait(who + " hết hạn mức phút này, chờ " + (left + 999) / 1000 + " giây")
                val step = minOf(left, 1000L)
                delay(step)
                left -= step
            }
        } finally {
            onWait(null)
        }
    }

    private suspend fun <T> FlowCollector<T>.attempt(
        a: Attempt,
        system: (Provider, Int) -> String,
        sizeOf: (Int) -> Int,
        open: (Attempt, String, Int) -> Flow<T>,
        multiKey: Boolean
    ): Outcome {
        var budget = a.entry.provider.budget
        var shrinks = 0
        var retries = 0
        val gk = a.entry.id + "|" + a.model
        while (true) {
            val sys = system(a.entry.provider, budget)
            val est = estimateTokens(sys) + sizeOf(budget)
            val w = gate.waitMs(gk, est)
            if (w > WAIT_LIMIT_MS) throw ApiError("", Fail.RATE, w)
            if (w > 0) countdown(w, a.entry.provider.label)
            gate.record(gk, est)
            lastEstimate = est
            try {
                open(a, sys, budget).collect { emit(it) }
                return Outcome.DONE
            } catch (e: ApiError) {
                e.rate?.let { gate.learn(gk, it.limit) }
                if (e.fail == Fail.RATE || e.fail == Fail.OVERSIZE) gate.undo(gk)
                val canRetryNow = retries < 1 && !halted
                val waitHere = e.retryAfterMs in 1..WAIT_LIMIT_MS && !(multiKey && e.retryAfterMs > SWITCH_KEY_AFTER_MS)
                when {
                    (e.fail == Fail.OVERSIZE || (e.fail == Fail.RATE && tooBig(e))) && shrinks < 3 && budget / 2 >= a.entry.provider.minBudget -> {
                        budget /= 2
                        shrinks++
                    }
                    e.fail == Fail.RATE && canRetryNow && waitHere -> {
                        retries++
                        countdown(e.retryAfterMs + 250, a.entry.provider.label)
                    }
                    e.fail == Fail.TOOLS && canRetryNow -> {
                        retries++
                    }
                    e.fail == Fail.SERVER && canRetryNow -> {
                        retries++
                        delay(1500)
                    }
                    else -> throw e
                }
            }
        }
    }

    private fun react(a: Attempt, e: ApiError, native: Boolean): Outcome {
        val key = "${a.entry.id}|${a.model}"
        return when (e.fail) {
            Fail.AUTH, Fail.BILLING -> {
                dead.add(a.entry.id)
                onEvent(a.entry.provider.label + " " + maskKey(a.entry.key) + ": " + (if (e.fail == Fail.AUTH) "khóa bị từ chối" else "hết hạn mức"))
                Outcome.NEXT_KEY
            }
            Fail.RATE -> {
                val wait = if (e.retryAfterMs > 0) e.retryAfterMs else 60_000L
                cooldown[key] = System.currentTimeMillis() + wait.coerceAtMost(600_000L)
                Outcome.NEXT_MODEL
            }
            Fail.MODEL, Fail.OVERSIZE, Fail.SERVER, Fail.NETWORK -> {
                cooldown[key] = System.currentTimeMillis() + 120_000L
                Outcome.NEXT_MODEL
            }
            Fail.TOOLS -> {
                caps.learnTools(a.entry.provider, a.model, ToolSupport.NO)
                DebugLog.log("công cụ", label(a) + " không gọi công cụ ổn định, ghi nhận để dùng giao thức văn bản")
                if (native) throw ToolFallback()
                cooldown[key] = System.currentTimeMillis() + 120_000L
                Outcome.NEXT_MODEL
            }
            Fail.OTHER -> throw e
        }
    }
}
