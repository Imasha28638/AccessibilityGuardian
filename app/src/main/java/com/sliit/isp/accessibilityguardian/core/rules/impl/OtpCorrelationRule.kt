package com.sliit.isp.accessibilityguardian.core.rules.impl

import com.sliit.isp.accessibilityguardian.core.model.DetectionContext
import com.sliit.isp.accessibilityguardian.core.model.RuleResult
import com.sliit.isp.accessibilityguardian.core.rules.DetectionRule
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity

class OtpCorrelationRule : DetectionRule {
    override suspend fun evaluate(event: EventRecordEntity, context: DetectionContext): RuleResult {
        val sensitiveForeground = context.foregroundPackage?.let { foreground ->
            listOf("bank", "wallet", "payment", "transfer", "upi", "finance")
                .any { foreground.contains(it, ignoreCase = true) }
        } == true
        val isMatched =
            context.otpWindowActive &&
                sensitiveForeground &&
                context.packageRunsAccessibilityService

        return RuleResult(
            ruleId = "otp_correlation",
            matched = isMatched,
            riskDelta = if (isMatched) 22 else 0,
            title = "OTP Correlation",
            description = if (isMatched) {
                "Detected OTP-sensitive foreground activity while package ${context.packageName} was also running an enabled accessibility service."
            } else {
                "OTP correlation did not overlap with package-specific accessibility-service ownership."
            },
            evidence = listOf(
                "package=${context.packageName}",
                "otpWindowActive=${context.otpWindowActive}",
                "foreground=${context.foregroundPackage}",
                "packageRunsAccessibilityService=${context.packageRunsAccessibilityService}"
            )
        )
    }
}
