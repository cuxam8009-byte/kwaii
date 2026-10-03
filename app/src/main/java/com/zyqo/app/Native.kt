package com.zyqo.app

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class ToolDef(val name: String, val description: String, val params: List<Param>)

sealed interface ChatEvent {
    data class Text(val text: String) : ChatEvent
    data class Use(val id: String, val name: String, val args: String) : ChatEvent
    data class Opaque(val key: String, val value: String) : ChatEvent
    data class Usage(val input: Int, val output: Int) : ChatEvent
}

private val GREP_LINE = Regex("^\\S+:\\d+:")

fun describeStep(name: String, args: String): String {
    val a = try {
        JSONObject(args)
    } catch (e: JSONException) {
        JSONObject()
    }
    val s = when (name) {
        "grep" -> "Tìm \"" + a.optString("pattern").take(40) + "\""
        "read_file" -> "Đọc " + a.optString("path").take(50)
        "list_files" -> "Xem cây thư mục"
        "validate" -> "Kiểm tra " + a.optString("path").take(40)
        "search" -> "Tìm web: " + a.optString("query").ifEmpty { a.optString("q") }.take(40)
        "fetch" -> "Đọc trang: " + a.optString("url").removePrefix("https://").take(40)
        else -> "Gọi " + name.take(30)
    }
    return s.trim()
}

fun summarizeResult(name: String, body: String, isError: Boolean): String {
    if (isError) return "lỗi"
    val inner = if (body.startsWith("<tool_result")) body.substringAfter("\n", "").substringBeforeLast("\n</tool_result>") else body
    return when (name) {
        "grep" -> {
            if (inner.startsWith("Không thấy")) {
                "không thấy"
            } else {
                val n = inner.lines().count { GREP_LINE.containsMatchIn(it) }
                if (n > 0) "$n kết quả" else "xong"
            }
        }
        "read_file" -> "${inner.length} ký tự"
        "list_files" -> "${inner.lines().size} dòng"
        else -> "xong"
    }
}

const val NATIVE_HINT = "\n\nCông cụ: dùng các công cụ được cung cấp để tìm và đọc code thật trong dự án trước khi trả lời về code. Không đoán tên hàm, đường dẫn hay số dòng. Nếu công cụ không thấy kết quả thì nói là không thấy. Phần Kết quả tìm tự động trong tin nhắn là bằng chứng thật từ dự án. Dùng find_symbol để kiểm tra một tên có tồn tại. Khi sửa code, read_file trước rồi dùng edit_file hoặc write_file, không dùng thẻ edit. Dùng note_add để ghi quyết định hoặc việc dang dở cần nhớ cho phiên sau. Trước khi gọi công cụ lần đầu, viết một câu ngắn nói bạn sắp làm gì và vì sao. Giữa các lượt công cụ, viết một dòng ngắn nói đã thấy gì và bước tiếp theo. Không lặp lại kết quả công cụ, không viết câu thừa."

class ToolFallback : RuntimeException("tools unsupported")

interface ChatParser {
    val truncated: Boolean
    fun feed(j: JSONObject): List<ChatEvent>
    fun finish(): List<ChatEvent>
}

private fun parseArgs(s: String): JSONObject = try {
    if (s.isBlank()) JSONObject() else JSONObject(s)
} catch (e: JSONException) {
    JSONObject()
}

private fun resultText(s: String): String = if (s.isBlank()) "(không có nội dung)" else s

fun schemaOf(params: List<Param>): JSONObject {
    val props = JSONObject()
    val req = JSONArray()
    for (p in params) {
        val d = JSONObject().put("type", p.type)
        if (p.type == "array") d.put("items", JSONObject().put("type", "string"))
        props.put(p.name, d)
        if (p.required) req.put(p.name)
    }
    val o = JSONObject().put("type", "object").put("properties", props)
    if (req.length() > 0) o.put("required", req)
    return o
}

fun anthropicMessages(msgs: List<CanonMsg>): JSONArray {
    val out = JSONArray()
    var role = ""
    var blocks = JSONArray()
    for (m in msgs) {
        val r = if (m.role == CanonRole.ASSISTANT) "assistant" else "user"
        if (r != role) {
            if (blocks.length() > 0) out.put(JSONObject().put("role", role).put("content", blocks))
            blocks = JSONArray()
            role = r
        }
        for (p in m.parts) {
            when (p) {
                is Part.Text -> {
                    if (p.text.isNotBlank()) blocks.put(JSONObject().put("type", "text").put("text", p.text))
                }
                is Part.ToolCall -> {
                    blocks.put(JSONObject().put("type", "tool_use").put("id", p.id).put("name", p.name).put("input", parseArgs(p.args)))
                }
                is Part.ToolResult -> {
                    val b = JSONObject().put("type", "tool_result").put("tool_use_id", p.id).put("content", resultText(p.content))
                    if (p.isError) b.put("is_error", true)
                    blocks.put(b)
                }
            }
        }
    }
    if (blocks.length() > 0) out.put(JSONObject().put("role", role).put("content", blocks))
    return out
}

fun anthropicBody(model: String, system: String, msgs: List<CanonMsg>, tools: List<ToolDef>, force: Boolean): JSONObject {
    val sys = JSONArray().put(
        JSONObject().put("type", "text").put("text", system).put("cache_control", JSONObject().put("type", "ephemeral"))
    )
    val o = JSONObject().put("model", model).put("max_tokens", 16000).put("stream", true)
        .put("system", sys).put("messages", anthropicMessages(msgs))
    if (tools.isNotEmpty()) {
        val arr = JSONArray()
        tools.forEachIndexed { i, t ->
            val d = JSONObject().put("name", t.name).put("description", t.description).put("input_schema", schemaOf(t.params))
            if (i == tools.lastIndex) d.put("cache_control", JSONObject().put("type", "ephemeral"))
            arr.put(d)
        }
        o.put("tools", arr)
        if (force) o.put("tool_choice", JSONObject().put("type", "any"))
    }
    return o
}

fun openAiMessages(system: String, msgs: List<CanonMsg>): JSONArray {
    val out = JSONArray().put(JSONObject().put("role", "system").put("content", system))
    for (m in msgs) {
        when (m.role) {
            CanonRole.USER -> {
                val t = m.parts.filterIsInstance<Part.Text>().joinToString("\n") { it.text }
                if (t.isNotBlank()) out.put(JSONObject().put("role", "user").put("content", t))
            }
            CanonRole.ASSISTANT -> {
                val t = m.parts.filterIsInstance<Part.Text>().joinToString("\n") { it.text }
                val calls = m.parts.filterIsInstance<Part.ToolCall>()
                val o = JSONObject().put("role", "assistant").put("content", t)
                if (calls.isNotEmpty()) {
                    val arr = JSONArray()
                    for (c in calls) {
                        arr.put(
                            JSONObject().put("id", c.id).put("type", "function")
                                .put("function", JSONObject().put("name", c.name).put("arguments", if (c.args.isBlank()) "{}" else c.args))
                        )
                    }
                    o.put("tool_calls", arr)
                }
                if (t.isNotBlank() || calls.isNotEmpty()) out.put(o)
            }
            CanonRole.TOOL -> {
                for (r in m.parts.filterIsInstance<Part.ToolResult>()) {
                    out.put(JSONObject().put("role", "tool").put("tool_call_id", r.id).put("content", resultText(r.content)))
                }
            }
        }
    }
    return out
}

fun openAiBody(p: Provider, model: String, system: String, msgs: List<CanonMsg>, tools: List<ToolDef>, force: Boolean): JSONObject {
    val o = JSONObject().put("model", model).put("stream", true).put("messages", openAiMessages(system, msgs))
    if (p == Provider.GROQ && model.contains("qwen")) o.put("reasoning_format", "hidden")
    if (tools.isNotEmpty()) {
        val arr = JSONArray()
        for (t in tools) {
            arr.put(
                JSONObject().put("type", "function").put(
                    "function",
                    JSONObject().put("name", t.name).put("description", t.description).put("parameters", schemaOf(t.params))
                )
            )
        }
        o.put("tools", arr)
        if (force) o.put("tool_choice", "required")
    }
    return o
}

fun geminiContents(msgs: List<CanonMsg>): JSONArray {
    val out = JSONArray()
    var role = ""
    var parts = JSONArray()
    for (m in msgs) {
        val r = if (m.role == CanonRole.ASSISTANT) "model" else "user"
        if (r != role) {
            if (parts.length() > 0) out.put(JSONObject().put("role", role).put("parts", parts))
            parts = JSONArray()
            role = r
        }
        val raw = m.opaque["gemini_parts"]
        if (m.role == CanonRole.ASSISTANT && raw != null) {
            val arr = try {
                JSONArray(raw)
            } catch (e: JSONException) {
                null
            }
            if (arr != null) {
                for (i in 0 until arr.length()) parts.put(arr.get(i))
                continue
            }
        }
        for (p in m.parts) {
            when (p) {
                is Part.Text -> {
                    if (p.text.isNotBlank()) parts.put(JSONObject().put("text", p.text))
                }
                is Part.ToolCall -> {
                    val fc = JSONObject().put("name", p.name).put("args", parseArgs(p.args))
                    if (!p.id.startsWith("call_g")) fc.put("id", p.id)
                    parts.put(JSONObject().put("functionCall", fc))
                }
                is Part.ToolResult -> {
                    val fr = JSONObject().put("name", p.name.ifEmpty { "tool" })
                        .put("response", JSONObject().put(if (p.isError) "error" else "result", resultText(p.content)))
                    if (!p.id.startsWith("call_g")) fr.put("id", p.id)
                    parts.put(JSONObject().put("functionResponse", fr))
                }
            }
        }
    }
    if (parts.length() > 0) out.put(JSONObject().put("role", role).put("parts", parts))
    return out
}

fun geminiBody(system: String, msgs: List<CanonMsg>, tools: List<ToolDef>, force: Boolean): JSONObject {
    val o = JSONObject()
        .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        .put("contents", geminiContents(msgs))
        .put("generationConfig", JSONObject().put("maxOutputTokens", 32768))
    if (tools.isNotEmpty()) {
        val decl = JSONArray()
        for (t in tools) {
            decl.put(JSONObject().put("name", t.name).put("description", t.description).put("parameters", schemaOf(t.params)))
        }
        o.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", decl)))
        if (force) o.put("toolConfig", JSONObject().put("functionCallingConfig", JSONObject().put("mode", "ANY")))
    }
    return o
}

fun nativeBody(p: Provider, model: String, system: String, msgs: List<CanonMsg>, tools: List<ToolDef>, force: Boolean): JSONObject = when (p) {
    Provider.ANTHROPIC -> anthropicBody(model, system, msgs, tools, force)
    Provider.GEMINI -> geminiBody(system, msgs, tools, force)
    else -> openAiBody(p, model, system, msgs, tools, force)
}

class AnthropicParser : ChatParser {
    private class Slot(val id: String, val name: String) {
        val buf = StringBuilder()
    }

    private var trunc = false
    override val truncated: Boolean get() = trunc
    private val slots = HashMap<Int, Slot>()
    private var inTok = 0
    private var outTok = 0

    override fun feed(j: JSONObject): List<ChatEvent> {
        val out = ArrayList<ChatEvent>()
        when (j.optString("type")) {
            "message_start" -> {
                val u = j.optJSONObject("message")?.optJSONObject("usage")
                if (u != null) inTok = u.optInt("input_tokens") + u.optInt("cache_read_input_tokens") + u.optInt("cache_creation_input_tokens")
            }
            "content_block_start" -> {
                val b = j.optJSONObject("content_block")
                if (b != null && b.optString("type") == "tool_use") {
                    slots[j.optInt("index", -1)] = Slot(b.optString("id"), b.optString("name"))
                }
            }
            "content_block_delta" -> {
                val d = j.optJSONObject("delta")
                if (d != null) {
                    val kind = d.optString("type")
                    if (kind == "text_delta") {
                        val t = d.optString("text")
                        if (t.isNotEmpty()) out.add(ChatEvent.Text(t))
                    } else if (kind == "input_json_delta") {
                        slots[j.optInt("index", -1)]?.buf?.append(d.optString("partial_json"))
                    }
                }
            }
            "content_block_stop" -> {
                val s = slots.remove(j.optInt("index", -1))
                if (s != null) out.add(ChatEvent.Use(s.id, s.name, if (s.buf.isBlank()) "{}" else s.buf.toString()))
            }
            "message_delta" -> {
                if (j.optJSONObject("delta")?.optString("stop_reason") == "max_tokens") trunc = true
                val u = j.optJSONObject("usage")
                if (u != null && u.has("output_tokens")) outTok = u.optInt("output_tokens")
            }
            "error" -> {
                throw ApiError(j.optJSONObject("error")?.optString("message") ?: "Lỗi từ nhà cung cấp", Fail.SERVER)
            }
        }
        return out
    }

    override fun finish(): List<ChatEvent> = if (inTok > 0 || outTok > 0) listOf(ChatEvent.Usage(inTok, outTok)) else emptyList()
}

class OpenAiParser : ChatParser {
    private class Acc {
        var id = ""
        var name = ""
        val args = StringBuilder()
    }

    private var trunc = false
    override val truncated: Boolean get() = trunc
    private val calls = LinkedHashMap<Int, Acc>()
    private var inTok = 0
    private var outTok = 0

    override fun feed(j: JSONObject): List<ChatEvent> {
        val out = ArrayList<ChatEvent>()
        val err = j.optJSONObject("error")
        if (err != null) throw ApiError(err.optString("message"), Fail.SERVER)
        val u = j.optJSONObject("usage") ?: j.optJSONObject("x_groq")?.optJSONObject("usage")
        if (u != null) {
            inTok = u.optInt("prompt_tokens", inTok)
            outTok = u.optInt("completion_tokens", outTok)
        }
        val c = j.optJSONArray("choices")?.optJSONObject(0) ?: return out
        if (c.optString("finish_reason") == "length") trunc = true
        val d = c.optJSONObject("delta") ?: return out
        if (d.has("content") && !d.isNull("content")) {
            val t = d.optString("content")
            if (t.isNotEmpty()) out.add(ChatEvent.Text(t))
        }
        val tcs = d.optJSONArray("tool_calls")
        if (tcs != null) {
            for (i in 0 until tcs.length()) {
                val tc = tcs.optJSONObject(i) ?: continue
                val acc = calls.getOrPut(tc.optInt("index", i)) { Acc() }
                val id = tc.optString("id")
                if (id.isNotEmpty()) acc.id = id
                val fn = tc.optJSONObject("function")
                if (fn != null) {
                    val n = fn.optString("name")
                    if (n.isNotEmpty() && acc.name.isEmpty()) acc.name = n
                    if (fn.has("arguments") && !fn.isNull("arguments")) acc.args.append(fn.optString("arguments"))
                }
            }
        }
        return out
    }

    override fun finish(): List<ChatEvent> {
        val out = ArrayList<ChatEvent>()
        for ((i, a) in calls) {
            if (a.name.isEmpty()) continue
            out.add(ChatEvent.Use(a.id.ifEmpty { "call_$i" }, a.name, if (a.args.isBlank()) "{}" else a.args.toString()))
        }
        calls.clear()
        if (inTok > 0 || outTok > 0) out.add(ChatEvent.Usage(inTok, outTok))
        return out
    }
}

class GeminiParser : ChatParser {
    private var trunc = false
    override val truncated: Boolean get() = trunc
    private val raw = JSONArray()
    private var count = 0
    private var inTok = 0
    private var outTok = 0

    override fun feed(j: JSONObject): List<ChatEvent> {
        val out = ArrayList<ChatEvent>()
        val err = j.optJSONObject("error")
        if (err != null) throw ApiError(err.optString("message"), Fail.SERVER)
        val block = j.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
        if (block.isNotEmpty()) throw ApiError("Gemini từ chối yêu cầu: $block")
        val um = j.optJSONObject("usageMetadata")
        if (um != null) {
            inTok = um.optInt("promptTokenCount", inTok)
            outTok = um.optInt("candidatesTokenCount", outTok)
        }
        val cand = j.optJSONArray("candidates")?.optJSONObject(0) ?: return out
        if (cand.optString("finishReason") == "MAX_TOKENS") trunc = true
        val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: return out
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            raw.put(part)
            val fc = part.optJSONObject("functionCall")
            if (fc != null) {
                count++
                val id = fc.optString("id").ifEmpty { "call_g$count" }
                out.add(ChatEvent.Use(id, fc.optString("name"), (fc.optJSONObject("args") ?: JSONObject()).toString()))
            } else if (!part.optBoolean("thought", false)) {
                val t = part.optString("text")
                if (t.isNotEmpty()) out.add(ChatEvent.Text(t))
            }
        }
        return out
    }

    override fun finish(): List<ChatEvent> {
        val out = ArrayList<ChatEvent>()
        if (count > 0) out.add(ChatEvent.Opaque("gemini_parts", raw.toString()))
        if (inTok > 0 || outTok > 0) out.add(ChatEvent.Usage(inTok, outTok))
        return out
    }
}

fun newParser(p: Provider): ChatParser = when (p) {
    Provider.ANTHROPIC -> AnthropicParser()
    Provider.GEMINI -> GeminiParser()
    else -> OpenAiParser()
}

fun looksError(body: String): Boolean {
    val inner = if (body.startsWith("<tool_result")) body.substringAfter("\n", "") else body
    val marks = listOf("Lỗi", "Sai tham số", "Tham số không phải", "Không có công cụ", "Người dùng từ chối", "Cần mở dự án", "Regex không hợp lệ")
    return marks.any { inner.startsWith(it) }
}

val WEB_SPECS: List<ToolSpec> = listOf(
    ToolSpec(
        "search", "tìm trên web", listOf(Param("query", "string", true)), true, "web",
        detail = "Tìm trên web, trả về tối đa 5 kết quả ngắn. Dùng khi cần tài liệu, phiên bản hoặc thông tin mới mà dự án không có. Không dùng cho điều có sẵn trong code. query là truy vấn ngắn, không dùng dấu ngoặc kép."
    ),
    ToolSpec(
        "fetch", "đọc một trang web", listOf(Param("url", "string", true), Param("q", "string", false)), true, "web",
        detail = "Đọc một trang web https và chỉ trả về các đoạn liên quan. Dùng sau search hoặc khi đã biết đúng URL tài liệu chính thức. url là địa chỉ https, q là điều cần tìm trong trang."
    )
)

fun nativeDefs(specs: List<ToolSpec>, useWeb: Boolean): List<ToolDef> {
    val all = ArrayList<ToolSpec>()
    for (s in specs) if (s.name != "tool_help") all.add(s)
    if (useWeb) all.addAll(WEB_SPECS)
    return all.map { ToolDef(it.name, if (it.detail.isNotBlank()) it.detail else it.summary, it.params) }
}

private val PRIORITY = listOf("grep", "find_symbol", "read_file", "edit_file", "list_files", "write_file", "search", "fetch", "validate", "delete_file", "note_add", "note_list")

fun pickTools(defs: List<ToolDef>, light: Boolean): List<ToolDef> {
    val ordered = defs.sortedBy { d ->
        val i = PRIORITY.indexOf(d.name)
        if (i < 0) PRIORITY.size else i
    }
    return if (light) ordered.take(7) else ordered.take(24)
}

fun toolsTokens(tools: List<ToolDef>): Int {
    var n = 0
    for (t in tools) n += estimateTokens(t.name + t.description) + 30 * t.params.size + 20
    return n
}

private val BACKTICK_RE = Regex("`([^`\\s]{2,80})`")
private val CAMEL_RE = Regex("\\b[A-Za-z]*[a-z][A-Z][A-Za-z0-9]*\\b")
private val SNAKE_RE = Regex("\\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\\b")
private val PATH_RE = Regex("\\b[\\w./-]+\\.[A-Za-z]{1,5}\\b")
private val INTENT = listOf("ở đâu", "nằm ở", "tìm ", "tìm chỗ", "hàm ", "dòng ", "ai gọi", "gọi ở", "file nào", "where", "find ", "which file")

fun extractIdentifiers(text: String): List<String> {
    val found = LinkedHashSet<String>()
    for (m in BACKTICK_RE.findAll(text)) found.add(m.groupValues[1].removeSuffix("()").trim('`', '"', '\'', ',', '.'))
    for (m in CAMEL_RE.findAll(text)) found.add(m.value)
    for (m in SNAKE_RE.findAll(text)) found.add(m.value)
    for (m in PATH_RE.findAll(text)) found.add(m.value)
    return found.filter { it.length >= 4 && !it.all { c -> c.isDigit() } }.take(4)
}

fun shouldForce(text: String, hasProject: Boolean): Boolean {
    if (!hasProject) return false
    if (extractIdentifiers(text).isNotEmpty()) return true
    val low = text.lowercase()
    return INTENT.any { low.contains(it) }
}

fun preRetrieve(text: String, proj: ProjectData, cap: Int = 1_800): String {
    val ids = extractIdentifiers(text).take(3)
    if (ids.isEmpty()) return ""
    val sb = StringBuilder("[Kết quả tìm tự động trong dự án, do ứng dụng chạy thật, dùng làm bằng chứng]\n")
    for (id in ids) {
        val pat = if (id.all { it.isLetterOrDigit() || it == '_' }) "\\b$id\\b" else Regex.escape(id)
        sb.append("tìm \"").append(id).append("\":\n")
        sb.append(LocalTools.grep(proj, pat, "", 8, false)).append('\n')
    }
    return capText(sb.toString().trimEnd(), cap)
}
