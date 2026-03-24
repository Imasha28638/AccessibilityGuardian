package com.sliit.isp.accessibilityguardian.core.rules.impl

import android.view.accessibility.AccessibilityEvent
import com.sliit.isp.accessibilityguardian.core.model.DetectionContext
import com.sliit.isp.accessibilityguardian.core.model.RuleResult
import com.sliit.isp.accessibilityguardian.core.rules.DetectionRule
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity

class RapidUiAutomationRule : DetectionRule {
    override suspend fun evaluate(event: EventRecordEntity, context: DetectionContext): RuleResult {
        if (!context.packageRunsAccessibilityService || event.sourcePackage.isNullOrBlank()) {
            return RuleResult(
                ruleId = "rapid_ui_automation",
                matched = false,
                riskDelta = 0,
                title = "Rapid UI Automation Pattern",
                description = "Rapid automation checks only apply to packages that currently own an enabled accessibility service."
            )
        }

        val burstStart = event.timestamp - BURST_WINDOW_MS
        val automationEvents = context.recentEvents
            .asSequence()
            .filter { recentEvent ->
                recentEvent.sourcePackage == event.sourcePackage &&
                    recentEvent.timestamp in burstStart..event.timestamp &&
                    recentEvent.eventType in AUTOMATION_EVENT_TYPES
            }
            .sortedBy { it.timestamp }
            .toList()

        val burstCount = automationEvents.size
        val windowTransitionCount = automationEvents.count {
            it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                it.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        }
        val ultraFastGapCount = automationEvents
            .zipWithNext()
            .count { (previous, current) ->
                val gap = current.timestamp - previous.timestamp
                gap in MIN_AUTOMATION_GAP_MS..MAX_AUTOMATION_GAP_MS
            }
        val duplicateActionPairs = automationEvents
            .zipWithNext()
            .count { (previous, current) ->
                previous.eventType == current.eventType &&
                    current.timestamp - previous.timestamp <= DUPLICATE_ACTION_WINDOW_MS
            }

        val isMatched =
            burstCount >= MIN_BURST_EVENT_COUNT &&
                windowTransitionCount >= MIN_WINDOW_TRANSITIONS &&
                ultraFastGapCount >= MIN_ULTRA_FAST_GAPS &&
                duplicateActionPairs >= MIN_DUPLICATE_ACTION_PAIRS

        return RuleResult(
            ruleId = "rapid_ui_automation",
            matched = isMatched,
            riskDelta = if (isMatched) 18 else 0,
            title = "Rapid UI Automation Pattern",
            description = if (isMatched) {
                "Detected an unusually dense burst of accessibility-driven UI transitions for ${event.sourcePackage}. Thresholds are tuned to avoid normal scrolling, typing, and screen browsing."
            } else {
                "Recent events did not cross the automation thresholds."
            },
            evidence = listOf(
                "package=${event.sourcePackage}",
                "burstWindowMs=$BURST_WINDOW_MS",
                "burstCount=$burstCount",
                "windowTransitionCount=$windowTransitionCount",
                "ultraFastGapCount=$ultraFastGapCount",
                "duplicateActionPairs=$duplicateActionPairs"
            )
        )
    }

    companion object {
        private val AUTOMATION_EVENT_TYPES = setOf(
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED
        )

        // Require a dense burst over a short interval so ordinary browsing and chat activity do not match.
        private const val BURST_WINDOW_MS = 4_000L

        // Human interaction rarely produces this many focus/window/click transitions in four seconds.
        private const val MIN_BURST_EVENT_COUNT = 12

        // Automated navigation tends to force multiple rapid window transitions.
        private const val MIN_WINDOW_TRANSITIONS = 4

        // Back-to-back gaps under 180 ms are a stronger automation signal than raw volume alone.
        private const val MIN_AUTOMATION_GAP_MS = 25L
        private const val MAX_AUTOMATION_GAP_MS = 180L
        private const val MIN_ULTRA_FAST_GAPS = 6

        // Repeating the exact same action almost instantly helps distinguish automation from real use.
        private const val DUPLICATE_ACTION_WINDOW_MS = 220L
        private const val MIN_DUPLICATE_ACTION_PAIRS = 3
    }
}
