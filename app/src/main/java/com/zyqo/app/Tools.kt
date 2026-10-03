package com.zyqo.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal val CALL_RE = Regex("<call tool=\"([A-Za-z0-9_.-]+)\">(.*?)</call>", RegexOption.DOT_MATCHES_ALL)

class Param(val name: String, val type: String, val required: Boolean)

class ToolSpec(
    val name: String,
    val summary: String,
    val params: List<Param>,
    val readOnly: Boolean,
    val origin: String,
    val remote: String = name,
    val detail: String = summary
) {
    val signature: String
        get() = name + "(" + params.joinToString(", ") { it.name + (if (it.required) "" else "?") + ":" + it.type } + ")"
}

class RawCall(val name: String, val args: String)

class Pending(val spec: ToolSpec, val args: String, val gate: CompletableDeferred<Boolean>)

fun rawCalls(text: String): List<RawCall> =
    CALL_RE.findAll(text).map { RawCall(it.groupValues[1], it.groupValues[2].trim()) }.toList()

fun capText(s: String, cap: Int): String = if (s.length <= cap) s else s.take(cap) + "\n[cắt bớt ${s.length - cap} ký tự]"

fun catalogText(specs: List<ToolSpec>): String {
    if (specs.isEmpty()) return ""
    val compact = specs.size > 12
    return specs.joinToString("\n") {
        if (compact && it.origin != "local") "- " + it.name else "- " + it.signature + ": " + it.summary
    } + if (compact) "\nCông cụ chỉ ghi tên: gọi tool_help với {\"name\":\"...\"} để xem tham số." else ""
}

fun compactOld(turns: MutableList<Turn>, from: Int, keep: Int) {
    val end = turns.size - keep
    for (i in from until end) {
        val t = turns[i]
        if (t.text.length > 400) turns[i] = Turn(t.role, t.text.take(300) + "\n[…đã rút gọn]")
    }
}

private class GrepTimeout : RuntimeException()

private class DeadlineSeq(private val s: CharSequence, private val deadline: Long) : CharSequence {
    override val length: Int get() = s.length
    override fun get(index: Int): Char {
        if (System.nanoTime() > deadline) throw GrepTimeout()
        return s[index]
    }
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = DeadlineSeq(s.subSequence(startIndex, endIndex), deadline)
    override fun toString(): String = s.toString()
}

object LocalTools {
    val help = ToolSpec("tool_help", "xem mô tả và tham số của một công cụ", listOf(Param("name", "string", true)), true, "local")

    val specs: List<ToolSpec> = listOf(
        ToolSpec("list_files", "liệt kê file, lọc theo tiền tố đường dẫn", listOf(Param("prefix", "string", false), Param("max", "integer", false)), true, "local"),
        ToolSpec(
            "grep", "tìm regex trong nội dung file, trả về path:dòng: nội dung",
            listOf(Param("pattern", "string", true), Param("path", "string", false), Param("max", "integer", false), Param("ignore_case", "boolean", false), Param("context", "integer", false)),
            true, "local"
        ),
        ToolSpec("read_file", "đọc file theo khoảng dòng, dòng đầu là 1", listOf(Param("path", "string", true), Param("start", "integer", false), Param("end", "integer", false)), true, "local"),
        ToolSpec("validate", "kiểm tra cú pháp cơ bản các file đã sửa hoặc một file", listOf(Param("path", "string", false)), true, "local"),
        help
    ) + EditTools.specs

    private fun clean(p: String): String = p.trim().trimStart('/')

    fun listFiles(p: ProjectData, prefix: String, max: Int): String {
        val pre = clean(prefix)
        val all = p.paths.filter { it.startsWith(pre) }
        if (all.isEmpty()) return "Không có file nào."
        val shown = all.take(max.coerceIn(1, 200))
        val sb = StringBuilder()
        shown.forEach { sb.append(it).append('\n') }
        if (all.size > shown.size) sb.append("... và ").append(all.size - shown.size).append(" file nữa")
        return sb.toString().trimEnd()
    }

    fun grep(p: ProjectData, pattern: String, prefix: String, max: Int, ignoreCase: Boolean): String {
        val re = try {
            Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        } catch (e: Exception) {
            return "Regex không hợp lệ: " + (e.message ?: "").lineSequence().first()
        }
        val deadline = System.nanoTime() + 1_500_000_000L
        val limit = max.coerceIn(1, 80)
        val pre = clean(prefix)
        val sb = StringBuilder()
        var found = 0
        var timeout = false
        try {
            outer@ for ((path, text) in p.files) {
                if (!path.startsWith(pre)) continue
                var ln = 0
                for (line in text.lineSequence()) {
                    ln++
                    if (System.nanoTime() > deadline) {
                        timeout = true
                        break@outer
                    }
                    if (re.containsMatchIn(DeadlineSeq(line, deadline))) {
                        sb.append(path).append(':').append(ln).append(": ").append(line.trim().take(160)).append('\n')
                        found++
                        if (found >= limit) break@outer
                    }
                }
            }
        } catch (e: GrepTimeout) {
            timeout = true
        }
        if (found == 0 && !timeout) return "Không thấy kết quả."
        if (found >= limit) sb.append("(đã đạt giới hạn ").append(limit).append(" kết quả)")
        if (timeout) sb.append("(dừng vì quá thời gian, kết quả có thể thiếu)")
        return sb.toString().trimEnd()
    }

    private val HIT = Regex("^(\\S+?):(\\d+): ")

    fun grepWithContext(p: ProjectData, pattern: String, prefix: String, max: Int, ignoreCase: Boolean, context: Int): String {
        val base = grep(p, pattern, prefix, max, ignoreCase)
        if (context <= 0 || base.startsWith("Không thấy") || base.startsWith("Regex")) return base
        val sb = StringBuilder()
        var lastPath = ""
        var lastEnd = 0
        for (line in base.lines()) {
            val m = HIT.find(line)
            if (m == null) {
                sb.append(line).append('\n')
                continue
            }
            val path = m.groupValues[1]
            val ln = m.groupValues[2].toIntOrNull() ?: continue
            val lines = p.files[path]?.lines() ?: continue
            val from = maxOf(ln - context, if (path == lastPath) lastEnd + 1 else 1)
            val to = minOf(ln + context, lines.size)
            if (from > to) continue
            if (path != lastPath || from > lastEnd + 1) sb.append("--\n")
            for (k in from..to) {
                sb.append(path).append(if (k == ln) ":" else "-").append(k).append(if (k == ln) ": " else "- ")
                sb.append(lines[k - 1].trim().take(160)).append('\n')
            }
            lastPath = path
            lastEnd = to
        }
        return sb.toString().trimEnd()
    }

    fun readFile(p: ProjectData, path: String, start: Int, end: Int): String {
        val key = clean(path)
        val text = p.files[key] ?: return "Không đọc được file: $path"
        p.seen.add(key)
        val lines = text.lines()
        val s = start.coerceAtLeast(1)
        if (s > lines.size) return "$key chỉ có ${lines.size} dòng."
        val e = maxOf(minOf(if (end <= 0) s + 199 else end, s + 399, lines.size), s)
        val clipped = e < lines.size && (end <= 0 || end > e)
        val hint = if (clipped) "\n(còn ${lines.size - e} dòng, đọc tiếp bằng start=${e + 1})" else ""
        return "$key [dòng $s-$e/${lines.size}]\n" + lines.subList(s - 1, e).joinToString("\n") + hint
    }

    fun validate(p: ProjectData, path: String): String {
        val targets = if (path.isBlank()) p.changed.keys.sorted() else listOf(clean(path))
        if (targets.isEmpty()) return "Chưa có file nào được sửa."
        val probs = targets.flatMap { verifyPath(p, it) }
        return if (probs.isEmpty()) "Không phát hiện lỗi cú pháp cơ bản ở ${targets.size} file." else probs.joinToString("\n")
    }
}

private fun typeOk(type: String, v: Any): Boolean = when (type) {
    "string" -> v is String
    "integer" -> v is Int || v is Long || (v is String && v.toLongOrNull() != null)
    "number" -> v is Number || (v is String && v.toDoubleOrNull() != null)
    "boolean" -> v is Boolean || v == "true" || v == "false"
    "array" -> v is JSONArray
    "object" -> v is JSONObject
    else -> true
}

fun validateArgs(spec: ToolSpec, args: JSONObject): String? {
    for (p in spec.params) {
        if (!args.has(p.name) || args.isNull(p.name)) {
            if (p.required) return "thiếu tham số ${p.name}"
            continue
        }
        if (!typeOk(p.type, args.get(p.name))) return "tham số ${p.name} phải là ${p.type}"
    }
    return null
}

class ToolHost(private val hub: McpHub) {
    private val allowed = HashSet<String>()

    fun specs(proj: ProjectData?): List<ToolSpec> {
        val mcp = hub.specs()
        if (proj == null && mcp.isEmpty()) return emptyList()
        return (if (proj != null) LocalTools.specs else listOf(LocalTools.help)) + mcp
    }

    fun allow(name: String) {
        allowed.add(name)
    }

    private fun wrap(name: String, body: String): String = "<tool_result name=\"$name\">\n$body\n</tool_result>"

    suspend fun call(
        c: RawCall,
        proj: ProjectData?,
        specs: List<ToolSpec>,
        cap: Int,
        approve: suspend (ToolSpec, String) -> Boolean
    ): String {
        val spec = specs.firstOrNull { it.name == c.name } ?: return wrap(c.name, "Không có công cụ này. Xem danh mục công cụ.")
        val args = try {
            if (c.args.isBlank()) JSONObject() else JSONObject(c.args)
        } catch (e: JSONException) {
            return wrap(c.name, "Tham số không phải JSON hợp lệ. Dạng đúng: " + spec.signature)
        }
        validateArgs(spec, args)?.let { return wrap(c.name, "Sai tham số: $it. Dạng đúng: " + spec.signature) }
        val gated = spec.origin != "local" && !spec.readOnly && spec.name !in allowed && !hub.trusted(spec)
        if (gated && !approve(spec, args.toString())) return wrap(c.name, "Người dùng từ chối chạy công cụ này.")
        val body = withContext(Dispatchers.IO) {
            try {
                execute(spec, args, proj, specs)
            } catch (e: Exception) {
                "Lỗi: " + (e.message ?: e.javaClass.simpleName)
            }
        }
        return wrap(c.name, capText(body, cap))
    }

    private fun execute(spec: ToolSpec, args: JSONObject, proj: ProjectData?, specs: List<ToolSpec>): String {
        if (spec.origin != "local") return hub.call(spec, args)
        if (spec.name == "tool_help") {
            val n = args.optString("name", "")
            val s = specs.firstOrNull { it.name == n } ?: return "Không có công cụ $n."
            return s.signature + "\n" + s.detail
        }
        val p = proj ?: return "Cần mở dự án trước."
        return when (spec.name) {
            "list_files" -> LocalTools.listFiles(p, args.optString("prefix", ""), args.optInt("max", 80))
            "grep" -> LocalTools.grepWithContext(p, args.getString("pattern"), args.optString("path", ""), args.optInt("max", 30), args.optBoolean("ignore_case", false), args.optInt("context", 0).coerceIn(0, 3))
            "note_add" -> EditTools.noteAdd(p, args.getString("text"))
            "note_list" -> EditTools.noteList(p)
            "read_file" -> LocalTools.readFile(p, args.getString("path"), args.optInt("start", 1), args.optInt("end", 0))
            "validate" -> LocalTools.validate(p, args.optString("path", ""))
            "find_symbol" -> findSymbolText(p.index(), args.getString("name"), args.optString("kind", ""))
            "edit_file" -> EditTools.editFile(p, args.getString("path"), args.getString("old_string"), args.getString("new_string"), args.optBoolean("replace_all", false))
            "write_file" -> EditTools.writeFile(p, args.getString("path"), args.getString("content"))
            "delete_file" -> EditTools.deleteFile(p, args.getString("path"))
            else -> "Công cụ chưa được cài đặt."
        }
    }
}
