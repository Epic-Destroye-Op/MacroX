package com.macrox.app

enum class ActionType { TAP, SWIPE, DELAY }

data class MacroStep(
    val type: ActionType,
    val x1: Int = 0,
    val y1: Int = 0,
    val x2: Int = 0,
    val y2: Int = 0,
    val durationMs: Long = 250L,
    val delayAfterMs: Long = 0L
)

data class Macro(
    val id: Long,
    val name: String,
    val steps: List<MacroStep>,
    val repeatCount: Int = 1
)

data class HistoryEntry(
    val id: Long,
    val macroName: String,
    val startedAt: Long,
    val durationMs: Long,
    val status: String
)

object MacroPrefs {
    const val PREFS = "macrox_prefs"
    const val ENABLED = "engine_enabled"
    const val TRIGGER = "trigger_mode"
    const val SELECTED_MACRO = "selected_macro"
    const val SPEED = "execution_speed"
    const val DEFAULT_REPEAT = "default_repeat"
    const val MAX_REPEAT = "max_repeat"
    const val HISTORY = "history_json"
    const val MACROS = "macros_json"
}
