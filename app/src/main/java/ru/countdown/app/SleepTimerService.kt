package ru.countdown.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class SleepTimerService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var timerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer(intent.getLongExtra(EXTRA_DURATION_MS, 0L))
            ACTION_CANCEL -> stopTimer()
        }
        return START_STICKY
    }

    private fun startTimer(durationMs: Long) {
        if (durationMs <= 0) return
        timerJob?.cancel()
        val endAt = System.currentTimeMillis() + durationMs
        startForeground(NOTIFICATION_ID, notification(durationMs))
        timerJob = scope.launch {
            while (isActive) {
                val remaining = endAt - System.currentTimeMillis()
                if (remaining <= 0) {
                    finishTimer()
                    break
                }
                updateNotification(remaining)
                sendBroadcast(Intent(ACTION_TICK).setPackage(packageName).putExtra(EXTRA_REMAINING_MS, remaining))
                delay(1000)
            }
        }
    }

    private fun finishTimer() {
        val policy = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, SleepDeviceAdminReceiver::class.java)
        if (policy.isAdminActive(admin)) policy.lockNow()
        sendBroadcast(Intent(ACTION_FINISHED).setPackage(packageName))
        stopTimer()
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(remainingMs: Long): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Countdown активен")
        .setContentText(format(remainingMs))
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", PendingIntent.getService(this, 1, Intent(this, SleepTimerService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .build()

    private fun updateNotification(remainingMs: Long) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(remainingMs))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Таймер сна", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "ru.countdown.app.START"
        const val ACTION_CANCEL = "ru.countdown.app.CANCEL"
        const val ACTION_TICK = "ru.countdown.app.TICK"
        const val ACTION_FINISHED = "ru.countdown.app.FINISHED"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_REMAINING_MS = "remaining_ms"
        private const val CHANNEL_ID = "sleep_timer"
        private const val NOTIFICATION_ID = 1001

        fun format(ms: Long): String {
            // Round up so a running timer never displays 00:00:00 before it finishes.
            val totalSeconds = (ms.coerceAtLeast(0) + 999) / 1_000
            val hours = totalSeconds / 3_600
            val minutes = (totalSeconds % 3_600) / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }
}
