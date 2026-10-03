package com.zyqo.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.TreeSet
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private const val MAX_ZIP = 80L * 1024 * 1024
private const val MAX_FILE = 300 * 1024
private const val MAX_CHARS = 4_000_000

private val IGNORED_DIRS = setOf(
    "node_modules", ".git", "build", "dist", ".gradle", ".idea", "__pycache__",
    "venv", ".venv", "target", ".next", "Pods", ".dart_tool", "out"
)
private val SKIP_NAMES = setOf("package-lock.json", "yarn.lock", "pnpm-lock.yaml", "gradlew.bat")
private val SPECIAL_TEXT = setOf("dockerfile", "makefile", "gradlew", "procfile")
private val TEXT_EXT = setOf(
    "kt", "kts", "java", "js", "jsx", "mjs", "cjs", "ts", "tsx", "json", "xml", "html", "htm", "css", "scss",
    "md", "txt", "py", "rb", "go", "rs", "c", "h", "cpp", "hpp", "cs", "swift", "php", "sql", "sh", "yml",
    "yaml", "toml", "gradle", "properties", "gitignore", "dart", "vue", "svelte", "lua", "r", "bat", "ini",
    "cfg", "conf", "csv", "tsv", "proto", "graphql", "pro"
)

private val FILE_RE = Regex("<file path=\"([^\"]+)\">\\r?\\n?(.*?)\\r?\\n?</file>", RegexOption.DOT_MATCHES_ALL)
private val DEL_RE = Regex("<delete path=\"([^\"]+)\"\\s*/>")
private val READ_RE = Regex("<read path=\"([^\"]+)\"\\s*/>")
private val EDIT_RE = Regex("<edit path=\"([^\"]+)\">(.*?)</edit>", RegexOption.DOT_MATCHES_ALL)
private val PAIR_RE = Regex("<find>\\r?\\n?(.*?)\\r?\\n?</find>\\s*<replace>\\r?\\n?(.*?)\\r?\\n?</replace>", RegexOption.DOT_MATCHES_ALL)
private val BLANKS_RE = Regex("\\n{3,}")
const val OPEN_TAG = "<file path=\""
const val EDIT_TAG = "<edit path=\""

fun stripBlocks(text: String): String {
    var t = FILE_RE.replace(text, "")
    t = EDIT_RE.replace(t, "")
    t = DEL_RE.replace(t, "")
    t = READ_RE.replace(t, "")
    t = SEARCH_RE.replace(t, "")
    t = FETCH_RE.replace(t, "")
    t = CALL_RE.replace(t, "")
    for ((open, close) in listOf(OPEN_TAG to "</file>", EDIT_TAG to "</edit>", "<search q=\"" to "/>", "<fetch url=\"" to "/>", "<call tool=\"" to "</call>")) {
        val i = t.lastIndexOf(open)
        if (i >= 0 && t.indexOf(close, i) < 0) t = t.substring(0, i)
    }
    return t.replace(BLANKS_RE, "\n\n").trim()
}

fun writingPath(text: String): String? {
    var best = -1
    var close = ""
    var open = ""
    for ((o, c) in listOf(OPEN_TAG to "</file>", EDIT_TAG to "</edit>")) {
        val i = text.lastIndexOf(o)
        if (i > best && text.indexOf(c, i) < 0) {
            best = i
            close = c
            open = o
        }
    }
    if (best < 0 || close.isEmpty()) return null
    val s = best + open.length
    val e = text.indexOf('"', s)
    return if (e > s) text.substring(s, e) else ""
}

fun hasBlocks(text: String): Boolean =
    text.contains(OPEN_TAG) || text.contains(EDIT_TAG) || text.contains("<read path=\"") || text.contains("<delete path=\"") ||
        text.contains("<search q=\"") || text.contains("<fetch url=\"") || text.contains("<call tool=\"")

fun readRequests(text: String): List<String> = READ_RE.findAll(text).map { it.groupValues[1] }.toList()

fun cleanPath(p: String): String? {
    val s = p.trim().replace('\\', '/').trimStart('/')
    if (s.isEmpty()) return null
    if (s.split('/').any { it == ".." || it == "." || it.isEmpty() }) return null
    return s
}

private fun unfence(c: String): String {
    val t = c.trim('\n', '\r')
    if (!t.startsWith("```") || !t.endsWith("```") || t.length < 6) return c
    val nl = t.indexOf('\n')
    if (nl < 0) return c
    return t.substring(nl + 1, t.length - 3).trimEnd('\n', '\r')
}

private fun applyPairs(original: String, body: String): Pair<String?, String?> {
    val crlf = original.contains("\r\n")
    var cur = if (crlf) original.replace("\r\n", "\n") else original
    val pairs = PAIR_RE.findAll(body).toList()
    if (pairs.isEmpty()) return null to "khối edit không có cặp find/replace hợp lệ"
    for ((n, m) in pairs.withIndex()) {
        val find = m.groupValues[1]
        val rep = m.groupValues[2]
        if (find.isEmpty()) return null to "cặp ${n + 1}: find rỗng"
        val first = cur.indexOf(find)
        if (first < 0) return null to "cặp ${n + 1}: không tìm thấy đoạn find"
        if (cur.indexOf(find, first + 1) >= 0) return null to "cặp ${n + 1}: đoạn find xuất hiện nhiều lần"
        cur = cur.substring(0, first) + rep + cur.substring(first + find.length)
    }
    return (if (crlf) cur.replace("\n", "\r\n") else cur) to null
}

private class Snap(
    val files: Map<String, String>,
    val changed: Map<String, String>,
    val deleted: Set<String>,
    val paths: Set<String>
)

class ApplyResult(val changes: List<String>, val errors: List<String>, val touched: List<String> = emptyList())

class ProjectData(
    val name: String,
    val source: File,
    val files: LinkedHashMap<String, String>,
    val paths: TreeSet<String>,
    val skipped: Int
) {
    val changed = HashMap<String, String>()
    val deleted = HashSet<String>()
    private val history = ArrayDeque<Snap>()
    val seen = HashSet<String>()
    val notes = ArrayList<String>()
    var notesDirty = false
    var notesLoaded = false
    val pendingChanges = ArrayList<String>()
    val pendingTouched = LinkedHashSet<String>()

    @Volatile
    var indexCache: CodeIndex? = null

    fun index(): CodeIndex {
        val c = indexCache
        if (c != null) return c
        val b = buildIndex(files)
        indexCache = b
        return b
    }

    fun checkpoint() {
        history.addLast(Snap(HashMap(files), HashMap(changed), HashSet(deleted), TreeSet(paths)))
        if (history.size > 10) history.removeFirst()
    }

    fun drain(): ApplyResult {
        val r = ApplyResult(pendingChanges.distinct(), emptyList(), pendingTouched.toList())
        pendingChanges.clear()
        pendingTouched.clear()
        return r
    }

    val canUndo: Boolean get() = history.isNotEmpty()

    fun snapshot(budget: Int, embed: Boolean = true): String {
        val sb = StringBuilder()
        sb.append("DỰ ÁN: ").append(name).append('\n')
        sb.append("Cây file (").append(paths.size).append(" file):\n")
        val treeCap = budget / 4
        var used = 0
        var listed = 0
        for (p in paths) {
            if (used + p.length + 1 > treeCap) break
            sb.append(p).append('\n')
            used += p.length + 1
            listed++
        }
        if (listed < paths.size) sb.append("... và ").append(paths.size - listed).append(" file khác\n")
        if (!embed) {
            sb.append("\nNội dung file chưa nạp sẵn để tiết kiệm token. Dùng grep, read_file hoặc <read path=\"...\"/> để xem đúng phần cần.\n")
            if (skipped > 0) sb.append("\n(Đã bỏ qua ").append(skipped).append(" file quá lớn.)")
            return sb.toString()
        }
        sb.append("\nNội dung các file văn bản đã nạp:\n")
        var left = budget - used
        val missing = ArrayList<String>()
        for ((p, c) in files) {
            val cost = c.length + p.length + 24
            if (cost > left) {
                missing.add(p)
                continue
            }
            left -= cost
            seen.add(p)
            sb.append(OPEN_TAG).append(p).append("\">\n").append(c).append("\n</file>\n")
        }
        if (missing.isNotEmpty()) {
            sb.append("\nCác file văn bản chưa nạp (").append(missing.size).append("), đọc bằng <read path=\"...\"/>:\n")
            missing.take(80).forEach { sb.append(it).append('\n') }
            if (missing.size > 80) sb.append("... và ").append(missing.size - 80).append(" file khác\n")
        }
        if (skipped > 0) sb.append("\n(Đã bỏ qua ").append(skipped).append(" file quá lớn.)")
        return sb.toString()
    }

    fun readBack(requested: List<String>, budget: Int): String {
        val sb = StringBuilder()
        var left = budget
        for (raw in requested.distinct()) {
            val p = cleanPath(raw)
            val c = if (p == null) null else files[p]
            if (p == null || c == null) {
                sb.append("Không đọc được file: ").append(raw).append('\n')
                continue
            }
            if (left <= 0) {
                sb.append("Hết giới hạn đọc, chưa nạp: ").append(p).append('\n')
                continue
            }
            val part = if (c.length > left) c.substring(0, left) else c
            if (part.length == c.length) seen.add(p)
            sb.append(OPEN_TAG).append(p).append("\">\n").append(part).append("\n</file>\n")
            if (part.length < c.length) sb.append("(đã cắt bớt ").append(c.length - part.length).append(" ký tự cuối)\n")
            left -= part.length
        }
        return sb.toString()
    }

    fun apply(text: String): ApplyResult {
        val fr = FILE_RE.findAll(text).toList()
        val er = EDIT_RE.findAll(text).toList()
        val dr = DEL_RE.findAll(text).toList()
        val out = ArrayList<String>()
        val errors = ArrayList<String>()
        val touched = ArrayList<String>()
        if (fr.isEmpty() && er.isEmpty() && dr.isEmpty()) return ApplyResult(out, errors)
        val snap = Snap(HashMap(files), HashMap(changed), HashSet(deleted), TreeSet(paths))
        for (m in fr) {
            val p = cleanPath(m.groupValues[1])
            if (p == null) {
                errors.add("Đường dẫn không hợp lệ: " + m.groupValues[1])
                continue
            }
            val existed = paths.contains(p)
            val c = unfence(m.groupValues[2])
            files[p] = c
            changed[p] = c
            deleted.remove(p)
            paths.add(p)
            touched.add(p)
            out.add((if (existed) "Sửa " else "Tạo ") + p)
        }
        for (m in er) {
            val p = cleanPath(m.groupValues[1])
            if (p == null) {
                errors.add("Đường dẫn không hợp lệ: " + m.groupValues[1])
                continue
            }
            val cur = files[p]
            if (cur == null) {
                errors.add("$p: chưa nạp nội dung, cần đọc bằng <read> trước khi sửa")
                continue
            }
            val (res, err) = applyPairs(cur, m.groupValues[2])
            if (res == null) {
                errors.add("$p: $err")
                continue
            }
            files[p] = res
            changed[p] = res
            touched.add(p)
            out.add("Sửa $p")
        }
        for (m in dr) {
            val p = cleanPath(m.groupValues[1]) ?: continue
            if (!paths.contains(p)) continue
            files.remove(p)
            changed.remove(p)
            deleted.add(p)
            paths.remove(p)
            out.add("Xóa $p")
        }
        if (out.isNotEmpty()) {
            indexCache = null
            history.addLast(snap)
            if (history.size > 10) history.removeFirst()
        }
        return ApplyResult(out.distinct(), errors, touched.distinct())
    }

    fun undo(): Boolean {
        val s = history.removeLastOrNull() ?: return false
        indexCache = null
        files.clear(); files.putAll(s.files)
        changed.clear(); changed.putAll(s.changed)
        deleted.clear(); deleted.addAll(s.deleted)
        paths.clear(); paths.addAll(s.paths)
        return true
    }

    private fun bytesOf(c: String): ByteArray = (if (c.endsWith("\n")) c else c + "\n").toByteArray(Charsets.UTF_8)

    fun export(out: OutputStream) {
        ZipOutputStream(out.buffered()).use { zo ->
            val written = HashSet<String>()
            ZipFile(source).use { zf ->
                val seen = HashSet<String>()
                for (e in zf.entries()) {
                    val n = e.name
                    if (!seen.add(n)) continue
                    if (e.isDirectory) {
                        zo.putNextEntry(ZipEntry(n))
                        zo.closeEntry()
                        continue
                    }
                    if (deleted.contains(n)) continue
                    zo.putNextEntry(ZipEntry(n))
                    val c = changed[n]
                    if (c != null) {
                        zo.write(bytesOf(c))
                        written.add(n)
                    } else {
                        zf.getInputStream(e).use { it.copyTo(zo) }
                    }
                    zo.closeEntry()
                }
            }
            for ((p, c) in changed) {
                if (p in written) continue
                zo.putNextEntry(ZipEntry(p))
                zo.write(bytesOf(c))
                zo.closeEntry()
            }
        }
    }
}

object ProjectLoader {
    private fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (e: Exception) {
        }
        return "project.zip"
    }

    fun load(ctx: Context, uri: Uri): ProjectData {
        val tmp = File(ctx.filesDir, "project.tmp")
        val dst = File(ctx.filesDir, "project.zip")
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("Không mở được tệp")
        input.use { i ->
            tmp.outputStream().use { o ->
                val buf = ByteArray(16384)
                var total = 0L
                while (true) {
                    val n = i.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_ZIP) throw IOException("Tệp zip lớn hơn 80 MB")
                    o.write(buf, 0, n)
                }
            }
        }
        val name = displayName(ctx, uri)
        val pd = try {
            parse(tmp, name)
        } catch (e: Exception) {
            tmp.delete()
            throw IOException("Đây không phải file zip hợp lệ")
        }
        tmp.copyTo(dst, overwrite = true)
        tmp.delete()
        File(ctx.filesDir, "project.name").writeText(name)
        return ProjectData(pd.name, dst, pd.files, pd.paths, pd.skipped)
    }

    fun restore(ctx: Context): ProjectData? {
        val f = File(ctx.filesDir, "project.zip")
        if (!f.exists()) return null
        return try {
            val n = File(ctx.filesDir, "project.name").takeIf { it.exists() }?.readText() ?: "project.zip"
            parse(f, n)
        } catch (e: Exception) {
            null
        }
    }

    fun clear(ctx: Context) {
        File(ctx.filesDir, "project.zip").delete()
        File(ctx.filesDir, "project.name").delete()
    }

    private fun isText(path: String): Boolean {
        val file = path.substringAfterLast('/')
        val lower = file.lowercase()
        if (lower in SKIP_NAMES || lower.endsWith(".min.js") || lower.endsWith(".min.css")) return false
        if (lower in SPECIAL_TEXT) return true
        val ext = if (lower.contains('.')) lower.substringAfterLast('.') else return false
        return ext in TEXT_EXT
    }

    private fun ignored(path: String): Boolean {
        val parts = path.split('/')
        return parts.dropLast(1).any { it in IGNORED_DIRS }
    }

    private fun readLimited(zf: ZipFile, e: ZipEntry, max: Int): ByteArray? {
        zf.getInputStream(e).use { s ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > max) return null
            }
            return out.toByteArray()
        }
    }

    private fun parse(file: File, name: String): ProjectData {
        val files = LinkedHashMap<String, String>()
        val paths = TreeSet<String>()
        var skipped = 0
        ZipFile(file).use { zf ->
            val cand = ArrayList<Pair<ZipEntry, String>>()
            for (e in zf.entries()) {
                if (e.isDirectory) continue
                val n = e.name.replace('\\', '/')
                if (ignored(n)) continue
                paths.add(n)
                if (isText(n)) cand.add(e to n)
            }
            cand.sortWith(compareBy<Pair<ZipEntry, String>>({ p -> p.second.count { ch -> ch == '/' } }, { p -> p.second }))
            var budget = MAX_CHARS
            for ((e, n) in cand) {
                val bytes = readLimited(zf, e, MAX_FILE)
                if (bytes == null) {
                    skipped++
                    continue
                }
                val text = String(bytes, Charsets.UTF_8)
                if (text.indexOf('\u0000') >= 0) continue
                if (text.length > budget) {
                    skipped++
                    continue
                }
                budget -= text.length
                files[n] = text
            }
        }
        return ProjectData(name, file, files, paths, skipped)
    }
}
