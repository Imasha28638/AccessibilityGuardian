package com.sliit.isp.accessibilityguardian.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sliit.isp.accessibilityguardian.MainActivity
import com.sliit.isp.accessibilityguardian.R
import com.sliit.isp.accessibilityguardian.data.local.entities.SecurityAlertEntity

class AlertNotifier(
    private val context: Context
) {
    fun notifyAlert(alert: SecurityAlertEntity) {
        if (!MonitoringPreferences.isRealtimeAlertsEnabled(context)) return
        if (!MonitoringPreferences.isThreatNotificationsEnabled(context)) return
        if (alert.severity.equals("LOW", ignoreCase = true)) return

        ensureChannel()
        if (!PermissionUtils.canPostThreatNotifications(context, CHANNEL_ID)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_DESTINATION_ID, R.id.navigation_inspect)
            putExtra(MainActivity.EXTRA_PACKAGE_NAME, alert.packageName)
            putExtra(MainActivity.EXTRA_ALERT_ID, alert.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(alert.title)
            .setContentText(alert.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.evidenceText.ifBlank { alert.description }))
            .setPriority(
                if (alert.severity.equals("CRITICAL", ignoreCase = true)) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(alert.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Posting notifications is optional; detection persistence must continue even if permission is missing.
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Accessibility Guardian Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real threat alerts from the accessibility abuse detection engine."
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "guardian_alerts"
    }
}
