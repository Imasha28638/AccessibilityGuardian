package com.sliit.isp.accessibilityguardian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_profiles")
data class AppProfileEntity(
    @PrimaryKey
    val packageName: String,
    val appLabel: String,
    val installerPackage: String?,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val isSystemApp: Boolean,
    val isLauncherVisible: Boolean,
    val isTrusted: Boolean = false,
    val currentRiskScore: Int = 0,
    val lastSeenAt: Long = System.currentTimeMillis()
)
