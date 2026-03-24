package com.sliit.isp.accessibilityguardian.core.engine

import android.util.Log
import com.sliit.isp.accessibilityguardian.core.model.DetectionContext
import com.sliit.isp.accessibilityguardian.core.model.RuleResult
import com.sliit.isp.accessibilityguardian.core.rules.DetectionRule
import com.sliit.isp.accessibilityguardian.core.rules.impl.NewAccessibilityServiceRule
import com.sliit.isp.accessibilityguardian.core.rules.impl.OtpCorrelationRule
import com.sliit.isp.accessibilityguardian.core.rules.impl.OverlayAccessibilityComboRule
import com.sliit.isp.accessibilityguardian.core.rules.impl.RapidUiAutomationRule
import com.sliit.isp.accessibilityguardian.core.rules.impl.RecentInstallAccessibilityRule
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity

class DetectionRuleEngine(
    private val rules: List<DetectionRule> = defaultRules()
) {

    fun registeredRuleIds(): List<String> = listOf(
        "new_accessibility_service",
        "recent_install_accessibility",
        "rapid_ui_automation",
        "otp_correlation",
        "overlay_accessibility_combo"
    )

    suspend fun evaluate(
        event: EventRecordEntity,
        context: DetectionContext
    ): List<RuleResult> {
        val matchedRules = rules
            .map { it.evaluate(event, context) }
            .filter { it.matched }

        val correlatedRules = applyCorrelationPolicy(matchedRules, context)

        if (matchedRules.isNotEmpty() || context.packageRunsAccessibilityService) {
            Log.d(
                TAG,
                buildString {
                    append("package=")
                    append(context.packageName ?: event.sourcePackage ?: "unknown")
                    append(" ownsEnabledService=")
                    append(context.packageRunsAccessibilityService)
                    append(" rawRules=")
                    append(matchedRules.joinToString(prefix = "[", postfix = "]") { "${it.ruleId}:${it.riskDelta}" })
                    append(" correlatedRules=")
                    append(correlatedRules.joinToString(prefix = "[", postfix = "]") { "${it.ruleId}:${it.riskDelta}" })
                }
            )
        }

        return correlatedRules
    }

    private fun applyCorrelationPolicy(
        matchedRules: List<RuleResult>,
        context: DetectionContext
    ): List<RuleResult> {
        if (matchedRules.isEmpty()) {
            return emptyList()
        }

        val matchedRuleIds = matchedRules.map { it.ruleId }.toSet()
        val strongSignalIds = setOf(
            "rapid_ui_automation",
            "overlay_accessibility_combo",
            "otp_correlation"
        )
        val supportSignalIds = setOf(
            "new_accessibility_service",
            "recent_install_accessibility"
        )
        val strongSignalCount = matchedRules.count { it.ruleId in strongSignalIds }
        val supportSignalCount = matchedRules.count { it.ruleId in supportSignalIds }
        val isAllowlistedWithoutStrongEvidence =
            context.packageIsAllowlisted && strongSignalCount < 2

        return matchedRules.mapNotNull { result ->
            val adjustedRiskDelta = when (result.ruleId) {
                "new_accessibility_service" -> when {
                    strongSignalCount > 0 -> 8
                    supportSignalCount > 1 -> 5
                    else -> 4
                }

                "recent_install_accessibility" -> when {
                    strongSignalCount > 0 -> 6
                    matchedRuleIds.contains("new_accessibility_service") -> 4
                    else -> 0
                }

                "otp_correlation" -> when {
                    matchedRuleIds.contains("rapid_ui_automation") ||
                        matchedRuleIds.contains("overlay_accessibility_combo") -> result.riskDelta
                    else -> 10
                }

                "rapid_ui_automation" -> if (context.packageRunsAccessibilityService) result.riskDelta else 0
                else -> result.riskDelta
            }

            val trustAdjustedDelta = when {
                adjustedRiskDelta <= 0 -> 0
                isAllowlistedWithoutStrongEvidence -> (adjustedRiskDelta * 0.5f).toInt().coerceAtLeast(1)
                else -> adjustedRiskDelta
            }

            if (trustAdjustedDelta <= 0) {
                null
            } else {
                result.copy(
                    riskDelta = trustAdjustedDelta,
                    evidence = result.evidence + "correlatedRiskDelta=$trustAdjustedDelta"
                )
            }
        }
    }

    companion object {
        private const val TAG = "DetectionRuleEngine"

        private fun defaultRules(): List<DetectionRule> = listOf(
            NewAccessibilityServiceRule(),
            RecentInstallAccessibilityRule(),
            RapidUiAutomationRule(),
            OtpCorrelationRule(),
            OverlayAccessibilityComboRule()
        )
    }
}
