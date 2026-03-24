package com.sliit.isp.accessibilityguardian.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.sliit.isp.accessibilityguardian.BlockedActivity
import com.sliit.isp.accessibilityguardian.core.monitor.DeviceIntegrityResult
import com.sliit.isp.accessibilityguardian.core.service.GuardianAccessibilityService
import com.sliit.isp.accessibilityguardian.core.service.GuardianNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object IntegrityBlocker {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enforce(activity: Activity, result: DeviceIntegrityResult) {
        enforce(activity.applicationContext, result)

        val blockedIntent = BlockedActivity.createIntent(activity, result).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        activity.startActivity(blockedIntent)
        activity.finish()
    }

    fun enforce(context: Context, result: DeviceIntegrityResult) {
        val appContext = context.applicationContext
        val repository = ServiceLocator.repository(appContext)

        repository.setMonitoringEnabled(false)
        GuardianAccessibilityService.shutdownIfRunning()
        GuardianNotificationListener.shutdownIfRunning()
        appContext.stopService(Intent(appContext, GuardianAccessibilityService::class.java))
        appContext.stopService(Intent(appContext, GuardianNotificationListener::class.java))

        ioScope.launch {
            repository.logIntegrityBlock(result)
        }
    }
}
