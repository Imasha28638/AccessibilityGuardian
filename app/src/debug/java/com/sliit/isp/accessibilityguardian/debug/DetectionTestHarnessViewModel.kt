package com.sliit.isp.accessibilityguardian.debug

import android.app.Application
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sliit.isp.accessibilityguardian.core.engine.DetectionRuleEngine
import com.sliit.isp.accessibilityguardian.core.engine.RiskEngine
import com.sliit.isp.accessibilityguardian.core.model.DetectionContext
import com.sliit.isp.accessibilityguardian.core.model.RuleResult
import com.sliit.isp.accessibilityguardian.core.monitor.AccessibilityStateMonitor
import com.sliit.isp.accessibilityguardian.data.local.entities.AppProfileEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity
import com.sliit.isp.accessibilityguardian.data.repository.SecurityRepository
import com.sliit.isp.accessibilityguardian.util.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DetectionHarnessUiState(
    val stageTitle: String = "Scenario Stage",
    val stageDescription: String = "Run one of the tests above.",
    val showBenignControls: Boolean = false,
    val showOtpStage: Boolean = false,
    val benignTapCount: Int = 0,
    val latestDetectedPackage: String = "-",
    val latestRiskScore: String = "-",
    val latestSeverity: String = "-",
    val matchedRules: String = "-",
    val packageOwnsAccessibilityService: String = "-",
    val enabledAccessibilityServices: String = "-",
    val newlyEnabledPackagesObserved: String = "-",
    val notes: String = "No harness action executed yet."
)

class DetectionTestHarnessViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: SecurityRepository =
        ServiceLocator.repository(application.applicationContext)
    private val ruleEngine: DetectionRuleEngine = ServiceLocator.detectionRuleEngine()
    private val riskEngine: RiskEngine = ServiceLocator.riskEngine()
    private val accessibilityStateMonitor = AccessibilityStateMonitor(application.applicationContext)
    private val appPackageName = application.applicationContext.packageName

    private val _uiState = MutableStateFlow(DetectionHarnessUiState())
    val uiState: StateFlow<DetectionHarnessUiState> = _uiState.asStateFlow()

    private var lastObservedEnabledPackages: Set<String> =
        accessibilityStateMonitor.getEnabledAccessibilityServicePackages()
    private var observedNewlyEnabledPackages: Set<String> = emptySet()

    init {
        refreshHarnessInfo("Harness ready. Long-press the Settings Developer row to reopen this screen.")
    }

    fun runBenignInteractionTest() {
        viewModelScope.launch {
            val (enabledPackages, newlyEnabledPackages) = refreshObservedAccessibilityPackages()
            val now = System.currentTimeMillis()
            val packageName = appPackageName
            val benignEvents = listOf(
                createEvent(
                    timestamp = now - 4_000L,
                    packageName = packageName,
                    eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    eventText = "Debug benign screen",
                    foregroundPackage = packageName
                ),
                createEvent(
                    timestamp = now - 2_400L,
                    packageName = packageName,
                    eventType = AccessibilityEvent.TYPE_VIEW_CLICKED,
                    eventText = "Tap benign button",
                    foregroundPackage = packageName
                ),
                createEvent(
                    timestamp = now - 1_300L,
                    packageName = packageName,
                    eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                    eventText = "Scroll debug content",
                    foregroundPackage = packageName
                ),
                createEvent(
                    timestamp = now,
                    packageName = packageName,
                    eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    eventText = "Return to benign stage",
                    foregroundPackage = packageName
                )
            )

            val result = evaluateScenario(
                testName = "Benign Interaction Test",
                packageName = packageName,
                enabledPackages = enabledPackages,
                newlyEnabledPackages = newlyEnabledPackages,
                event = benignEvents.last(),
                recentEvents = benignEvents,
                note = "Benign taps, scrolling, and screen changes should not produce strong rules or alerts."
            )

            _uiState.value = _uiState.value.copy(
                stageTitle = "Benign Baseline Stage",
                stageDescription = "Tap the benign controls below, scroll the screen, and dismiss the safe dialog. These interactions should stay low-risk.",
                showBenignControls = true,
                showOtpStage = false,
                notes = result.note,
                latestDetectedPackage = result.packageName,
                latestRiskScore = result.score.toString(),
                latestSeverity = result.severity,
                matchedRules = result.matchedRulesText,
                packageOwnsAccessibilityService = result.packageOwnsService.toString(),
                enabledAccessibilityServices = result.enabledServicesText,
                newlyEnabledPackagesObserved = result.newlyEnabledText
            )
        }
    }

    fun showEnabledAccessibilityServices() {
        viewModelScope.launch {
            val (enabledPackages, newlyEnabledPackages) = refreshObservedAccessibilityPackages()
            val targetPackage = enabledPackages.firstOrNull()

            if (targetPackage == null) {
                _uiState.value = _uiState.value.copy(
                    stageTitle = "Enabled Accessibility Services",
                    stageDescription = "No third-party accessibility-service packages are currently enabled on this device.",
                    showBenignControls = false,
                    showOtpStage = false,
                    latestDetectedPackage = appPackageName,
                    latestRiskScore = "0",
                    latestSeverity = "LOW",
                    matchedRules = "None",
                    packageOwnsAccessibilityService = "false",
                    enabledAccessibilityServices = formatPackages(enabledPackages),
                    newlyEnabledPackagesObserved = formatPackages(observedNewlyEnabledPackages),
                    notes = "Enable a real accessibility service, then rerun this action to inspect package ownership and contextual detection."
                )
                logHarnessAction(
                    testName = "Show Enabled Accessibility Services",
                    packageName = appPackageName,
                    enabledPackages = enabledPackages,
                    newlyEnabledPackages = newlyEnabledPackages,
                    score = 0,
                    severity = "LOW",
                    matchedRules = emptyList()
                )
                return@launch
            }

            val event = createEvent(
                timestamp = System.currentTimeMillis(),
                packageName = targetPackage,
                eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                eventText = "Enable accessibility service",
                foregroundPackage = "com.android.settings"
            )
            val result = evaluateScenario(
                testName = "Show Enabled Accessibility Services",
                packageName = targetPackage,
                enabledPackages = enabledPackages,
                newlyEnabledPackages = setOf(targetPackage),
                event = event,
                recentEvents = listOf(event),
                note = "Contextual accessibility ownership check for a real enabled service-owning package."
            )

            _uiState.value = _uiState.value.copy(
                stageTitle = "Enabled Accessibility Services",
                stageDescription = "Current enabled service packages are shown in the results panel. The rule evaluation below treats the first enabled package as newly enabled for safe contextual verification.",
                showBenignControls = false,
                showOtpStage = false,
                latestDetectedPackage = result.packageName,
                latestRiskScore = result.score.toString(),
                latestSeverity = result.severity,
                matchedRules = result.matchedRulesText,
                packageOwnsAccessibilityService = result.packageOwnsService.toString(),
                enabledAccessibilityServices = result.enabledServicesText,
                newlyEnabledPackagesObserved = result.newlyEnabledText,
                notes = result.note
            )
        }
    }

    fun runRecentInstallAccessibilityTest() {
        viewModelScope.launch {
            val (enabledPackages, newlyEnabledPackages) = refreshObservedAccessibilityPackages()
            val now = System.currentTimeMillis()
            val event = createEvent(
                timestamp = now,
                packageName = appPackageName,
                eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                eventText = "Accessibility service active after install",
                foregroundPackage = appPackageName
            )
            val profile = syntheticProfile(
                firstInstallTime = now - 15 * 60 * 1000L,
                currentRiskScore = 0
            )
            val result = evaluateScenario(
                testName = "Recent Install + Accessibility Test",
                packageName = appPackageName,
                enabledPackages = setOf(appPackageName),
                newlyEnabledPackages = newlyEnabledPackages,
                event = event,
                recentEvents = listOf(event),
                appProfileOverride = profile,
                note = "Synthetic recent-install context with accessibility ownership. This should stay lower risk than correlated abuse patterns."
            )

            _uiState.value = _uiState.value.copy(
                stageTitle = "Recent Install + Accessibility",
                stageDescription = "This simulation uses the current app package with a synthetic recent install timestamp so the rule can be verified safely.",
                showBenignControls = false,
                showOtpStage = false,
                latestDetectedPackage = result.packageName,
                latestRiskScore = result.score.toString(),
                latestSeverity = result.severity,
                matchedRules = result.matchedRulesText,
                packageOwnsAccessibilityService = result.packageOwnsService.toString(),
                enabledAccessibilityServices = result.enabledServicesText,
                newlyEnabledPackagesObserved = result.newlyEnabledText,
                notes = result.note
            )
        }
    }

    fun runRapidUiAutomationTest() {
        viewModelScope.launch {
            val (_, newlyEnabledPackages) = refreshObservedAccessibilityPackages()
            val now = System.currentTimeMillis()
            val rapidEvents = buildRapidAutomationEvents(now)
            val result = evaluateScenario(
                testName = "Rapid UI Automation Test",
                packageName = appPackageName,
                enabledPackages = setOf(appPackageName),
                newlyEnabledPackages = newlyEnabledPackages,
                event = rapidEvents.last(),
                recentEvents = rapidEvents,
                note = "Dense repeated transitions are injected into the evaluation context to exercise RapidUiAutomationRule without requiring unsafe automation."
            )

            _uiState.value = _uiState.value.copy(
                stageTitle = "Rapid UI Automation Simulation",
                stageDescription = "This action feeds a dense event burst through the real rule engine to verify the stricter automation thresholds.",
                showBenignControls = false,
                showOtpStage = false,
                latestDetectedPackage = result.packageName,
                latestRiskScore = result.score.toString(),
                latestSeverity = result.severity,
                matchedRules = result.matchedRulesText,
                packageOwnsAccessibilityService = result.packageOwnsService.toString(),
                enabledAccessibilityServices = result.enabledServicesText,
                newlyEnabledPackagesObserved = result.newlyEnabledText,
                notes = result.note
            )
        }
    }

    fun runOverlayTest() {
        viewModelScope.launch {
            val (_, newlyEnabledPackages) = refreshObservedAccessibilityPackages()
            val event = createEvent(
                timestamp = System.currentTimeMillis(),
                packageName = appPackageName,
                eventType = AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                eventText = "confirm transfer overlay",
                foregroundPackage = "com.debug.bank.wallet"
            ).copy(sourceClass = "DebugOverlayPanel", isSensitiveContext = true)

            val result = evaluateScenario(
                testName = "Overlay Test",
                packageName = appPackageName,
                enabledPackages = setOf(appPackageName),
                newlyEnabledPackages = newlyEnabledPackages,
                event = event,
                recentEvents = listOf(event),
                overlayLikelyOverride = true,
                note = "Simulated overlay-like pattern only. No real overlay is drawn; this safely tests the overlay/accessibility correlation path."
            )

            _uiState.value = _uiState.value.copy(
                stageTitle = "Overlay Correlation Simulation",
                stageDescription = "This debug flow simulates the closest safe overlay pattern by using overlay-like source metadata and a sensitive foreground context.",
                showBenignControls = false,
                showOtpStage = false,
                latestDetectedPackage = result.packageName,
                latestRiskScore = result.score.toString(),
                latestSeverity = result.severity,
                matchedRules = result.matchedRulesText,
                packageOwnsAccessibilityService = result.packageOwnsService.toString(),
                enabledAccessibilityServices = result.enabledServicesText,
                newlyEnabledPackagesObserved = result.newlyEnabledText,
                notes = result.note
            )
        }
    }

    fun runOtpContextTest() {
        viewModelScope.launch {
            val (_, newlyEnabledPackages) = refreshObservedAccessibilityPackages()
            val event = createEvent(
                timestamp = System.currentTimeMillis(),
                packageName = appPackageName,
                eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                eventText = "OTP verification code bank wallet confirm transfer",
                foregroundPackage = "com.debug.bank.wallet"
            ).copy(isSensitiveContext = true)

            val result = evaluateScenario(
                testName = "OTP Context Test",
                packageName = appPackageName,
                enabledPackages = setOf(appPackageName),
                newlyEnabledPackages = newlyEnabledPackages,
                event = event,
                recentEvents = listOf(event),
                otpWindowActiveOverride = true,
                note = "OTP-sensitive keywords and a payment foreground context are supplied locally so OtpCorrelationRule can be verified without external SMS."
            )

            _uiState.value = _uiState.value.copy(
                stageTitle = "OTP Context Stage",
                stageDescription = "The panel below uses safe local-only OTP and payment text so you can validate OTP correlation behavior without real banking or SMS activity.",
                showBenignControls = false,
                showOtpStage = true,
                latestDetectedPackage = result.packageName,
                latestRiskScore = result.score.toString(),
                latestSeverity = result.severity,
                matchedRules = result.matchedRulesText,
                packageOwnsAccessibilityService = result.packageOwnsService.toString(),
                enabledAccessibilityServices = result.enabledServicesText,
                newlyEnabledPackagesObserved = result.newlyEnabledText,
                notes = result.note
            )
        }
    }

    fun runRecalculateRiskTest() {
        viewModelScope.launch {
            repository.recalculateAllRisks()
            val (enabledPackages, newlyEnabledPackages) = refreshObservedAccessibilityPackages()
            val persistedAssessment = repository
                .observeLatestRiskAssessmentForPackage(appPackageName)
                .first()
            val latestAlert = repository.observeLatestAlertForPackage(appPackageName).first()
            val score = persistedAssessment?.score ?: 0
            val severity = persistedAssessment?.severity ?: "LOW"

            _uiState.value = _uiState.value.copy(
                stageTitle = "Recalculate Risk",
                stageDescription = "Runs the repository-wide recalculation path using the real current accessibility state.",
                showBenignControls = false,
                showOtpStage = false,
                latestDetectedPackage = appPackageName,
                latestRiskScore = score.toString(),
                latestSeverity = severity,
                matchedRules = latestAlert?.evidenceText ?: "No persisted alert evidence",
                packageOwnsAccessibilityService = (appPackageName in enabledPackages).toString(),
                enabledAccessibilityServices = formatPackages(enabledPackages),
                newlyEnabledPackagesObserved = formatPackages(observedNewlyEnabledPackages),
                notes = "Persisted risk recalculation completed."
            )
            logHarnessAction(
                testName = "Recalculate Risk Test",
                packageName = appPackageName,
                enabledPackages = enabledPackages,
                newlyEnabledPackages = newlyEnabledPackages,
                score = score,
                severity = severity,
                matchedRules = latestAlert?.evidenceText
                    ?.lineSequence()
                    ?.filter { it.isNotBlank() }
                    ?.toList()
                    .orEmpty()
            )
        }
    }

    fun runClearDetectionDataTest() {
        viewModelScope.launch {
            repository.clearDetectionDataForDebug()
            val (enabledPackages, newlyEnabledPackages) = refreshObservedAccessibilityPackages()
            _uiState.value = _uiState.value.copy(
                stageTitle = "Detection Data Cleared",
                stageDescription = "Alerts, snapshots, stored events, OTP windows, and persisted risk scores were reset.",
                showBenignControls = false,
                showOtpStage = false,
                latestDetectedPackage = appPackageName,
                latestRiskScore = "0",
                latestSeverity = "LOW",
                matchedRules = "None",
                packageOwnsAccessibilityService = (appPackageName in enabledPackages).toString(),
                enabledAccessibilityServices = formatPackages(enabledPackages),
                newlyEnabledPackagesObserved = formatPackages(observedNewlyEnabledPackages),
                notes = "Detection data cleared. Production screens remain unchanged unless you manually open them in this debug build."
            )
            logHarnessAction(
                testName = "Clear Detection Data Test",
                packageName = appPackageName,
                enabledPackages = enabledPackages,
                newlyEnabledPackages = newlyEnabledPackages,
                score = 0,
                severity = "LOW",
                matchedRules = emptyList()
            )
        }
    }

    fun onBenignTap() {
        _uiState.value = _uiState.value.copy(
            benignTapCount = _uiState.value.benignTapCount + 1,
            notes = "Benign tap count: ${_uiState.value.benignTapCount + 1}. Ordinary interaction should stay low-risk."
        )
    }

    private fun refreshHarnessInfo(note: String) {
        val enabledPackages = accessibilityStateMonitor.getEnabledAccessibilityServicePackages()
        _uiState.value = _uiState.value.copy(
            enabledAccessibilityServices = formatPackages(enabledPackages),
            newlyEnabledPackagesObserved = formatPackages(observedNewlyEnabledPackages),
            notes = note
        )
    }

    private suspend fun refreshObservedAccessibilityPackages(): Pair<Set<String>, Set<String>> {
        val enabledPackages = accessibilityStateMonitor.getEnabledAccessibilityServicePackages()
        val newlyEnabledPackages = enabledPackages - lastObservedEnabledPackages
        if (newlyEnabledPackages.isNotEmpty()) {
            observedNewlyEnabledPackages = observedNewlyEnabledPackages + newlyEnabledPackages
        }
        lastObservedEnabledPackages = enabledPackages
        return enabledPackages to newlyEnabledPackages
    }

    private suspend fun evaluateScenario(
        testName: String,
        packageName: String,
        enabledPackages: Set<String>,
        newlyEnabledPackages: Set<String>,
        event: EventRecordEntity,
        recentEvents: List<EventRecordEntity>,
        appProfileOverride: AppProfileEntity? = null,
        overlayLikelyOverride: Boolean? = null,
        otpWindowActiveOverride: Boolean? = null,
        note: String
    ): HarnessScenarioResult {
        val baseContext = repository.buildDetectionContext(
            event = event.copy(sourcePackage = packageName),
            accessibilityGloballyEnabled = accessibilityStateMonitor.isAccessibilityGloballyEnabled(),
            enabledAccessibilityServicePackages = enabledPackages,
            newlyEnabledAccessibilityServicePackages = newlyEnabledPackages
        )
        val context = baseContext.copy(
            appProfile = appProfileOverride ?: baseContext.appProfile ?: syntheticProfile(),
            foregroundPackage = event.foregroundPackage,
            recentEvents = recentEvents,
            overlayLikely = overlayLikelyOverride ?: baseContext.overlayLikely,
            otpWindowActive = otpWindowActiveOverride ?: baseContext.otpWindowActive
        )
        val matchedRules = ruleEngine.evaluate(event.copy(sourcePackage = packageName), context)
        val assessment = riskEngine.calculate(
            baseScore = context.appProfile?.currentRiskScore ?: 0,
            triggeredRules = matchedRules,
            trustAdjustment = context.trustAdjustment
        )

        logHarnessAction(
            testName = testName,
            packageName = packageName,
            enabledPackages = enabledPackages,
            newlyEnabledPackages = newlyEnabledPackages,
            score = assessment.score,
            severity = assessment.severity.name,
            matchedRules = matchedRules.map(RuleResult::ruleId)
        )

        return HarnessScenarioResult(
            packageName = packageName,
            score = assessment.score,
            severity = assessment.severity.name,
            matchedRulesText = matchedRules.joinToString { it.ruleId }.ifBlank { "None" },
            packageOwnsService = context.packageRunsAccessibilityService,
            enabledServicesText = formatPackages(enabledPackages),
            newlyEnabledText = formatPackages(observedNewlyEnabledPackages.ifEmpty { newlyEnabledPackages }),
            note = note
        )
    }

    private fun buildRapidAutomationEvents(now: Long): List<EventRecordEntity> {
        val eventTypes = listOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED
        )
        val events = mutableListOf<EventRecordEntity>()
        var timestamp = now - 1_200L
        repeat(3) { cycle ->
            eventTypes.forEachIndexed { index, type ->
                events += createEvent(
                    timestamp = timestamp,
                    packageName = appPackageName,
                    eventType = type,
                    eventText = "Automation cycle $cycle event $index",
                    foregroundPackage = appPackageName
                )
                timestamp += when (index) {
                    0 -> 60L
                    1 -> 45L
                    2 -> 55L
                    else -> 70L
                }
            }
        }
        return events
    }

    private fun syntheticProfile(
        firstInstallTime: Long = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L,
        currentRiskScore: Int = 0
    ): AppProfileEntity {
        return AppProfileEntity(
            packageName = appPackageName,
            appLabel = "AccessibilityGuardian Debug Harness",
            installerPackage = "debug.harness",
            firstInstallTime = firstInstallTime,
            lastUpdateTime = System.currentTimeMillis(),
            isSystemApp = false,
            isLauncherVisible = true,
            isTrusted = false,
            currentRiskScore = currentRiskScore,
            lastSeenAt = System.currentTimeMillis()
        )
    }

    private fun createEvent(
        timestamp: Long,
        packageName: String,
        eventType: Int,
        eventText: String,
        foregroundPackage: String
    ): EventRecordEntity {
        return EventRecordEntity(
            timestamp = timestamp,
            sourcePackage = packageName,
            sourceClass = "DetectionHarnessStage",
            eventType = eventType,
            eventText = eventText,
            foregroundPackage = foregroundPackage,
            isSensitiveContext = eventText.contains("otp", ignoreCase = true) ||
                eventText.contains("bank", ignoreCase = true) ||
                eventText.contains("transfer", ignoreCase = true)
        )
    }

    private fun formatPackages(packages: Set<String>): String {
        return packages.sorted().joinToString().ifBlank { "None" }
    }

    private fun logHarnessAction(
        testName: String,
        packageName: String,
        enabledPackages: Set<String>,
        newlyEnabledPackages: Set<String>,
        score: Int,
        severity: String,
        matchedRules: List<String>
    ) {
        Log.d(
            TAG,
            "test=$testName package=$packageName enabledPackages=$enabledPackages " +
                "newlyEnabledPackages=$newlyEnabledPackages score=$score severity=$severity matchedRules=$matchedRules"
        )
    }

    private data class HarnessScenarioResult(
        val packageName: String,
        val score: Int,
        val severity: String,
        val matchedRulesText: String,
        val packageOwnsService: Boolean,
        val enabledServicesText: String,
        val newlyEnabledText: String,
        val note: String
    )

    companion object {
        private const val TAG = "DetectionHarness"
    }
}
