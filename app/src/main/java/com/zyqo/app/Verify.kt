package com.zyqo.app

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.xml.sax.InputSource

private val BRACKET_EXT = setOf("kt", "kts", "java", "gradle", "c", "h", "cpp", "hpp", "cs", "go", "swift", "dart", "rs")
private val PLACEHOLDER_EXT = BRACKET_EXT + setOf("js", "jsx", "ts", "tsx", "py", "rb", "php")
private val PLACEHOLDER_RE = Regex(
    "^\\s*(?://|#|/\\*|<!--)?\\s*\\.\\.\\.\\s*(?:existing|rest|unchanged|giữ nguyên|phần còn lại)",
    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
)
private val CONFLICT_RE = Regex("^(?:<<<<<<<|>>>>>>>) ", RegexOption.MULTILINE)

private fun charLiteralEnd(t: String, i: Int): Int {
    if (i + 2 >= t.length) return -1
    if (t[i + 1] == '\\') {
        val e = t.indexOf('\'', i + 3)
        if (e < 0 || e > i + 9) return -1
        return if (t.substring(i, e).contains('\n')) -1 else e
    }
    return if (t[i + 2] == '\'') i + 2 else -1
}

private fun bracketProblems(t: String): List<String> {
    var curly = 0
    var paren = 0
    var square = 0
    var i = 0
    val n = t.length
    while (i < n) {
        val c = t[i]
        val nx = if (i + 1 < n) t[i + 1] else ' '
        when {
            c == '/' && nx == '/' -> {
                while (i < n && t[i] != '\n') i++
                continue
            }
            c == '/' && nx == '*' -> {
                val e = t.indexOf("*/", i + 2)
                i = if (e < 0) n else e + 2
                continue
            }
            c == '"' -> {
                if (t.startsWith("\"\"\"", i)) {
                    val e = t.indexOf("\"\"\"", i + 3)
                    i = if (e < 0) n else e + 3
                    continue
                }
                i++
                while (i < n && t[i] != '"' && t[i] != '\n') {
                    if (t[i] == '\\') i++
                    i++
                }
                i++
                continue
            }
            c == '`' -> {
                val e = t.indexOf('`', i + 1)
                i = if (e < 0) n else e + 1
                continue
            }
            c == '\'' -> {
                val e = charLiteralEnd(t, i)
                if (e > 0) {
                    i = e + 1
                    continue
                }
            }
            c == '{' -> curly++
            c == '}' -> curly--
            c == '(' -> paren++
            c == ')' -> paren--
            c == '[' -> square++
            c == ']' -> square--
        }
        i++
    }
    val out = ArrayList<String>()
    if (curly != 0) out.add("số { và } lệch nhau ($curly)")
    if (paren != 0) out.add("số ( và ) lệch nhau ($paren)")
    if (square != 0) out.add("số [ và ] lệch nhau ($square)")
    return out
}

private fun xmlProblem(text: String): String? {
    return try {
        val f = DocumentBuilderFactory.newInstance()
        try {
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (e: Exception) {
        }
        val b = f.newDocumentBuilder()
        b.setErrorHandler(null)
        b.parse(InputSource(StringReader(text)))
        null
    } catch (e: Exception) {
        "XML không hợp lệ: " + (e.message ?: "").lineSequence().first().take(100)
    }
}

private fun jsonProblem(text: String): String? {
    return try {
        val t = text.trim()
        if (t.startsWith("[")) JSONArray(t) else JSONObject(t)
        null
    } catch (e: JSONException) {
        "JSON không hợp lệ: " + (e.message ?: "").take(100)
    }
}

fun verifyText(path: String, text: String): List<String> {
    val out = ArrayList<String>()
    val ext = path.substringAfterLast('.', "").lowercase()
    if (CONFLICT_RE.containsMatchIn(text)) out.add("còn dấu xung đột merge")
    if (ext in PLACEHOLDER_EXT && PLACEHOLDER_RE.containsMatchIn(text)) out.add("có dòng viết tắt kiểu '... existing', file bị thiếu nội dung")
    if (ext in BRACKET_EXT) out.addAll(bracketProblems(text))
    if (ext == "json") jsonProblem(text)?.let { out.add(it) }
    if (ext == "xml") xmlProblem(text)?.let { out.add(it) }
    return out
}

fun verifyPath(p: ProjectData, path: String): List<String> {
    val text = p.files[path] ?: return emptyList()
    return verifyText(path, text).map { "$path: $it" }
}
