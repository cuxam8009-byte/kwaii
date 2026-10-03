package com.zyqo.app

import java.io.File
import java.util.TreeSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2Test {
    private fun feedAll(p: ChatParser, lines: List<String>): List<ChatEvent> {
        val out = ArrayList<ChatEvent>()
        for (l in lines) out.addAll(p.feed(JSONObject(l)))
        out.addAll(p.finish())
        return out
    }

    private fun proj(vararg f: Pair<String, String>): ProjectData {
        val files = LinkedHashMap<String, String>()
        val paths = TreeSet<String>()
        for ((k, v) in f) {
            files[k] = v
            paths.add(k)
        }
        return ProjectData("t", File("t.zip"), files, paths, 0)
    }

    private val tools = listOf(
        ToolDef("grep", "tìm", listOf(Param("pattern", "string", true), Param("max", "integer", false)))
    )

    @Test
    fun anthropicParserAssemblesToolUse() {
        val ev = feedAll(
            AnthropicParser(),
            listOf(
                """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
                """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Để mình tìm"}}""",
                """{"type":"content_block_stop","index":0}""",
                """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_1","name":"grep","input":{}}}""",
                """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":""}}""",
                """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"pattern\":"}}""",
                """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":" \"detectProvider\"}"}}""",
                """{"type":"content_block_stop","index":1}""",
                """{"type":"message_delta","delta":{"stop_reason":"tool_use"}}"""
            )
        )
        assertEquals(ChatEvent.Text("Để mình tìm"), ev[0])
        val use = ev[1] as ChatEvent.Use
        assertEquals("toolu_1", use.id)
        assertEquals("grep", use.name)
        assertEquals("detectProvider", JSONObject(use.args).getString("pattern"))
    }

    @Test
    fun openAiParserAccumulatesByIndex() {
        val ev = feedAll(
            OpenAiParser(),
            listOf(
                """{"choices":[{"delta":{"role":"assistant","content":null,"tool_calls":[{"index":0,"id":"call_a","type":"function","function":{"name":"grep","arguments":""}}]}}]}""",
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"pattern\":"}}]}}]}""",
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"x\"}"}}]}}]}""",
                """{"choices":[{"delta":{"tool_calls":[{"index":1,"id":"call_b","function":{"name":"read_file","arguments":"{}"}}]}}]}""",
                """{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}"""
            )
        )
        val uses = ev.filterIsInstance<ChatEvent.Use>()
        assertEquals(2, uses.size)
        assertEquals("call_a", uses[0].id)
        assertEquals("x", JSONObject(uses[0].args).getString("pattern"))
        assertEquals("read_file", uses[1].name)
    }

    @Test
    fun geminiParserKeepsRawPartsWithSignature() {
        val ev = feedAll(
            GeminiParser(),
            listOf(
                """{"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"grep","args":{"pattern":"x"}},"thoughtSignature":"SIG1"}]}}]}"""
            )
        )
        val use = ev.filterIsInstance<ChatEvent.Use>().single()
        assertEquals("grep", use.name)
        val op = ev.filterIsInstance<ChatEvent.Opaque>().single()
        assertTrue(op.value.contains("SIG1"))
        val back = geminiContents(
            listOf(
                CanonMsg(CanonRole.USER, listOf(Part.Text("hỏi"))),
                CanonMsg(CanonRole.ASSISTANT, listOf(Part.ToolCall(use.id, use.name, use.args)), mapOf("gemini_parts" to op.value)),
                CanonMsg(CanonRole.TOOL, listOf(Part.ToolResult(use.id, "kq", false, "grep")))
            )
        )
        val modelParts = back.getJSONObject(1).getJSONArray("parts")
        assertEquals("SIG1", modelParts.getJSONObject(0).getString("thoughtSignature"))
        assertEquals("user", back.getJSONObject(2).getString("role"))
        assertTrue(back.getJSONObject(2).getJSONArray("parts").getJSONObject(0).has("functionResponse"))
    }

    @Test
    fun anthropicBodyForcesToolAndMergesToolResults() {
        val msgs = listOf(
            CanonMsg(CanonRole.USER, listOf(Part.Text("tìm"))),
            CanonMsg(CanonRole.ASSISTANT, listOf(Part.Text("ok"), Part.ToolCall("t1", "grep", "{\"pattern\":\"a\"}"))),
            CanonMsg(CanonRole.TOOL, listOf(Part.ToolResult("t1", "A.kt:1: a", false, "grep"))),
            CanonMsg(CanonRole.USER, listOf(Part.Text("follow")))
        )
        val b = anthropicBody("m", "sys", msgs, tools, true)
        assertEquals("any", b.getJSONObject("tool_choice").getString("type"))
        val arr = b.getJSONArray("messages")
        assertEquals(3, arr.length())
        val last = arr.getJSONObject(2).getJSONArray("content")
        assertEquals("tool_result", last.getJSONObject(0).getString("type"))
        assertEquals("text", last.getJSONObject(1).getString("type"))
        val noForce = anthropicBody("m", "sys", msgs, tools, false)
        assertFalse(noForce.has("tool_choice"))
    }

    @Test
    fun openAiBodyUsesToolMessagesAndRequired() {
        val msgs = listOf(
            CanonMsg(CanonRole.USER, listOf(Part.Text("tìm"))),
            CanonMsg(CanonRole.ASSISTANT, listOf(Part.ToolCall("c1", "grep", "{}"))),
            CanonMsg(CanonRole.TOOL, listOf(Part.ToolResult("c1", "kq", false, "grep")))
        )
        val b = openAiBody(Provider.GROQ, "llama", "sys", msgs, tools, true)
        assertEquals("required", b.getString("tool_choice"))
        val m = b.getJSONArray("messages")
        assertEquals("system", m.getJSONObject(0).getString("role"))
        assertEquals("assistant", m.getJSONObject(2).getString("role"))
        assertEquals("c1", m.getJSONObject(2).getJSONArray("tool_calls").getJSONObject(0).getString("id"))
        assertEquals("tool", m.getJSONObject(3).getString("role"))
        assertEquals("c1", m.getJSONObject(3).getString("tool_call_id"))
    }

    @Test
    fun geminiBodyForcesAny() {
        val b = geminiBody("sys", listOf(CanonMsg(CanonRole.USER, listOf(Part.Text("hi")))), tools, true)
        assertEquals("ANY", b.getJSONObject("toolConfig").getJSONObject("functionCallingConfig").getString("mode"))
        assertEquals("grep", b.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations").getJSONObject(0).getString("name"))
    }

    @Test
    fun schemaMarksRequired() {
        val s = schemaOf(tools[0].params)
        assertEquals("pattern", s.getJSONArray("required").getString(0))
        assertEquals("integer", s.getJSONObject("properties").getJSONObject("max").getString("type"))
    }

    @Test
    fun classifiesToolFormatErrors() {
        assertEquals(Fail.TOOLS, classify(400, "{\"error\":{\"code\":\"tool_use_failed\",\"message\":\"Failed to call a function\"}}"))
        assertEquals(Fail.AUTH, classify(401, "tool_use_failed"))
    }

    @Test
    fun extractsIdentifiersAndForces() {
        val ghost = "init" + "Provider"
        val ids = extractIdentifiers("hàm `detectProvider` ở đâu, và $ghost, file app/Data.kt")
        assertTrue(ids.contains("detectProvider"))
        assertTrue(ids.contains(ghost))
        assertTrue(ids.contains("app/Data.kt"))
        assertTrue(shouldForce("detectProvider nằm ở đâu", true))
        assertFalse(shouldForce("detectProvider nằm ở đâu", false))
        assertFalse(shouldForce("chào bạn", true))
    }

    @Test
    fun preRetrievalGroundsRealAndMissingNames() {
        val p = proj(
            "Data.kt" to "fun detectProvider(key: String): Provider? = null",
            "Vm.kt" to "val p = detectProvider(k)"
        )
        val out = preRetrieve("detectProvider và " + "init" + "Provider ở đâu", p)
        assertTrue(out, out.contains("Data.kt"))
        assertTrue(out, out.contains("Vm.kt"))
        assertTrue(out, out.contains("Không thấy kết quả"))
    }

    @Test
    fun trimMsgsKeepsToolPairsTogether() {
        val big = "x".repeat(3000)
        val msgs = listOf(
            CanonMsg(CanonRole.USER, listOf(Part.Text("q1"))),
            CanonMsg(CanonRole.ASSISTANT, listOf(Part.ToolCall("a", "grep", big))),
            CanonMsg(CanonRole.TOOL, listOf(Part.ToolResult("a", big))),
            CanonMsg(CanonRole.USER, listOf(Part.Text("q2"))),
            CanonMsg(CanonRole.ASSISTANT, listOf(Part.Text("a2")))
        )
        val out = trimMsgs(msgs, 500)
        assertEquals(CanonRole.USER, out.first().role)
        assertTrue(out.first().parts.all { it is Part.Text })
        assertEquals("a2", ((out.last().parts.first()) as Part.Text).text)
    }

    @Test
    fun looksErrorSeesWrappedFailures() {
        assertTrue(looksError("<tool_result name=\"x\">\nSai tham số: thiếu\n</tool_result>"))
        assertFalse(looksError("<tool_result name=\"x\">\nA.kt:1: ok\n</tool_result>"))
    }

    @Test
    fun routerFallsBackWhenToolsUnsupportedTwice() = runBlocking {
        val api = object : LlmApi {
            override val truncated: Boolean = false
            override fun cancel() {}
            override fun stream(p: Provider, key: String, model: String, system: String, turns: List<Turn>): Flow<String> = flow { emit("x") }
            override fun chat(
                p: Provider, key: String, model: String, system: String,
                msgs: List<CanonMsg>, tools: List<ToolDef>, force: Boolean
            ): Flow<ChatEvent> = flow { throw ApiError("x", Fail.TOOLS, 0, null, "tool_use_failed") }
        }
        val router = Router(api) { listOf(KeyEntry("A", Provider.GROQ, "gsk_abcdefghijklA", listOf("m1"))) }
        var fell = false
        try {
            router.runChat(listOf(CanonMsg(CanonRole.USER, listOf(Part.Text("hi")))), { _, _ -> "sys" }, tools, false).collect { }
        } catch (e: ToolFallback) {
            fell = true
        }
        assertTrue(fell)
        assertEquals(ToolSupport.NO, router.caps.tools(Provider.GROQ, "m1"))
    }

    @Test
    fun routerChatPassesEventsThrough() = runBlocking {
        val api = object : LlmApi {
            override val truncated: Boolean = false
            override fun cancel() {}
            override fun stream(p: Provider, key: String, model: String, system: String, turns: List<Turn>): Flow<String> = flow { emit("x") }
            override fun chat(
                p: Provider, key: String, model: String, system: String,
                msgs: List<CanonMsg>, tools: List<ToolDef>, force: Boolean
            ): Flow<ChatEvent> = flow {
                emit(ChatEvent.Text("ok"))
                emit(ChatEvent.Use("1", "grep", "{}"))
            }
        }
        val router = Router(api) { listOf(KeyEntry("A", Provider.GROQ, "gsk_abcdefghijklA", listOf("m1"))) }
        val got = ArrayList<ChatEvent>()
        router.runChat(listOf(CanonMsg(CanonRole.USER, listOf(Part.Text("hi")))), { _, _ -> "sys" }, tools, false).collect { got.add(it) }
        assertEquals(2, got.size)
        assertEquals(ToolSupport.YES, router.caps.tools(Provider.GROQ, "m1"))
        assertEquals("GROQ|m1", router.lastOrigin)
    }
}
