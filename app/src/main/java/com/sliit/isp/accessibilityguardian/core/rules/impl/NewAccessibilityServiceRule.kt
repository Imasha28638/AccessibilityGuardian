package com.sliit.isp.accessibilityguardian.core.rules.impl

import com.sliit.isp.accessibilityguardian.core.model.DetectionContext
import com.sliit.isp.accessibilityguardian.core.model.RuleResult
import com.sliit.isp.accessibilityguardian.core.rules.DetectionRule
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity

class NewAccessibilityServiceRule : DetectionRule {
    override suspend fun evaluate(event: EventRecordEntity, context: DetectionContext): RuleResult {
        val packageName = context.packageName ?: event.sourcePackage
        val enabledPackages = context.enabledAccessibilityServicePackages
            .sorted()
            .joinToString(", ")
            .ifBlank { "none" }
        val newlyEnabledPackages = context.newlyEnabledAccessibilityServicePackages
            .sorted()
            .joinToString(", ")
            .ifBlank { "none" }
        val isMatched =
            context.packageRunsAccessibilityService &&
                !packageName.isNullOrBlank() &&
                packageName in context.newlyEnabledAccessibilityServicePackages &&
                context.appProfile?.isSystemApp == false

        return RuleResult(
            ruleId = "new_accessibility_service",
            matched = isMatched,
            riskDelta = when {
                !isMatched -> 0
                context.packageIsAllowlisted || context.appProfile?.isTrusted == true -> 3
                else -> 8
            },
            title = "New Accessibility Service",
            description = if (isMatched) {
                "Package $packageName owns an enabled accessibility service that was just enabled. This is contextual evidence and should only escalate with other suspicious signals."
            } else {
                "No package-specific accessibility service enablement was detected."
            },
            evidence = listOf(
                "package=$packageName",
                "packageRunsAccessibilityService=${context.packageRunsAccessibilityService}",
                "newlyEnabledForPackage=${!packageName.isNullOrBlank() && packageName in context.newlyEnabledAccessibilityServicePackages}",
                "newlyEnabledServicePackages=$newlyEnabledPackages",
                "enabledServicePackages=$enabledPackages"
            )
        )
    }
}
