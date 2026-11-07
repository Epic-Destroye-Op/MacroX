package com.macrox.app

import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.TextView

/**
 * Macro X keyboard. Used only for the "Macro X keyboard" key output. It works without root or
 * Shizuku, but Android only delivers its key events to the text field that currently has focus.
 */
class MacroKeyboardService : InputMethodService() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View = TextView(this).apply {
        text = "Macro X keyboard. It only sends macro keys to a focused text field. Switch back to your normal keyboard to type."
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#111111"))
        textSize = 14f
        setPadding(48, 40, 48, 40)
    }

    private fun sendKey(keyCode: Int): Boolean {
        val connection = currentInputConnection ?: return false
        val now = SystemClock.uptimeMillis()
        connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        return true
    }

    companion object {
        @Volatile
        private var instance: MacroKeyboardService? = null

        /** Sends one key press to the focused text field. False if this keyboard is not active or no field has focus. */
        fun send(keyCode: Int): Boolean = instance?.sendKey(keyCode) ?: false

        /** True if Macro X keyboard is the active input method. */
        fun isSelected(context: Context): Boolean {
            val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?: return false
            return current.startsWith(context.packageName)
        }
    }
}
