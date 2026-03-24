package com.sliit.isp.accessibilityguardian.core.model

data class RiskAssessment(
    val score: Int,
    val severity: Severity,
    val triggeredRules: List<RuleResult>
)
