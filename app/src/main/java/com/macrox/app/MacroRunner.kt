package com.macrox.app

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

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
                    val ok = when (step.type) {
                        ActionType.TAP -> service.tap(step.x1, step.y1)
                        ActionType.SWIPE -> service.swipe(step.x1, step.y1, step.x2, step.y2, scaled(step.durationMs, speed))
                        ActionType.DELAY -> {
                            delay(scaled(step.durationMs, speed))
                            true
                        }
                    }
                    if (!ok) throw IllegalStateException("Android rejected the accessibility gesture.")
                    if (isStopped()) return Result.failure(IllegalStateException("Stopped by user"))
                    val after = scaled(step.delayAfterMs, speed)
                    if (after > 0) delay(after)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun scaled(ms: Long, speed: Float): Long =
        (ms.coerceAtLeast(0L) / speed.coerceIn(0.25f, 4f)).toLong().coerceAtLeast(1L)
}
