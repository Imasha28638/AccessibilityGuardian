package com.sliit.isp.accessibilityguardian.core.model

data class RuleResult(
    val ruleId: String,
    val matched: Boolean,
    val riskDelta: Int,
    val title: String,
    val description: String,
    val evidence: List<String> = emptyList()
)
