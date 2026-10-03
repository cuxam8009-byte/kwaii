package com.zyqo.app

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val MCP_VERSION = "2025-03-26"
private val NAME_BAD = Regex("[^A-Za-z0-9_.-]")

class McpException(message: String) : Exception(message)

private class SessionExpired : Exception()

class McpTool(val name: String, val description: String, val params: List<Param>, val readOnly: Boolean)

fun mcpSlug(name: String): String {
    val s = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(12)
    return s.ifEmpty { "mcp" }
}

private fun paramsOf(schema: JSONObject?): List<Param> {
    if (schema == null) return emptyList()
    val props = schema.optJSONObject("properties") ?: return emptyList()
    val req = HashSet<String>()
    schema.optJSONArray("required")?.let { a -> for (i in 0 until a.length()) req.add(a.optString(i)) }
    val out = ArrayList<Param>()
    val keys = props.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        val t = props.optJSONObject(k)?.opt("type")
        val type = when (t) {
            is String -> t
            is JSONArray -> (0 until t.length()).map { t.optString(it) }.firstOrNull { it != "null" } ?: "any"
            else -> "any"
        }
        out.add(Param(k, type, k in req))
    }
    return out.sortedByDescending { it.required }.take(12)
}

class McpClient {
    private val http = OkHttpClient.Builder()
        .addNetworkInterceptor(Interceptor { chain ->
            if (chain.request().url.scheme != "https") throw IOException("MCP chỉ hỗ trợ https")
            chain.proceed(chain.request())
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    private val json = "application/json".toMediaType()
    private val sessions = ConcurrentHashMap<String, String>()
    private val versions = ConcurrentHashMap<String, String>()
    private val ready = ConcurrentHashMap.newKeySet<String>()
    private val seq = AtomicInteger(1)

    fun forget(id: String) {
        sessions.remove(id)
        versions.remove(id)
        ready.remove(id)
    }

    private fun match(text: String, id: Int): JSONObject? {
        val t = text.trim()
        if (t.isEmpty()) return null
        try {
            if (t.startsWith("[")) {
                val a = JSONArray(t)
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    if (o.optInt("id", -1) == id && (o.has("result") || o.has("error"))) return o
                }
                return null
            }
            val o = JSONObject(t)
            return if (o.optInt("id", -1) == id && (o.has("result") || o.has("error"))) o else null
        } catch (e: JSONException) {
            return null
        }
    }

    private fun send(s: McpServer, body: JSONObject, expect: Int?): JSONObject? {
        val rb = Request.Builder().url(s.url)
            .post(body.toString().toRequestBody(json))
            .header("Accept", "application/json, text/event-stream")
        if (s.token.isNotEmpty()) rb.header("Authorization", "Bearer " + s.token)
        sessions[s.id]?.let { rb.header("Mcp-Session-Id", it) }
        versions[s.id]?.let { rb.header("MCP-Protocol-Version", it) }
        http.newCall(rb.build()).execute().use { r ->
            r.header("Mcp-Session-Id")?.let { sessions[s.id] = it }
            if (r.code == 404 && sessions.containsKey(s.id) && expect != null) throw SessionExpired()
            if (!r.isSuccessful) {
                val msg = r.body?.string().orEmpty().take(200)
                throw McpException("HTTP ${r.code}" + if (msg.isNotBlank()) ": $msg" else "")
            }
            if (expect == null) return null
            val src = r.body!!.source()
            if (r.header("Content-Type").orEmpty().contains("text/event-stream")) {
                val data = StringBuilder()
                while (true) {
                    val line = src.readUtf8Line()
                    if (line == null || line.isEmpty()) {
                        if (data.isNotEmpty()) {
                            match(data.toString(), expect)?.let { return it }
                            data.setLength(0)
                        }
                        if (line == null) break
                        continue
                    }
                    if (line.startsWith("data:")) data.append(line.substring(5).trim())
                }
                throw McpException("Máy chủ không trả phản hồi")
            }
            return match(src.readUtf8(), expect) ?: throw McpException("Phản hồi không hợp lệ")
        }
    }

    private fun call(s: McpServer, method: String, params: JSONObject?): JSONObject {
        val id = seq.getAndIncrement()
        val body = JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method)
        if (params != null) body.put("params", params)
        val resp = send(s, body, id) ?: throw McpException("Không có phản hồi")
        resp.optJSONObject("error")?.let { throw McpException(it.optString("message", "Lỗi MCP")) }
        return resp.optJSONObject("result") ?: JSONObject()
    }

    private fun init(s: McpServer) {
        forget(s.id)
        val info = JSONObject().put("name", "zyqo").put("version", BuildInfo.VERSION)
        val res = call(
            s, "initialize",
            JSONObject().put("protocolVersion", MCP_VERSION).put("capabilities", JSONObject()).put("clientInfo", info)
        )
        versions[s.id] = res.optString("protocolVersion", MCP_VERSION)
        send(s, JSONObject().put("jsonrpc", "2.0").put("method", "notifications/initialized"), null)
        ready.add(s.id)
    }

    private fun rpc(s: McpServer, method: String, params: JSONObject?): JSONObject {
        if (!ready.contains(s.id)) init(s)
        return try {
            call(s, method, params)
        } catch (e: SessionExpired) {
            init(s)
            call(s, method, params)
        }
    }

    fun listTools(s: McpServer): List<McpTool> {
        val out = ArrayList<McpTool>()
        var cursor: String? = null
        var pages = 0
        do {
            val res = rpc(s, "tools/list", cursor?.let { JSONObject().put("cursor", it) })
            val arr = res.optJSONArray("tools")
            if (arr != null) for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                if (name.isEmpty()) continue
                val ro = o.optJSONObject("annotations")?.optBoolean("readOnlyHint", false) ?: false
                out.add(McpTool(name, o.optString("description"), paramsOf(o.optJSONObject("inputSchema")), ro))
            }
            cursor = res.optString("nextCursor", "").ifEmpty { null }
            pages++
        } while (cursor != null && pages < 10)
        return out
    }

    fun callTool(s: McpServer, name: String, args: JSONObject): String {
        val res = rpc(s, "tools/call", JSONObject().put("name", name).put("arguments", args))
        val sb = StringBuilder()
        val content = res.optJSONArray("content")
        if (content != null) for (i in 0 until content.length()) {
            val c = content.optJSONObject(i) ?: continue
            when (val type = c.optString("type")) {
                "text" -> sb.append(c.optString("text"))
                "image" -> sb.append("[ảnh]")
                "resource" -> sb.append(c.optJSONObject("resource")?.let { it.optString("text").ifEmpty { it.optString("uri") } } ?: "[resource]")
                else -> sb.append("[").append(type).append("]")
            }
            sb.append('\n')
        }
        if (sb.isBlank()) res.optJSONObject("structuredContent")?.let { sb.append(it.toString()) }
        val text = sb.toString().trim()
        return if (res.optBoolean("isError", false)) "Lỗi công cụ: $text" else text
    }
}

class McpHub(private val servers: () -> List<McpServer>) {
    private val client = McpClient()
    private val cache = ConcurrentHashMap<String, List<McpTool>>()

    fun has(id: String): Boolean = cache.containsKey(id)

    fun drop(id: String) {
        cache.remove(id)
        client.forget(id)
    }

    fun refresh(s: McpServer): Int {
        client.forget(s.id)
        val tools = client.listTools(s)
        cache[s.id] = tools
        return tools.size
    }

    fun specs(): List<ToolSpec> {
        val used = HashSet<String>()
        val out = ArrayList<ToolSpec>()
        for (s in servers().filter { it.enabled }) {
            val tools = cache[s.id] ?: continue
            val slug = mcpSlug(s.name)
            for (t in tools) {
                val base = (slug + "__" + NAME_BAD.replace(t.name, "_")).take(60)
                var n = base
                var k = 2
                while (!used.add(n)) {
                    n = base + k
                    k++
                }
                out.add(
                    ToolSpec(
                        n, t.description.lineSequence().firstOrNull().orEmpty().take(110), t.params,
                        t.readOnly, "mcp:" + s.id, t.name, t.description.take(600)
                    )
                )
            }
        }
        return out
    }

    private fun serverOf(spec: ToolSpec): McpServer? = servers().firstOrNull { "mcp:" + it.id == spec.origin }

    fun trusted(spec: ToolSpec): Boolean = serverOf(spec)?.trusted == true

    fun call(spec: ToolSpec, args: JSONObject): String {
        val s = serverOf(spec) ?: return "Máy chủ MCP không còn tồn tại."
        return client.callTool(s, spec.remote, args)
    }
}
