package com.sliit.isp.accessibilityguardian.ui

data class SecurityLog(
    val logId: Long = 0L,
    val title: String = "",
    val description: String = "",
    val time: String = "",
    val severity: String,
    val appName: String = title,
    val packageName: String = "",
    val message: String = description.ifBlank { title },
    val timestampLabel: String = time,
    val timestampMillis: Long = 0L,
    val eventCount: Int? = null
)
