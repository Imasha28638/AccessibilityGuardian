package com.sliit.isp.accessibilityguardian.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sliit.isp.accessibilityguardian.data.repository.SecurityRepository
import com.sliit.isp.accessibilityguardian.util.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class LogsUiState(
    val entries: List<SecurityLog> = emptyList(),
    val selectedFilter: LogsFilter = LogsFilter.ALL,
    val sortMode: LogsSortMode = LogsSortMode.LATEST_FIRST,
    val criticalCount: Int = 0,
    val highCount: Int = 0,
    val mediumCount: Int = 0,
    val lowCount: Int = 0
)

enum class LogsFilter {
    ALL,
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}

enum class LogsSortMode {
    LATEST_FIRST
}

class LogsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: SecurityRepository =
        ServiceLocator.repository(application.applicationContext)
    private val packageManager = application.packageManager

    private val allLogs = MutableStateFlow<List<SecurityLog>>(emptyList())
    private val selectedFilter = MutableStateFlow(LogsFilter.ALL)
    private val sortMode = MutableStateFlow(LogsSortMode.LATEST_FIRST)
    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    init {
        observeUiState()
        observeAlerts()
    }

    fun setFilter(filter: LogsFilter) {
        selectedFilter.value = filter
    }

    private fun observeUiState() {
        viewModelScope.launch {
            combine(allLogs, selectedFilter, sortMode) { logs, filter, currentSortMode ->
                val sortedLogs = applySort(logs, currentSortMode)
                val filteredLogs = applyFilter(sortedLogs, filter)

                LogsUiState(
                    entries = filteredLogs,
                    selectedFilter = filter,
                    sortMode = currentSortMode,
                    criticalCount = sortedLogs.count { normalizeSeverity(it.severity) == LogsFilter.CRITICAL },
                    highCount = sortedLogs.count { normalizeSeverity(it.severity) == LogsFilter.HIGH },
                    mediumCount = sortedLogs.count { normalizeSeverity(it.severity) == LogsFilter.MEDIUM },
                    lowCount = sortedLogs.count { normalizeSeverity(it.severity) == LogsFilter.LOW }
                )
            }.collect { _uiState.value = it }
        }
    }

    private fun observeAlerts() {
        viewModelScope.launch {
            repository.observeAlerts().collect { alerts ->
                allLogs.value = alerts.map { alert ->
                    val packageName = alert.packageName ?: "Unknown package"
                    SecurityLog(
                        logId = alert.id,
                        appName = resolveAppName(packageName),
                        packageName = packageName,
                        message = alert.title.ifBlank { alert.description },
                        timestampLabel = formatDateTime(alert.timestamp),
                        timestampMillis = alert.timestamp,
                        severity = alert.severity,
                        eventCount = null
                    )
                }
            }
        }
    }

    private fun applySort(
        logs: List<SecurityLog>,
        sortMode: LogsSortMode
    ): List<SecurityLog> {
        return when (sortMode) {
            LogsSortMode.LATEST_FIRST -> logs.sortedByDescending(SecurityLog::timestampMillis)
        }
    }

    private fun applyFilter(
        logs: List<SecurityLog>,
        filter: LogsFilter
    ): List<SecurityLog> {
        if (filter == LogsFilter.ALL) {
            return logs
        }

        return logs.filter { log ->
            normalizeSeverity(log.severity) == filter
        }
    }

    private fun resolveAppName(packageName: String): String {
        if (packageName == "Unknown package") return packageName

        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun formatDateTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60_000L
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes} min ago"
            minutes < 1440 -> "${minutes / 60} hr ago"
            else -> "${minutes / 1440} day(s) ago"
        }
    }

    private fun normalizeSeverity(severity: String?): LogsFilter? {
        val normalized = severity
            ?.trim()
            ?.uppercase()
            ?.replace(Regex("\\s+"), " ")
            .orEmpty()

        return when {
            normalized.isBlank() -> null
            normalized == LogsFilter.CRITICAL.name || normalized.contains(LogsFilter.CRITICAL.name) -> LogsFilter.CRITICAL
            normalized == LogsFilter.HIGH.name || normalized.contains(LogsFilter.HIGH.name) -> LogsFilter.HIGH
            normalized == LogsFilter.MEDIUM.name || normalized.contains(LogsFilter.MEDIUM.name) -> LogsFilter.MEDIUM
            normalized == LogsFilter.LOW.name || normalized.contains(LogsFilter.LOW.name) -> LogsFilter.LOW
            else -> null
        }
    }
}
