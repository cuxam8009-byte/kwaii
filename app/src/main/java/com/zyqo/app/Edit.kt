package com.zyqo.app

object EditTools {
    val specs: List<ToolSpec> = listOf(
        ToolSpec(
            "find_symbol", "tìm nơi khai báo hàm, lớp, đối tượng, hằng theo tên",
            listOf(Param("name", "string", true), Param("kind", "string", false)), true, "local",
            detail = "Tra chỉ mục khai báo của dự án, trả về path:dòng: loại tên. Dùng để biết một tên có tồn tại và nằm ở đâu trước khi nhắc đến nó. Chỉ thấy nơi khai báo, muốn thấy nơi gọi thì dùng grep. kind tùy chọn: hàm, lớp, đối tượng, giao diện, kiểu, giá trị."
        ),
        ToolSpec(
            "edit_file", "sửa file bằng cặp chuỗi cũ và mới",
            listOf(Param("path", "string", true), Param("old_string", "string", true), Param("new_string", "string", true), Param("replace_all", "boolean", false)),
            false, "local",
            detail = "Thay old_string bằng new_string trong một file đã đọc bằng read_file. old_string phải khớp từng ký tự và duy nhất trong file, nếu không thì thêm ngữ cảnh xung quanh hoặc đặt replace_all=true. Nếu không khớp, kết quả trả về đoạn gần giống nhất. Dùng cho mọi thay đổi trên file có sẵn."
        ),
        ToolSpec(
            "write_file", "tạo file mới hoặc viết lại toàn bộ file ngắn",
            listOf(Param("path", "string", true), Param("content", "string", true)), false, "local",
            detail = "Tạo file mới, hoặc ghi đè toàn bộ một file đã đọc. Với file dài có sẵn thì dùng edit_file. content là toàn bộ nội dung, không viết tắt."
        ),
        ToolSpec(
            "note_add", "ghi nhớ một quyết định hoặc việc dang dở cho các phiên sau", listOf(Param("text", "string", true)), true, "local",
            detail = "Ghi một ghi chú ngắn (tối đa 300 ký tự) gắn với dự án: quyết định đã chốt, quy ước phát hiện được, việc dang dở. Ghi chú được nạp lại ở các phiên sau. Không ghi khóa API hay thông tin bí mật."
        ),
        ToolSpec("note_list", "xem các ghi chú đã lưu của dự án", emptyList(), true, "local"),
        ToolSpec(
            "delete_file", "xóa một file", listOf(Param("path", "string", true)), false, "local",
            detail = "Xóa một file khỏi dự án. Có thể hoàn tác trong ứng dụng."
        )
    )

    private fun countOccurrences(text: String, find: String): Int {
        var n = 0
        var i = text.indexOf(find)
        while (i >= 0) {
            n++
            i = text.indexOf(find, i + find.length)
        }
        return n
    }

    private fun tokens(s: String): List<String> = s.split(Regex("[^A-Za-z0-9_]+")).filter { it.isNotEmpty() }

    fun nearestSnippet(content: String, find: String): Pair<Int, String>? {
        val lines = content.lines()
        val fl = find.lines().filter { it.isNotBlank() }
        if (fl.isEmpty() || lines.isEmpty() || lines.size > 20_000) return null
        val want = HashSet<String>()
        for (l in fl) want.addAll(tokens(l))
        if (want.isEmpty()) return null
        val win = minOf(fl.size, lines.size)
        var best = 0.0
        var at = -1
        for (i in 0..(lines.size - win)) {
            val have = HashSet<String>()
            for (k in i until i + win) have.addAll(tokens(lines[k]))
            if (have.isEmpty()) continue
            val inter = want.count { it in have }
            val score = inter.toDouble() / (want.size + have.size - inter)
            if (score > best) {
                best = score
                at = i
            }
        }
        if (at < 0 || best < 0.3) return null
        return Pair(at + 1, lines.subList(at, at + win).joinToString("\n").take(800))
    }

    fun editFile(p: ProjectData, rawPath: String, old: String, new: String, all: Boolean): String {
        val path = cleanPath(rawPath) ?: return "Đường dẫn không hợp lệ: $rawPath"
        val orig = p.files[path]
            ?: return "Không có file $path. Dùng list_files hoặc find_symbol để tìm đúng đường dẫn, hoặc write_file để tạo mới."
        if (path !in p.seen) return "Chưa đọc $path trong phiên này. Gọi read_file cho file này trước khi sửa."
        if (old.isEmpty()) return "old_string rỗng."
        if (old == new) return "old_string và new_string giống nhau, không có gì để sửa."
        val crlf = orig.contains("\r\n")
        val cur = if (crlf) orig.replace("\r\n", "\n") else orig
        val find = if (old.contains("\r\n")) old.replace("\r\n", "\n") else old
        val rep = if (new.contains("\r\n")) new.replace("\r\n", "\n") else new
        val count = countOccurrences(cur, find)
        if (count == 0) {
            val near = nearestSnippet(cur, find)
            return "Không tìm thấy old_string trong $path." + if (near != null) {
                " Đoạn gần giống nhất (từ dòng ${near.first}):\n${near.second}\nHãy chép đúng từng ký tự, kể cả khoảng trắng."
            } else {
                " Hãy đọc lại file bằng read_file."
            }
        }
        if (count > 1 && !all) {
            return "old_string xuất hiện $count lần trong $path. Thêm ngữ cảnh xung quanh để duy nhất, hoặc đặt replace_all=true."
        }
        val first = cur.indexOf(find)
        val line = cur.substring(0, first).count { it == '\n' } + 1
        var res = if (all) cur.replace(find, rep) else cur.substring(0, first) + rep + cur.substring(first + find.length)
        if (crlf) res = res.replace("\n", "\r\n")
        p.checkpoint()
        p.files[path] = res
        p.changed[path] = res
        p.indexCache = null
        p.pendingChanges.add("Sửa $path")
        p.pendingTouched.add(path)
        return "Đã sửa $path tại dòng $line" + (if (count > 1) " ($count chỗ)." else ".")
    }

    fun writeFile(p: ProjectData, rawPath: String, content: String): String {
        val path = cleanPath(rawPath) ?: return "Đường dẫn không hợp lệ: $rawPath"
        val known = p.paths.contains(path)
        if (known && !p.files.containsKey(path)) return "$path không phải file văn bản, không ghi được."
        if (known && path !in p.seen) return "Chưa đọc $path trong phiên này. Gọi read_file trước khi ghi đè."
        p.checkpoint()
        p.files[path] = content
        p.changed[path] = content
        p.deleted.remove(path)
        p.paths.add(path)
        p.seen.add(path)
        p.indexCache = null
        p.pendingChanges.add((if (known) "Sửa " else "Tạo ") + path)
        p.pendingTouched.add(path)
        return (if (known) "Đã ghi đè " else "Đã tạo ") + path + " (${content.lines().size} dòng)."
    }

    fun noteAdd(p: ProjectData, text: String): String {
        val t = text.trim().replace('\n', ' ').take(300)
        if (t.isEmpty()) return "Ghi chú rỗng."
        if (p.notes.contains(t)) return "Ghi chú này đã có."
        while (p.notes.size >= 30) p.notes.removeAt(0)
        p.notes.add(t)
        p.notesDirty = true
        return "Đã ghi chú (${p.notes.size} ghi chú)."
    }

    fun noteList(p: ProjectData): String =
        if (p.notes.isEmpty()) "Chưa có ghi chú nào." else p.notes.mapIndexed { i, n -> "${i + 1}. $n" }.joinToString("\n")

    fun deleteFile(p: ProjectData, rawPath: String): String {
        val path = cleanPath(rawPath) ?: return "Đường dẫn không hợp lệ: $rawPath"
        if (!p.paths.contains(path)) return "Không có file $path."
        p.checkpoint()
        p.files.remove(path)
        p.changed.remove(path)
        p.deleted.add(path)
        p.paths.remove(path)
        p.seen.remove(path)
        p.indexCache = null
        p.pendingChanges.add("Xóa $path")
        return "Đã xóa $path."
    }
}
