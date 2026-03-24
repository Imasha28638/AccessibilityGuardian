package com.sliit.isp.accessibilityguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sliit.isp.accessibilityguardian.data.local.entities.AppProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: AppProfileEntity)

    @Update
    suspend fun update(profile: AppProfileEntity)

    @Query("SELECT * FROM app_profiles WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): AppProfileEntity?

    @Query("SELECT * FROM app_profiles WHERE packageName = :packageName LIMIT 1")
    fun observeByPackage(packageName: String): Flow<AppProfileEntity?>

    @Query("SELECT * FROM app_profiles ORDER BY currentRiskScore DESC, appLabel ASC")
    fun observeAll(): Flow<List<AppProfileEntity>>

    @Query("SELECT * FROM app_profiles")
    suspend fun getAll(): List<AppProfileEntity>

    @Query("SELECT * FROM app_profiles WHERE currentRiskScore >= :minScore ORDER BY currentRiskScore DESC")
    fun observeSuspicious(minScore: Int = 30): Flow<List<AppProfileEntity>>

    @Query("SELECT * FROM app_profiles WHERE currentRiskScore >= :minScore ORDER BY lastSeenAt DESC, currentRiskScore DESC LIMIT 1")
    fun observeLatestSuspicious(minScore: Int = 30): Flow<AppProfileEntity?>

    @Query("SELECT * FROM app_profiles WHERE currentRiskScore >= :minScore ORDER BY lastSeenAt DESC, currentRiskScore DESC LIMIT 1")
    fun observeLatestPositiveRisk(minScore: Int = 1): Flow<AppProfileEntity?>

    @Query("SELECT * FROM app_profiles ORDER BY lastSeenAt DESC, currentRiskScore DESC LIMIT 1")
    fun observeLatestSeen(): Flow<AppProfileEntity?>

    @Query("UPDATE app_profiles SET isTrusted = :trusted WHERE packageName = :packageName")
    suspend fun updateTrust(packageName: String, trusted: Boolean)

    @Query("UPDATE app_profiles SET currentRiskScore = :score, lastSeenAt = :lastSeenAt WHERE packageName = :packageName")
    suspend fun updateRisk(packageName: String, score: Int, lastSeenAt: Long)

    @Query("UPDATE app_profiles SET currentRiskScore = 0")
    suspend fun resetAllRiskScores()
}
