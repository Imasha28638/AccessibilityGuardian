package com.sliit.isp.accessibilityguardian.core.engine

import com.sliit.isp.accessibilityguardian.core.model.RiskAssessment
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity
import com.sliit.isp.accessibilityguardian.data.repository.SecurityRepository
import com.sliit.isp.accessibilityguardian.util.AlertNotifier

class AlertEngine(
    private val repository: SecurityRepository,
    private val alertNotifier: AlertNotifier
) {
    suspend fun processAssessment(event: EventRecordEntity, assessment: RiskAssessment) {
        val result = repository.persistEvaluation(event, assessment)
        result.alert?.let(alertNotifier::notifyAlert)
    }
}
