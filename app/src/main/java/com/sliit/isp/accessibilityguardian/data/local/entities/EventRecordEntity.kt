package com.sliit.isp.accessibilityguardian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_records")
data class EventRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val sourcePackage: String?,
    val sourceClass: String?,
    val eventType: Int,
    val eventText: String?,
    val foregroundPackage: String?,
    val isSensitiveContext: Boolean,
    val riskDelta: Int = 0
)
