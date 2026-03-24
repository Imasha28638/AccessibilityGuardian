package com.sliit.isp.accessibilityguardian.ui.inspect

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sliit.isp.accessibilityguardian.R
import com.sliit.isp.accessibilityguardian.data.local.entities.AppProfileEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.RiskSnapshotEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.SecurityAlertEntity
import com.sliit.isp.accessibilityguardian.data.repository.InspectionLogDetails
import com.sliit.isp.accessibilityguardian.data.repository.InstalledAppInfo
import com.sliit.isp.accessibilityguardian.data.repository.LatestRiskAssessment
import com.sliit.isp.accessibilityguardian.data.repository.SecurityRepository
import com.sliit.isp.accessibilityguardian.util.ServiceLocator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class InspectUiState(
    val hasDetection: Boolean = false,
    val selectedPackage: String? = null,
    val selectedLogId: Long? = null,
    val appName: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val installedAgeLabel: String = "",
    val riskScore: Int = 0,
    val riskLevel: String = "LOW",
    val recommendation: String = "",
    val behaviorMetrics: List<BehaviorMetricItem> = emptyList(),
    val recentEvents: List<RecentEventItem> = emptyList(),
    val timelineItems: List<TimelinePoint> = emptyList(),
    val peakRiskLabel: String = "—",
    val avgRiskLabel: String = "—",
    val spikesLabel: String = "0",
    val durationLabel: String = "0m",
    val recentEventsCountLabel: String = "0 events",
    val timelineThreshold: Int = SPIKE_RISK_THRESHOLD,
    val emptyTitle: String = "No suspicious app detected",
    val emptyMessage: String = "Inspect populates automatically when Accessibility Guardian stores a suspicious detection."
)

private const val THIRTY_MINUTES_MS = 30 * 60 * 1000L
private const val SIXTY_MINUTES_MS = 60 * 60 * 1000L
private const val SPIKE_DELTA_THRESHOLD = 20
private const val SPIKE_RISK_THRESHOLD = 70
private const val TAG = "InspectViewModel"

sealed interface InspectUiEvent {
    data class Message(val text: String) : InspectUiEvent
}

class InspectViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    companion object {
        private const val KEY_SELECTED_PACKAGE = "selected_package"
        private const val KEY_SELECTED_LOG_ID = "selected_log_id"
    }

    private val repository: SecurityRepository =
        ServiceLocator.repository(application.applicationContext)

    private val _uiState = MutableStateFlow(InspectUiState())
    val uiState: StateFlow<InspectUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<InspectUiEvent>()
    val events = _events.asSharedFlow()

    private var loadJob: Job? = null

    init {
        val initialLogId = savedStateHandle.get<Long>(KEY_SELECTED_LOG_ID) ?: -1L
        val initialPackage = savedStateHandle.get<String>(KEY_SELECTED_PACKAGE)
        when {
            initialLogId > 0L -> loadInspectionForLog(initialLogId)
            !initialPackage.isNullOrBlank() -> selectPackage(initialPackage)
            else -> loadLatestInspection()
        }
    }

    fun loadLatestInspection() {
        savedStateHandle[KEY_SELECTED_LOG_ID] = -1L
        savedStateHandle[KEY_SELECTED_PACKAGE] = null

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            repository.observeLatestDetectedPackage()
                .distinctUntilChanged()
                .collectLatest { packageName ->
                    if (packageName.isNullOrBlank()) {
                        _uiState.value = InspectUiState()
                    } else {
                        observePackage(packageName)
                    }
                }
        }
    }

    fun loadInspectionForLog(logId: Long) {
        if (logId <= 0L) {
            loadLatestInspection()
            return
        }

        savedStateHandle[KEY_SELECTED_LOG_ID] = logId
        savedStateHandle[KEY_SELECTED_PACKAGE] = null

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val details = repository.getInspectionLogDetails(logId)
            _uiState.value = if (details == null) {
                InspectUiState()
            } else {
                buildUiStateForLog(details)
            }
        }
    }

    fun selectPackage(packageName: String) {
        if (packageName.isBlank()) {
            loadLatestInspection()
            return
        }

        savedStateHandle[KEY_SELECTED_PACKAGE] = packageName
        savedStateHandle[KEY_SELECTED_LOG_ID] = -1L

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            observePackage(packageName)
        }
    }

    fun onMarkAsSafe() {
        val pkg = _uiState.value.selectedPackage
        if (pkg.isNullOrBlank()) {
            viewModelScope.launch {
                _events.emit(InspectUiEvent.Message("No app selected"))
            }
            return
        }

        Log.d(TAG, "markAsSafeTapped package=$pkg")
        viewModelScope.launch {
            repository.markAppAsSafe(pkg)
            _events.emit(InspectUiEvent.Message("App marked as safe"))
            selectPackage(pkg)
        }
    }

    fun onBlockApp() {
        val pkg = _uiState.value.selectedPackage
        if (pkg.isNullOrBlank()) {
            viewModelScope.launch {
                _events.emit(InspectUiEvent.Message("No app selected"))
            }
            return
        }

        Log.d(TAG, "blockAppTapped package=$pkg")
        viewModelScope.launch {
            repository.blockApp(pkg)
            _events.emit(InspectUiEvent.Message("App blocked"))
            selectPackage(pkg)
        }
    }

    private suspend fun observePackage(packageName: String) {
        val now = System.currentTimeMillis()
        val timelineSince = now - THIRTY_MINUTES_MS
        val behaviorSince = now - SIXTY_MINUTES_MS
        val installedInfoFlow = flow {
            emit(repository.getInstalledAppInfo(packageName, now))
        }

        repository.observeAppProfile(packageName)
            .combine(repository.observeLatestRiskAssessmentForPackage(packageName)) { appProfile, latestRiskAssessment ->
                CombinedInspectData(
                    appProfile = appProfile,
                    latestRiskAssessment = latestRiskAssessment
                )
            }
            .combine(repository.observeLatestAlertForPackage(packageName)) { data, latestAlert ->
                data.copy(latestAlert = latestAlert)
            }
            .combine(repository.observeDetectionEventsForPackage(packageName, 12)) { data, detectionEvents ->
                data.copy(detectionEvents = detectionEvents)
            }
            .combine(repository.observeBehaviorWindowEventsForPackage(packageName, behaviorSince)) { data, behaviorEvents ->
                data.copy(behaviorEvents = behaviorEvents)
            }
            .combine(repository.observeRiskTimelinePointsForPackage(packageName, timelineSince)) { data, riskSnapshots ->
                data.copy(riskSnapshots = riskSnapshots)
            }
            .combine(installedInfoFlow) { data, installedInfo ->
                buildUiState(
                    packageName = packageName,
                    data = data,
                    installedInfo = installedInfo,
                    anchorTimestamp = now,
                    timelineSince = timelineSince
                )
            }
            .collect { _uiState.value = it }
    }

    private fun buildUiState(
        packageName: String,
        data: CombinedInspectData,
        installedInfo: InstalledAppInfo,
        anchorTimestamp: Long,
        timelineSince: Long
    ): InspectUiState {
        val score = data.latestAlert?.score
            ?: data.latestRiskAssessment?.score
            ?: data.appProfile?.currentRiskScore
            ?: 0
        val severity = data.latestAlert?.severity
            ?: data.latestRiskAssessment?.severity
            ?: severityForScore(score)
        val timelinePoints = buildTimelinePoints(
            snapshots = data.riskSnapshots,
            alerts = data.detectionEvents,
            timelineSince = timelineSince
        )
        val timelineStats = buildTimelineStats(timelinePoints)
        val recentEvents = buildRecentEvents(data.detectionEvents)
        val description = data.latestAlert?.description?.takeIf { it.isNotBlank() }

        return InspectUiState(
            hasDetection = data.appProfile != null || data.latestAlert != null || data.riskSnapshots.isNotEmpty() || data.detectionEvents.isNotEmpty(),
            selectedPackage = packageName,
            selectedLogId = data.latestAlert?.id,
            appName = data.appProfile?.appLabel ?: packageName,
            packageName = packageName,
            versionName = installedInfo.versionName,
            installedAgeLabel = formatInstalledAge(installedInfo.firstInstallTime),
            riskScore = score.coerceIn(0, 100),
            riskLevel = severity,
            recommendation = description ?: recommendationForScore(score),
            behaviorMetrics = buildBehaviorMetrics(data.behaviorEvents, anchorTimestamp),
            recentEvents = recentEvents,
            timelineItems = timelinePoints,
            peakRiskLabel = timelineStats.peakRisk,
            avgRiskLabel = timelineStats.averageRisk,
            spikesLabel = timelineStats.spikes,
            durationLabel = timelineStats.duration,
            recentEventsCountLabel = if (recentEvents.size == 1) "1 event" else "${recentEvents.size} events",
            timelineThreshold = SPIKE_RISK_THRESHOLD
        )
    }

    private fun buildUiStateForLog(details: InspectionLogDetails): InspectUiState {
        val alert = details.alert
        val packageName = alert.packageName.orEmpty()
        val displayPackage = packageName.ifBlank { "Unknown package" }
        val appName = details.appProfile?.appLabel ?: displayPackage
        val timelineSince = (alert.timestamp - THIRTY_MINUTES_MS).coerceAtLeast(0L)
        val timelinePoints = buildTimelinePoints(
            snapshots = details.riskSnapshots,
            alerts = details.relatedAlerts.ifEmpty { listOf(alert) },
            timelineSince = timelineSince
        ).ifEmpty {
            listOf(
                TimelinePoint(
                    score = alert.score.coerceIn(0, 100),
                    label = formatChartTime(alert.timestamp),
                    timestamp = alert.timestamp,
                    severity = alert.severity
                )
            )
        }
        val timelineStats = buildTimelineStats(timelinePoints)
        val recentEvents = buildRecentEvents(details.relatedAlerts.ifEmpty { listOf(alert) })

        return InspectUiState(
            hasDetection = true,
            selectedPackage = packageName.takeIf { it.isNotBlank() },
            selectedLogId = alert.id,
            appName = appName,
            packageName = displayPackage,
            versionName = details.installedInfo.versionName,
            installedAgeLabel = formatInstalledAge(details.installedInfo.firstInstallTime),
            riskScore = alert.score.coerceIn(0, 100),
            riskLevel = alert.severity.ifBlank { severityForScore(alert.score) },
            recommendation = alert.description.ifBlank { recommendationForScore(alert.score) },
            behaviorMetrics = buildBehaviorMetrics(details.behaviorEvents, alert.timestamp),
            recentEvents = recentEvents,
            timelineItems = timelinePoints,
            peakRiskLabel = timelineStats.peakRisk,
            avgRiskLabel = timelineStats.averageRisk,
            spikesLabel = timelineStats.spikes,
            durationLabel = timelineStats.duration,
            recentEventsCountLabel = if (recentEvents.size == 1) "1 event" else "${recentEvents.size} events",
            timelineThreshold = SPIKE_RISK_THRESHOLD
        )
    }

    private fun buildBehaviorMetrics(
        events: List<EventRecordEntity>,
        anchorTimestamp: Long
    ): List<BehaviorMetricItem> {
        val currentStart = anchorTimestamp - THIRTY_MINUTES_MS
        val previousStart = anchorTimestamp - SIXTY_MINUTES_MS

        val currentWindow = events.filter { it.timestamp in currentStart..anchorTimestamp }
        val previousWindow = events.filter { it.timestamp in previousStart until currentStart }

        return listOf(
            metricItem(
                label = "Rapid Click Events",
                currentCount = currentWindow.count { it.eventType == 1 },
                previousCount = previousWindow.count { it.eventType == 1 },
                iconRes = R.drawable.ic_tap_small,
                accentColorRes = R.color.inspect_orange
            ),
            metricItem(
                label = "Window State Changes",
                currentCount = currentWindow.count { it.eventType == 32 || it.eventType == 2048 },
                previousCount = previousWindow.count { it.eventType == 32 || it.eventType == 2048 },
                iconRes = R.drawable.ic_window_small,
                accentColorRes = R.color.inspect_purple
            ),
            metricItem(
                label = "Text Input Changes",
                currentCount = currentWindow.count { it.eventType == 16 },
                previousCount = previousWindow.count { it.eventType == 16 },
                iconRes = R.drawable.ic_clipboard_small,
                accentColorRes = R.color.inspect_yellow
            ),
            metricItem(
                label = "Sensitive App Targeting",
                currentCount = currentWindow.count { it.isSensitiveContext },
                previousCount = previousWindow.count { it.isSensitiveContext },
                iconRes = R.drawable.ic_target_small,
                accentColorRes = R.color.inspect_red
            )
        )
    }

    private fun metricItem(
        label: String,
        currentCount: Int,
        previousCount: Int,
        iconRes: Int,
        accentColorRes: Int
    ): BehaviorMetricItem {
        val deltaText = if (previousCount > 0) {
            val change = ((currentCount - previousCount) * 100f / previousCount).roundToInt()
            if (change > 0) "+$change%" else "$change%"
        } else {
            null
        }

        val deltaColorRes = when {
            deltaText == null -> accentColorRes
            deltaText.startsWith("-") -> R.color.inspect_low
            else -> accentColorRes
        }

        return BehaviorMetricItem(
            label = label,
            value = currentCount.toString(),
            iconRes = iconRes,
            accentColorRes = accentColorRes,
            deltaText = deltaText,
            deltaColorRes = deltaColorRes
        )
    }

    private fun buildRecentEvents(alerts: List<SecurityAlertEntity>): List<RecentEventItem> {
        return alerts
            .sortedByDescending { it.timestamp }
            .map { alert ->
                val severityStyle = severityStyle(alert.severity)
                RecentEventItem(
                    title = alert.title,
                    subtitle = alert.description.ifBlank { alert.evidenceText },
                    time = formatRelativeTime(alert.timestamp),
                    severity = alert.severity,
                    iconRes = severityStyle.iconRes,
                    iconTintRes = severityStyle.tintColorRes,
                    severityTextColorRes = severityStyle.tintColorRes,
                    severityBackgroundRes = severityStyle.backgroundRes
                )
            }
    }

    private fun buildTimelinePoints(
        snapshots: List<RiskSnapshotEntity>,
        alerts: List<SecurityAlertEntity>,
        timelineSince: Long
    ): List<TimelinePoint> {
        val snapshotPoints = snapshots
            .sortedBy { it.timestamp }
            .map {
                TimelinePoint(
                    score = it.score,
                    label = formatChartTime(it.timestamp),
                    timestamp = it.timestamp,
                    severity = it.severity
                )
            }
        if (snapshotPoints.isNotEmpty()) {
            return downsampleTimeline(snapshotPoints)
        }

        val alertPoints = alerts
            .filter { it.timestamp >= timelineSince }
            .sortedBy { it.timestamp }
            .map {
                TimelinePoint(
                    score = it.score,
                    label = formatChartTime(it.timestamp),
                    timestamp = it.timestamp,
                    severity = it.severity
                )
            }
        return downsampleTimeline(alertPoints)
    }

    private fun downsampleTimeline(points: List<TimelinePoint>, maxPoints: Int = 12): List<TimelinePoint> {
        if (points.size <= maxPoints) {
            return points
        }

        val step = ((points.size - 1).toFloat() / (maxPoints - 1)).coerceAtLeast(1f)
        return (0 until maxPoints)
            .map { index -> points[(index * step).roundToInt().coerceIn(0, points.lastIndex)] }
            .distinctBy { it.timestamp }
    }

    private fun buildTimelineStats(points: List<TimelinePoint>): TimelineStats {
        if (points.isEmpty()) {
            return TimelineStats(peakRisk = "—", averageRisk = "—", spikes = "0", duration = "0m")
        }

        val peakRisk = points.maxOf { it.score }.toString()
        val averageRisk = points.map { it.score }.average().roundToInt().toString()
        val spikes = points.zipWithNext().count { (current, next) ->
            val scoreJump = next.score - current.score >= SPIKE_DELTA_THRESHOLD
            val severityJump = severityRank(next.severity) > severityRank(current.severity)
            scoreJump || severityJump
        }.toString()
        val durationMinutes = ((points.last().timestamp - points.first().timestamp) / 60_000L).coerceAtLeast(0L)
        val durationLabel = if (durationMinutes >= 60) {
            val hours = durationMinutes / 60
            val minutes = durationMinutes % 60
            if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
        } else {
            "${durationMinutes}m"
        }

        return TimelineStats(
            peakRisk = peakRisk,
            averageRisk = averageRisk,
            spikes = spikes,
            duration = durationLabel
        )
    }

    private fun recommendationForScore(score: Int): String {
        return when {
            score >= 80 -> "Revoke accessibility access immediately and review the app."
            score >= 60 -> "Review this app’s permissions and accessibility access closely."
            score >= 30 -> "Monitor this app for repeated suspicious accessibility behavior."
            else -> "Current activity remains low risk, but keep monitoring enabled."
        }
    }

    private fun formatRelativeTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60_000L
        return when {
            minutes < 1 -> "Now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            else -> "${minutes / 1440}d ago"
        }
    }

    private fun formatInstalledAge(firstInstallTime: Long): String {
        val days = ((System.currentTimeMillis() - firstInstallTime) / (24 * 60 * 60 * 1000L)).coerceAtLeast(0L)
        return if (days == 1L) "Installed 1 day ago" else "Installed $days days ago"
    }

    private fun formatChartTime(timestamp: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    private fun severityForScore(score: Int): String {
        return when {
            score >= 80 -> "CRITICAL"
            score >= 60 -> "HIGH"
            score >= 30 -> "MEDIUM"
            else -> "LOW"
        }
    }

    private fun severityRank(severity: String): Int {
        return when (severity.uppercase()) {
            "CRITICAL" -> 4
            "HIGH" -> 3
            "MEDIUM" -> 2
            else -> 1
        }
    }

    private fun severityStyle(severity: String): SeverityStyle {
        return when (severity.uppercase()) {
            "CRITICAL" -> SeverityStyle(R.drawable.ic_alert_small, R.color.inspect_red, R.drawable.bg_severity_chip_critical)
            "HIGH" -> SeverityStyle(R.drawable.ic_alert_small, R.color.inspect_orange, R.drawable.bg_severity_chip_high)
            "MEDIUM" -> SeverityStyle(R.drawable.ic_alert_small, R.color.inspect_yellow, R.drawable.bg_severity_chip_medium)
            else -> SeverityStyle(R.drawable.ic_alert_small, R.color.inspect_low, R.drawable.bg_severity_chip_low)
        }
    }

    private data class SeverityStyle(
        val iconRes: Int,
        val tintColorRes: Int,
        val backgroundRes: Int
    )

    private data class TimelineStats(
        val peakRisk: String,
        val averageRisk: String,
        val spikes: String,
        val duration: String
    )

    private data class CombinedInspectData(
        val appProfile: AppProfileEntity? = null,
        val latestRiskAssessment: LatestRiskAssessment? = null,
        val latestAlert: SecurityAlertEntity? = null,
        val detectionEvents: List<SecurityAlertEntity> = emptyList(),
        val behaviorEvents: List<EventRecordEntity> = emptyList(),
        val riskSnapshots: List<RiskSnapshotEntity> = emptyList()
    )
}
