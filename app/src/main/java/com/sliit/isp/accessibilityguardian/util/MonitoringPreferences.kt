package com.sliit.isp.accessibilityguardian.util

import android.content.Context

object MonitoringPreferences {

    private const val PREFS_NAME = "monitoring_prefs"
    private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
    private const val KEY_REALTIME_ALERTS_ENABLED = "realtime_alerts_enabled"
    private const val KEY_THREAT_NOTIFICATIONS_ENABLED = "threat_notifications_enabled"
    private const val KEY_SENSITIVITY_PERCENT = "sensitivity_percent"

    fun isMonitoringEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_MONITORING_ENABLED, true)
    }

    fun setMonitoringEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()
    }

    fun isRealtimeAlertsEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_REALTIME_ALERTS_ENABLED, true)
    }

    fun setRealtimeAlertsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REALTIME_ALERTS_ENABLED, enabled).apply()
    }

    fun isThreatNotificationsEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_THREAT_NOTIFICATIONS_ENABLED, true)
    }

    fun setThreatNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_THREAT_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun sensitivityPercent(context: Context): Int {
        return prefs(context).getInt(KEY_SENSITIVITY_PERCENT, 65)
    }

    fun setSensitivityPercent(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_SENSITIVITY_PERCENT, value.coerceIn(0, 100)).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
