package com.zamankilidi.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Geri sayımı arka planda tutan ön plan servisi. Süre her saniye kontrol
 * edilir, bildirimde kalan süre gösterilir. Süre dolduğunda BlockActivity'i
 * "süre doldu" moduyla açar ve kendini durdurur - o andan sonra çocuğun
 * telefonu kullanmaya devam etmesini AccessibilityService zaten engelliyor
 * (izinli uygulama kalmadığı için her uygulama açılışında geri
 * yönlendiriliyor).
 */
class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    companion object {
        private const val CHANNEL_ID = "zaman_kilidi_timer"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, TimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(SessionManager.remainingMillis(this))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        scheduleTick()
        return START_STICKY
    }

    private fun scheduleTick() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                if (!SessionManager.isActive(this@TimerService)) {
                    stopSelf()
                    return
                }
                val remaining = SessionManager.remainingMillis(this@TimerService)
                if (remaining <= 0L) {
                    onTimeUp()
                    return
                }
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(remaining))
                handler.postDelayed(this, 1000L)
            }
        }
        tickRunnable = runnable
        handler.post(runnable)
    }

    private fun onTimeUp() {
        val intent = Intent(this, BlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BlockActivity.EXTRA_MODE, BlockActivity.MODE_TIME_UP)
        }
        startActivity(intent)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(remainingMillis: Long): android.app.Notification {
        val minutes = (remainingMillis / 60000L).toInt()
        val seconds = ((remainingMillis / 1000L) % 60L).toInt()
        val text = if (remainingMillis > 0) {
            getString(R.string.notification_remaining_format, minutes, seconds)
        } else {
            getString(R.string.time_up_title)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title_active))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                nm.createNotificationChannel(channel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tickRunnable?.let { handler.removeCallbacks(it) }
    }
}
