package com.zyqo.app

import java.io.File
import java.util.TreeSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase0ReproTest {
    private val groqTpm = "Rate limit reached for model `llama-3.3-70b-versatile` in organization `org_x` service tier `on_demand` " +
        "on tokens per minute (TPM): Limit 8000, Used 4978, Requested 5336. Please try again in 17.355s."

    private fun project(count: Int): ProjectData {
        val files = LinkedHashMap<String, String>()
        val paths = TreeSet<String>()
        for (i in 1..count) {
            val p = "src/module$i/File$i.kt"
            files[p] = (1..60).joinToString("\n") { "fun f${i}_$it(x: Int): Int = x + $it" }
            paths.add(p)
        }
        return ProjectData("big", File("big.zip"), files, paths, 0)
    }

    @Test
    fun groqTpmErrorIsClassifiedAsRateWithDelay() {
        assertEquals(Fail.RATE, classify(429, groqTpm))
        val d = retryDelayMs(null, groqTpm)
        assertTrue("delay=$d", d in 17_354L..17_355L)
    }

    @Test
    fun groqRequestForFiftyFileProjectFitsInOneMinuteBudget() {
        val proj = project(50)
        val skill = SKILLS.first()
        val prompt = systemPrompt(skill, proj, Provider.GROQ.budget, true, catalogText(LocalTools.specs))
        val estTokens = estimateTokens(prompt)
        assertTrue("estTokens=$estTokens", estTokens <= 3_000)
    }

    @Test
    fun groqPromptDoesNotEmbedFileBodies() {
        val proj = project(50)
        val prompt = systemPrompt(SKILLS.first(), proj, Provider.GROQ.budget, false, catalogText(LocalTools.specs))
        assertTrue(!prompt.contains("fun f1_1(x: Int)"))
    }
}
