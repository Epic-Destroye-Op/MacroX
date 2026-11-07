package com.macrox.app

import android.view.KeyEvent

enum class ActionType {
    DELAY,
    KEY_PRESS,
    MOUSE_LEFT_CLICK, MOUSE_LEFT_DOWN, MOUSE_LEFT_UP;

    companion object {
        /** Unknown saved names return null so the store skips that step instead of crashing. */
        fun fromName(name: String?): ActionType? = entries.firstOrNull { it.name == name }
    }
}

data class MacroStep(
    val type: ActionType,
    val x1: Int = 0,
    val y1: Int = 0,
    val x2: Int = 0,
    val y2: Int = 0,
    val durationMs: Long = 250L,
    val delayAfterMs: Long = 0L,
    /** MacroKeys id (e.g. "W", "F1", "SPACE") for KEY_PRESS steps. Null for every other type. */
    val key: String? = null
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

enum class KeyCategory(val label: String) {
    LETTERS("Letters"),
    NUMBERS("Numbers"),
    FUNCTIONS("Function Keys"),
    NAVIGATION("Navigation"),
    MODIFIERS("Modifiers"),
    SPECIAL("Special Keys")
}

data class MacroKeyDef(
    val id: String,
    val label: String,
    val category: KeyCategory,
    val keyCode: Int
)

/** Android KeyEvent codes. Injection of these is only possible through root, see MacroRunner. */
object MacroKeys {
    val all: List<MacroKeyDef> = buildList {
        ('A'..'Z').forEachIndexed { i, c ->
            add(MacroKeyDef(c.toString(), c.toString(), KeyCategory.LETTERS, KeyEvent.KEYCODE_A + i))
        }
        ('0'..'9').forEachIndexed { i, c ->
            add(MacroKeyDef(c.toString(), c.toString(), KeyCategory.NUMBERS, KeyEvent.KEYCODE_0 + i))
        }
        (1..12).forEach { n ->
            add(MacroKeyDef("F$n", "F$n", KeyCategory.FUNCTIONS, KeyEvent.KEYCODE_F1 + n - 1))
        }
        add(MacroKeyDef("UP", "Up", KeyCategory.NAVIGATION, KeyEvent.KEYCODE_DPAD_UP))
        add(MacroKeyDef("DOWN", "Down", KeyCategory.NAVIGATION, KeyEvent.KEYCODE_DPAD_DOWN))
        add(MacroKeyDef("LEFT", "Left", KeyCategory.NAVIGATION, KeyEvent.KEYCODE_DPAD_LEFT))
        add(MacroKeyDef("RIGHT", "Right", KeyCategory.NAVIGATION, KeyEvent.KEYCODE_DPAD_RIGHT))
        add(MacroKeyDef("HOME", "Home", KeyCategory.NAVIGATION, KeyEvent.KEYCODE_MOVE_HOME))
        add(MacroKeyDef("END", "End", KeyCategory.NAVIGATION, KeyEvent.KEYCODE_MOVE_END))
        add(MacroKeyDef("PGUP", "PgUp", KeyCategory.NAVIGATION, KeyEvent.KEYCODE_PAGE_UP))
        add(MacroKeyDef("PGDN", "PgDn", KeyCategory.NAVIGATION, KeyEvent.KEYCODE_PAGE_DOWN))
        add(MacroKeyDef("INSERT", "Insert", KeyCategory.NAVIGATION, KeyEvent.KEYCODE_INSERT))
        add(MacroKeyDef("DELETE", "Delete", KeyCategory.NAVIGATION, KeyEvent.KEYCODE_FORWARD_DEL))
        add(MacroKeyDef("SHIFT", "Shift", KeyCategory.MODIFIERS, KeyEvent.KEYCODE_SHIFT_LEFT))
        add(MacroKeyDef("CTRL", "Ctrl", KeyCategory.MODIFIERS, KeyEvent.KEYCODE_CTRL_LEFT))
        add(MacroKeyDef("ALT", "Alt", KeyCategory.MODIFIERS, KeyEvent.KEYCODE_ALT_LEFT))
        add(MacroKeyDef("META", "Win", KeyCategory.MODIFIERS, KeyEvent.KEYCODE_META_LEFT))
        add(MacroKeyDef("SPACE", "Space", KeyCategory.SPECIAL, KeyEvent.KEYCODE_SPACE))
        add(MacroKeyDef("ENTER", "Enter", KeyCategory.SPECIAL, KeyEvent.KEYCODE_ENTER))
        add(MacroKeyDef("TAB", "Tab", KeyCategory.SPECIAL, KeyEvent.KEYCODE_TAB))
        add(MacroKeyDef("ESC", "Esc", KeyCategory.SPECIAL, KeyEvent.KEYCODE_ESCAPE))
        add(MacroKeyDef("BACKSPACE", "Backspace", KeyCategory.SPECIAL, KeyEvent.KEYCODE_DEL))
        add(MacroKeyDef("MINUS", "-", KeyCategory.SPECIAL, KeyEvent.KEYCODE_MINUS))
        add(MacroKeyDef("EQUALS", "=", KeyCategory.SPECIAL, KeyEvent.KEYCODE_EQUALS))
        add(MacroKeyDef("LBRACKET", "[", KeyCategory.SPECIAL, KeyEvent.KEYCODE_LEFT_BRACKET))
        add(MacroKeyDef("RBRACKET", "]", KeyCategory.SPECIAL, KeyEvent.KEYCODE_RIGHT_BRACKET))
        add(MacroKeyDef("BACKSLASH", "\\", KeyCategory.SPECIAL, KeyEvent.KEYCODE_BACKSLASH))
        add(MacroKeyDef("SEMICOLON", ";", KeyCategory.SPECIAL, KeyEvent.KEYCODE_SEMICOLON))
        add(MacroKeyDef("APOSTROPHE", "'", KeyCategory.SPECIAL, KeyEvent.KEYCODE_APOSTROPHE))
        add(MacroKeyDef("GRAVE", "`", KeyCategory.SPECIAL, KeyEvent.KEYCODE_GRAVE))
        add(MacroKeyDef("COMMA", ",", KeyCategory.SPECIAL, KeyEvent.KEYCODE_COMMA))
        add(MacroKeyDef("PERIOD", ".", KeyCategory.SPECIAL, KeyEvent.KEYCODE_PERIOD))
        add(MacroKeyDef("SLASH", "/", KeyCategory.SPECIAL, KeyEvent.KEYCODE_SLASH))
    }

    fun find(id: String?): MacroKeyDef? = if (id == null) null else all.firstOrNull { it.id == id }
}

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
    const val KEY_OUTPUT = "key_output"
    const val OUTPUT_SHIZUKU = "SHIZUKU_ROOT"
    const val OUTPUT_KEYBOARD = "KEYBOARD"
}
