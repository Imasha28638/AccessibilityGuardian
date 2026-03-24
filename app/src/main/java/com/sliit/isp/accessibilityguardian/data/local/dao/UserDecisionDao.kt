package com.sliit.isp.accessibilityguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sliit.isp.accessibilityguardian.data.local.entities.UserDecisionEntity

@Dao
interface UserDecisionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(decision: UserDecisionEntity)

    @Query(
        """
        SELECT * FROM user_decisions
        WHERE packageName = :packageName
        ORDER BY timestamp DESC LIMIT 1
    """
    )
    suspend fun getDecisionForPackage(packageName: String): UserDecisionEntity?

    @Query("""
        SELECT * FROM user_decisions
        WHERE packageName = :packageName
        ORDER BY timestamp DESC LIMIT 1
    """)
    suspend fun latestForPackage(packageName: String): UserDecisionEntity?
}
