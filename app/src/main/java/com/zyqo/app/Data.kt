package com.zyqo.app

enum class Provider(val label: String, val budget: Int, val minBudget: Int) {
    ANTHROPIC("Anthropic", 240_000, 12_000),
    OPENAI("OpenAI", 200_000, 12_000),
    GEMINI("Google Gemini", 400_000, 16_000),
    GROQ("Groq", 12_000, 3_000),
    OPENROUTER("OpenRouter", 120_000, 8_000)
}

enum class Role { USER, ASSISTANT }

enum class Screen(val title: String) {
    CHAT("Trò chuyện"),
    PROJECT("Dự án"),
    SKILLS("Kỹ năng"),
    TOOLS("Công cụ"),
    SETTINGS("Cài đặt")
}

data class Msg(
    val id: Long,
    val role: Role,
    val text: String,
    val changes: List<String> = emptyList(),
    val local: Boolean = false,
    val error: Boolean = false,
    val steps: List<String> = emptyList()
)

data class Turn(val role: Role, val text: String)

data class Skill(
    val id: String,
    val name: String,
    val hint: String,
    val prompt: String,
    val custom: Boolean = false
)

data class KeyEntry(
    val id: String,
    val provider: Provider,
    val key: String,
    val models: List<String>,
    val enabled: Boolean = true
) {
    val model: String get() = models.firstOrNull().orEmpty()
}

data class McpServer(
    val id: String,
    val name: String,
    val url: String,
    val token: String,
    val trusted: Boolean = false,
    val enabled: Boolean = true
)

enum class Fail { AUTH, BILLING, MODEL, RATE, OVERSIZE, SERVER, NETWORK, TOOLS, OTHER }

class ApiError(
    message: String,
    val fail: Fail = Fail.OTHER,
    val retryAfterMs: Long = 0L,
    val rate: RateInfo? = null,
    val detail: String = ""
) : Exception(message) {
    var provider: Provider? = null
}

fun normalizeKey(raw: String): String {
    var s = raw.trim()
    if (s.startsWith("Bearer ", ignoreCase = true)) s = s.substring(7)
    return s.filter { !it.isWhitespace() }.trim('"', '\'', '`')
}

fun detectProvider(key: String): Provider? = when {
    key.startsWith("sk-ant-") -> Provider.ANTHROPIC
    key.startsWith("sk-or-") -> Provider.OPENROUTER
    key.startsWith("gsk_") -> Provider.GROQ
    key.startsWith("AQ.") || key.startsWith("AIza") -> Provider.GEMINI
    key.startsWith("sk-") -> Provider.OPENAI
    else -> null
}

fun providerOf(name: String): Provider? = Provider.entries.firstOrNull { it.name == name }

fun maskKey(k: String): String =
    if (k.length <= 12) "••••••••" else k.take(6) + "••••••••" + k.takeLast(4)
