package com.macrox.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object MacroStore {
    private fun prefs(context: Context) =
        context.getSharedPreferences(MacroPrefs.PREFS, Context.MODE_PRIVATE)

    fun getMacros(context: Context): List<Macro> {
        val raw = prefs(context).getString(MacroPrefs.MACROS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val steps = o.optJSONArray("steps") ?: JSONArray()
                    val parsed = buildList {
                        for (j in 0 until steps.length()) {
                            val s = steps.getJSONObject(j)
                            add(
                                MacroStep(
                                    type = ActionType.valueOf(s.getString("type")),
                                    x1 = s.optInt("x1"),
                                    y1 = s.optInt("y1"),
                                    x2 = s.optInt("x2"),
                                    y2 = s.optInt("y2"),
                                    durationMs = s.optLong("durationMs", 250L),
                                    delayAfterMs = s.optLong("delayAfterMs", 0L)
                                )
                            )
                        }
                    }
                    add(
                        Macro(
                            id = o.getLong("id"),
                            name = o.getString("name"),
                            steps = parsed,
                            repeatCount = o.optInt("repeatCount", 1).coerceAtLeast(1)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveMacros(context: Context, macros: List<Macro>) {
        val arr = JSONArray()
        macros.forEach { macro ->
            val o = JSONObject()
                .put("id", macro.id)
                .put("name", macro.name)
                .put("repeatCount", macro.repeatCount.coerceAtLeast(1))
            val steps = JSONArray()
            macro.steps.forEach { s ->
                steps.put(
                    JSONObject()
                        .put("type", s.type.name)
                        .put("x1", s.x1).put("y1", s.y1)
                        .put("x2", s.x2).put("y2", s.y2)
                        .put("durationMs", s.durationMs)
                        .put("delayAfterMs", s.delayAfterMs)
                )
            }
            o.put("steps", steps)
            arr.put(o)
        }
        prefs(context).edit().putString(MacroPrefs.MACROS, arr.toString()).apply()
    }

    fun getHistory(context: Context): List<HistoryEntry> {
        val raw = prefs(context).getString(MacroPrefs.HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        HistoryEntry(
                            id = o.getLong("id"),
                            macroName = o.getString("macroName"),
                            startedAt = o.getLong("startedAt"),
                            durationMs = o.getLong("durationMs"),
                            status = o.getString("status")
                        )
                    )
                }
            }.sortedByDescending { it.startedAt }
        }.getOrDefault(emptyList())
    }

    fun addHistory(context: Context, entry: HistoryEntry) {
        val list = getHistory(context).toMutableList()
        list.add(0, entry)
        val trimmed = list.take(100)
        val arr = JSONArray()
        trimmed.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("macroName", it.macroName)
                    .put("startedAt", it.startedAt)
                    .put("durationMs", it.durationMs)
                    .put("status", it.status)
            )
        }
        prefs(context).edit().putString(MacroPrefs.HISTORY, arr.toString()).apply()
    }

    fun clearHistory(context: Context) {
        prefs(context).edit().remove(MacroPrefs.HISTORY).apply()
    }
}
