package com.zyqo.app

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private val RETRY_BODY = Regex("\"retryDelay\"\\s*:\\s*\"([\\d.]+)s\"")
private val RETRY_TEXT = Regex("try again in ([\\d.]+)\\s*(ms|s)", RegexOption.IGNORE_CASE)
private val OVERSIZE_HINTS = listOf(
    "too long", "context length", "maximum context", "too many tokens", "exceeds the maximum",
    "request too large", "too large for model", "reduce the length"
)
private val TOOL_HINTS = listOf(
    "tool_use_failed", "failed to call a function", "does not support tool", "doesn't support tool",
    "support tool use", "tools are not supported", "tool calling is not", "function calling is not"
)
private val BILLING_HINTS = listOf("billing", "insufficient_quota", "credit balance", "exceeded your current quota")

fun classify(code: Int, detail: String): Fail {
    val low = detail.lowercase()
    return when {
        code == 413 -> Fail.OVERSIZE
        (code == 400 || code == 429) && OVERSIZE_HINTS.any { low.contains(it) } -> Fail.OVERSIZE
        code == 402 -> Fail.BILLING
        code == 401 || code == 403 -> Fail.AUTH
        code == 400 && (low.contains("api key") || low.contains("credentials")) -> Fail.AUTH
        (code == 400 || code == 404 || code == 422) && TOOL_HINTS.any { low.contains(it) } -> Fail.TOOLS
        code == 404 -> Fail.MODEL
        code == 429 -> if (BILLING_HINTS.any { low.contains(it) }) Fail.BILLING else Fail.RATE
        code == 408 -> Fail.NETWORK
        code in 500..599 -> Fail.SERVER
        else -> Fail.OTHER
    }
}

fun retryDelayMs(header: String?, body: String): Long {
    header?.trim()?.toDoubleOrNull()?.let { return (it * 1000).toLong() }
    RETRY_BODY.find(body)?.let { return (it.groupValues[1].toDouble() * 1000).toLong() }
    RETRY_TEXT.find(body)?.let {
        val v = it.groupValues[1].toDouble()
        return if (it.groupValues[2].equals("ms", true)) v.toLong() else (v * 1000).toLong()
    }
    return 0L
}

interface LlmApi {
    val truncated: Boolean
    fun cancel()
    fun stream(p: Provider, key: String, model: String, system: String, turns: List<Turn>): Flow<String>

    fun chat(
        p: Provider,
        key: String,
        model: String,
        system: String,
        msgs: List<CanonMsg>,
        tools: List<ToolDef>,
        force: Boolean
    ): Flow<ChatEvent> = flow {
        stream(p, key, model, system, flattenForFallback(msgs)).collect { emit(ChatEvent.Text(it)) }
    }
}

class LlmClient : LlmApi {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json".toMediaType()

    @Volatile
    private var active: Call? = null

    @Volatile
    private var trunc: Boolean = false

    override val truncated: Boolean get() = trunc

    override fun cancel() {
        active?.cancel()
    }

    private fun failure(code: Int, body: String, header: String?): ApiError {
        val detail = try {
            val j = JSONObject(body)
            when (val e = j.opt("error")) {
                is JSONObject -> e.optString("message")
                is String -> e
                else -> j.optString("message")
            }
        } catch (x: Exception) {
            ""
        }
        val fail = classify(code, detail)
        val base = when (fail) {
            Fail.AUTH -> "Khóa API bị từ chối."
            Fail.BILLING -> "Tài khoản hết số dư hoặc hết hạn mức."
            Fail.MODEL -> "Không tìm thấy model."
            Fail.RATE -> "Chạm giới hạn tốc độ."
            Fail.OVERSIZE -> "Yêu cầu vượt giới hạn kích thước của model."
            Fail.SERVER -> "Nhà cung cấp đang gặp sự cố."
            Fail.NETWORK -> "Hết thời gian chờ."
            Fail.TOOLS -> "Model không gọi được công cụ đúng định dạng."
            Fail.OTHER -> "Lỗi $code từ nhà cung cấp."
        }
        DebugLog.log("http", "$code $fail " + detail.take(300))
        return ApiError(base, fail, retryDelayMs(header, body), parseRate(detail), detail.take(300))
    }

    private fun listRequest(p: Provider, key: String): Request {
        val rb = Request.Builder()
        when (p) {
            Provider.ANTHROPIC -> rb.url("https://api.anthropic.com/v1/models?limit=100")
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
            Provider.OPENAI -> rb.url("https://api.openai.com/v1/models")
                .header("Authorization", "Bearer $key")
            Provider.GEMINI -> rb.url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200")
                .header("x-goog-api-key", key)
            Provider.GROQ -> rb.url("https://api.groq.com/openai/v1/models")
                .header("Authorization", "Bearer $key")
            Provider.OPENROUTER -> rb.url("https://openrouter.ai/api/v1/auth/key")
                .header("Authorization", "Bearer $key")
        }
        return rb.build()
    }

    fun detectModels(p: Provider, key: String): List<String> {
        http.newCall(listRequest(p, key)).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw failure(r.code, body, r.header("Retry-After"))
            val j = JSONObject(body)
            val ids: List<String> = when (p) {
                Provider.GEMINI -> {
                    val arr = j.optJSONArray("models")
                    val out = ArrayList<String>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val methods = o.optJSONArray("supportedGenerationMethods")?.toString().orEmpty()
                            if (methods.contains("generateContent")) out.add(o.optString("name").removePrefix("models/"))
                        }
                    }
                    out
                }
                Provider.OPENROUTER -> emptyList()
                else -> ids(j.optJSONArray("data"), "id")
            }
            return rankModels(p, ids)
        }
    }

    private fun ids(arr: JSONArray?, field: String): List<String> {
        val out = ArrayList<String>()
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i)?.optString(field).orEmpty()
            if (v.isNotEmpty()) out.add(v)
        }
        return out
    }

    private fun roleName(t: Turn, assistant: String): String = if (t.role == Role.USER) "user" else assistant

    private fun build(p: Provider, key: String, model: String, system: String, turns: List<Turn>): Request {
        val rb = Request.Builder()
        val body: JSONObject = when (p) {
            Provider.ANTHROPIC -> {
                rb.url("https://api.anthropic.com/v1/messages")
                    .header("x-api-key", key)
                    .header("anthropic-version", "2023-06-01")
                val sys = JSONArray().put(
                    JSONObject().put("type", "text").put("text", system)
                        .put("cache_control", JSONObject().put("type", "ephemeral"))
                )
                val msgs = JSONArray()
                turns.forEach { t -> msgs.put(JSONObject().put("role", roleName(t, "assistant")).put("content", t.text)) }
                JSONObject().put("model", model).put("max_tokens", 16000).put("stream", true)
                    .put("system", sys).put("messages", msgs)
            }
            Provider.OPENAI, Provider.GROQ, Provider.OPENROUTER -> {
                val url = when (p) {
                    Provider.OPENAI -> "https://api.openai.com/v1/chat/completions"
                    Provider.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
                    else -> "https://openrouter.ai/api/v1/chat/completions"
                }
                rb.url(url).header("Authorization", "Bearer $key")
                val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
                turns.forEach { t -> msgs.put(JSONObject().put("role", roleName(t, "assistant")).put("content", t.text)) }
                val o = JSONObject().put("model", model).put("stream", true).put("messages", msgs)
                if (p == Provider.GROQ && model.contains("qwen")) o.put("reasoning_format", "hidden")
                o
            }
            Provider.GEMINI -> {
                rb.url("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse")
                    .header("x-goog-api-key", key)
                val contents = JSONArray()
                turns.forEach { t ->
                    contents.put(
                        JSONObject().put("role", roleName(t, "model"))
                            .put("parts", JSONArray().put(JSONObject().put("text", t.text)))
                    )
                }
                JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put("contents", contents)
                    .put("generationConfig", JSONObject().put("maxOutputTokens", 32768))
            }
        }
        return rb.post(body.toString().toRequestBody(jsonType)).build()
    }

    private fun extract(p: Provider, j: JSONObject): String? {
        return when (p) {
            Provider.ANTHROPIC -> when (j.optString("type")) {
                "content_block_delta" -> j.optJSONObject("delta")?.optString("text")
                "message_delta" -> {
                    if (j.optJSONObject("delta")?.optString("stop_reason") == "max_tokens") trunc = true
                    null
                }
                "error" -> throw ApiError(j.optJSONObject("error")?.optString("message") ?: "Lỗi từ nhà cung cấp", Fail.SERVER)
                else -> null
            }
            Provider.OPENAI, Provider.GROQ, Provider.OPENROUTER -> {
                val err = j.optJSONObject("error")
                if (err != null) throw ApiError(err.optString("message"), Fail.SERVER)
                val c = j.optJSONArray("choices")?.optJSONObject(0)
                if (c?.optString("finish_reason") == "length") trunc = true
                val d = c?.optJSONObject("delta")
                if (d == null || d.isNull("content")) null else d.optString("content")
            }
            Provider.GEMINI -> {
                val err = j.optJSONObject("error")
                if (err != null) throw ApiError(err.optString("message"), Fail.SERVER)
                val block = j.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                if (block.isNotEmpty()) throw ApiError("Gemini từ chối yêu cầu: $block")
                val cand = j.optJSONArray("candidates")?.optJSONObject(0)
                if (cand?.optString("finishReason") == "MAX_TOKENS") trunc = true
                val parts = cand?.optJSONObject("content")?.optJSONArray("parts")
                if (parts == null) null else {
                    val sb = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        if (part.optBoolean("thought", false)) continue
                        sb.append(part.optString("text"))
                    }
                    sb.toString()
                }
            }
        }
    }

    private fun nativeRequest(p: Provider, key: String, model: String, body: JSONObject): Request {
        val rb = Request.Builder()
        when (p) {
            Provider.ANTHROPIC -> rb.url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
            Provider.OPENAI -> rb.url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer $key")
            Provider.GROQ -> rb.url("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer $key")
            Provider.OPENROUTER -> rb.url("https://openrouter.ai/api/v1/chat/completions")
                .header("Authorization", "Bearer $key")
            Provider.GEMINI -> rb.url("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse")
                .header("x-goog-api-key", key)
        }
        return rb.post(body.toString().toRequestBody(jsonType)).build()
    }

    override fun chat(
        p: Provider,
        key: String,
        model: String,
        system: String,
        msgs: List<CanonMsg>,
        tools: List<ToolDef>,
        force: Boolean
    ): Flow<ChatEvent> = flow {
        trunc = false
        val parser = newParser(p)
        val call = http.newCall(nativeRequest(p, key, model, nativeBody(p, model, system, msgs, tools, force)))
        active = call
        try {
            call.execute().use { r ->
                if (!r.isSuccessful) throw failure(r.code, r.body?.string().orEmpty(), r.header("Retry-After"))
                val src = r.body!!.source()
                while (true) {
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val d = line.substring(5).trim()
                    if (d.isEmpty() || d == "[DONE]") continue
                    val evs = try {
                        parser.feed(JSONObject(d))
                    } catch (e: JSONException) {
                        emptyList()
                    }
                    for (ev in evs) emit(ev)
                }
            }
            for (ev in parser.finish()) emit(ev)
            if (parser.truncated) trunc = true
        } finally {
            active = null
        }
    }.flowOn(Dispatchers.IO)

    override fun stream(p: Provider, key: String, model: String, system: String, turns: List<Turn>): Flow<String> = flow {
        trunc = false
        val call = http.newCall(build(p, key, model, system, turns))
        active = call
        try {
            call.execute().use { r ->
                if (!r.isSuccessful) throw failure(r.code, r.body?.string().orEmpty(), r.header("Retry-After"))
                val src = r.body!!.source()
                while (true) {
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val d = line.substring(5).trim()
                    if (d.isEmpty() || d == "[DONE]") continue
                    val t = try {
                        extract(p, JSONObject(d))
                    } catch (e: JSONException) {
                        null
                    }
                    if (!t.isNullOrEmpty()) emit(t)
                }
            }
        } finally {
            active = null
        }
    }.flowOn(Dispatchers.IO)
}
