package com.sliit.isp.accessibilityguardian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "security_alerts")
data class SecurityAlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val packageName: String?,
    val severity: String,
    val score: Int,
    val title: String,
    val description: String,
    val evidenceText: String,
    val status: String = "OPEN"
)
