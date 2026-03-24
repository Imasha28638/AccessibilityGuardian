package com.sliit.isp.accessibilityguardian.core.monitor

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

class AccessibilityStateMonitor(private val context: Context) {

    private val accessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    fun isAccessibilityGloballyEnabled(): Boolean {
        return accessibilityManager.isEnabled
    }

    fun getEnabledAccessibilityServicePackages(): Set<String> {
        if (!isAccessibilityGloballyEnabled()) {
            return emptySet()
        }

        return accessibilityManager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
            .toSet()
    }

    fun isPackageRunningAccessibilityService(packageName: String): Boolean {
        if (packageName.isBlank()) {
            return false
        }
        return packageName in getEnabledAccessibilityServicePackages()
    }
}
