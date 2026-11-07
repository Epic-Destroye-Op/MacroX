package com.macrox.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import androidx.core.content.ContextCompat\nimport kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MacroAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        val prefs = getSharedPreferences(MacroPrefs.PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(MacroPrefs.ENABLED, false)) return false
        val configured = prefs.getString(MacroPrefs.TRIGGER, "VOLUME_UP") ?: "VOLUME_UP"
        val matched = when (configured) {
            "VOLUME_UP" -> event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
            "VOLUME_DOWN" -> event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            else -> false
        }
        if (matched) {
            ContextCompat.startForegroundService(this, Intent(this, MacroEngineService).setAction(MacroEngineService.ACTION_TRIGGER))
            return true
        }
        return false
    }

    suspend fun tap(x: Int, y: Int): Boolean =
        dispatch(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, 1L)

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean =
        dispatch(Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }, durationMs.coerceIn(1L, 60_000L))

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean =
        suspendCancellableCoroutine { cont ->
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val accepted = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null
            )
            if (!accepted && cont.isActive) cont.resume(false)
        }

    companion object {
        @Volatile var instance: MacroAccessibilityService? = null
            private set
    }
}
