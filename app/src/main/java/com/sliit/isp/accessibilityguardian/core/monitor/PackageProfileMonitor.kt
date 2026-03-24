package com.sliit.isp.accessibilityguardian.core.monitor

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.sliit.isp.accessibilityguardian.data.local.entities.AppProfileEntity

class PackageProfileMonitor(private val context: Context) {

    fun getAppProfile(packageName: String): AppProfileEntity? {
        val pm = context.packageManager
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val packageInfo = pm.getPackageInfo(packageName, 0)
            
            val appLabel = pm.getApplicationLabel(appInfo).toString()
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }

            AppProfileEntity(
                packageName = packageName,
                appLabel = appLabel,
                installerPackage = installer,
                firstInstallTime = packageInfo.firstInstallTime,
                lastUpdateTime = packageInfo.lastUpdateTime,
                isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                isLauncherVisible = pm.getLaunchIntentForPackage(packageName) != null,
                lastSeenAt = System.currentTimeMillis()
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
