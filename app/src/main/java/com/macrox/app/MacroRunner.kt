package com.macrox.app

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

class MacroRunner(
    private val context: Context,
    private val isPaused: () -> Boolean,
    private val isStopped: () -> Boolean
) {
    suspend fun execute(macro: Macro, speed: Float): Result<Unit> {
        val service = MacroAccessibilityService.instance
            ?: return Result.failure(IllegalStateException("Accessibility service is not enabled."))

        val repeatLimit = macro.repeatCount.coerceAtLeast(1)
        return try {
            repeat(repeatLimit) {
                for (step in macro.steps) {
                    while (isPaused() && !isStopped()) delay(50)
                    if (isStopped()) return Result.failure(IllegalStateException("Stopped by user"))
                    runStep(service, step, speed)
                    if (isStopped()) return Result.failure(IllegalStateException("Stopped by user"))
                    if (step.delayAfterMs > 0) delay(scaled(step.delayAfterMs, speed))
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            // Never leave the left button held down if the macro ends, fails, or is stopped mid-hold.
            withContext(NonCancellable) {
                if (service.isHoldingMouse) service.mouseUp()
            }
        }
    }

    private suspend fun runStep(service: MacroAccessibilityService, step: MacroStep, speed: Float) {
        when (step.type) {
            ActionType.DELAY -> delay(scaled(step.durationMs, speed))
            ActionType.MOUSE_LEFT_CLICK -> accept(service.click(step.x1, step.y1, scaled(step.durationMs, speed)))
            ActionType.MOUSE_LEFT_DOWN -> {
                if (service.isHoldingMouse) throw IllegalStateException("Mouse Down while the left button is already held.")
                accept(service.mouseDown(step.x1, step.y1))
            }
            ActionType.MOUSE_LEFT_UP -> {
                if (!service.isHoldingMouse) throw IllegalStateException("Mouse Up has no matching Mouse Down.")
                accept(service.mouseUp())
            }
            ActionType.KEY_PRESS -> {
                val key = MacroKeys.find(step.key)
                    ?: throw IllegalStateException("Key step has no valid key selected.")
                // Android has no public API for one app to send key events to another.
                // KeyInjector uses Shizuku (ADB-level shell) or root to run `input keyevent`.
                if (!KeyInjector.press(key.keyCode)) {
                    throw IllegalStateException("Key ${key.label} needs Shizuku (running and granted) or root.")
                }
            }
        }
    }

    private fun accept(ok: Boolean) {
        if (!ok) throw IllegalStateException("Android rejected the accessibility gesture.")
    }

    private fun scaled(ms: Long, speed: Float): Long =
        (ms.coerceAtLeast(0L) / speed.coerceIn(0.25f, 4f)).toLong().coerceAtLeast(1L)
}

enum class ShizukuState { NOT_RUNNING, NOT_GRANTED, READY }

/**
 * Sends key presses through the shell identity that Shizuku (or root) provides.
 * Only key *presses* are possible this way: `input keyevent` cannot hold a key down for a set time.
 */
object KeyInjector {
    private const val SHIZUKU_REQUEST_CODE = 4107
    private const val COMMAND_TIMEOUT_SECONDS = 10L

    fun shizukuState(): ShizukuState = runCatching {
        when {
            !Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
            else -> ShizukuState.NOT_GRANTED
        }
    }.getOrDefault(ShizukuState.NOT_RUNNING)

    fun requestShizukuPermission() {
        runCatching {
            if (!Shizuku.isPreV11()) Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    /** One key press (down + up). Tries Shizuku first, then root. */
    suspend fun press(keyCode: Int): Boolean = withContext(Dispatchers.IO) {
        val command = "input keyevent $keyCode"
        (shizukuState() == ShizukuState.READY &&
            runCommand { Shizuku.newProcess(arrayOf("sh", "-c", command), null, null) }) ||
            runCommand { ProcessBuilder("su", "-c", command).redirectErrorStream(true).start() }
    }

    private fun runCommand(start: () -> Process): Boolean = runCatching {
        val process = start()
        // Wait with a timeout instead of reading to EOF: a stuck root prompt must not hang the macro.
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroy()
            false
        } else {
            process.exitValue() == 0
        }
    }.getOrDefault(false)
}
