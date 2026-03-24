package com.sliit.isp.accessibilityguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sliit.isp.accessibilityguardian.data.local.entities.SecurityAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityAlertDao {

    @Insert
    suspend fun insert(alert: SecurityAlertEntity): Long

    @Query("SELECT * FROM security_alerts ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SecurityAlertEntity>

    @Query("SELECT * FROM security_alerts ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SecurityAlertEntity>>

    @Query("SELECT * FROM security_alerts WHERE id = :alertId LIMIT 1")
    suspend fun getById(alertId: Long): SecurityAlertEntity?

    @Query("SELECT * FROM security_alerts WHERE packageName IS NOT NULL ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<SecurityAlertEntity?>

    @Query(
        """
        SELECT * FROM security_alerts
        WHERE packageName IS NOT NULL
          AND status = 'OPEN'
        ORDER BY timestamp DESC LIMIT 1
    """
    )
    fun observeLatestOpen(): Flow<SecurityAlertEntity?>

    @Query(
        """
        SELECT * FROM security_alerts
        WHERE packageName = :packageName
        ORDER BY timestamp DESC LIMIT :limit
    """
    )
    suspend fun recentForPackage(packageName: String, limit: Int): List<SecurityAlertEntity>

    @Query(
        """
        SELECT * FROM security_alerts
        WHERE packageName = :packageName
        ORDER BY timestamp DESC LIMIT :limit
    """
    )
    fun observeForPackage(packageName: String, limit: Int): Flow<List<SecurityAlertEntity>>

    @Query(
        """
        SELECT * FROM security_alerts
        WHERE packageName = :packageName
          AND timestamp <= :timestamp
        ORDER BY timestamp DESC LIMIT :limit
    """
    )
    suspend fun recentForPackageUpTo(
        packageName: String,
        timestamp: Long,
        limit: Int
    ): List<SecurityAlertEntity>

    @Query(
        """
        SELECT * FROM security_alerts
        WHERE packageName = :packageName
        ORDER BY timestamp DESC LIMIT 1
    """
    )
    suspend fun latestForPackage(packageName: String): SecurityAlertEntity?

    @Query(
        """
        SELECT * FROM security_alerts
        WHERE packageName = :packageName
          AND status = 'OPEN'
        ORDER BY timestamp DESC LIMIT 1
    """
    )
    suspend fun latestOpenForPackage(packageName: String): SecurityAlertEntity?

    @Query(
        """
        SELECT * FROM security_alerts
        WHERE packageName = :packageName
        ORDER BY timestamp DESC LIMIT 1
    """
    )
    fun observeLatestForPackage(packageName: String): Flow<SecurityAlertEntity?>

    @Query(
        """
        SELECT * FROM security_alerts
        WHERE packageName = :packageName
          AND status = 'OPEN'
        ORDER BY timestamp DESC LIMIT 1
    """
    )
    fun observeLatestOpenForPackage(packageName: String): Flow<SecurityAlertEntity?>

    @Query("UPDATE security_alerts SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM security_alerts")
    suspend fun clearAll()
}
