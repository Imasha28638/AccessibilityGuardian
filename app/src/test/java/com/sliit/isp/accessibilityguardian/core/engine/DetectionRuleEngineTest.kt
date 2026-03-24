package com.sliit.isp.accessibilityguardian.core.engine

import com.sliit.isp.accessibilityguardian.core.model.DetectionContext
import com.sliit.isp.accessibilityguardian.data.local.entities.AppProfileEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionRuleEngineTest {

    @Test
    fun `registers all concrete rules and executes matching ones`() = runBlocking {
        val engine = DetectionRuleEngine()
        assertEquals(
            listOf(
                "new_accessibility_service",
                "recent_install_accessibility",
                "rapid_ui_automation",
                "otp_correlation",
                "overlay_accessibility_combo"
            ),
            engine.registeredRuleIds()
        )

        val subjectPackage = "com.test.accessibilitytool"
        val event = baseEvent(subjectPackage)
        val context = DetectionContext(
            packageName = subjectPackage,
            appProfile = baseProfile(subjectPackage),
            foregroundPackage = "com.test.mobilebank",
            recentEvents = List(5) { baseEvent(subjectPackage, timestamp = 1_000L + it) },
            otpWindowActive = true,
            accessibilityJustEnabled = true,
            overlayLikely = true
        )

        val results = engine.evaluate(event, context)

        assertTrue(results.any { it.ruleId == "new_accessibility_service" })
        assertTrue(results.any { it.ruleId == "recent_install_accessibility" })
        assertTrue(results.any { it.ruleId == "rapid_ui_automation" })
        assertTrue(results.any { it.ruleId == "otp_correlation" })
        assertTrue(results.any { it.ruleId == "overlay_accessibility_combo" })
    }

    private fun baseProfile(packageName: String) = AppProfileEntity(
        packageName = packageName,
        appLabel = "Test App",
        installerPackage = "com.android.vending",
        firstInstallTime = System.currentTimeMillis() - 1_000L,
        lastUpdateTime = System.currentTimeMillis(),
        isSystemApp = false,
        isLauncherVisible = true,
        isTrusted = false,
        currentRiskScore = 50
    )

    private fun baseEvent(packageName: String, timestamp: Long = System.currentTimeMillis()) = EventRecordEntity(
        timestamp = timestamp,
        sourcePackage = packageName,
        sourceClass = "android.widget.Button",
        eventType = 1,
        eventText = "Allow",
        foregroundPackage = "com.test.mobilebank",
        isSensitiveContext = true
    )
}
