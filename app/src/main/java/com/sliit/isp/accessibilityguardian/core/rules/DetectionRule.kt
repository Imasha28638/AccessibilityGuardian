package com.sliit.isp.accessibilityguardian.core.rules

import com.sliit.isp.accessibilityguardian.core.model.DetectionContext
import com.sliit.isp.accessibilityguardian.core.model.RuleResult
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity

interface DetectionRule {
    suspend fun evaluate(
        event: EventRecordEntity,
        context: DetectionContext
    ): RuleResult
}
