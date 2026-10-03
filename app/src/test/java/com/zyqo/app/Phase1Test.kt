package com.zyqo.app

import java.io.File
import java.util.TreeSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1Test {
    private val groqBody = "Rate limit reached for model x on tokens per minute (TPM): Limit 8000, Used 4978, Requested 5336. Please try again in 17.355s."

    private class Fake(private val steps: List<() -> String>) : LlmApi {
        var calls = 0
        override val truncated: Boolean = false
        override fun cancel() {}
        override fun stream(p: Provider, key: String, model: String, system: String, turns: List<Turn>): Flow<String> = flow {
            val s = steps[minOf(calls, steps.lastIndex)]
            calls++
            emit(s())
        }
    }

    private fun entry(id: String, p: Provider = Provider.GROQ) = KeyEntry(id, p, "gsk_abcdefghijkl$id", listOf("m1"))

    @Test
    fun parsesGroqRateInfo() {
        val r = parseRate(groqBody)
        assertNotNull(r)
        assertEquals(8000, r!!.limit)
        assertEquals(4978, r.used)
        assertEquals(5336, r.requested)
        assertNull(parseRate("other text"))
    }

    @Test
    fun humanizedRateErrorHasNoRawEnglish() {
        val m = humanizeError(Fail.RATE, "Groq", 17_355, parseRate(groqBody))
        assertTrue(m, m.contains("17") || m.contains("18"))
        assertTrue(m.contains("Groq"))
        assertFalse(m.contains("Rate limit reached"))
        for (f in Fail.entries) assertFalse(humanizeError(f, "X", 0, null).isBlank())
    }

    @Test
    fun estimatesTokensMonotonically() {
        assertEquals(0, estimateTokens(""))
        assertTrue(estimateTokens("a".repeat(100)) < estimateTokens("a".repeat(400)))
        assertTrue(estimateTokens("đường dẫn tệp") > estimateTokens("path to fil"))
    }

    @Test
    fun gateWaitsWhenWindowIsFull() {
        var now = 1_000_000L
        val g = TpmGate { now }
        assertEquals(0L, g.waitMs("k", 5000))
        g.learn("k", 8000)
        g.record("k", 5000)
        assertEquals(0L, g.waitMs("k", 2000))
        val w = g.waitMs("k", 5000)
        assertTrue("w=$w", w in 59_000L..60_000L)
        now += 61_000L
        assertEquals(0L, g.waitMs("k", 5000))
    }

    @Test
    fun gateIgnoresRequestsLargerThanLimit() {
        val g = TpmGate { 0L }
        g.learn("k", 8000)
        g.record("k", 7000)
        assertEquals(0L, g.waitMs("k", 9000))
    }

    @Test
    fun gateExportsAndLoadsLimits() {
        val g = TpmGate()
        g.learn("a|m", 6000)
        val h = TpmGate()
        h.load(g.export())
        assertEquals(6000, h.limitOf("a|m"))
    }

    @Test
    fun scrubHidesKeys() {
        val s = scrub("Authorization: Bearer abc.def and gsk_abcdefgh12345 and AIzaSyABCDEFGH123 and sk-ant-api03-xxxxxxxx")
        assertFalse(s.contains("gsk_abcdefgh12345"))
        assertFalse(s.contains("AIzaSyABCDEFGH123"))
        assertFalse(s.contains("sk-ant-api03"))
        assertFalse(s.contains("abc.def"))
    }

    @Test
    fun flattensToolHistoryForFallback() {
        val msgs = listOf(
            CanonMsg(CanonRole.USER, listOf(Part.Text("tìm"))),
            CanonMsg(CanonRole.ASSISTANT, listOf(Part.ToolCall("1", "grep", "{\"pattern\":\"x\"}"))),
            CanonMsg(CanonRole.TOOL, listOf(Part.ToolResult("1", "A.kt:1: x")))
        )
        val turns = flattenForFallback(msgs)
        assertEquals(3, turns.size)
        assertEquals(Role.USER, turns[2].role)
        assertTrue(turns[1].text.contains("grep"))
        assertTrue(turns[2].text.contains("A.kt:1"))
    }

    @Test
    fun capBookRoundTrips() {
        val b = CapBook()
        assertEquals(ToolSupport.UNKNOWN, b.tools(Provider.GROQ, "m"))
        b.learnTools(Provider.GROQ, "m", ToolSupport.NO)
        val c = CapBook()
        c.load(b.export())
        assertEquals(ToolSupport.NO, c.tools(Provider.GROQ, "m"))
    }

    @Test
    fun routerFallsBackToSecondKeyOnAuthError() = runBlocking {
        val bad = Fake(listOf({ throw ApiError("x", Fail.AUTH) }))
        val good = Fake(listOf({ "ok" }))
        val api = object : LlmApi {
            override val truncated: Boolean = false
            override fun cancel() {}
            override fun stream(p: Provider, key: String, model: String, system: String, turns: List<Turn>): Flow<String> =
                if (key.endsWith("A")) bad.stream(p, key, model, system, turns) else good.stream(p, key, model, system, turns)
        }
        val router = Router(api) { listOf(entry("A"), entry("B")) }
        val sb = StringBuilder()
        router.run(listOf(Turn(Role.USER, "hi"))) { _, _ -> "sys" }.collect { sb.append(it) }
        assertEquals("ok", sb.toString())
        assertTrue(router.isDead("A"))
    }

    @Test
    fun routerShrinksWhenRequestExceedsLimit() = runBlocking {
        val sizes = ArrayList<Int>()
        val api = object : LlmApi {
            override val truncated: Boolean = false
            override fun cancel() {}
            override fun stream(p: Provider, key: String, model: String, system: String, turns: List<Turn>): Flow<String> = flow {
                sizes.add(system.length)
                if (system.length > 6000) throw ApiError("x", Fail.RATE, 5000, RateInfo(2000, 0, 3000))
                emit("done")
            }
        }
        val router = Router(api) { listOf(entry("A")) }
        val sb = StringBuilder()
        router.run(listOf(Turn(Role.USER, "hi"))) { _, budget -> "s".repeat(budget) }.collect { sb.append(it) }
        assertEquals("done", sb.toString())
        assertTrue(sizes.size >= 2)
        assertTrue(sizes.last() <= 6000)
    }

    @Test
    fun routerFinalErrorIsFriendly() = runBlocking {
        val api = object : LlmApi {
            override val truncated: Boolean = false
            override fun cancel() {}
            override fun stream(p: Provider, key: String, model: String, system: String, turns: List<Turn>): Flow<String> = flow {
                throw ApiError("Rate limit reached for model x", Fail.RATE, 120_000, parseRate(groqBody), groqBody)
            }
        }
        val router = Router(api) { listOf(entry("A")) }
        var msg = ""
        try {
            router.run(listOf(Turn(Role.USER, "hi"))) { _, _ -> "sys" }.collect { }
        } catch (e: ApiError) {
            msg = e.message.orEmpty()
        }
        assertTrue(msg, msg.contains("Groq"))
        assertFalse(msg, msg.contains("Rate limit reached"))
    }

    @Test
    fun lightModeOmitsFileBodiesForLargeProjects() {
        val files = LinkedHashMap<String, String>()
        val paths = TreeSet<String>()
        files["A.kt"] = "secret_body_marker ".repeat(400)
        paths.add("A.kt")
        val p = ProjectData("t", File("t.zip"), files, paths, 0)
        assertFalse(embedFiles(p, Provider.GROQ.budget))
        assertTrue(embedFiles(p, Provider.GEMINI.budget))
        assertFalse(p.snapshot(Provider.GROQ.budget, false).contains("secret_body_marker"))
    }
}
