package com.sliit.isp.accessibilityguardian.core.rules

import com.sliit.isp.accessibilityguardian.core.model.DetectionContext
import com.sliit.isp.accessibilityguardian.core.rules.impl.NewAccessibilityServiceRule
import com.sliit.isp.accessibilityguardian.core.rules.impl.OtpCorrelationRule
import com.sliit.isp.accessibilityguardian.core.rules.impl.OverlayAccessibilityComboRule
import com.sliit.isp.accessibilityguardian.core.rules.impl.RapidUiAutomationRule
import com.sliit.isp.accessibilityguardian.core.rules.impl.RecentInstallAccessibilityRule
import com.sliit.isp.accessibilityguardian.data.local.entities.AppProfileEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleImplementationsTest {

    @Test
    fun `new accessibility rule only matches newly enabled untrusted non-system apps`() = runBlocking {
        val result = NewAccessibilityServiceRule().evaluate(
            event = eventRecord("com.test.tool"),
            context = context(accessibilityJustEnabled = true)
        )

        assertTrue(result.matched)
        assertTrue(result.riskDelta > 0)
    }

    @Test
    fun `recent install rule uses package install age`() = runBlocking {
        val result = RecentInstallAccessibilityRule().evaluate(
            event = eventRecord("com.test.tool"),
            context = context()
        )

        assertTrue(result.matched)
    }

    @Test
    fun `rapid UI automation rule matches burst activity`() = runBlocking {
        val result = RapidUiAutomationRule().evaluate(
            event = eventRecord("com.test.tool"),
            context = context(recentEvents = List(5) { eventRecord("com.test.tool", timestamp = 10L + it) })
        )

        assertTrue(result.matched)
    }

    @Test
    fun `otp correlation rule requires otp window and sensitive foreground`() = runBlocking {
        val matched = OtpCorrelationRule().evaluate(
            event = eventRecord("com.test.tool"),
            context = context(otpWindowActive = true, foregroundPackage = "com.test.bankapp")
        )
        val unmatched = OtpCorrelationRule().evaluate(
            event = eventRecord("com.test.tool"),
            context = context(otpWindowActive = true, foregroundPackage = "com.test.chat")
        )

        assertTrue(matched.matched)
        assertFalse(unmatched.matched)
    }

    @Test
    fun `overlay accessibility combo rule requires overlay sensitive foreground and elevated current risk`() = runBlocking {
        val result = OverlayAccessibilityComboRule().evaluate(
            event = eventRecord("com.test.tool"),
            context = context(
                overlayLikely = true,
                foregroundPackage = "com.test.wallet",
                currentRiskScore = 55
            )
        )

        assertTrue(result.matched)
    }

    private fun context(
        recentEvents: List<EventRecordEntity> = emptyList(),
        foregroundPackage: String = "com.test.bankapp",
        otpWindowActive: Boolean = false,
        accessibilityJustEnabled: Boolean = false,
        overlayLikely: Boolean = false,
        currentRiskScore: Int = 45
    ) = DetectionContext(
        packageName = "com.test.tool",
        appProfile = AppProfileEntity(
            packageName = "com.test.tool",
            appLabel = "Test Tool",
            installerPackage = "com.android.vending",
            firstInstallTime = System.currentTimeMillis() - 1_000L,
            lastUpdateTime = System.currentTimeMillis(),
            isSystemApp = false,
            isLauncherVisible = true,
            isTrusted = false,
            currentRiskScore = currentRiskScore
        ),
        foregroundPackage = foregroundPackage,
        recentEvents = recentEvents,
        otpWindowActive = otpWindowActive,
        accessibilityJustEnabled = accessibilityJustEnabled,
        overlayLikely = overlayLikely
    )

    private fun eventRecord(packageName: String, timestamp: Long = System.currentTimeMillis()) = EventRecordEntity(
        timestamp = timestamp,
        sourcePackage = packageName,
        sourceClass = "android.widget.Button",
        eventType = 1,
        eventText = "Allow",
        foregroundPackage = "com.test.bankapp",
        isSensitiveContext = true
    )
}
