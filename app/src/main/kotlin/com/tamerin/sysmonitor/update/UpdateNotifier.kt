package com.tamerin.sysmonitor.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object UpdateNotifier {
    private const val CHANNEL = "redmonitor_updates"
    private const val NOTIF_ID = 5050

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(
                CHANNEL, "RedMonitor-Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            ch.description = "Benachrichtigt über neue Versionen von RedMonitor"
            nm.createNotificationChannel(ch)
        }
    }

    fun show(context: Context, release: ReleaseInfo) {
        if (!UpdatePrefs.notificationsEnabled(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }
        ensureChannel(context)

        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
        val pi = PendingIntent.getActivity(
            context, 1, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update verfügbar: ${release.name}")
            .setContentText("Tippen für Details & Download")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(release.body.take(500))
            )
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, n)
    }
}
