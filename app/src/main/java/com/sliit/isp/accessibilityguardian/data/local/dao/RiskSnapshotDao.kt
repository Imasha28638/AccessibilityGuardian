package com.sliit.isp.accessibilityguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sliit.isp.accessibilityguardian.data.local.entities.RiskSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RiskSnapshotDao {

    @Insert
    suspend fun insert(snapshot: RiskSnapshotEntity)

    @Query(
        """
        SELECT * FROM risk_snapshots
        ORDER BY timestamp DESC LIMIT 1
    """
    )
    fun observeLatest(): Flow<RiskSnapshotEntity?>

    @Query(
        """
        SELECT * FROM risk_snapshots
        WHERE packageName = :packageName
        ORDER BY timestamp DESC LIMIT :limit
    """
    )
    suspend fun recentForPackage(packageName: String, limit: Int): List<RiskSnapshotEntity>

    @Query(
        """
        SELECT * FROM risk_snapshots
        WHERE packageName = :packageName
        ORDER BY timestamp DESC LIMIT :limit
    """
    )
    fun observeRecentForPackage(packageName: String, limit: Int): Flow<List<RiskSnapshotEntity>>

    @Query(
        """
        SELECT * FROM risk_snapshots
        WHERE packageName = :packageName
          AND timestamp >= :since
        ORDER BY timestamp ASC
    """
    )
    fun observeForPackageSince(packageName: String, since: Long): Flow<List<RiskSnapshotEntity>>

    @Query(
        """
        SELECT * FROM risk_snapshots
        WHERE packageName = :packageName
          AND timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp ASC
    """
    )
    suspend fun recentForPackageBetween(
        packageName: String,
        startTime: Long,
        endTime: Long
    ): List<RiskSnapshotEntity>

    @Query("DELETE FROM risk_snapshots")
    suspend fun clearAll()
}
