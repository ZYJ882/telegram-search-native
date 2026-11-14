package app.lingogram.tgsearchnative

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SyncForegroundService : Service() {
    companion object {
        const val ACTION_START = "app.lingogram.tgsearchnative.START_SYNC"
        const val ACTION_STOP = "app.lingogram.tgsearchnative.STOP_SYNC"
        const val EXTRA_CHAT_IDS = "chat_ids"
        private const val CHANNEL_ID = "telegram_sync"
        private const val NOTIFICATION_ID = 4101
        @Volatile var running = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: ArchiveStore
    private lateinit var secure: SecureSettings
    private var gateway: TdLibGateway? = null

    override fun onCreate() {
        super.onCreate()
        store = ArchiveStore(this)
        secure = SecureSettings(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                gateway?.cancelSync("已从通知停止同步")
                stopSelf()
            }
            ACTION_START -> {
                val ids = intent.getLongArrayExtra(EXTRA_CHAT_IDS)?.toList().orEmpty()
                if (ids.isNotEmpty() && !running) startSync(ids)
            }
        }
        return START_NOT_STICKY
    }

    private fun startSync(ids: List<Long>) {
        running = true
        startForeground(NOTIFICATION_ID, notification("正在准备后台同步…", false))
        val bridge = TdLibGateway(
            this,
            secure,
            { items, afterInsert ->
                scope.launch {
                    val inserted = store.insert(items)
                    updateNotification("后台同步中：本页新增 $inserted 条索引", false)
                    afterInsert(inserted)
                }
            },
            { message -> updateNotification(message, false) },
            { message ->
                updateNotification(message, true)
                stopForeground(false)
                stopSelf()
            }
        )
        gateway = bridge
        bridge.syncSelectedChatIds(ids)
        bridge.start()
    }

    private fun updateNotification(text: String, completed: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(text, completed))
    }

    private fun notification(text: String, completed: Boolean): Notification {
        val stopIntent = Intent(this, SyncForegroundService::class.java).setAction(ACTION_STOP)
        val stopPending = android.app.PendingIntent.getService(
            this, 1, stopIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (completed) "Telegram Search Native：同步完成" else "Telegram Search Native：后台同步")
            .setContentText(text)
            .setOngoing(!completed)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply { if (!completed) addAction(0, "停止同步", stopPending) }
            .build()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Telegram 同步", NotificationManager.IMPORTANCE_LOW).apply {
                description = "本地消息索引后台同步进度和完成通知"
            }
        )
    }

    override fun onDestroy() {
        gateway?.cancelSync("后台同步服务已停止")
        gateway = null
        running = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
