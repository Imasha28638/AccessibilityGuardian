package com.sliit.isp.accessibilityguardian.core.engine

import com.sliit.isp.accessibilityguardian.core.model.RiskAssessment
import com.sliit.isp.accessibilityguardian.core.model.RuleResult
import com.sliit.isp.accessibilityguardian.core.model.Severity

class RiskEngine {

    fun calculate(
        baseScore: Int,
        triggeredRules: List<RuleResult>,
        trustAdjustment: Int,
        sensitivityPercent: Int = 65
    ): RiskAssessment {
        val strongSignalCount = triggeredRules.count { it.ruleId in STRONG_RULE_IDS }
        val weakSignalCount = triggeredRules.count { it.ruleId in WEAK_RULE_IDS }
        val scaledRuleRisk =
            (triggeredRules.sumOf { it.riskDelta } * sensitivityMultiplier(sensitivityPercent)).toInt()

        val dampenedBaseScore = when {
            triggeredRules.isEmpty() -> (baseScore * 0.45f).toInt()
            strongSignalCount == 0 -> minOf((baseScore * 0.35f).toInt(), 12)
            strongSignalCount == 1 && weakSignalCount == 0 -> minOf((baseScore * 0.65f).toInt(), 20)
            else -> baseScore
        }

        val raw = dampenedBaseScore + scaledRuleRisk - trustAdjustment
        val score = raw.coerceIn(0, 100)

        val severity = when {
            score >= 80 -> Severity.CRITICAL
            score >= 60 -> Severity.HIGH
            score >= 30 -> Severity.MEDIUM
            else -> Severity.LOW
        }

        return RiskAssessment(
            score = score,
            severity = severity,
            triggeredRules = triggeredRules
        )
    }

    private fun sensitivityMultiplier(sensitivityPercent: Int): Float {
        return when {
            sensitivityPercent < 35 -> 0.75f
            sensitivityPercent < 75 -> 1.0f
            else -> 1.25f
        }
    }

    companion object {
        private val STRONG_RULE_IDS = setOf(
            "rapid_ui_automation",
            "overlay_accessibility_combo",
            "otp_correlation"
        )

        private val WEAK_RULE_IDS = setOf(
            "new_accessibility_service",
            "recent_install_accessibility"
        )
    }
}
