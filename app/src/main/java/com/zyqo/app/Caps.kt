package com.zyqo.app

import org.json.JSONException
import org.json.JSONObject

enum class ToolSupport { UNKNOWN, YES, NO }

class CapBook {
    private val tools = HashMap<String, ToolSupport>()

    private fun id(p: Provider, model: String): String = p.name + "|" + model

    @Synchronized
    fun tools(p: Provider, model: String): ToolSupport = tools[id(p, model)] ?: ToolSupport.UNKNOWN

    @Synchronized
    fun learnTools(p: Provider, model: String, v: ToolSupport) {
        if (v == ToolSupport.UNKNOWN) tools.remove(id(p, model)) else tools[id(p, model)] = v
    }

    @Synchronized
    fun export(): String {
        val o = JSONObject()
        for ((k, v) in tools) o.put(k, v.name)
        return o.toString()
    }

    @Synchronized
    fun load(raw: String) {
        if (raw.isBlank()) return
        try {
            val o = JSONObject(raw)
            for (k in o.keys()) {
                val v = ToolSupport.entries.firstOrNull { it.name == o.optString(k) } ?: continue
                tools[k] = v
            }
        } catch (e: JSONException) {
        }
    }
}
