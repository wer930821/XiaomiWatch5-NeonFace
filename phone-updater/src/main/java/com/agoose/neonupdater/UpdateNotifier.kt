package com.agoose.neonupdater

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object UpdateNotifier {
    const val CHANNEL_ID = "neonface_updates"
    private const val NOTIFICATION_ID = 1001
    private const val CYBER_NOTIFICATION_ID = 1002
    private const val PREFS = "update_notifications"
    private const val KEY_LAST_CYBER_VERSION = "last_cyber_version"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "NeonFace 更新通知",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "NeonFace 更新器與錶盤有新版時通知"
                }
            )
        }
    }

    fun notifyCyberWatchFace(context: Context, versionCode: Int, versionName: String?) {
        if (versionCode <= 0) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (versionCode <= prefs.getInt(KEY_LAST_CYBER_VERSION, 0)) return

        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val versionText = if (versionName.isNullOrBlank()) "" else " $versionName"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Cyber Neon City 已可下載安裝")
            .setContentText("新版錶盤$versionText 已發布")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Cyber Neon City$versionText 已發布。打開 NeonFace 更新器，按「下載 Cyber Neon City」後即可安裝到手錶。"
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(CYBER_NOTIFICATION_ID, notification)
        prefs.edit().putInt(KEY_LAST_CYBER_VERSION, versionCode).apply()
    }

    fun notifyUpdaterUpdate(context: Context, versionName: String?) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = if (versionName.isNullOrBlank()) {
            "有新版 NeonFace 更新器可以安裝"
        } else {
            "NeonFace 更新器 $versionName 已可更新"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("NeonFace 有新版本")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text。打開 App 後按「更新更新器」即可。"))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
