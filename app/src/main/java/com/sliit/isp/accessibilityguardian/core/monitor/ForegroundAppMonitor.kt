package com.sliit.isp.accessibilityguardian.core.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

class ForegroundAppMonitor(
    private val context: Context
) {
    fun getCurrentForegroundPackage(): String? {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null

        val end = System.currentTimeMillis()
        val start = end - 10_000L

        val usageEvents = usageStatsManager.queryEvents(start, end)
        val event = UsageEvents.Event()

        var lastForegroundPackage: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundPackage = event.packageName
            }
        }

        return lastForegroundPackage
    }
}
