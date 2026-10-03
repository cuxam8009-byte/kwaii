package com.zyqo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProvidersTest {
    @Test
    fun detectsGeminiAuthKey() {
        assertEquals(Provider.GEMINI, detectProvider("AQ.Ab8RN6exampleKey"))
    }

    @Test
    fun detectsGeminiLegacyKey() {
        assertEquals(Provider.GEMINI, detectProvider("AIzaSyExample"))
    }

    @Test
    fun detectsGroqKey() {
        assertEquals(Provider.GROQ, detectProvider("gsk_example"))
    }

    @Test
    fun distinguishesSkPrefixes() {
        assertEquals(Provider.ANTHROPIC, detectProvider("sk-ant-api03-x"))
        assertEquals(Provider.OPENROUTER, detectProvider("sk-or-v1-x"))
        assertEquals(Provider.OPENAI, detectProvider("sk-proj-x"))
        assertNull(detectProvider("unknown"))
    }

    @Test
    fun normalizesPastedKeys() {
        assertEquals("AQ.Ab12", normalizeKey("  Bearer \"AQ.Ab12\" \n"))
    }

    @Test
    fun ranksGeminiNewestFlashFirst() {
        val ids = listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-flash-image", "gemini-3-flash-preview")
        val ranked = rankModels(Provider.GEMINI, ids)
        assertEquals("gemini-3-flash-preview", ranked.first())
        assertEquals(false, ranked.contains("gemini-2.5-flash-image"))
    }

    @Test
    fun ranksGroqSkippingAudioAndGuard() {
        val ids = listOf("whisper-large-v3", "llama-3.3-70b-versatile", "openai/gpt-oss-120b", "meta-llama/llama-guard-4-12b")
        assertEquals(listOf("openai/gpt-oss-120b", "llama-3.3-70b-versatile"), rankModels(Provider.GROQ, ids))
    }

    @Test
    fun ranksOpenAiByVersion() {
        val ids = listOf("gpt-4.1", "gpt-5-mini", "gpt-5", "gpt-4o-audio-preview", "text-embedding-3-small")
        assertEquals(listOf("gpt-5", "gpt-5-mini", "gpt-4.1"), rankModels(Provider.OPENAI, ids))
    }

    @Test
    fun fallsBackToDefaultsWhenNothingMatches() {
        assertEquals(defaultModels(Provider.GEMINI), rankModels(Provider.GEMINI, listOf("embedding-001")))
    }

    @Test
    fun classifiesFailures() {
        assertEquals(Fail.RATE, classify(429, "Rate limit reached"))
        assertEquals(Fail.OVERSIZE, classify(413, ""))
        assertEquals(Fail.BILLING, classify(429, "You exceeded your current quota, check billing"))
        assertEquals(Fail.AUTH, classify(401, ""))
        assertEquals(Fail.AUTH, classify(400, "API key not valid"))
        assertEquals(Fail.SERVER, classify(529, ""))
        assertEquals(Fail.MODEL, classify(404, ""))
    }

    @Test
    fun readsRetryDelay() {
        assertEquals(5000L, retryDelayMs("5", ""))
        assertEquals(23000L, retryDelayMs(null, "{\"retryDelay\": \"23s\"}"))
        assertEquals(7000L, retryDelayMs(null, "Please try again in 7s."))
        assertEquals(0L, retryDelayMs(null, "nothing"))
    }

    @Test
    fun plansAcrossKeysAndModels() {
        val a = KeyEntry("a", Provider.GEMINI, "AQ.a", listOf("m1", "m2", "m3", "m4"))
        val b = KeyEntry("b", Provider.GROQ, "gsk_b", listOf("g1"))
        val off = KeyEntry("c", Provider.OPENAI, "sk-c", listOf("o1"), enabled = false)
        val router = Router(LlmClient()) { listOf(a, b, off) }
        val plan = router.plan()
        assertEquals(listOf("m1", "m2", "m3", "g1"), plan.map { it.model })
    }

    @Test
    fun trimsTurnsKeepingLatestAndStartingWithUser() {
        val turns = listOf(
            Turn(Role.USER, "a".repeat(100)),
            Turn(Role.ASSISTANT, "b".repeat(100)),
            Turn(Role.USER, "c".repeat(100))
        )
        val kept = trimTurns(turns, 150)
        assertEquals(1, kept.size)
        assertEquals(Role.USER, kept.first().role)
    }

    @Test
    fun mergesConsecutiveTurns() {
        val merged = mergeTurns(listOf(Turn(Role.USER, "a"), Turn(Role.USER, "b"), Turn(Role.ASSISTANT, "c")))
        assertEquals(2, merged.size)
    }
}
