package com.patrollink.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.patrollink.MainActivity
import com.patrollink.R
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.PatrolNotificationGateway

class AndroidPatrolNotificationGateway(
    private val context: Context
) : PatrolNotificationGateway {
    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun notifySosActive(location: GpsLocation) {
        ensureChannel()
        vibrate()
        notify(
            id = 2001,
            title = "SOS 紧急上报已激活",
            body = "位置 ${"%.4f".format(location.latitude)}, ${"%.4f".format(location.longitude)}"
        )
    }

    override fun notifyAlert(title: String, body: String) {
        ensureChannel()
        notify(id = 2002, title = title, body = body)
    }

    override fun notifyVersionUpdate(versionName: String, forceUpdate: Boolean) {
        ensureChannel()
        notify(
            id = 2100 + versionName.hashCode().absoluteValue % 500,
            title = if (forceUpdate) "PatrolLink 必须更新" else "PatrolLink 新版本可用",
            body = "版本 $versionName 已发布，点击进入应用完成校验和安装"
        )
    }

    private fun notify(id: Int, title: String, body: String) {
        manager.notify(id, buildNotification(title, body))
    }

    private fun buildNotification(title: String, body: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .build()
    }

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "执法事件提醒", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 80, 240), -1))
    }

    private companion object {
        const val CHANNEL_ID = "patrol_events"
    }
}

private val Int.absoluteValue: Int
    get() = if (this == Int.MIN_VALUE) 0 else kotlin.math.abs(this)
