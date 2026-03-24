package com.sliit.isp.accessibilityguardian.ui

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sliit.isp.accessibilityguardian.data.local.entities.SecurityAlertEntity
import com.sliit.isp.accessibilityguardian.data.repository.SecurityRepository
import com.sliit.isp.accessibilityguardian.util.PermissionUtils
import com.sliit.isp.accessibilityguardian.util.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class MonitorUiState(
    val monitoringEnabled: Boolean = true,
    val serviceActive: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val notificationAccessEnabled: Boolean = false,
    val usageAccessEnabled: Boolean = false,
    val suspiciousApps: List<SuspiciousApp> = emptyList(),
    val selectedFilter: MonitorSeverityFilter = MonitorSeverityFilter.ALL,
    val securityLogs: List<SecurityLog> = emptyList(),
    val alertCount: Int = 0,
    val scannedCount: Int = 0,
    val blockedCount: Int = 0,
    val overallRiskScore: Int = 0,
    val overallRiskSubtitle: String = "Last scanned just now",
    val isRefreshingRisk: Boolean = false
)

private data class MonitorPermissionState(
    val accessibilityEnabled: Boolean = false,
    val notificationAccessEnabled: Boolean = false,
    val usageAccessEnabled: Boolean = false
)

enum class MonitorSeverityFilter {
    ALL,
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}

class MonitorViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: SecurityRepository =
        ServiceLocator.repository(application.applicationContext)
    private val packageManager = application.packageManager

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()
    private val permissionState = MutableStateFlow(MonitorPermissionState())
    private val selectedFilter = MutableStateFlow(MonitorSeverityFilter.ALL)
    private val isRefreshingRisk = MutableStateFlow(false)
    private val lastRefreshAt = MutableStateFlow<Long?>(null)
    private var refreshJob: Job? = null
    private var lastLoggedHomeRiskSignature: String? = null

    init {
        refreshMonitoringState()
        refreshPermissionState()
        observeData()
    }

    fun refreshMonitoringState() {
        repository.refreshMonitoringEnabled()
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        repository.setMonitoringEnabled(enabled)
    }

    fun refreshPermissionState() {
        val context = getApplication<Application>().applicationContext
        permissionState.value = MonitorPermissionState(
            accessibilityEnabled = PermissionUtils.isGuardianAccessibilityEnabled(context),
            notificationAccessEnabled = PermissionUtils.isNotificationAccessEnabled(context),
            usageAccessEnabled = PermissionUtils.hasUsageAccess(context)
        )
    }

    fun refreshRiskScore() {
        if (refreshJob?.isActive == true) {
            return
        }

        refreshJob = viewModelScope.launch {
            isRefreshingRisk.value = true
            Log.d(TAG, "refreshRiskScore started")
            try {
                repository.recalculateAllRisks()
                Log.d(TAG, "refreshRiskScore completed")
            } catch (exception: Exception) {
                Log.e(TAG, "refreshRiskScore failed", exception)
            } finally {
                lastRefreshAt.value = System.currentTimeMillis()
                isRefreshingRisk.value = false
            }
        }
    }

    fun setSelectedFilter(filter: MonitorSeverityFilter) {
        selectedFilter.value = filter
    }

    private fun observeData() {
        viewModelScope.launch {
            val monitorDataFlow = combine(
                repository.observeSuspiciousApps(),
                repository.observeAlerts(),
                repository.observeRecentEvents(20),
                repository.observeLatestDetectionSummary()
            ) { suspiciousProfiles, alerts, recentEvents, latestDetection ->
                MonitorData(
                    suspiciousProfiles = suspiciousProfiles,
                    alerts = alerts,
                    recentEventsCount = recentEvents.size,
                    latestDetection = latestDetection
                )
            }

            val baseUiFlow = combine(
                repository.observeMonitoringEnabled(),
                permissionState,
                monitorDataFlow,
                selectedFilter
            ) { monitoringEnabled, permissions, monitorData, filter ->
                val allSuspiciousApps = monitorData.suspiciousProfiles.map {
                    SuspiciousApp(
                        appName = it.appLabel,
                        packageName = it.packageName,
                        riskScore = it.currentRiskScore.coerceIn(0, 100),
                        riskLevel = when {
                            it.currentRiskScore >= 80 -> "Critical"
                            it.currentRiskScore >= 60 -> "High"
                            it.currentRiskScore >= 30 -> "Medium"
                            else -> "Low"
                        },
                        reason = buildReason(it.packageName, monitorData.alerts)
                    )
                }

                val suspiciousApps = allSuspiciousApps.filterBy(filter)

                val securityLogs = monitorData.alerts
                    .sortedByDescending { it.timestamp }
                    .take(10)
                    .map {
                        val packageName = it.packageName ?: "Unknown package"
                        SecurityLog(
                            logId = it.id,
                            title = it.title,
                            description = it.description,
                            time = formatTime(it.timestamp),
                            severity = it.severity,
                            appName = resolveAppName(packageName),
                            packageName = packageName,
                            message = it.title.ifBlank { it.description },
                            timestampLabel = formatTime(it.timestamp),
                            timestampMillis = it.timestamp
                        )
                    }

                MonitorUiState(
                    monitoringEnabled = monitoringEnabled,
                    serviceActive = monitoringEnabled && permissions.accessibilityEnabled,
                    accessibilityEnabled = permissions.accessibilityEnabled,
                    notificationAccessEnabled = permissions.notificationAccessEnabled,
                    usageAccessEnabled = permissions.usageAccessEnabled,
                    suspiciousApps = suspiciousApps,
                    selectedFilter = filter,
                    securityLogs = securityLogs,
                    alertCount = monitorData.alerts.count { it.status == "OPEN" },
                    scannedCount = monitorData.recentEventsCount,
                    blockedCount = monitorData.alerts.count { it.status == "RESOLVED" },
                    overallRiskScore = monitorData.latestDetection?.score?.coerceIn(0, 100) ?: 0,
                    overallRiskSubtitle = monitorData.latestDetection?.let {
                        "${it.appName} • ${formatTime(it.timestamp)}"
                    } ?: "Last scanned just now"
                )
            }

            combine(baseUiFlow, isRefreshingRisk, lastRefreshAt) { baseState, refreshing, refreshedAt ->
                baseState.copy(
                    overallRiskSubtitle = if (baseState.overallRiskSubtitle != "Last scanned just now") {
                        baseState.overallRiskSubtitle
                    } else {
                        formatLastScanned(refreshedAt)
                    },
                    isRefreshingRisk = refreshing
                )
            }.collect { state ->
                val homeRiskSignature =
                    "${state.overallRiskScore}|${state.overallRiskSubtitle}|${state.alertCount}|${state.scannedCount}"
                if (homeRiskSignature != lastLoggedHomeRiskSignature) {
                    lastLoggedHomeRiskSignature = homeRiskSignature
                    Log.d(
                        TAG,
                        "homeRisk score=${state.overallRiskScore} subtitle=${state.overallRiskSubtitle} alerts=${state.alertCount}"
                    )
                }
                _uiState.value = state
            }
        }
    }

    private fun buildReason(
        packageName: String,
        alerts: List<SecurityAlertEntity>
    ): String {
        val latest = alerts.firstOrNull { it.packageName == packageName }
        return latest?.title ?: "Suspicious accessibility-related activity detected"
    }

    private fun resolveAppName(packageName: String): String {
        if (packageName == "Unknown package") {
            return packageName
        }

        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun formatTime(timestamp: Long): String {
        val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
        val minutes = diff / 60_000L
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            else -> "${minutes / 1440}d ago"
        }
    }

    private fun formatLastScanned(timestamp: Long?): String {
        if (timestamp == null) {
            return "Last scanned just now"
        }

        val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
        val minutes = diff / 60_000L
        val suffix = when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            else -> "${minutes / 1440}d ago"
        }
        return "Last scanned $suffix"
    }
}

private data class MonitorData(
    val suspiciousProfiles: List<com.sliit.isp.accessibilityguardian.data.local.entities.AppProfileEntity>,
    val alerts: List<SecurityAlertEntity>,
    val recentEventsCount: Int,
    val latestDetection: com.sliit.isp.accessibilityguardian.data.repository.LatestDetectionSummary?
)

private fun List<SuspiciousApp>.filterBy(filter: MonitorSeverityFilter): List<SuspiciousApp> {
    if (filter == MonitorSeverityFilter.ALL) {
        return this
    }

    return filter { app ->
        normalizeSeverity(app.riskLevel) == filter
    }
}

private fun normalizeSeverity(severity: String?): MonitorSeverityFilter? {
    return when (severity?.trim()?.uppercase()) {
        MonitorSeverityFilter.CRITICAL.name -> MonitorSeverityFilter.CRITICAL
        MonitorSeverityFilter.HIGH.name -> MonitorSeverityFilter.HIGH
        MonitorSeverityFilter.MEDIUM.name -> MonitorSeverityFilter.MEDIUM
        MonitorSeverityFilter.LOW.name -> MonitorSeverityFilter.LOW
        else -> null
    }
}

private const val TAG = "MonitorViewModel"
