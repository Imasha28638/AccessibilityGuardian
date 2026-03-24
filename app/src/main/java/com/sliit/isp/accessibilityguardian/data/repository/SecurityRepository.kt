package com.sliit.isp.accessibilityguardian.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.sliit.isp.accessibilityguardian.core.model.DetectionContext
import com.sliit.isp.accessibilityguardian.core.model.RiskAssessment
import com.sliit.isp.accessibilityguardian.core.monitor.AccessibilityStateMonitor
import com.sliit.isp.accessibilityguardian.core.monitor.DeviceIntegrityResult
import com.sliit.isp.accessibilityguardian.data.local.AppDatabase
import com.sliit.isp.accessibilityguardian.data.local.entities.AppProfileEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.OtpWindowEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.RiskSnapshotEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.SecurityAlertEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.UserDecisionEntity
import com.sliit.isp.accessibilityguardian.util.MonitoringPreferences
import com.sliit.isp.accessibilityguardian.util.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val versionName: String,
    val firstInstallTime: Long
)

data class LatestRiskAssessment(
    val score: Int,
    val severity: String
)

data class LatestDetectionSummary(
    val packageName: String,
    val appName: String,
    val score: Int,
    val severity: String,
    val timestamp: Long,
    val title: String,
    val description: String
)

data class PersistedEvaluation(
    val alert: SecurityAlertEntity?
)

object UserDecisionType {
    const val SAFE = "SAFE"
    const val BLOCKED = "BLOCKED"
}

data class InspectionLogDetails(
    val alert: SecurityAlertEntity,
    val appProfile: AppProfileEntity?,
    val installedInfo: InstalledAppInfo,
    val relatedAlerts: List<SecurityAlertEntity>,
    val behaviorEvents: List<EventRecordEntity>,
    val riskSnapshots: List<RiskSnapshotEntity>
)

class SecurityRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    private val appProfileDao = db.appProfileDao()
    private val eventDao = db.eventRecordDao()
    private val alertDao = db.securityAlertDao()
    private val snapshotDao = db.riskSnapshotDao()
    private val userDecisionDao = db.userDecisionDao()
    private val otpWindowDao = db.otpWindowDao()
    private val monitoringEnabled = MutableStateFlow(MonitoringPreferences.isMonitoringEnabled(context))
    private val realtimeAlertsEnabled = MutableStateFlow(MonitoringPreferences.isRealtimeAlertsEnabled(context))
    private val threatNotificationsEnabled = MutableStateFlow(MonitoringPreferences.isThreatNotificationsEnabled(context))
    private val riskSensitivity = MutableStateFlow(MonitoringPreferences.sensitivityPercent(context))

    fun observeMonitoringEnabled(): StateFlow<Boolean> = monitoringEnabled.asStateFlow()
    fun observeRealtimeAlertsEnabled(): StateFlow<Boolean> = realtimeAlertsEnabled.asStateFlow()
    fun observeThreatNotificationsEnabled(): StateFlow<Boolean> = threatNotificationsEnabled.asStateFlow()
    fun observeRiskSensitivity(): StateFlow<Int> = riskSensitivity.asStateFlow()

    fun getMonitoringEnabled(): Boolean = monitoringEnabled.value
    fun getRealtimeAlertsEnabled(): Boolean = realtimeAlertsEnabled.value
    fun getThreatNotificationsEnabled(): Boolean = threatNotificationsEnabled.value
    fun getRiskSensitivity(): Int = riskSensitivity.value

    fun setMonitoringEnabled(enabled: Boolean) {
        if (monitoringEnabled.value == enabled) {
            return
        }
        MonitoringPreferences.setMonitoringEnabled(context, enabled)
        monitoringEnabled.value = enabled
    }

    fun refreshMonitoringEnabled() {
        monitoringEnabled.value = MonitoringPreferences.isMonitoringEnabled(context)
    }

    fun setRealtimeAlertsEnabled(enabled: Boolean) {
        if (realtimeAlertsEnabled.value == enabled) {
            return
        }
        MonitoringPreferences.setRealtimeAlertsEnabled(context, enabled)
        realtimeAlertsEnabled.value = enabled
    }

    fun refreshRealtimeAlertsEnabled() {
        realtimeAlertsEnabled.value = MonitoringPreferences.isRealtimeAlertsEnabled(context)
    }

    fun setThreatNotificationsEnabled(enabled: Boolean) {
        if (threatNotificationsEnabled.value == enabled) {
            return
        }
        MonitoringPreferences.setThreatNotificationsEnabled(context, enabled)
        threatNotificationsEnabled.value = enabled
    }

    fun refreshThreatNotificationsEnabled() {
        threatNotificationsEnabled.value = MonitoringPreferences.isThreatNotificationsEnabled(context)
    }

    fun setRiskSensitivity(value: Int) {
        val normalizedValue = value.coerceIn(0, 100)
        if (riskSensitivity.value == normalizedValue) {
            return
        }
        MonitoringPreferences.setSensitivityPercent(context, normalizedValue)
        riskSensitivity.value = normalizedValue
    }

    fun refreshRiskSensitivity() {
        riskSensitivity.value = MonitoringPreferences.sensitivityPercent(context)
    }

    fun observeAlerts(): Flow<List<SecurityAlertEntity>> = alertDao.observeAll()

    fun observeSuspiciousApps() = appProfileDao.observeSuspicious()

    fun observeLatestDetectedPackage(): Flow<String?> {
        return combine(
            alertDao.observeLatestOpen(),
            appProfileDao.observeLatestSuspicious()
        ) { latestAlert, latestSuspicious ->
            latestAlert?.packageName ?: latestSuspicious?.packageName
        }
    }

    fun observeLatestDetectionSummary(): Flow<LatestDetectionSummary?> {
        return combine(
            alertDao.observeLatestOpen(),
            appProfileDao.observeLatestPositiveRisk(),
            appProfileDao.observeLatestSeen(),
            snapshotDao.observeLatest()
        ) { latestOpenAlert, latestPositiveProfile, latestSeenProfile, latestSnapshot ->
            val positiveCandidates = listOfNotNull(
                latestOpenAlert
                    ?.takeIf { it.score > 0 }
                    ?.toDetectionSummary(profile = latestPositiveProfile),
                latestPositiveProfile
                    ?.takeIf { it.currentRiskScore > 0 }
                    ?.toDetectionSummary(),
                latestSnapshot
                    ?.takeIf { it.score > 0 }
                    ?.toDetectionSummary(profile = latestPositiveProfile)
            )

            positiveCandidates.maxByOrNull { it.timestamp }
                ?: latestSnapshot?.toDetectionSummary(profile = latestSeenProfile)
                ?: latestSeenProfile?.toDetectionSummary()
        }
    }

    fun observeAppProfile(packageName: String) =
        appProfileDao.observeByPackage(packageName)

    fun observeRecentEvents(limit: Int) = eventDao.observeRecent(limit)

    fun observeRecentEventsForPackage(packageName: String, limit: Int) =
        eventDao.observeRecentForPackage(packageName, limit)

    fun observeBehaviorWindowEventsForPackage(packageName: String, since: Long) =
        eventDao.observeForPackageSince(packageName, since)

    fun observeRiskSnapshotsForPackage(packageName: String, limit: Int) =
        snapshotDao.observeRecentForPackage(packageName, limit)

    fun observeRiskTimelinePointsForPackage(packageName: String, since: Long) =
        snapshotDao.observeForPackageSince(packageName, since)

    fun observeDetectionEventsForPackage(packageName: String, limit: Int) =
        alertDao.observeForPackage(packageName, limit)

    fun observeLatestAlertForPackage(packageName: String) =
        alertDao.observeLatestForPackage(packageName)

    fun observeLatestRiskAssessmentForPackage(packageName: String): Flow<LatestRiskAssessment?> {
        return combine(
            observeAppProfile(packageName),
            alertDao.observeLatestOpenForPackage(packageName),
            observeRiskSnapshotsForPackage(packageName, 1)
        ) { profile, latestAlert, snapshots ->
            val latestSnapshot = snapshots.firstOrNull()
            val positiveCandidates = listOfNotNull(
                profile
                    ?.takeIf { it.currentRiskScore > 0 }
                    ?.let {
                        TimestampedRiskAssessment(
                            timestamp = it.lastSeenAt,
                            assessment = LatestRiskAssessment(
                                score = it.currentRiskScore,
                                severity = severityForScore(it.currentRiskScore)
                            )
                        )
                    },
                latestAlert
                    ?.takeIf { it.score > 0 }
                    ?.let {
                        TimestampedRiskAssessment(
                            timestamp = it.timestamp,
                            assessment = LatestRiskAssessment(
                                score = it.score,
                                severity = it.severity
                            )
                        )
                    },
                latestSnapshot
                    ?.takeIf { it.score > 0 }
                    ?.let {
                        TimestampedRiskAssessment(
                            timestamp = it.timestamp,
                            assessment = LatestRiskAssessment(
                                score = it.score,
                                severity = it.severity
                            )
                        )
                    }
            )

            positiveCandidates.maxByOrNull { it.timestamp }?.assessment
                ?: latestSnapshot?.let {
                    LatestRiskAssessment(
                        score = it.score,
                        severity = it.severity
                    )
                }
                ?: profile?.let {
                    LatestRiskAssessment(
                        score = it.currentRiskScore,
                        severity = severityForScore(it.currentRiskScore)
                    )
                }
        }
    }

    suspend fun getInspectionLogDetails(logId: Long): InspectionLogDetails? = withContext(Dispatchers.IO) {
        val alert = alertDao.getById(logId) ?: return@withContext null
        val packageName = alert.packageName.orEmpty()
        val windowEnd = alert.timestamp
        val windowStart = (windowEnd - 60 * 60 * 1000L).coerceAtLeast(0L)

        InspectionLogDetails(
            alert = alert,
            appProfile = if (packageName.isBlank()) {
                null
            } else {
                appProfileDao.getByPackage(packageName)
            },
            installedInfo = getInstalledAppInfo(packageName, alert.timestamp),
            relatedAlerts = if (packageName.isBlank()) {
                listOf(alert)
            } else {
                alertDao.recentForPackageUpTo(packageName, alert.timestamp, 12)
            },
            behaviorEvents = if (packageName.isBlank()) {
                emptyList()
            } else {
                eventDao.recentForPackageBetween(packageName, windowStart, windowEnd)
            },
            riskSnapshots = if (packageName.isBlank()) {
                emptyList()
            } else {
                snapshotDao.recentForPackageBetween(packageName, windowStart, windowEnd)
            }
        )
    }

    suspend fun getInstalledAppInfo(packageName: String, fallbackFirstInstallTime: Long = System.currentTimeMillis()): InstalledAppInfo {
        val packageInfo = try {
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (_: Exception) {
            null
        }

        return InstalledAppInfo(
            versionName = packageInfo?.versionName ?: "Unknown version",
            firstInstallTime = packageInfo?.firstInstallTime ?: fallbackFirstInstallTime
        )
    }

    suspend fun buildDetectionContext(
        event: EventRecordEntity,
        accessibilityGloballyEnabled: Boolean = false,
        enabledAccessibilityServicePackages: Set<String> = emptySet(),
        newlyEnabledAccessibilityServicePackages: Set<String> = emptySet()
    ): DetectionContext {
        val packageName = event.sourcePackage
        val profile = packageName?.let { getOrCreateAppProfile(it) }
        val packageRunsAccessibilityService =
            !packageName.isNullOrBlank() && packageName in enabledAccessibilityServicePackages
        val recentEvents = when {
            packageName.isNullOrBlank() -> eventDao.recent(10)
            else -> eventDao.recentForPackage(packageName, 10)
        }

        otpWindowDao.clearExpired(System.currentTimeMillis())
        val activeOtpWindow = otpWindowDao.getActive(System.currentTimeMillis())

        val lastDecision = packageName?.let { userDecisionDao.getDecisionForPackage(it) }
        val userTrustAdjustment = when (lastDecision?.decision) {
            UserDecisionType.SAFE -> 100
            else -> 0
        }
        val trustProfile = buildTrustProfile(
            packageName = packageName,
            profile = profile,
            packageRunsAccessibilityService = packageRunsAccessibilityService
        )
        val overlayLikely =
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                event.sourceClass?.contains("overlay", ignoreCase = true) == true ||
                (
                    !packageName.isNullOrBlank() &&
                        !event.foregroundPackage.isNullOrBlank() &&
                        packageName != event.foregroundPackage &&
                        event.isSensitiveContext
                    )

        return DetectionContext(
            packageName = packageName,
            appProfile = profile,
            foregroundPackage = event.foregroundPackage,
            recentEvents = recentEvents,
            otpWindowActive = activeOtpWindow != null,
            accessibilityGloballyEnabled = accessibilityGloballyEnabled,
            enabledAccessibilityServicePackages = enabledAccessibilityServicePackages,
            newlyEnabledAccessibilityServicePackages = newlyEnabledAccessibilityServicePackages,
            packageRunsAccessibilityService = packageRunsAccessibilityService,
            overlayLikely = overlayLikely,
            trustAdjustment = userTrustAdjustment + trustProfile.score,
            packageTrustScore = trustProfile.score,
            packageIsAllowlisted = trustProfile.allowlisted
        )
    }

    suspend fun persistEvaluation(
        event: EventRecordEntity,
        assessment: RiskAssessment
    ): PersistedEvaluation {
        return withContext(Dispatchers.IO) {
            val adjustedEvent = event.copy(riskDelta = assessment.triggeredRules.sumOf { it.riskDelta })
            eventDao.insert(adjustedEvent)
            PersistedEvaluation(alert = persistRiskOutputs(adjustedEvent, assessment))
        }
    }

    suspend fun recalculateAllRisks() {
        withContext(Dispatchers.IO) {
            val apps = appProfileDao.getAll()
            val ruleEngine = ServiceLocator.detectionRuleEngine()
            val riskEngine = ServiceLocator.riskEngine()
            val accessibilityStateMonitor = AccessibilityStateMonitor(context)
            val accessibilityGloballyEnabled = accessibilityStateMonitor.isAccessibilityGloballyEnabled()
            val enabledAccessibilityServicePackages = accessibilityStateMonitor
                .getEnabledAccessibilityServicePackages()

            apps.forEach { profile ->
                val lastEvent = eventDao.recentForPackage(profile.packageName, 1).firstOrNull() ?: return@forEach
                val context = buildDetectionContext(
                    event = lastEvent,
                    accessibilityGloballyEnabled = accessibilityGloballyEnabled,
                    enabledAccessibilityServicePackages = enabledAccessibilityServicePackages
                )
                val ruleResults = ruleEngine.evaluate(lastEvent, context)
                val assessment = riskEngine.calculate(
                    baseScore = context.appProfile?.currentRiskScore ?: 0,
                    triggeredRules = ruleResults,
                    trustAdjustment = context.trustAdjustment
                )
                Log.d(
                    TAG,
                    "recalculateRisk package=${profile.packageName} score=${assessment.score} severity=${assessment.severity}"
                )
                val adjustedEvent = lastEvent.copy(riskDelta = assessment.triggeredRules.sumOf { it.riskDelta })
                eventDao.update(adjustedEvent)
                persistRiskOutputs(adjustedEvent, assessment)
            }
        }
    }

    private suspend fun persistRiskOutputs(
        event: EventRecordEntity,
        assessment: RiskAssessment
    ): SecurityAlertEntity? {
        val packageName = event.sourcePackage
        if (packageName.isNullOrBlank()) {
            return null
        }

        val trusted = isAppTrusted(packageName)
        val persistedScore = if (trusted) 0 else assessment.score.coerceIn(0, 100)
        val persistedAt = System.currentTimeMillis()
        val existing = getOrCreateAppProfile(packageName)
        val updated = existing.copy(
            isTrusted = trusted || existing.isTrusted,
            currentRiskScore = persistedScore,
            lastSeenAt = persistedAt
        )
        appProfileDao.upsert(updated)

        snapshotDao.insert(
            RiskSnapshotEntity(
                timestamp = persistedAt,
                packageName = packageName,
                score = persistedScore,
                severity = assessment.severity.name,
                triggeredRulesText = assessment.triggeredRules.joinToString(" | ") { it.title }
            )
        )

        Log.d(
            TAG,
            "persistedRisk package=$packageName score=$persistedScore severity=${assessment.severity} rules=${assessment.triggeredRules.joinToString { it.ruleId }}"
        )

        return maybeCreateAlert(packageName, assessment.copy(score = persistedScore))
    }

    suspend fun startOtpSensitivityWindow(sourcePackage: String, timestamp: Long) {
        otpWindowDao.insert(
            OtpWindowEntity(
                sourcePackage = sourcePackage,
                startedAt = timestamp,
                expiresAt = timestamp + 60_000L
            )
        )
    }

    suspend fun markAppAsSafe(packageName: String) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "markAppAsSafe package=$packageName")
            val existing = getOrCreateAppProfile(packageName)
            val now = System.currentTimeMillis()
            appProfileDao.upsert(
                existing.copy(
                    isTrusted = true,
                    currentRiskScore = 0,
                    lastSeenAt = now
                )
            )
            userDecisionDao.insert(
                UserDecisionEntity(
                    packageName = packageName,
                    decision = UserDecisionType.SAFE,
                    timestamp = now
                )
            )
            alertDao.latestOpenForPackage(packageName)?.let { alert ->
                alertDao.updateStatus(alert.id, "RESOLVED")
            }
            Log.d(TAG, "decisionSaved package=$packageName decision=${UserDecisionType.SAFE}")
        }
    }

    suspend fun blockApp(packageName: String) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "blockApp package=$packageName")
            val existing = getOrCreateAppProfile(packageName)
            val now = System.currentTimeMillis()
            appProfileDao.upsert(
                existing.copy(
                    isTrusted = false,
                    lastSeenAt = now
                )
            )
            userDecisionDao.insert(
                UserDecisionEntity(
                    packageName = packageName,
                    decision = UserDecisionType.BLOCKED,
                    timestamp = now
                )
            )
            Log.d(TAG, "decisionSaved package=$packageName decision=${UserDecisionType.BLOCKED}")
        }
    }

    suspend fun getUserDecisionForPackage(packageName: String): UserDecisionEntity? =
        withContext(Dispatchers.IO) {
            userDecisionDao.getDecisionForPackage(packageName)
        }

    suspend fun isAppTrusted(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val decision = userDecisionDao.getDecisionForPackage(packageName)
        when {
            decision?.decision == UserDecisionType.SAFE -> true
            decision?.decision == UserDecisionType.BLOCKED -> false
            else -> appProfileDao.getByPackage(packageName)?.isTrusted == true
        }
    }

    suspend fun isAppBlocked(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val decision = userDecisionDao.getDecisionForPackage(packageName)
        when {
            decision?.decision == UserDecisionType.BLOCKED -> true
            decision?.decision == UserDecisionType.SAFE -> false
            else -> false
        }
    }

    suspend fun markAlertResolved(alertId: Long) {
        alertDao.updateStatus(alertId, "RESOLVED")
    }

    suspend fun clearDetectionDataForDebug() {
        withContext(Dispatchers.IO) {
            alertDao.clearAll()
            snapshotDao.clearAll()
            eventDao.clearAll()
            otpWindowDao.clearAll()
            appProfileDao.resetAllRiskScores()
        }
    }

    suspend fun logIntegrityBlock(result: DeviceIntegrityResult) {
        withContext(Dispatchers.IO) {
            alertDao.insert(
                SecurityAlertEntity(
                    timestamp = System.currentTimeMillis(),
                    packageName = null,
                    severity = "CRITICAL",
                    score = 100,
                    title = "Security restriction enforced",
                    description = buildString {
                        append("Application launch blocked because ")
                        when {
                            result.rooted && result.emulator -> append("a rooted device and an emulator profile were detected.")
                            result.rooted -> append("a rooted device was detected.")
                            result.emulator -> append("an emulator profile was detected.")
                        }
                    },
                    evidenceText = result.detailText.ifBlank { "No additional integrity details were available." },
                    status = "OPEN"
                )
            )
        }
    }

    private suspend fun maybeCreateAlert(
        packageName: String,
        assessment: RiskAssessment
    ): SecurityAlertEntity? {
        if (isAppTrusted(packageName)) {
            Log.d(TAG, "suppressAlertForTrusted package=$packageName")
            return null
        }
        if (assessment.score < 30) return null

        val latest = alertDao.latestOpenForPackage(packageName)
        val shouldCreate = latest == null ||
            (System.currentTimeMillis() - latest.timestamp > 10 * 60 * 1000L) ||
            latest.severity != assessment.severity.name

        if (!shouldCreate) return null

        val alert = SecurityAlertEntity(
            timestamp = System.currentTimeMillis(),
            packageName = packageName,
            severity = assessment.severity.name,
            score = assessment.score,
            title = when (assessment.severity.name) {
                "CRITICAL" -> "Critical accessibility abuse risk"
                "HIGH" -> "High accessibility abuse risk"
                "MEDIUM" -> "Suspicious accessibility activity"
                else -> "Low risk activity"
            },
            description = "Risk score reached ${assessment.score} for $packageName.",
            evidenceText = assessment.triggeredRules.joinToString("\n") {
                "${it.title}: ${it.description}"
            }
        )
        val id = alertDao.insert(alert)
        return alert.copy(id = id)
    }

    private suspend fun getOrCreateAppProfile(packageName: String): AppProfileEntity {
        val existing = appProfileDao.getByPackage(packageName)
        if (existing != null) return existing

        val pm = context.packageManager
        val appInfo = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (_: Exception) {
            null
        }

        val packageInfo = try {
            pm.getPackageInfo(packageName, 0)
        } catch (_: Exception) {
            null
        }

        val appLabel = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName
        val installer = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        } catch (_: Exception) {
            null
        }

        val isSystem = appInfo?.let {
            (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } ?: false
        val launcherVisible = isLauncherVisible(pm, packageName)

        val profile = AppProfileEntity(
            packageName = packageName,
            appLabel = appLabel,
            installerPackage = installer,
            firstInstallTime = packageInfo?.firstInstallTime ?: System.currentTimeMillis(),
            lastUpdateTime = packageInfo?.lastUpdateTime ?: System.currentTimeMillis(),
            isSystemApp = isSystem,
            isLauncherVisible = launcherVisible
        )

        appProfileDao.upsert(profile)
        return profile
    }

    private fun isLauncherVisible(pm: PackageManager, packageName: String): Boolean {
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        return launchIntent != null
    }

    private fun buildTrustProfile(
        packageName: String?,
        profile: AppProfileEntity?,
        packageRunsAccessibilityService: Boolean
    ): PackageTrustProfile {
        if (packageName.isNullOrBlank()) {
            return PackageTrustProfile()
        }

        var trustScore = 0
        var allowlisted = false

        if (profile?.isTrusted == true) {
            trustScore += 20
            allowlisted = true
        }

        if (profile?.isSystemApp == true) {
            trustScore += 12
        }

        if (profile?.installerPackage in TRUSTED_INSTALLERS) {
            trustScore += 5
        }

        if (packageName in KNOWN_SYSTEM_ACCESSIBILITY_PACKAGES) {
            trustScore += 25
            allowlisted = true
        } else if (packageName in TRUSTED_COMMON_APP_PACKAGES && !packageRunsAccessibilityService) {
            trustScore += 15
            allowlisted = true
        }

        return PackageTrustProfile(
            score = trustScore,
            allowlisted = allowlisted
        )
    }

    private data class PackageTrustProfile(
        val score: Int = 0,
        val allowlisted: Boolean = false
    )

    private data class TimestampedRiskAssessment(
        val timestamp: Long,
        val assessment: LatestRiskAssessment
    )

    private fun severityForScore(score: Int): String {
        return when {
            score >= 80 -> "CRITICAL"
            score >= 60 -> "HIGH"
            score >= 30 -> "MEDIUM"
            else -> "LOW"
        }
    }

    companion object {
        private val TRUSTED_INSTALLERS = setOf(
            "com.android.vending",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller"
        )

        private val KNOWN_SYSTEM_ACCESSIBILITY_PACKAGES = setOf(
            "com.google.android.marvin.talkback",
            "com.android.talkback",
            "com.android.systemui",
            "com.android.settings",
            "com.samsung.android.accessibility",
            "com.samsung.android.app.talkback",
            "com.google.android.accessibility.switchaccess"
        )

        private val TRUSTED_COMMON_APP_PACKAGES = setOf(
            "com.whatsapp",
            "com.facebook.katana",
            "com.facebook.orca",
            "com.instagram.android",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.google.android.gm",
            "com.android.chrome"
        )

        private const val TAG = "SecurityRepository"
    }
}

private fun SecurityAlertEntity.toDetectionSummary(
    profile: AppProfileEntity?
): LatestDetectionSummary {
    val packageName = packageName.orEmpty()
    return LatestDetectionSummary(
        packageName = packageName,
        appName = profile?.takeIf { it.packageName == packageName }?.appLabel ?: packageName.ifBlank { "Unknown app" },
        score = score.coerceIn(0, 100),
        severity = severity,
        timestamp = timestamp,
        title = title,
        description = description
    )
}

private fun RiskSnapshotEntity.toDetectionSummary(
    profile: AppProfileEntity?
): LatestDetectionSummary {
    val packageName = packageName.orEmpty()
    val score = score.coerceIn(0, 100)
    return LatestDetectionSummary(
        packageName = packageName,
        appName = profile?.takeIf { it.packageName == packageName }?.appLabel ?: packageName.ifBlank { "Unknown app" },
        score = score,
        severity = severity,
        timestamp = timestamp,
        title = "Suspicious accessibility activity",
        description = "Persisted risk score is currently $score for ${packageName.ifBlank { "this app" }}."
    )
}

private fun AppProfileEntity.toDetectionSummary(): LatestDetectionSummary {
    val score = currentRiskScore.coerceIn(0, 100)
    return LatestDetectionSummary(
        packageName = packageName,
        appName = appLabel,
        score = score,
        severity = when {
            score >= 80 -> "CRITICAL"
            score >= 60 -> "HIGH"
            score >= 30 -> "MEDIUM"
            else -> "LOW"
        },
        timestamp = lastSeenAt,
        title = "Suspicious accessibility activity",
        description = "Persisted risk score is currently $score for $packageName."
    )
}
