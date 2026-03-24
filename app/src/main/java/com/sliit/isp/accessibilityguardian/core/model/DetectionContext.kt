package com.sliit.isp.accessibilityguardian.core.model

import com.sliit.isp.accessibilityguardian.data.local.entities.AppProfileEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity

data class DetectionContext(
    val packageName: String?,
    val appProfile: AppProfileEntity?,
    val foregroundPackage: String?,
    val recentEvents: List<EventRecordEntity>,
    val otpWindowActive: Boolean,
    val accessibilityGloballyEnabled: Boolean,
    val enabledAccessibilityServicePackages: Set<String>,
    val newlyEnabledAccessibilityServicePackages: Set<String>,
    val packageRunsAccessibilityService: Boolean,
    val overlayLikely: Boolean = false,
    val trustAdjustment: Int = 0,
    val packageTrustScore: Int = 0,
    val packageIsAllowlisted: Boolean = false
)
