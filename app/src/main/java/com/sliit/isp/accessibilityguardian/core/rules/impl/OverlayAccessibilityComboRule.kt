package com.sliit.isp.accessibilityguardian.core.rules.impl

import com.sliit.isp.accessibilityguardian.core.model.DetectionContext
import com.sliit.isp.accessibilityguardian.core.model.RuleResult
import com.sliit.isp.accessibilityguardian.core.rules.DetectionRule
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity

class OverlayAccessibilityComboRule : DetectionRule {
    override suspend fun evaluate(event: EventRecordEntity, context: DetectionContext): RuleResult {
        val sensitiveForeground = context.foregroundPackage != null && (
                context.foregroundPackage.contains("bank", ignoreCase = true) ||
                context.foregroundPackage.contains("wallet", ignoreCase = true) ||
                context.foregroundPackage.contains("payment", ignoreCase = true) ||
                context.foregroundPackage == "com.android.settings"
        )

        val isMatched =
            context.packageRunsAccessibilityService &&
                context.overlayLikely &&
                sensitiveForeground

        return RuleResult(
            ruleId = "overlay_accessibility_combo",
            matched = isMatched,
            riskDelta = if (isMatched) 20 else 0,
            title = "Overlay & Accessibility Conflict",
            description = if (isMatched) {
                "Package ${context.packageName} owns an enabled accessibility service and also appears to be interacting through overlay-like behavior in a sensitive foreground context."
            } else {
                "Overlay correlation thresholds were not met."
            },
            evidence = listOf(
                "package=${context.packageName}",
                "packageRunsAccessibilityService=${context.packageRunsAccessibilityService}",
                "overlayLikely=${context.overlayLikely}",
                "sensitiveForeground=$sensitiveForeground"
            )
        )
    }
}
