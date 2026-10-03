package com.zyqo.app

import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal val SEARCH_RE = Regex("<search q=\"([^\"]+)\"\\s*/>")
internal val FETCH_RE = Regex("<fetch url=\"([^\"]+)\"(?:\\s+q=\"([^\"]*)\")?\\s*/>")

private val DROP_RE = Regex(
    "<(script|style|noscript|svg|head|nav|header|footer|aside|form|iframe|template)\\b[^>]*>.*?</\\1>",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
)
private val COMMENT_RE = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
private val BLOCK_TAG_RE = Regex("</?(p|div|br|li|ul|ol|h[1-6]|tr|table|section|article|pre|blockquote|main)\\b[^>]*>", RegexOption.IGNORE_CASE)
private val TAG_RE = Regex("<[^>]+>")
private val ENTITY_RE = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")
private val SPACE_RE = Regex("\\s+")
private val TITLE_RE = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val TERM_SPLIT = Regex("[^\\p{L}\\p{N}]+")
private val DDG_A = Regex("<a\\s[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*>", RegexOption.IGNORE_CASE)
private val HREF_RE = Regex("href=\"([^\"]+)\"")
private val DDG_SNIPPET = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)

private const val UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
private const val MAX_PAGE = 1_500_000L

sealed class ToolCall {
    class Search(val q: String) : ToolCall()
    class Fetch(val url: String, val q: String) : ToolCall()
}

class Hit(val title: String, val url: String, val snippet: String)

fun toolCalls(text: String): List<ToolCall> {
    val out = ArrayList<ToolCall>()
    SEARCH_RE.findAll(text).forEach { out.add(ToolCall.Search(decodeEntities(it.groupValues[1]).trim())) }
    FETCH_RE.findAll(text).forEach { out.add(ToolCall.Fetch(decodeEntities(it.groupValues[1]).trim(), decodeEntities(it.groupValues[2]).trim())) }
    return out.distinctBy { if (it is ToolCall.Search) "s" + it.q else "f" + (it as ToolCall.Fetch).url }
}

fun describeCalls(calls: List<ToolCall>): String {
    val first = calls.first()
    val label = if (first is ToolCall.Search) "Tìm: " + first.q else "Đọc: " + (first as ToolCall.Fetch).url.removePrefix("https://")
    return (label.take(40)) + if (calls.size > 1) " +${calls.size - 1}" else ""
}

fun decodeEntities(s: String): String {
    if (!s.contains('&')) return s
    return ENTITY_RE.replace(s) { m ->
        val g = m.groupValues[1]
        when {
            g.startsWith("#x") || g.startsWith("#X") -> g.substring(2).toIntOrNull(16)?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: m.value
            g.startsWith("#") -> g.substring(1).toIntOrNull()?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: m.value
            g == "amp" -> "&"
            g == "lt" -> "<"
            g == "gt" -> ">"
            g == "quot" -> "\""
            g == "apos" -> "'"
            g == "nbsp" -> " "
            else -> m.value
        }
    }
}

fun htmlToText(html: String): String {
    var t = COMMENT_RE.replace(html, "")
    t = DROP_RE.replace(t, "")
    t = BLOCK_TAG_RE.replace(t, "\n")
    t = TAG_RE.replace(t, "")
    t = decodeEntities(t)
    return t.split('\n').map { SPACE_RE.replace(it, " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}

fun pageTitle(html: String): String =
    TITLE_RE.find(html)?.let { SPACE_RE.replace(decodeEntities(it.groupValues[1]), " ").trim() }.orEmpty()

fun pickRelevant(text: String, q: String, budget: Int): String {
    if (text.length <= budget) return text
    val terms = q.lowercase().split(TERM_SPLIT).filter { it.length >= 3 }.toSet()
    if (terms.isEmpty()) return text.take(budget)
    val paras = text.split('\n')
    val scored = paras.mapIndexed { i, p ->
        val l = p.lowercase()
        val hits = terms.count { l.contains(it) }
        Triple(i, p, if (p.length < 30) 0 else hits * 2 + if (i < 3) 1 else 0)
    }
    val chosen = HashSet<Int>()
    var used = 0
    for ((i, p, s) in scored.filter { it.third > 0 }.sortedByDescending { it.third }) {
        val cost = p.length.coerceAtMost(900) + 1
        if (used + cost > budget) continue
        chosen.add(i)
        used += cost
    }
    if (chosen.isEmpty()) return text.take(budget)
    return chosen.sorted().joinToString("\n") { paras[it].take(900) }
}

fun blockedAddress(a: InetAddress): Boolean =
    a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress ||
        (a is Inet6Address && (a.address[0].toInt() and 0xFE) == 0xFC)

fun urlAllowed(scheme: String, host: String): Boolean {
    if (scheme != "https") return false
    val h = host.lowercase()
    if (h == "localhost" || h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".localdomain")) return false
    val literal = h.contains(':') || h.all { it.isDigit() || it == '.' }
    if (literal) return try {
        !blockedAddress(InetAddress.getByName(h))
    } catch (e: Exception) {
        false
    }
    return true
}

private fun resolveDdg(href: String): String? {
    var u = decodeEntities(href)
    if (u.startsWith("//")) u = "https:$u"
    val i = u.indexOf("uddg=")
    if (i >= 0) {
        val rest = u.substring(i + 5)
        u = URLDecoder.decode(rest.substringBefore('&'), "UTF-8")
    }
    if (u.contains("duckduckgo.com/y.js")) return null
    return if (u.startsWith("http://") || u.startsWith("https://")) u else null
}

fun parseDdg(html: String, max: Int): List<Hit> {
    val tags = DDG_A.findAll(html).toList()
    val out = ArrayList<Hit>()
    for ((idx, m) in tags.withIndex()) {
        if (out.size >= max) break
        val href = HREF_RE.find(m.value)?.groupValues?.get(1) ?: continue
        val url = resolveDdg(href) ?: continue
        val close = html.indexOf("</a>", m.range.last + 1)
        if (close < 0) continue
        val title = SPACE_RE.replace(decodeEntities(TAG_RE.replace(html.substring(m.range.last + 1, close), "")), " ").trim()
        val end = if (idx + 1 < tags.size) tags[idx + 1].range.first else html.length
        val snip = DDG_SNIPPET.find(html.substring(close, end))?.groupValues?.get(1).orEmpty()
        val snippet = SPACE_RE.replace(decodeEntities(TAG_RE.replace(snip, "")), " ").trim()
        if (title.isNotEmpty()) out.add(Hit(title, url, snippet))
    }
    return out
}

fun formatHits(q: String, hits: List<Hit>): String {
    if (hits.isEmpty()) return "<web query=\"$q\">\nKhông có kết quả.\n</web>"
    val sb = StringBuilder("<web query=\"").append(q).append("\">\n")
    hits.forEachIndexed { i, h ->
        sb.append(i + 1).append(". ").append(h.title.take(120)).append('\n').append(h.url).append('\n')
        if (h.snippet.isNotEmpty()) sb.append(h.snippet.take(260)).append('\n')
    }
    return sb.append("</web>").toString()
}

private object SafeDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all = Dns.SYSTEM.lookup(hostname)
        if (all.any { blockedAddress(it) }) throw UnknownHostException("Địa chỉ nội bộ bị chặn")
        return all
    }
}

class WebTools(private val tavilyKey: () -> String) {
    private val calls = ConcurrentLinkedQueue<Call>()
    private val cache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > 24
    }

    private val http = OkHttpClient.Builder()
        .dns(SafeDns)
        .addNetworkInterceptor(Interceptor { chain ->
            val u = chain.request().url
            if (!urlAllowed(u.scheme, u.host)) throw IOException("Địa chỉ không được phép")
            chain.proceed(chain.request())
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    val engine: String get() = if (tavilyKey().isNotEmpty()) "Tavily" else "DuckDuckGo"

    fun cancel() {
        calls.forEach { it.cancel() }
    }

    private fun <T> exec(req: Request, block: (okhttp3.Response) -> T): T {
        val call = http.newCall(req)
        calls.add(call)
        try {
            return call.execute().use(block)
        } finally {
            calls.remove(call)
        }
    }

    private fun cached(key: String): String? = synchronized(cache) { cache[key] }

    private fun store(key: String, v: String): String {
        synchronized(cache) { cache[key] = v }
        return v
    }

    private fun tavily(q: String, key: String): List<Hit> {
        val body = JSONObject().put("query", q).put("max_results", 5).put("search_depth", "basic")
            .toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("https://api.tavily.com/search")
            .header("Authorization", "Bearer $key").post(body).build()
        return exec(req) { r ->
            val txt = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("Tavily ${r.code}")
            val arr = JSONObject(txt).optJSONArray("results")
            val out = ArrayList<Hit>()
            if (arr != null) for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(Hit(o.optString("title"), o.optString("url"), o.optString("content")))
            }
            out
        }
    }

    private fun duck(q: String): List<Hit> {
        val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8")
        val req = Request.Builder().url(url).header("User-Agent", UA).header("Accept-Language", "vi,en;q=0.8").build()
        return exec(req) { r ->
            val html = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("DuckDuckGo ${r.code}")
            val hits = parseDdg(html, 5)
            if (hits.isEmpty() && html.contains("anomaly", true)) throw IOException("DuckDuckGo chặn tạm thời")
            hits
        }
    }

    fun search(q: String): String {
        val key = "s|" + q.lowercase()
        cached(key)?.let { return it }
        val tk = tavilyKey()
        var hits: List<Hit>? = null
        var err = ""
        if (tk.isNotEmpty()) {
            try {
                hits = tavily(q, tk)
            } catch (e: Exception) {
                err = e.message.orEmpty()
            }
        }
        if (hits == null || hits.isEmpty()) {
            try {
                hits = duck(q)
            } catch (e: Exception) {
                return "<web query=\"$q\">\nTìm kiếm thất bại: ${e.message ?: err}\n</web>"
            }
        }
        return store(key, formatHits(q, hits ?: emptyList()))
    }

    fun fetch(url: String, q: String, cap: Int): String {
        val key = "f|$url|${q.lowercase()}|$cap"
        cached(key)?.let { return it }
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null || !urlAllowed(parsed.scheme, parsed.host)) {
            return "<page url=\"$url\">\nURL không hợp lệ hoặc không được phép (chỉ https công khai).\n</page>"
        }
        val req = Request.Builder().url(parsed).header("User-Agent", UA).header("Accept", "text/html,text/plain;q=0.9,*/*;q=0.5").build()
        return try {
            exec(req) { r ->
                if (!r.isSuccessful) return@exec "<page url=\"$url\">\nLỗi ${r.code}.\n</page>"
                val type = r.header("Content-Type").orEmpty().lowercase()
                val textual = type.isEmpty() || type.contains("text") || type.contains("json") || type.contains("xml")
                if (!textual) return@exec "<page url=\"$url\">\nKhông đọc được loại nội dung $type.\n</page>"
                val body = r.body!!
                val src = body.source()
                src.request(MAX_PAGE)
                val bytes = src.buffer.readByteArray(minOf(src.buffer.size, MAX_PAGE))
                val raw = String(bytes, body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                val isHtml = type.contains("html") || raw.trimStart().startsWith("<")
                val title = if (isHtml) pageTitle(raw) else ""
                val text = if (isHtml) htmlToText(raw) else raw
                val picked = pickRelevant(text, q, cap)
                store(key, "<page url=\"$url\" title=\"${title.take(100)}\">\n$picked\n</page>")
            }
        } catch (e: Exception) {
            "<page url=\"$url\">\nKhông tải được: ${e.message}\n</page>"
        }
    }

    fun run(c: ToolCall, cap: Int): String = when (c) {
        is ToolCall.Search -> search(c.q)
        is ToolCall.Fetch -> fetch(c.url, c.q, cap)
    }
}
