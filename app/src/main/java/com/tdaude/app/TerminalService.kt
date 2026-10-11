package com.tdaude.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.IBinder
import android.util.Log

/**
 * Foreground service: giữ tiến trình app chạy nền để các shell không bị Android giết
 * khi bạn thoát ra màn hình chính. Thông báo có nút "Thoát" để tắt hết mọi phiên.
 */
class TerminalService : Service() {

    companion object {
        const val ACTION_EXIT = "com.tdaude.app.EXIT"
        private const val CHANNEL_ID = "tdaude_sessions"
        private const val NOTIF_ID = 1
        private const val TAG = "TDaude"

        fun start(ctx: Context) {
            try {
                ctx.startForegroundService(Intent(ctx, TerminalService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Không khởi động được service nền", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)
        nm.createNotificationChannel(channel)
        startForeground(NOTIF_ID, buildNotification())
        Sessions.onCountChanged = { refreshNotification() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT) {
            Sessions.killAll()
            Sessions.listener?.onExitRequested()
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        refreshNotification()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Sessions.onCountChanged = null
        super.onDestroy()
    }

    private fun refreshNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val count = Sessions.list.size
        val text = if (Sessions.hasWakeLock) {
            getString(R.string.notif_text_wake, count)
        } else {
            getString(R.string.notif_text, count)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val exitIntent = Intent(this, TerminalService::class.java)
        exitIntent.action = ACTION_EXIT
        val exitPi = PendingIntent.getService(
            this, 1, exitIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val exitAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_close),
            getString(R.string.notif_exit),
            exitPi
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openPi)
            .setOngoing(true)
            .setShowWhen(false)
            .setColor(0xFF2DD4BF.toInt())
            .addAction(exitAction)
            .build()
    }
}
