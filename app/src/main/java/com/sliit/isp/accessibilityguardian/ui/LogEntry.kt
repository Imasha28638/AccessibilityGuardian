package com.sliit.isp.accessibilityguardian.ui

data class LogEntry(
    val title: String,
    val description: String,
    val timestamp: String,
    val severity: String,
    val packageName: String
)
