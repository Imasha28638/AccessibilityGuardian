package com.sliit.isp.accessibilityguardian

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sliit.isp.accessibilityguardian.core.model.RiskAssessment
import com.sliit.isp.accessibilityguardian.core.model.RuleResult
import com.sliit.isp.accessibilityguardian.core.model.Severity
import com.sliit.isp.accessibilityguardian.data.local.AppDatabase
import com.sliit.isp.accessibilityguardian.data.local.entities.AppProfileEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity
import com.sliit.isp.accessibilityguardian.data.repository.SecurityRepository
import com.sliit.isp.accessibilityguardian.ui.LogsViewModel
import com.sliit.isp.accessibilityguardian.ui.MonitorViewModel
import com.sliit.isp.accessibilityguardian.ui.inspect.InspectViewModel
import com.sliit.isp.accessibilityguardian.util.MonitoringPreferences
import com.sliit.isp.accessibilityguardian.util.ServiceLocator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetectionPipelineInstrumentedTest {

    private lateinit var application: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: SecurityRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        application = context.applicationContext as Application
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SecurityRepository(context, database)
        ServiceLocator.setRepositoryForTests(repository)
        MonitoringPreferences.setMonitoringEnabled(context, true)
        MonitoringPreferences.setRealtimeAlertsEnabled(context, false)
        MonitoringPreferences.setThreatNotificationsEnabled(context, false)
    }

    @After
    fun tearDown() {
        ServiceLocator.resetForTests()
        database.close()
    }

    @Test
    fun persisted_detection_updates_repository_and_all_three_viewmodels() = runBlocking {
        val packageName = "com.test.malware"
        val event = EventRecordEntity(
            timestamp = System.currentTimeMillis(),
            sourcePackage = packageName,
            sourceClass = "android.widget.Button",
            eventType = 1,
            eventText = "Allow",
            foregroundPackage = "com.test.bankapp",
            isSensitiveContext = true
        )
        val assessment = RiskAssessment(
            score = 85,
            severity = Severity.CRITICAL,
            triggeredRules = listOf(
                RuleResult(
                    ruleId = "new_accessibility_service",
                    matched = true,
                    riskDelta = 25,
                    title = "New Accessibility Service",
                    description = "A new non-system accessibility service was recently enabled."
                )
            )
        )

        repository.persistEvaluation(event, assessment)

        val storedEvent = database.eventRecordDao().recentForPackage(packageName, 1).single()
        val storedProfile = database.appProfileDao().getByPackage(packageName)
        val storedSnapshot = database.riskSnapshotDao().recentForPackage(packageName, 1).single()
        val storedAlert = database.securityAlertDao().recentForPackage(packageName, 1).single()

        assertEquals(25, storedEvent.riskDelta)
        assertEquals(85, storedProfile?.currentRiskScore)
        assertEquals(85, storedSnapshot.score)
        assertEquals("CRITICAL", storedAlert.severity)

        val latestSummary = repository.observeLatestDetectionSummary().first()
        assertEquals(packageName, latestSummary?.packageName)
        assertEquals(85, latestSummary?.score)

        val monitorViewModel = MonitorViewModel(application)
        val logsViewModel = LogsViewModel(application)
        val inspectViewModel = InspectViewModel(application, SavedStateHandle())
        inspectViewModel.selectPackage(packageName)

        waitFor {
            monitorViewModel.uiState.value.overallRiskScore == 85 &&
                logsViewModel.uiState.value.entries.isNotEmpty() &&
                inspectViewModel.uiState.value.riskScore == 85
        }

        assertEquals(85, monitorViewModel.uiState.value.overallRiskScore)
        assertEquals(1, monitorViewModel.uiState.value.alertCount)
        assertFalse(logsViewModel.uiState.value.entries.isEmpty())
        assertEquals("CRITICAL", logsViewModel.uiState.value.entries.first().severity)
        assertEquals(85, inspectViewModel.uiState.value.riskScore)
        assertEquals("CRITICAL", inspectViewModel.uiState.value.riskLevel)
        assertTrue(inspectViewModel.uiState.value.recentEvents.isNotEmpty())
    }

    @Test
    fun latest_detection_summary_prefers_most_recent_suspicious_profile() = runBlocking {
        val now = System.currentTimeMillis()
        database.appProfileDao().upsert(
            AppProfileEntity(
                packageName = "com.test.older",
                appLabel = "Older",
                installerPackage = null,
                firstInstallTime = now - 10_000L,
                lastUpdateTime = now - 10_000L,
                isSystemApp = false,
                isLauncherVisible = true,
                currentRiskScore = 95,
                lastSeenAt = now - 10_000L
            )
        )
        database.appProfileDao().upsert(
            AppProfileEntity(
                packageName = "com.test.newer",
                appLabel = "Newer",
                installerPackage = null,
                firstInstallTime = now,
                lastUpdateTime = now,
                isSystemApp = false,
                isLauncherVisible = true,
                currentRiskScore = 40,
                lastSeenAt = now
            )
        )

        val latestSummary = repository.observeLatestDetectionSummary().first()
        val latestPackage = repository.observeLatestDetectedPackage().first()

        assertEquals("com.test.newer", latestPackage)
        assertEquals("com.test.newer", latestSummary?.packageName)
        assertEquals(40, latestSummary?.score)
    }

    @Test
    fun trust_package_resolves_alert_but_keeps_current_risk_assessment_consistent() = runBlocking {
        val packageName = "com.test.resolved"
        val event = EventRecordEntity(
            timestamp = System.currentTimeMillis(),
            sourcePackage = packageName,
            sourceClass = "android.view.View",
            eventType = 32,
            eventText = "Enable service",
            foregroundPackage = "com.test.bank",
            isSensitiveContext = true
        )
        val assessment = RiskAssessment(
            score = 65,
            severity = Severity.HIGH,
            triggeredRules = listOf(
                RuleResult(
                    ruleId = "recent_install_accessibility",
                    matched = true,
                    riskDelta = 20,
                    title = "Recent install accessibility",
                    description = "Recently installed app involved in accessibility activity."
                )
            )
        )

        repository.persistEvaluation(event, assessment)
        repository.trustPackage(packageName)

        val latestRisk = repository.observeLatestRiskAssessmentForPackage(packageName).first()
        val latestOpenAlert = database.securityAlertDao().latestOpenForPackage(packageName)
        val storedProfile = database.appProfileDao().getByPackage(packageName)
        val decision = database.userDecisionDao().latestForPackage(packageName)

        assertEquals(0, latestRisk?.score)
        assertEquals("LOW", latestRisk?.severity)
        assertNull(latestOpenAlert)
        assertTrue(storedProfile?.isTrusted == true)
        assertEquals(0, storedProfile?.currentRiskScore)
        assertEquals("TRUST", decision?.decision)
    }

    @Test
    fun otp_window_and_user_decision_are_read_back_into_detection_context() = runBlocking {
        val packageName = "com.test.otp"
        val now = System.currentTimeMillis()

        repository.startOtpSensitivityWindow(sourcePackage = packageName, timestamp = now)
        repository.ignorePackageOnce(packageName)

        val context = repository.buildDetectionContext(
            event = EventRecordEntity(
                timestamp = now + 1_000L,
                sourcePackage = packageName,
                sourceClass = "android.widget.TextView",
                eventType = 16,
                eventText = "OTP",
                foregroundPackage = "com.test.bank",
                isSensitiveContext = true
            )
        )

        assertTrue(context.otpWindowActive)
        assertEquals(10, context.trustAdjustment)
    }

    private fun waitFor(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Condition was not met within $timeoutMs ms")
            }
            Thread.sleep(50)
        }
    }
}
