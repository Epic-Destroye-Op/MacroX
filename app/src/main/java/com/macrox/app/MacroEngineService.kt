package com.macrox.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class MacroEngineService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var execution: Job? = null
    private var paused = false
    private var stopped = false
    private var overlay: View? = null
    private var windowManager: WindowManager? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Ready"))
        if (Settings.canDrawOverlays(this)) showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopEngine()
            ACTION_TRIGGER -> {
                val selected = getSelectedMacro()
                if (selected != null) startMacro(selected)
            }
            ACTION_RUN_SELECTED -> getSelectedMacro()?.let(::startMacro)
            ACTION_PAUSE -> paused = true
            ACTION_RESUME -> paused = false
            ACTION_EMERGENCY_STOP -> stopCurrent("STOPPED")
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
        paused = false
        updateNotification("Running ${macro.name}")
        execution = scope.launch {
            val started = System.currentTimeMillis()
            val speed = getSharedPreferences(MacroPrefs.PREFS, MODE_PRIVATE)
                .getFloat(MacroPrefs.SPEED, 1f)
            val result = MacroRunner(applicationContext, { paused }, { stopped })
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
            updateNotification(if (status == "COMPLETED") "Completed ${macro.name}" else status)
            execution = null
        }
    }

    private fun stopCurrent(status: String) {
        stopped = true
        paused = false
        updateNotification(status.replaceFirstChar { it.uppercase() })
    }

    private fun stopEngine() {
        stopped = true
        execution?.cancel()
        execution = null
        removeOverlay()
        getSharedPreferences(MacroPrefs.PREFS, MODE_PRIVATE).edit().putBoolean(MacroPrefs.ENABLED, false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showOverlay() {
        if (overlay != null || !Settings.canDrawOverlays(this)) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.argb(235, 18, 18, 18))
        }
        val title = TextView(this).apply {
            text = "Macro X"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(8, 4, 8, 8)
        }
        box.addView(title)
        fun control(label: String, action: String) {
            val b = Button(this).apply {
                text = label
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(190, 30, 30))
                setOnClickListener { startService(Intent(this@MacroEngineService, MacroEngineService::class.java).setAction(action)) }
            }
            box.addView(b, LinearLayout.LayoutParams(220, 48).apply { setMargins(0, 4, 0, 4) })
        }
        control("Start", ACTION_RUN_SELECTED)
        control("Pause", ACTION_PAUSE)
        control("Resume", ACTION_RESUME)
        control("Emergency Stop", ACTION_EMERGENCY_STOP)
        overlay = box
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 16; y = 120 }
        windowManager?.addView(box, params)
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { windowManager?.removeView(it) } }
        overlay = null
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
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
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.macrox.app.STOP"
        const val ACTION_TRIGGER = "com.macrox.app.TRIGGER"
        const val ACTION_RUN_SELECTED = "com.macrox.app.RUN_SELECTED"
        const val ACTION_PAUSE = "com.macrox.app.PAUSE"
        const val ACTION_RESUME = "com.macrox.app.RESUME"
        const val ACTION_EMERGENCY_STOP = "com.macrox.app.EMERGENCY_STOP"
        private const val CHANNEL_ID = "macrox_engine"
        private const val NOTIFICATION_ID = 4107
    }
}
