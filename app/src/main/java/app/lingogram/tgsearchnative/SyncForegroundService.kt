package app.lingogram.tgsearchnative

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class SyncForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    companion object {
        const val ACTION_START = "app.lingogram.tgsearchnative.START_SYNC"
        const val ACTION_STOP = "app.lingogram.tgsearchnative.STOP_SYNC"
        private const val CHANNEL_ID = "telegram_sync"
        private const val NOTIFICATION_ID = 4101
        @Volatile var running = false

        fun update(context: Context, text: String, completed: Boolean = false) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification(context, text, completed))
        }

        private fun buildNotification(context: Context, text: String, completed: Boolean): Notification {
            val stopIntent = Intent(context, SyncForegroundService::class.java).setAction(ACTION_STOP)
            val stopPending = android.app.PendingIntent.getService(
                context, 1, stopIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(if (completed) "Telegram Search Native：同步完成" else "Telegram Search Native：后台同步")
                .setContentText(text)
                .setOngoing(!completed)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .apply { if (!completed) addAction(0, "停止同步", stopPending) }
                .build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                running = false
                stopSelf()
            }
            ACTION_START -> {
                running = true
                val manager = getSystemService(PowerManager::class.java)
                wakeLock = manager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "TelegramSearchNative:Sync"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(this, "正在同步，锁屏后继续运行…", false),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification(this, "正在同步，锁屏后继续运行…", false))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Telegram 同步", NotificationManager.IMPORTANCE_LOW).apply {
                description = "本地消息索引后台同步进度和完成通知"
            }
        )
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
