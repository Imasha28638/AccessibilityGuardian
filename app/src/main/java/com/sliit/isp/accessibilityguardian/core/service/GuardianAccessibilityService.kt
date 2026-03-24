package com.sliit.isp.accessibilityguardian.core.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.sliit.isp.accessibilityguardian.core.engine.AlertEngine
import com.sliit.isp.accessibilityguardian.core.engine.DetectionRuleEngine
import com.sliit.isp.accessibilityguardian.core.engine.RiskEngine
import com.sliit.isp.accessibilityguardian.core.monitor.AccessibilityStateMonitor
import com.sliit.isp.accessibilityguardian.core.monitor.ForegroundAppMonitor
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity
import com.sliit.isp.accessibilityguardian.data.repository.SecurityRepository
import com.sliit.isp.accessibilityguardian.util.MonitoringPreferences
import com.sliit.isp.accessibilityguardian.util.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GuardianAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var repository: SecurityRepository
    private lateinit var alertEngine: AlertEngine
    private lateinit var accessibilityStateMonitor: AccessibilityStateMonitor
    private lateinit var foregroundAppMonitor: ForegroundAppMonitor
    private lateinit var detectionRuleEngine: DetectionRuleEngine
    private lateinit var riskEngine: RiskEngine
    private var lastEnabledAccessibilityPackages: Set<String> = emptySet()
    private var lastBlockedPackage: String? = null
    private var lastBlockedActionAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        repository = ServiceLocator.repository(applicationContext)
        alertEngine = ServiceLocator.alertEngine(applicationContext)
        accessibilityStateMonitor = AccessibilityStateMonitor(applicationContext)
        foregroundAppMonitor = ForegroundAppMonitor(applicationContext)
        detectionRuleEngine = ServiceLocator.detectionRuleEngine()
        riskEngine = ServiceLocator.riskEngine()
        lastEnabledAccessibilityPackages = accessibilityStateMonitor
            .getEnabledAccessibilityServicePackages()
            .filterNot { it == packageName }
            .toSet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!repository.getMonitoringEnabled()) return

        val currentEnabledPackages = accessibilityStateMonitor
            .getEnabledAccessibilityServicePackages()
            .filterNot { it == packageName }
            .toSet()
        val newlyEnabledPackages = currentEnabledPackages - lastEnabledAccessibilityPackages
        if (newlyEnabledPackages.isNotEmpty()) {
            Log.d(TAG, "newlyEnabledAccessibilityServicePackages=$newlyEnabledPackages")
        }
        lastEnabledAccessibilityPackages = currentEnabledPackages

        val subjectPackage = event.packageName?.toString()
        if (subjectPackage == this.packageName) return

        val eventText = buildEventText(event)

        val foregroundPackage = foregroundAppMonitor.getCurrentForegroundPackage()
        val record = EventRecordEntity(
            timestamp = System.currentTimeMillis(),
            sourcePackage = subjectPackage,
            sourceClass = event.className?.toString(),
            eventType = event.eventType,
            eventText = eventText,
            foregroundPackage = foregroundPackage,
            isSensitiveContext = isSensitiveContext(foregroundPackage ?: subjectPackage, eventText)
        )

        serviceScope.launch {
            val packageToCheck = foregroundPackage ?: record.sourcePackage
            if (!packageToCheck.isNullOrBlank() &&
                packageToCheck != this@GuardianAccessibilityService.packageName &&
                repository.isAppBlocked(packageToCheck)
            ) {
                maybeRedirectBlockedApp(packageToCheck)
                return@launch
            }

            val context = repository.buildDetectionContext(
                event = record,
                accessibilityGloballyEnabled = accessibilityStateMonitor.isAccessibilityGloballyEnabled(),
                enabledAccessibilityServicePackages = currentEnabledPackages,
                newlyEnabledAccessibilityServicePackages = newlyEnabledPackages
            )

            val rules = detectionRuleEngine.evaluate(record, context)
            val assessment = riskEngine.calculate(
                baseScore = context.appProfile?.currentRiskScore ?: 0,
                triggeredRules = rules,
                trustAdjustment = context.trustAdjustment,
                sensitivityPercent = MonitoringPreferences.sensitivityPercent(applicationContext)
            )

            if (rules.isNotEmpty() || context.packageRunsAccessibilityService) {
                Log.d(
                    TAG,
                    "package=${record.sourcePackage} ownsEnabledService=${context.packageRunsAccessibilityService} " +
                        "rules=${rules.joinToString(prefix = "[", postfix = "]") { "${it.ruleId}:${it.riskDelta}" }} " +
                        "severity=${assessment.severity} score=${assessment.score}"
                )
            }

            alertEngine.processAssessment(record, assessment)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        activeInstance = null
        super.onDestroy()
    }

    private fun maybeRedirectBlockedApp(packageName: String) {
        val now = System.currentTimeMillis()
        if (lastBlockedPackage == packageName && now - lastBlockedActionAt < BLOCK_ACTION_DEBOUNCE_MS) {
            return
        }

        lastBlockedPackage = packageName
        lastBlockedActionAt = now
        Log.d(TAG, "blockedForegroundDetected package=$packageName")

        Handler(Looper.getMainLooper()).post {
            val redirected = performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d(TAG, "blockedRedirectHome package=$packageName success=$redirected")
        }
    }

    private fun buildEventText(event: AccessibilityEvent): String {
        val listText = event.text?.joinToString(" ").orEmpty()
        val contentDescription = event.contentDescription?.toString().orEmpty()
        return "$listText $contentDescription".trim()
    }

    private fun isSensitiveContext(
        packageName: String?,
        eventText: String?
    ): Boolean {
        val combined = "${packageName.orEmpty()} ${eventText.orEmpty()}".lowercase()
        val keywords = listOf(
            "settings", "accessibility", "permission", "install",
            "allow", "enable", "bank", "wallet", "otp", "confirm", "transfer"
        )
        return keywords.any { combined.contains(it) }
    }

    companion object {
        private const val TAG = "GuardianDetection"
        private const val BLOCK_ACTION_DEBOUNCE_MS = 2_000L

        @Volatile
        private var activeInstance: GuardianAccessibilityService? = null

        fun shutdownIfRunning() {
            activeInstance?.apply {
                disableSelf()
                stopSelf()
            }
            activeInstance = null
        }
    }
}
