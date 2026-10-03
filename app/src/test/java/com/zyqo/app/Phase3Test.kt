package com.zyqo.app

import java.io.File
import java.util.TreeSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3Test {
    private fun proj(vararg f: Pair<String, String>): ProjectData {
        val files = LinkedHashMap<String, String>()
        val paths = TreeSet<String>()
        for ((k, v) in f) {
            files[k] = v
            paths.add(k)
        }
        return ProjectData("t", File("t.zip"), files, paths, 0)
    }

    private val data = "package a\n\nenum class Provider(val label: String) {\n    GROQ(\"Groq\")\n}\n\nfun detectProvider(key: String): Provider? {\n    return null\n}\n\nprivate const val MAX_CALLS = 4\n"
    private val vm = "class ChatVm {\n    fun go() {\n        val p = detectProvider(key)\n    }\n}\n"

    @Test
    fun indexFindsDeclarationsAcrossKinds() {
        val idx = buildIndex(
            mapOf(
                "Data.kt" to data,
                "x.py" to "def helper(a):\n    pass\n",
                "s.go" to "func (s *S) Run(ctx int) {\n}\n",
                "T.kt" to "    private suspend fun <T> FlowCollector<T>.attempt(a: Int) {\n"
            )
        )
        assertEquals(7, idx.find("detectProvider").single().line)
        assertEquals("lớp", idx.find("Provider").single().kind)
        assertEquals("giá trị", idx.find("MAX_CALLS").single().kind)
        assertEquals(1, idx.find("helper").size)
        assertEquals(1, idx.find("Run").size)
        assertEquals(1, idx.find("attempt").size)
    }

    @Test
    fun findSymbolReportsMissingWithSuggestions() {
        val idx = buildIndex(mapOf("Data.kt" to data))
        val ok = findSymbolText(idx, "detectProvider", "")
        assertTrue(ok, ok.startsWith("Data.kt:7:"))
        val miss = findSymbolText(idx, "detectProvide", "")
        assertTrue(miss, miss.contains("Không thấy") && miss.contains("detectProvider"))
    }

    @Test
    fun editRequiresReadFirst() {
        val p = proj("Data.kt" to data)
        val r = EditTools.editFile(p, "Data.kt", "return null", "return Provider.GROQ", false)
        assertTrue(r, r.contains("Chưa đọc"))
        LocalTools.readFile(p, "Data.kt", 1, 0)
        val ok = EditTools.editFile(p, "Data.kt", "return null", "return Provider.GROQ", false)
        assertTrue(ok, ok.startsWith("Đã sửa Data.kt tại dòng 8"))
        assertTrue(p.files["Data.kt"]!!.contains("return Provider.GROQ"))
        assertEquals(listOf("Sửa Data.kt"), p.drain().changes)
        assertTrue(p.drain().changes.isEmpty())
    }

    @Test
    fun editReportsNearestSnippetAndAmbiguity() {
        val p = proj("A.kt" to "fun a() {\n    val x = 1\n    val y = 1\n}\n")
        LocalTools.readFile(p, "A.kt", 1, 0)
        val near = EditTools.editFile(p, "A.kt", "fun a()  {\n   val x = 2", "z", false)
        assertTrue(near, near.contains("Đoạn gần giống nhất"))
        val amb = EditTools.editFile(p, "A.kt", "= 1", "= 2", false)
        assertTrue(amb, amb.contains("2 lần"))
        val all = EditTools.editFile(p, "A.kt", "= 1", "= 2", true)
        assertTrue(all, all.contains("2 chỗ"))
        assertFalse(p.files["A.kt"]!!.contains("= 1"))
    }

    @Test
    fun editKeepsCrlfAndUndoRestores() {
        val p = proj("A.kt" to "a\r\nb\r\n")
        LocalTools.readFile(p, "A.kt", 1, 0)
        EditTools.editFile(p, "A.kt", "a\nb", "a\nc", false)
        assertEquals("a\r\nc\r\n", p.files["A.kt"])
        assertTrue(p.undo())
        assertEquals("a\r\nb\r\n", p.files["A.kt"])
    }

    @Test
    fun writeAndDeleteFollowRules() {
        val p = proj("A.kt" to "x")
        val created = EditTools.writeFile(p, "B.kt", "fun b() {}\n")
        assertTrue(created, created.startsWith("Đã tạo"))
        assertTrue(p.paths.contains("B.kt"))
        assertTrue(EditTools.writeFile(p, "A.kt", "y").contains("Chưa đọc"))
        assertTrue(EditTools.writeFile(p, "../x", "y").contains("không hợp lệ"))
        assertTrue(EditTools.deleteFile(p, "B.kt").startsWith("Đã xóa"))
        assertFalse(p.paths.contains("B.kt"))
        assertTrue(EditTools.deleteFile(p, "B.kt").startsWith("Không có"))
    }

    @Test
    fun indexRefreshesAfterEdit() {
        val p = proj("A.kt" to "fun one() {}\n")
        assertEquals(1, p.index().find("one").size)
        EditTools.writeFile(p, "B.kt", "fun two() {}\n")
        assertEquals(1, p.index().find("two").size)
    }

    @Test
    fun verifyAcceptsTrueReferences() {
        val p = proj("app/Data.kt" to data, "app/ChatVm.kt" to vm)
        val ans = "`detectProvider` được định nghĩa ở Data.kt:7 và gọi ở app/ChatVm.kt:3."
        assertTrue(verifyAnswer(ans, p).toString(), verifyAnswer(ans, p).isEmpty())
    }

    @Test
    fun verifyCatchesInventedFileLineAndSymbol() {
        val p = proj("app/Data.kt" to data, "app/ChatVm.kt" to vm)
        val bad = verifyAnswer("`detectProvider` gọi ở MainActivity.kt:30 và Web.kt:58.", p)
        assertTrue(bad.toString(), bad.any { it.contains("MainActivity.kt") })
        val wrongLine = verifyAnswer("`detectProvider` gọi ở ChatVm.kt:1.", p)
        assertTrue(wrongLine.toString(), wrongLine.any { it.contains("không chứa") })
        val beyond = verifyAnswer("Xem Data.kt:999", p)
        assertTrue(beyond.toString(), beyond.any { it.contains("vượt quá") })
        val ghost = "init" + "Provider"
        val sym = verifyAnswer("Hàm `$ghost()` nằm ở ChatVm.kt.", p)
        assertTrue(sym.toString(), sym.any { it.contains(ghost) })
    }

    @Test
    fun verifySkipsProposalsUrlsAndAppliedEdits() {
        val p = proj("Data.kt" to data)
        assertTrue(verifyAnswer("Nên thêm hàm `loadSettings()` vào Data.kt.", p).isEmpty())
        assertTrue(verifyAnswer("Xem https://example.com/doc/guide.md để biết thêm.", p).isEmpty())
        assertTrue(verifyAnswer("Đã thêm `loadSettings` vào Data.kt.", p, true).isEmpty())
    }

    @Test
    fun verifyChecksQuotedCode() {
        val p = proj("Data.kt" to data)
        val good = "Trong Data.kt:\n```kotlin\nfun detectProvider(key: String): Provider? {\n    return null\n}\nprivate const val MAX_CALLS = 4\n```"
        assertTrue(verifyAnswer(good, p).toString(), verifyAnswer(good, p).isEmpty())
        val bad = "Trong Data.kt:\n```kotlin\nfun detectProvider(): Provider {\n    return Provider.OPENAI\n}\nval fallback = Provider.OPENAI\n```"
        assertTrue(verifyAnswer(bad, p).toString(), verifyAnswer(bad, p).any { it.contains("không khớp") })
    }

    @Test
    fun fixPromptListsProblems() {
        val s = fixPrompt(listOf("file không có trong dự án: X.kt"))
        assertTrue(s.contains("X.kt") && s.contains("find_symbol"))
    }

    @Test
    fun lightModeKeepsEditAndSymbolTools() {
        val defs = nativeDefs(LocalTools.specs, true)
        val names = pickTools(defs, true).map { it.name }
        assertTrue(names.toString(), names.containsAll(listOf("grep", "find_symbol", "read_file", "edit_file")))
        assertEquals(7, names.size)
    }
}
