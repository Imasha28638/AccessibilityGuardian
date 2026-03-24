package com.sliit.isp.accessibilityguardian.util

import android.content.Context
import com.sliit.isp.accessibilityguardian.core.engine.AlertEngine
import com.sliit.isp.accessibilityguardian.core.engine.DetectionRuleEngine
import com.sliit.isp.accessibilityguardian.core.engine.RiskEngine
import com.sliit.isp.accessibilityguardian.data.local.AppDatabase
import com.sliit.isp.accessibilityguardian.data.repository.SecurityRepository

object ServiceLocator {

    @Volatile
    private var repositoryInstance: SecurityRepository? = null
    @Volatile
    private var repositoryOverride: SecurityRepository? = null
    @Volatile
    private var detectionRuleEngineInstance: DetectionRuleEngine? = null
    @Volatile
    private var detectionRuleEngineOverride: DetectionRuleEngine? = null
    @Volatile
    private var riskEngineInstance: RiskEngine? = null
    @Volatile
    private var riskEngineOverride: RiskEngine? = null

    fun repository(context: Context): SecurityRepository {
        repositoryOverride?.let { return it }
        return repositoryInstance ?: synchronized(this) {
            repositoryInstance ?: SecurityRepository(
                context.applicationContext,
                AppDatabase.getInstance(context.applicationContext)
            ).also { repositoryInstance = it }
        }
    }

    fun riskEngine(): RiskEngine {
        riskEngineOverride?.let { return it }
        return riskEngineInstance ?: synchronized(this) {
            riskEngineInstance ?: RiskEngine().also { riskEngineInstance = it }
        }
    }

    fun detectionRuleEngine(): DetectionRuleEngine {
        detectionRuleEngineOverride?.let { return it }
        return detectionRuleEngineInstance ?: synchronized(this) {
            detectionRuleEngineInstance ?: DetectionRuleEngine().also { detectionRuleEngineInstance = it }
        }
    }

    fun alertEngine(context: Context): AlertEngine {
        return AlertEngine(
            repository = repository(context),
            alertNotifier = AlertNotifier(context.applicationContext)
        )
    }

    fun setRepositoryForTests(repository: SecurityRepository?) {
        repositoryOverride = repository
    }

    fun setDetectionRuleEngineForTests(engine: DetectionRuleEngine?) {
        detectionRuleEngineOverride = engine
    }

    fun setRiskEngineForTests(engine: RiskEngine?) {
        riskEngineOverride = engine
    }

    fun resetForTests() {
        repositoryOverride = null
        detectionRuleEngineOverride = null
        riskEngineOverride = null
    }
}
