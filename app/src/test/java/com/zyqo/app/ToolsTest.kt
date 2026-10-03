package com.zyqo.app

import java.io.File
import java.util.TreeSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsTest {
    private fun project(vararg f: Pair<String, String>): ProjectData {
        val files = LinkedHashMap<String, String>()
        val paths = TreeSet<String>()
        f.forEach {
            files[it.first] = it.second
            paths.add(it.first)
        }
        return ProjectData("t.zip", File("t.zip"), files, paths, 0)
    }

    @Test
    fun parsesCallsAndStripsThem() {
        val text = "Xem.\n<call tool=\"grep\">{\"pattern\":\"foo\"}</call>\n<call tool=\"read_file\">{\"path\":\"a.kt\"}</call>"
        val calls = rawCalls(text)
        assertEquals(2, calls.size)
        assertEquals("grep", calls[0].name)
        assertEquals("{\"pattern\":\"foo\"}", calls[0].args)
        assertEquals("Xem.", stripBlocks(text))
        assertEquals("Xem.", stripBlocks("Xem.\n<call tool=\"grep\">{\"pat"))
        assertTrue(hasBlocks(text))
    }

    @Test
    fun grepFindsLinesWithNumbers() {
        val p = project("a/A.kt" to "x\nval foo = 1\ny", "b/B.kt" to "FOO here")
        val out = LocalTools.grep(p, "foo", "", 30, false)
        assertTrue(out.contains("a/A.kt:2: val foo = 1"))
        assertFalse(out.contains("B.kt"))
        val ci = LocalTools.grep(p, "foo", "b/", 30, true)
        assertTrue(ci.contains("b/B.kt:1"))
    }

    @Test
    fun grepReportsBadRegexAndNoMatch() {
        val p = project("A.kt" to "abc")
        assertTrue(LocalTools.grep(p, "(", "", 30, false).startsWith("Regex không hợp lệ"))
        assertEquals("Không thấy kết quả.", LocalTools.grep(p, "zzz", "", 30, false))
    }

    @Test
    fun grepRespectsLimit() {
        val p = project("A.kt" to (1..50).joinToString("\n") { "hit $it" })
        val out = LocalTools.grep(p, "hit", "", 5, false)
        assertEquals(5, out.lines().count { it.startsWith("A.kt:") })
        assertTrue(out.contains("giới hạn"))
    }

    @Test
    fun readsLineRanges() {
        val p = project("A.kt" to "l1\nl2\nl3\nl4")
        val out = LocalTools.readFile(p, "A.kt", 2, 3)
        assertEquals("A.kt [dòng 2-3/4]\nl2\nl3", out)
        assertTrue(LocalTools.readFile(p, "A.kt", 9, 0).contains("chỉ có 4 dòng"))
        assertTrue(LocalTools.readFile(p, "none.kt", 1, 0).startsWith("Không đọc được"))
    }

    @Test
    fun listsByPrefix() {
        val p = project("a/A.kt" to "", "a/B.kt" to "", "b/C.kt" to "")
        assertEquals("a/A.kt\na/B.kt", LocalTools.listFiles(p, "a/", 80))
        assertEquals("Không có file nào.", LocalTools.listFiles(p, "z/", 80))
    }

    @Test
    fun validatesBalancedAndUnbalancedCode() {
        val ok = "fun a() {\n    val s = \"}{\"\n    val c = '{'\n    // )(\n    println(s + c)\n}\n"
        assertTrue(verifyText("A.kt", ok).isEmpty())
        val bad = "fun a() {\n    println(1)\n"
        assertEquals(1, verifyText("A.kt", bad).size)
        assertTrue(verifyText("A.kt", bad).first().contains("{"))
    }

    @Test
    fun ignoresBracketsInRawStringsAndComments() {
        val code = "val t = \"\"\"\n{ ( [\n\"\"\"\n/* ( */\nfun f() {}\n"
        assertTrue(verifyText("A.kt", code).isEmpty())
    }

    @Test
    fun detectsPlaceholdersAndConflicts() {
        assertTrue(verifyText("A.kt", "fun a() {}\n// ... existing code\n").any { it.contains("viết tắt") })
        assertTrue(verifyText("A.kt", "<<<<<<< HEAD\nx\n").any { it.contains("xung đột") })
        assertTrue(verifyText("notes.md", "... existing\n").isEmpty())
    }

    @Test
    fun validateToolUsesChangedFiles() {
        val p = project("A.kt" to "fun a() {")
        p.apply("<file path=\"B.kt\">\nfun b() {\n</file>")
        val out = LocalTools.validate(p, "")
        assertTrue(out.contains("B.kt"))
        assertEquals("Chưa có file nào được sửa.", LocalTools.validate(project("A.kt" to "x"), ""))
    }

    @Test
    fun applyReportsTouchedPaths() {
        val p = project("A.kt" to "x")
        val r = p.apply("<file path=\"B.kt\">\nfun b() {}\n</file>")
        assertEquals(listOf("B.kt"), r.touched)
    }

    @Test
    fun buildsSignaturesAndCatalog() {
        val spec = ToolSpec("grep", "tìm", listOf(Param("pattern", "string", true), Param("max", "integer", false)), true, "local")
        assertEquals("grep(pattern:string, max?:integer)", spec.signature)
        val few = catalogText(listOf(spec))
        assertTrue(few.contains("grep(pattern:string, max?:integer): tìm"))
        val many = (1..13).map { ToolSpec("s__t$it", "mô tả", emptyList(), false, "mcp:x") } + spec
        val compact = catalogText(many)
        assertTrue(compact.contains("- s__t1\n"))
        assertTrue(compact.contains("grep(pattern:string"))
        assertTrue(compact.contains("tool_help"))
        assertEquals("", catalogText(emptyList()))
    }

    @Test
    fun capsText() {
        assertEquals("abc", capText("abc", 10))
        assertTrue(capText("a".repeat(50), 10).contains("cắt bớt 40"))
    }

    @Test
    fun compactsOldTurnsButNotRecentOnes() {
        val turns = ArrayList<Turn>()
        turns.add(Turn(Role.USER, "đầu"))
        repeat(6) { turns.add(Turn(if (it % 2 == 0) Role.ASSISTANT else Role.USER, "x".repeat(1000))) }
        compactOld(turns, 1, 2)
        assertEquals("đầu", turns[0].text)
        assertTrue(turns[1].text.length < 400)
        assertEquals(1000, turns.last().text.length)
    }

    @Test
    fun slugsServerNames() {
        assertEquals("github", mcpSlug("GitHub"))
        assertEquals("my_server", mcpSlug("  My Server!! "))
        assertEquals("mcp", mcpSlug("???"))
    }
}
