package com.zyqo.app

class Sym(val name: String, val kind: String, val path: String, val line: Int)

class CodeIndex(val syms: List<Sym>) {
    val byName: Map<String, List<Sym>> = syms.groupBy { it.name }

    fun find(name: String): List<Sym> {
        val exact = byName[name]
        if (exact != null) return exact
        val low = name.lowercase()
        return syms.filter { it.name.lowercase() == low }
    }

    fun closest(name: String, limit: Int = 3): List<String> {
        val low = name.lowercase()
        return byName.keys
            .filter { k ->
                val kl = k.lowercase()
                kl.contains(low) || (kl.length >= 4 && low.contains(kl)) || editDistance(kl, low) <= 2
            }
            .sortedBy { Math.abs(it.length - name.length) }
            .take(limit)
    }
}

private fun editDistance(a: String, b: String): Int {
    if (Math.abs(a.length - b.length) > 2) return 99
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1)
        cur[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
        }
        prev = cur
    }
    return prev[b.length]
}

private val FN_RE = Regex("""\b(?:fun|function|def|func|fn)\s+(?:<[^>]*>\s*)?(?:[A-Za-z_]\w*(?:<[^>]*>)?\.)*([A-Za-z_]\w*)\s*\(""")
private val GO_RE = Regex("""\bfunc\s+\([^)]*\)\s*([A-Za-z_]\w*)\s*\(""")
private val CLASS_RE = Regex(
    """^\s*(?:(?:public|private|protected|internal|open|abstract|final|sealed|data|enum|inline|value|annotation|export|default|static|partial|case|expect|actual)\s+)*(class|interface|object|struct|trait|record|typealias|type|protocol|enum)\s+([A-Za-z_]\w*)"""
)
private val VAL_RE = Regex(
    """^\s{0,4}(?:(?:private|public|internal|protected|const|lateinit|override|static|final|export)\s+)*(?:val|var|const|let)\s+([A-Za-z_]\w*)"""
)

private val CODE_EXT = setOf(
    "kt", "kts", "java", "js", "jsx", "ts", "tsx", "py", "go", "rs", "c", "cc", "cpp", "h", "hpp", "cs", "rb", "php", "swift", "dart", "scala", "lua"
)

fun isCodePath(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in CODE_EXT

fun buildIndex(files: Map<String, String>): CodeIndex {
    val out = ArrayList<Sym>()
    for ((path, text) in files) {
        if (!isCodePath(path)) continue
        var n = 0
        for (line in text.lineSequence()) {
            n++
            val t = line.trimStart()
            if (t.isEmpty() || t.startsWith("//") || t.startsWith("#") || t.startsWith("*") || t.startsWith("/*")) continue
            if (line.length > 400) continue
            val c = CLASS_RE.find(line)
            if (c != null) out.add(Sym(c.groupValues[2], kindOf(c.groupValues[1]), path, n))
            val f = FN_RE.find(line) ?: GO_RE.find(line)
            if (f != null) out.add(Sym(f.groupValues[1], "hàm", path, n))
            if (c == null && f == null) {
                val v = VAL_RE.find(line)
                if (v != null) out.add(Sym(v.groupValues[1], "giá trị", path, n))
            }
        }
    }
    return CodeIndex(out)
}

private fun kindOf(kw: String): String = when (kw) {
    "interface", "protocol", "trait" -> "giao diện"
    "object" -> "đối tượng"
    "typealias", "type" -> "kiểu"
    else -> "lớp"
}

fun findSymbolText(index: CodeIndex, name: String, kind: String): String {
    val hits = index.find(name.trim()).filter { kind.isBlank() || it.kind == kind }
    if (hits.isEmpty()) {
        val near = index.closest(name.trim())
        val hint = if (near.isEmpty()) "" else " Tên gần giống: " + near.joinToString(", ") + "."
        return "Không thấy khai báo nào tên $name trong chỉ mục.$hint Dùng grep để tìm nơi gọi hoặc nhắc đến."
    }
    val sb = StringBuilder()
    for (s in hits.take(20)) sb.append(s.path).append(':').append(s.line).append(": ").append(s.kind).append(' ').append(s.name).append('\n')
    if (hits.size > 20) sb.append("... và ").append(hits.size - 20).append(" khai báo nữa")
    return sb.toString().trimEnd()
}
