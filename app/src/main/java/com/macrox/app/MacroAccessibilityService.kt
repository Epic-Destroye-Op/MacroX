package com.macrox.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MacroAccessibilityService : AccessibilityService() {
    // A left button held by MOUSE_LEFT_DOWN, kept as a continuing stroke until MOUSE_LEFT_UP.
    private var heldStroke: GestureDescription.StrokeDescription? = null
    private var heldX = 0f
    private var heldY = 0f

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        heldStroke = null
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
            ContextCompat.startForegroundService(this, Intent(this, MacroEngineService::class.java).setAction(MacroEngineService.ACTION_TRIGGER))
            return true
        }
        return false
    }

    /** True while a MOUSE_LEFT_DOWN is waiting for its MOUSE_LEFT_UP. */
    val isHoldingMouse: Boolean get() = heldStroke != null

    /** Left click at x,y held for durationMs. */
    suspend fun click(x: Int, y: Int, durationMs: Long): Boolean =
        dispatch(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, durationMs.coerceIn(1L, 60_000L))

    /** Presses the left button at x,y and keeps it down until [mouseUp]. Touch-level only. */
    suspend fun mouseDown(x: Int, y: Int): Boolean {
        if (heldStroke != null) return false
        val stroke = GestureDescription.StrokeDescription(
            Path().apply { moveTo(x.toFloat(), y.toFloat()) },
            0L,
            1L,
            true // willContinue: the touch stays down until mouseUp continues it
        )
        val ok = dispatchStroke(stroke)
        if (ok) {
            heldStroke = stroke
            heldX = x.toFloat()
            heldY = y.toFloat()
        }
        return ok
    }

    /** Lifts the finger started by [mouseDown]. */
    suspend fun mouseUp(): Boolean {
        val previous = heldStroke ?: return false
        heldStroke = null
        val release = previous.continueStroke(
            Path().apply { moveTo(heldX, heldY) },
            0L,
            1L,
            false
        )
        return dispatchStroke(release)
    }

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean =
        dispatchStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))

    private suspend fun dispatchStroke(stroke: GestureDescription.StrokeDescription): Boolean =
        suspendCancellableCoroutine { cont ->
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
