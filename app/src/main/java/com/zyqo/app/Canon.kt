package com.zyqo.app

enum class CanonRole { USER, ASSISTANT, TOOL }

sealed interface Part {
    data class Text(val text: String) : Part
    data class ToolCall(val id: String, val name: String, val args: String) : Part
    data class ToolResult(val id: String, val content: String, val isError: Boolean = false, val name: String = "") : Part
}

data class CanonMsg(
    val role: CanonRole,
    val parts: List<Part>,
    val opaque: Map<String, String> = emptyMap()
)

fun msgChars(m: CanonMsg): Int {
    var n = 0
    for (p in m.parts) {
        n += when (p) {
            is Part.Text -> p.text.length
            is Part.ToolCall -> p.name.length + p.args.length
            is Part.ToolResult -> p.content.length
        }
    }
    return n
}

fun estimateMsgs(msgs: List<CanonMsg>): Int {
    var n = 0
    for (m in msgs) {
        for (p in m.parts) {
            n += when (p) {
                is Part.Text -> estimateTokens(p.text)
                is Part.ToolCall -> estimateTokens(p.name + p.args)
                is Part.ToolResult -> estimateTokens(p.content)
            }
        }
    }
    return n
}

fun trimMsgs(msgs: List<CanonMsg>, maxChars: Int): List<CanonMsg> {
    if (msgs.isEmpty()) return msgs
    var total = 0
    var start = msgs.size
    for (i in msgs.indices.reversed()) {
        val c = msgChars(msgs[i])
        if (total + c > maxChars && start < msgs.size) break
        total += c
        start = i
    }
    var s = start
    while (s < msgs.size && !(msgs[s].role == CanonRole.USER && msgs[s].parts.all { it is Part.Text })) s++
    return if (s >= msgs.size) msgs else msgs.subList(s, msgs.size)
}

fun Turn.toCanon(): CanonMsg =
    CanonMsg(if (role == Role.USER) CanonRole.USER else CanonRole.ASSISTANT, listOf(Part.Text(text)))

fun flattenForFallback(msgs: List<CanonMsg>, resultCap: Int = 400): List<Turn> {
    val out = ArrayList<Turn>()
    for (m in msgs) {
        val role = if (m.role == CanonRole.ASSISTANT) Role.ASSISTANT else Role.USER
        val text = m.parts.joinToString("\n") { p ->
            when (p) {
                is Part.Text -> p.text
                is Part.ToolCall -> "[đã gọi công cụ " + p.name + " " + p.args + "]"
                is Part.ToolResult -> "[kết quả công cụ" + (if (p.isError) " (lỗi)" else "") + ": " + capText(p.content, resultCap) + "]"
            }
        }
        val last = out.lastOrNull()
        if (last != null && last.role == role) out[out.lastIndex] = Turn(role, last.text + "\n\n" + text) else out.add(Turn(role, text))
    }
    return out
}
