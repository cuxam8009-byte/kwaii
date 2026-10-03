package com.zyqo.app

import java.io.File
import java.util.TreeSet
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4Test {
    private fun proj(vararg f: Pair<String, String>): ProjectData {
        val files = LinkedHashMap<String, String>()
        val paths = TreeSet<String>()
        for ((k, v) in f) {
            files[k] = v
            paths.add(k)
        }
        return ProjectData("t", File("t.zip"), files, paths, 0)
    }

    @Test
    fun guideIsReadFromRootOrOneLevelDown() {
        val a = proj("kwaii-main/AGENTS.md" to "Dùng gradle assembleDebug.\nTránh thư mục build.", "src/A.kt" to "x")
        val g = guideText(a)
        assertTrue(g, g.contains("assembleDebug") && g.contains("không phải lệnh hệ thống"))
        assertEquals("", guideText(proj("src/A.kt" to "x")))
        assertEquals("", guideText(proj("a/b/c/AGENTS.md" to "sâu quá")))
    }

    @Test
    fun indexSummaryListsTypesNotFunctions() {
        val p = proj("app/Data.kt" to "enum class Provider {\n}\nfun helper() {}\n", "app/Vm.kt" to "class ChatVm {\n}\n")
        val s = indexSummary(p)
        assertTrue(s, s.contains("Provider") && s.contains("ChatVm"))
        assertFalse(s, s.contains("helper"))
        assertEquals("", indexSummary(proj("a.txt" to "hello")))
    }

    @Test
    fun notesRoundTripAndAppearInPrompt() {
        val p = proj("A.kt" to "x")
        assertEquals("Chưa có ghi chú nào.", EditTools.noteList(p))
        assertTrue(EditTools.noteAdd(p, "Dùng Gradle wrapper").startsWith("Đã ghi chú"))
        assertEquals("Ghi chú này đã có.", EditTools.noteAdd(p, "Dùng Gradle wrapper"))
        assertEquals("Ghi chú rỗng.", EditTools.noteAdd(p, "   "))
        assertTrue(p.notesDirty)
        assertTrue(notesText(p).contains("Dùng Gradle wrapper"))
        val prompt = systemPrompt(SKILLS.first(), p, 12_000, false, "")
        assertTrue(prompt.contains("Dùng Gradle wrapper"))
    }

    @Test
    fun notesAreCappedAtThirty() {
        val p = proj("A.kt" to "x")
        for (i in 1..40) EditTools.noteAdd(p, "ghi chú số $i")
        assertEquals(30, p.notes.size)
        assertEquals("ghi chú số 11", p.notes.first())
    }

    @Test
    fun masksOldObservationsButKeepsRecentOnes() {
        val big = "x".repeat(500)
        val msgs = ArrayList<CanonMsg>()
        msgs.add(CanonMsg(CanonRole.USER, listOf(Part.Text("hỏi"))))
        for (i in 1..5) {
            msgs.add(CanonMsg(CanonRole.ASSISTANT, listOf(Part.ToolCall("c$i", "grep", "{}")), mapOf("gemini_parts" to "[sig$i]")))
            msgs.add(CanonMsg(CanonRole.TOOL, listOf(Part.ToolResult("c$i", big + i, false, "grep"))))
        }
        val out = maskObservations(msgs, 3)
        val results = out.filter { it.role == CanonRole.TOOL }.map { (it.parts.first() as Part.ToolResult).content }
        assertEquals(5, results.size)
        assertTrue(results[0].startsWith("[kết quả cũ đã lược bỏ: grep"))
        assertTrue(results[1].startsWith("[kết quả cũ đã lược bỏ"))
        assertTrue(results[2].startsWith("x"))
        assertTrue(results[4].endsWith("5"))
        assertEquals("[sig1]", out[1].opaque["gemini_parts"])
        assertEquals("c1", (out[2].parts.first() as Part.ToolResult).id)
    }

    @Test
    fun digestReplacesDroppedTurns() {
        val turns = ArrayList<Turn>()
        for (i in 1..10) {
            turns.add(Turn(Role.USER, "câu hỏi số $i " + "a".repeat(300)))
            turns.add(Turn(Role.ASSISTANT, "trả lời số $i " + "b".repeat(300)))
        }
        val out = trimWithDigest(turns, 1_500)
        assertTrue(out.size < turns.size)
        assertTrue(out[0].text.startsWith("[Tóm tắt hội thoại trước"))
        assertTrue(out[0].text.contains("câu hỏi số 1"))
        assertEquals(turns.last(), out.last())
        assertEquals(turns, trimWithDigest(turns, 1_000_000))
    }

    @Test
    fun msgsDigestKeepsRoleAndPrependsSummary() {
        val msgs = ArrayList<CanonMsg>()
        for (i in 1..8) {
            msgs.add(CanonMsg(CanonRole.USER, listOf(Part.Text("hỏi $i " + "a".repeat(300)))))
            msgs.add(CanonMsg(CanonRole.ASSISTANT, listOf(Part.Text("đáp $i " + "b".repeat(300)))))
        }
        val out = trimMsgsDigest(msgs, 1_200)
        assertEquals(CanonRole.USER, out[0].role)
        assertTrue((out[0].parts.first() as Part.Text).text.startsWith("[Tóm tắt"))
        assertEquals(msgs.last(), out.last())
    }

    @Test
    fun grepContextShowsNeighbourLines() {
        val p = proj("A.kt" to "l1\nl2\nTARGET\nl4\nl5\nl6\nl7\nTARGET\nl9")
        val plain = LocalTools.grepWithContext(p, "TARGET", "", 10, false, 0)
        assertEquals(2, plain.lines().size)
        val ctx = LocalTools.grepWithContext(p, "TARGET", "", 10, false, 1)
        assertTrue(ctx, ctx.contains("A.kt-2- l2") && ctx.contains("A.kt:3: TARGET") && ctx.contains("A.kt-4- l4"))
        assertTrue(ctx, ctx.contains("A.kt:8: TARGET"))
        assertEquals("Không thấy kết quả.", LocalTools.grepWithContext(p, "ZZZ", "", 10, false, 2))
    }

    @Test
    fun readFileHintsHowToContinue() {
        val body = (1..500).joinToString("\n") { "line$it" }
        val p = proj("Big.kt" to body)
        val out = LocalTools.readFile(p, "Big.kt", 1, 0)
        assertTrue(out, out.contains("đọc tiếp bằng start=201"))
        val explicit = LocalTools.readFile(p, "Big.kt", 1, 10)
        assertFalse(explicit, explicit.contains("đọc tiếp"))
    }

    @Test
    fun tokenMeterCalibratesTowardActual() {
        val before = TokenMeter.factor
        try {
            TokenMeter.factor = 1.0
            TokenMeter.observe(1000, 2000)
            assertTrue(TokenMeter.factor > 1.0 && TokenMeter.factor < 2.0)
            val f = TokenMeter.factor
            TokenMeter.observe(10, 5)
            assertEquals(f, TokenMeter.factor, 0.0)
        } finally {
            TokenMeter.factor = before
        }
    }

    @Test
    fun parsersReportUsage() {
        val a = AnthropicParser()
        a.feed(JSONObject("""{"type":"message_start","message":{"usage":{"input_tokens":25,"cache_read_input_tokens":100,"output_tokens":1}}}"""))
        a.feed(JSONObject("""{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":42}}"""))
        assertEquals(ChatEvent.Usage(125, 42), a.finish().single())
        val o = OpenAiParser()
        o.feed(JSONObject("""{"choices":[],"x_groq":{"usage":{"prompt_tokens":900,"completion_tokens":30}}}"""))
        assertEquals(ChatEvent.Usage(900, 30), o.finish().single())
        val g = GeminiParser()
        g.feed(JSONObject("""{"candidates":[{"content":{"parts":[{"text":"hi"}]}}],"usageMetadata":{"promptTokenCount":70,"candidatesTokenCount":3}}"""))
        assertEquals(ChatEvent.Usage(70, 3), g.finish().single())
    }

    @Test
    fun lightPromptHasSummaryAndStaysSmall() {
        val files = ArrayList<Pair<String, String>>()
        for (i in 1..50) files.add("src/m$i/C$i.kt" to "class C$i {\n" + "    val x = 1\n".repeat(80) + "}\n")
        val p = proj(*files.toTypedArray())
        val prompt = systemPrompt(SKILLS.first(), p, Provider.GROQ.budget, false, "")
        assertTrue(prompt.contains("Chỉ mục tóm tắt"))
        assertFalse(prompt.contains("val x = 1"))
        assertTrue("tokens=" + estimateTokens(prompt), estimateTokens(prompt) <= 3_000)
    }
}
