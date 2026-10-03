package com.zyqo.app

private val GPT_RE = Regex("^gpt-(\\d+(?:\\.\\d+)?)(-mini)?$")
private val GEMINI_RE = Regex("^gemini-(\\d+(?:\\.\\d+)?)-(flash|pro)(-lite)?(-preview)?")
private val GEMINI_BAD = listOf("image", "tts", "embedding", "live", "audio", "robotics", "computer", "learnlm", "native", "thinking-exp")
private val GROQ_BAD = listOf("whisper", "guard", "tts", "orpheus", "playai", "distil", "compound", "safeguard", "embed")
private val GROQ_PREF = listOf("gpt-oss-120b", "llama-3.3-70b", "kimi", "qwen3-32b", "llama-4", "gpt-oss-20b")

private fun familyWeight(id: String): Int = when {
    id.contains("sonnet") -> 3
    id.contains("opus") -> 2
    id.contains("haiku") -> 1
    else -> 0
}

fun rankModels(p: Provider, ids: List<String>): List<String> {
    val ranked: List<String> = when (p) {
        Provider.ANTHROPIC -> ids.filter { it.startsWith("claude-") }.sortedByDescending { familyWeight(it) }
        Provider.OPENAI -> ids.mapNotNull { id ->
            GPT_RE.matchEntire(id)?.let { m -> Triple(id, m.groupValues[1].toDouble(), m.groupValues[2].isNotEmpty()) }
        }.sortedWith(compareByDescending<Triple<String, Double, Boolean>> { it.second }.thenBy { it.third }).map { it.first }
        Provider.GEMINI -> ids.filter { id -> id.startsWith("gemini-") && GEMINI_BAD.none { id.contains(it) } }
            .mapNotNull { id ->
                GEMINI_RE.find(id)?.let { m ->
                    val tier = when {
                        m.groupValues[3].isNotEmpty() -> 1
                        m.groupValues[2] == "flash" -> 3
                        else -> 2
                    }
                    Triple(id, m.groupValues[1].toDouble(), tier * 2 + if (m.groupValues[4].isEmpty()) 1 else 0)
                }
            }
            .sortedWith(compareByDescending<Triple<String, Double, Int>> { it.second }.thenByDescending { it.third })
            .map { it.first }
        Provider.GROQ -> ids.filter { id -> GROQ_BAD.none { id.lowercase().contains(it) } }
            .sortedBy { id ->
                val i = GROQ_PREF.indexOfFirst { id.contains(it) }
                if (i < 0) GROQ_PREF.size else i
            }
        Provider.OPENROUTER -> ids
    }
    val out = ranked.distinct().take(4)
    return out.ifEmpty { defaultModels(p) }
}

fun defaultModels(p: Provider): List<String> = when (p) {
    Provider.ANTHROPIC -> listOf("claude-sonnet-4-5")
    Provider.OPENAI -> listOf("gpt-4o")
    Provider.GEMINI -> listOf("gemini-2.5-flash", "gemini-2.5-pro")
    Provider.GROQ -> listOf("llama-3.3-70b-versatile")
    Provider.OPENROUTER -> listOf("anthropic/claude-sonnet-4.5", "openrouter/auto")
}
