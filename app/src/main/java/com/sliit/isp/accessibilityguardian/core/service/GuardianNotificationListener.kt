package com.sliit.isp.accessibilityguardian.core.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sliit.isp.accessibilityguardian.util.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GuardianNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeInstance = this
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!ServiceLocator.repository(applicationContext).getMonitoringEnabled()) return

        val extras = sbn.notification.extras
        val title = extras?.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras?.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras?.getCharSequence("android.bigText")?.toString().orEmpty()

        val combined = "$title $text $bigText".lowercase()

        val isOtpLike = Regex("\\b(otp|verification|code|one[- ]time|authenticate|passcode)\\b")
            .containsMatchIn(combined)

        if (isOtpLike) {
            serviceScope.launch {
                ServiceLocator.repository(applicationContext)
                    .startOtpSensitivityWindow(
                        sourcePackage = sbn.packageName,
                        timestamp = System.currentTimeMillis()
                    )
            }
        }
    }

    override fun onDestroy() {
        activeInstance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var activeInstance: GuardianNotificationListener? = null

        fun shutdownIfRunning() {
            activeInstance?.stopSelf()
            activeInstance = null
        }
    }
}
