package com.sliit.isp.accessibilityguardian.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sliit.isp.accessibilityguardian.data.local.dao.AppProfileDao
import com.sliit.isp.accessibilityguardian.data.local.dao.EventRecordDao
import com.sliit.isp.accessibilityguardian.data.local.dao.OtpWindowDao
import com.sliit.isp.accessibilityguardian.data.local.dao.RiskSnapshotDao
import com.sliit.isp.accessibilityguardian.data.local.dao.SecurityAlertDao
import com.sliit.isp.accessibilityguardian.data.local.dao.UserDecisionDao
import com.sliit.isp.accessibilityguardian.data.local.entities.AppProfileEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.EventRecordEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.OtpWindowEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.RiskSnapshotEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.SecurityAlertEntity
import com.sliit.isp.accessibilityguardian.data.local.entities.UserDecisionEntity

@Database(
    entities = [
        AppProfileEntity::class,
        EventRecordEntity::class,
        SecurityAlertEntity::class,
        RiskSnapshotEntity::class,
        UserDecisionEntity::class,
        OtpWindowEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appProfileDao(): AppProfileDao
    abstract fun eventRecordDao(): EventRecordDao
    abstract fun securityAlertDao(): SecurityAlertDao
    abstract fun riskSnapshotDao(): RiskSnapshotDao
    abstract fun userDecisionDao(): UserDecisionDao
    abstract fun otpWindowDao(): OtpWindowDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "guardian_db"
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
