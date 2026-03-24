package com.sliit.isp.accessibilityguardian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "otp_windows")
data class OtpWindowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourcePackage: String,
    val startedAt: Long,
    val expiresAt: Long
)
