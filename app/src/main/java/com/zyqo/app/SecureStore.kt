package com.zyqo.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

class SecureStore(private val ctx: Context) {
    private val prefs: SharedPreferences? = open()
    private val memory = HashMap<String, String>()

    private fun build(): SharedPreferences {
        val master = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx,
            "zyqo_secure",
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun open(): SharedPreferences? {
        return try {
            build()
        } catch (e: Exception) {
            try {
                ctx.deleteSharedPreferences("zyqo_secure")
                build()
            } catch (e2: Exception) {
                null
            }
        }
    }

    val persistent: Boolean get() = prefs != null

    private fun read(k: String): String? = prefs?.getString(k, null) ?: memory[k]

    private fun write(k: String, v: String) {
        if (prefs != null) prefs.edit().putString(k, v).apply() else memory[k] = v
    }

    private fun drop(k: String) {
        if (prefs != null) prefs.edit().remove(k).apply() else memory.remove(k)
    }

    var keys: List<KeyEntry>
        get() {
            val raw = read("keys")
            if (raw == null) return migrate()
            return try {
                val a = JSONArray(raw)
                (0 until a.length()).mapNotNull { i ->
                    val o = a.getJSONObject(i)
                    val p = providerOf(o.getString("p")) ?: return@mapNotNull null
                    val ms = o.optJSONArray("m")
                    val models = if (ms == null) emptyList() else (0 until ms.length()).map { ms.getString(it) }
                    KeyEntry(o.getString("id"), p, o.getString("k"), models.ifEmpty { defaultModels(p) }, o.optBoolean("e", true))
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(v) {
            val a = JSONArray()
            v.forEach { e ->
                a.put(
                    JSONObject().put("id", e.id).put("p", e.provider.name).put("k", e.key)
                        .put("m", JSONArray(e.models)).put("e", e.enabled)
                )
            }
            write("keys", a.toString())
        }

    private fun migrate(): List<KeyEntry> {
        val old = read("key").orEmpty()
        val p = detectProvider(old)
        drop("key")
        val model = read("model").orEmpty()
        drop("model")
        if (old.isEmpty() || p == null) return emptyList()
        val list = listOf(KeyEntry(newId(), p, old, if (model.isEmpty()) defaultModels(p) else listOf(model)))
        keys = list
        return list
    }

    var searchKey: String
        get() = read("search_key").orEmpty()
        set(v) = if (v.isEmpty()) drop("search_key") else write("search_key", v)

    var webOn: Boolean
        get() = read("web_on") != "0"
        set(v) = write("web_on", if (v) "1" else "0")

    var mcpServers: List<McpServer>
        get() = try {
            val a = JSONArray(read("mcp") ?: "[]")
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                McpServer(o.getString("id"), o.getString("name"), o.getString("url"), o.optString("token"), o.optBoolean("trusted", false), o.optBoolean("enabled", true))
            }
        } catch (e: Exception) {
            emptyList()
        }
        set(v) {
            val a = JSONArray()
            v.forEach { s ->
                a.put(JSONObject().put("id", s.id).put("name", s.name).put("url", s.url).put("token", s.token).put("trusted", s.trusted).put("enabled", s.enabled))
            }
            write("mcp", a.toString())
        }

    var caps: String
        get() = read("caps").orEmpty()
        set(v) = write("caps", v)

    var limits: String
        get() = read("limits").orEmpty()
        set(v) = write("limits", v)

    var maxRounds: Int
        get() = read("max_rounds")?.toIntOrNull()?.coerceIn(2, 12) ?: 6
        set(v) = write("max_rounds", v.toString())

    var skill: String
        get() = read("skill").orEmpty()
        set(v) = write("skill", v)

    var customSkills: List<Skill>
        get() = try {
            val a = JSONArray(read("custom_skills") ?: "[]")
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Skill(o.getString("id"), o.getString("name"), o.optString("hint"), o.getString("prompt"), true)
            }
        } catch (e: Exception) {
            emptyList()
        }
        set(v) {
            val a = JSONArray()
            v.forEach { s ->
                a.put(JSONObject().put("id", s.id).put("name", s.name).put("hint", s.hint).put("prompt", s.prompt))
            }
            write("custom_skills", a.toString())
        }

    companion object {
        fun newId(): String = java.util.UUID.randomUUID().toString().take(8)
    }
}
