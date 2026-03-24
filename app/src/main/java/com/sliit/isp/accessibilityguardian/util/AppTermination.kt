package com.sliit.isp.accessibilityguardian.util

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.Process

object AppTermination {

    fun close(activity: Activity) {
        activity.finishAffinity()
        activity.finishAndRemoveTask()
        activity.moveTaskToBack(true)
        activity.finish()
        Handler(Looper.getMainLooper()).postDelayed(
            { Process.killProcess(Process.myPid()) },
            250L
        )
    }
}
