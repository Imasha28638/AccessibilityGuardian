package com.sliit.isp.accessibilityguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventRecordDao {

    @Insert
    suspend fun insert(event: EventRecordEntity)

    @Update
    suspend fun update(event: EventRecordEntity)

    @Query("SELECT * FROM event_records ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<EventRecordEntity>

    @Query("SELECT * FROM event_records ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EventRecordEntity>>

    @Query(
        """
        SELECT * FROM event_records
        WHERE sourcePackage = :packageName
        ORDER BY timestamp DESC LIMIT :limit
    """
    )
    suspend fun recentForPackage(packageName: String, limit: Int): List<EventRecordEntity>

    @Query(
        """
        SELECT * FROM event_records
        WHERE sourcePackage = :packageName
        ORDER BY timestamp DESC LIMIT :limit
    """
    )
    fun observeRecentForPackage(packageName: String, limit: Int): Flow<List<EventRecordEntity>>

    @Query(
        """
        SELECT * FROM event_records
        WHERE sourcePackage = :packageName
          AND timestamp >= :since
        ORDER BY timestamp DESC
    """
    )
    fun observeForPackageSince(packageName: String, since: Long): Flow<List<EventRecordEntity>>

    @Query(
        """
        SELECT * FROM event_records
        WHERE sourcePackage = :packageName
          AND timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp DESC
    """
    )
    suspend fun recentForPackageBetween(
        packageName: String,
        startTime: Long,
        endTime: Long
    ): List<EventRecordEntity>

    @Query("DELETE FROM event_records")
    suspend fun clearAll()
}
