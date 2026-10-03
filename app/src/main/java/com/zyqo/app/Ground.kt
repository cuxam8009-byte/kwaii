package com.zyqo.app

private val PATH_REF = Regex("""([A-Za-z0-9_./-]+\.(?:kt|kts|java|js|jsx|ts|tsx|py|go|rs|c|cc|cpp|h|hpp|cs|rb|php|swift|dart|xml|json|yml|yaml|toml|gradle|md|html|css|sql|sh))(?::(\d+))?""")
private val URL_RE = Regex("""https?://\S+""")
private val TICK_RE = Regex("""`([^`\n]{2,80})`""")
private val IDENT_RE = Regex("""^([A-Za-z_][A-Za-z0-9_]*)(?:\(.*\))?$""")
private val SPACES = Regex("""\s+""")
private val PROPOSE = listOf(
    "nên thêm", "đề xuất", "tạo mới", "thêm hàm", "sẽ thêm", "thêm một", "gợi ý", "ví dụ", "chẳng hạn",
    "đổi tên thành", "đặt tên", "tên mới", "sửa thành", "thay bằng", "e.g"
)

private class Fence(val before: String, val body: List<String>)

private fun scan(answer: String): Pair<String, List<Fence>> {
    val prose = StringBuilder()
    val fences = ArrayList<Fence>()
    var inFence = false
    var buf = ArrayList<String>()
    var prev = ""
    var before = ""
    for (line in answer.lines()) {
        if (line.trimStart().startsWith("```")) {
            if (!inFence) {
                inFence = true
                buf = ArrayList()
                before = prev
            } else {
                inFence = false
                fences.add(Fence(before, buf))
            }
            continue
        }
        if (inFence) {
            buf.add(line)
        } else {
            prose.append(line).append('\n')
            if (line.isNotBlank()) prev = line
        }
    }
    return Pair(prose.toString(), fences)
}

fun resolvePath(proj: ProjectData, ref: String): String? {
    val r = ref.trimStart('/')
    if (proj.paths.contains(r)) return r
    val name = r.substringAfterLast('/')
    val cands = proj.paths.filter { it == name || it.endsWith("/$name") }
    if (cands.isEmpty()) return null
    if (r.contains('/')) return cands.firstOrNull { it.endsWith(r) }
    return cands.first()
}

private fun looksProjectSpecific(id: String): Boolean {
    if (id.length < 5) return false
    var upperAfterLower = false
    for (i in 1 until id.length) if (id[i].isUpperCase() && id[i - 1].isLowerCase()) upperAfterLower = true
    return upperAfterLower || id.contains('_')
}

private fun normLine(s: String): String = SPACES.replace(s.trim(), " ")

fun verifyAnswer(answer: String, proj: ProjectData, appliedEdits: Boolean = false): List<String> {
    val problems = LinkedHashSet<String>()
    val (prose, fences) = scan(answer)
    val clean = URL_RE.replace(prose, " ")
    var symbolBudget = 12
    for (line in clean.lines()) {
        val low = line.lowercase()
        val proposing = PROPOSE.any { low.contains(it) }
        val idents = ArrayList<String>()
        val symbols = ArrayList<String>()
        for (m in TICK_RE.findAll(line)) {
            val tok = m.groupValues[1].trim()
            if (PATH_REF.matches(tok)) continue
            val im = IDENT_RE.find(tok) ?: continue
            val id = im.groupValues[1]
            idents.add(id)
            if (looksProjectSpecific(id)) symbols.add(id)
        }
        for (m in PATH_REF.findAll(line)) {
            val ref = m.groupValues[1]
            val resolved = resolvePath(proj, ref)
            if (resolved == null) {
                problems.add("file không có trong dự án: $ref")
                continue
            }
            val ln = m.groupValues[2].toIntOrNull() ?: continue
            val content = proj.files[resolved] ?: continue
            val fl = content.lines()
            if (ln < 1 || ln > fl.size) {
                problems.add("$resolved:$ln vượt quá số dòng của file (${fl.size})")
                continue
            }
            if (idents.isNotEmpty()) {
                val from = maxOf(ln - 2, 0)
                val to = minOf(ln, fl.size - 1)
                val window = fl.subList(from, to + 1).joinToString("\n")
                if (idents.none { window.contains(it) }) {
                    problems.add("$resolved:$ln không chứa " + idents.take(3).joinToString(" hoặc "))
                }
            }
        }
        if (!proposing && !appliedEdits) {
            for (id in symbols) {
                if (symbolBudget <= 0) break
                symbolBudget--
                if (proj.index().find(id).isNotEmpty()) continue
                if (proj.files.values.any { it.contains(id) }) continue
                problems.add("định danh $id không có trong dự án")
            }
        }
    }
    if (!appliedEdits) {
        for (f in fences) {
            val bl = f.before.lowercase()
            if (PROPOSE.any { bl.contains(it) } || bl.contains("như sau")) continue
            val ref = PATH_REF.find(URL_RE.replace(f.before, " ")) ?: continue
            val resolved = resolvePath(proj, ref.groupValues[1]) ?: continue
            val content = proj.files[resolved] ?: continue
            val quoted = f.body.map { normLine(it) }.filter { it.length >= 8 }
            if (quoted.size < 3) continue
            val fileLines = HashSet<String>()
            for (l in content.lines()) fileLines.add(normLine(l))
            val missing = quoted.count { it !in fileLines }
            if (missing * 2 > quoted.size) problems.add("đoạn code trích từ $resolved không khớp nội dung file")
        }
    }
    return problems.take(8)
}

fun fixPrompt(problems: List<String>): String {
    val sb = StringBuilder("Kiểm tra tự động: các tham chiếu sau không xác minh được trong dự án:\n")
    for (p in problems) sb.append("- ").append(p).append('\n')
    sb.append("Hãy dùng công cụ (find_symbol, grep, read_file) để kiểm tra, rồi viết lại câu trả lời chỉ với điều đã thấy. Nếu không tìm thấy thì nói rõ là không tìm thấy, không nêu file hay dòng.")
    return sb.toString()
}
