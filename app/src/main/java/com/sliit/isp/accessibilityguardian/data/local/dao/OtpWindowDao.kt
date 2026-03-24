package com.sliit.isp.accessibilityguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sliit.isp.accessibilityguardian.data.local.entities.OtpWindowEntity

@Dao
interface OtpWindowDao {

    @Insert
    suspend fun insert(window: OtpWindowEntity)

    @Query("""
        SELECT * FROM otp_windows
        WHERE expiresAt > :currentTime
        ORDER BY expiresAt DESC LIMIT 1
    """)
    suspend fun getActive(currentTime: Long): OtpWindowEntity?

    @Query("DELETE FROM otp_windows WHERE expiresAt <= :currentTime")
    suspend fun clearExpired(currentTime: Long)

    @Query("DELETE FROM otp_windows")
    suspend fun clearAll()
}
