package com.sliit.isp.accessibilityguardian.ui

data class SuspiciousApp(
    val appName: String,
    val packageName: String,
    val riskScore: Int,
    val riskLevel: String,
    val reason: String
)
