package com.macrox.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MacroEngineService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var execution: Job? = null
    private var stopped = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Ready"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP, ACTION_EMERGENCY_STOP -> stopEngine()
            ACTION_TRIGGER, ACTION_RUN_SELECTED -> {
                val macro = getSelectedMacro()
                if (macro == null) {
                    updateNotification("No macro selected")
                } else {
                    startMacro(macro)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun getSelectedMacro(): Macro? {
        val id = getSharedPreferences(MacroPrefs.PREFS, MODE_PRIVATE)
            .getLong(MacroPrefs.SELECTED_MACRO, -1L)
        return MacroStore.getMacros(this).firstOrNull { it.id == id }
    }

    private fun startMacro(macro: Macro) {
        if (execution?.isActive == true) return
        stopped = false
        isRunning = true
        updateNotification("Running ${macro.name}")
        execution = scope.launch {
            val started = System.currentTimeMillis()
            val speed = getSharedPreferences(MacroPrefs.PREFS, MODE_PRIVATE)
                .getFloat(MacroPrefs.SPEED, 1f)
            val result = MacroRunner(applicationContext, { false }, { stopped })
                .execute(macro, speed)
            val status = when {
                result.isSuccess -> "COMPLETED"
                stopped -> "STOPPED"
                else -> "FAILED"
            }
            MacroStore.addHistory(
                applicationContext,
                HistoryEntry(System.currentTimeMillis(), macro.name, started, System.currentTimeMillis() - started, status)
            )
            isRunning = false
            execution = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopEngine() {
        stopped = true
        execution?.cancel()
        execution = null
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Macro X engine", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Macro X")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    override fun onDestroy() {
        scope.cancel()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile var isRunning: Boolean = false

        const val ACTION_STOP = "com.macrox.app.STOP"
        const val ACTION_TRIGGER = "com.macrox.app.TRIGGER"
        const val ACTION_RUN_SELECTED = "com.macrox.app.RUN_SELECTED"
        const val ACTION_EMERGENCY_STOP = "com.macrox.app.EMERGENCY_STOP"
        private const val CHANNEL_ID = "macrox_engine"
        private const val NOTIFICATION_ID = 4107
    }
}
