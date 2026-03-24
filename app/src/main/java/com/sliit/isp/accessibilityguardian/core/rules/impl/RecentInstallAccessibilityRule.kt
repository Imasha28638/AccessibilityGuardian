package com.sliit.isp.accessibilityguardian.core.rules.impl

import com.sliit.isp.accessibilityguardian.core.model.DetectionContext
import com.sliit.isp.accessibilityguardian.core.model.RuleResult
import com.sliit.isp.accessibilityguardian.core.rules.DetectionRule
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity

class RecentInstallAccessibilityRule : DetectionRule {
    override suspend fun evaluate(event: EventRecordEntity, context: DetectionContext): RuleResult {
        val installedRecently =
            context.appProfile?.firstInstallTime?.let {
                System.currentTimeMillis() - it < 48L * 60L * 60L * 1000L
            } ?: false
        val isMatched =
            installedRecently &&
                context.packageRunsAccessibilityService &&
                context.appProfile?.isTrusted == false &&
                !context.packageIsAllowlisted

        return RuleResult(
            ruleId = "recent_install_accessibility",
            matched = isMatched,
            riskDelta = if (isMatched) 6 else 0,
            title = "Recently Installed App",
            description = if (isMatched) {
                "Package ${event.sourcePackage} was installed recently and currently owns an enabled accessibility service."
            } else {
                "Recent install status alone is not treated as suspicious."
            },
            evidence = listOf(
                "package=${event.sourcePackage.orEmpty()}",
                "installedRecently=$installedRecently",
                "packageRunsAccessibilityService=${context.packageRunsAccessibilityService}"
            )
        )
    }
}
