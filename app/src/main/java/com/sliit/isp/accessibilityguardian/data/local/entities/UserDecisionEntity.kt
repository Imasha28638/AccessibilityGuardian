package com.sliit.isp.accessibilityguardian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_decisions")
data class UserDecisionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val decision: String,
    val timestamp: Long = System.currentTimeMillis()
)
