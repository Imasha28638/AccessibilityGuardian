package com.sliit.isp.accessibilityguardian.core.engine

import com.sliit.isp.accessibilityguardian.core.model.RuleResult
import com.sliit.isp.accessibilityguardian.core.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskEngineTest {

    private val engine = RiskEngine()

    @Test
    fun `calculates severity bands consistently`() {
        val low = engine.calculate(0, emptyList(), trustAdjustment = 0)
        val medium = engine.calculate(30, emptyList(), trustAdjustment = 0)
        val high = engine.calculate(60, emptyList(), trustAdjustment = 0)
        val critical = engine.calculate(80, emptyList(), trustAdjustment = 0)

        assertEquals(Severity.LOW, low.severity)
        assertEquals(Severity.MEDIUM, medium.severity)
        assertEquals(Severity.HIGH, high.severity)
        assertEquals(Severity.CRITICAL, critical.severity)
    }

    @Test
    fun `higher sensitivity produces higher risk from the same triggered rules`() {
        val rules = listOf(
            RuleResult(
                ruleId = "rapid_ui_automation",
                matched = true,
                riskDelta = 20,
                title = "Rapid UI Automation",
                description = "Burst interaction"
            )
        )

        val lowSensitivity = engine.calculate(10, rules, trustAdjustment = 0, sensitivityPercent = 10)
        val highSensitivity = engine.calculate(10, rules, trustAdjustment = 0, sensitivityPercent = 90)

        assertTrue(highSensitivity.score > lowSensitivity.score)
    }
}
