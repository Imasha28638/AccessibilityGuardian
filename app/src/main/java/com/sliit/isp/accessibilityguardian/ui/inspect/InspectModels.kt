package com.sliit.isp.accessibilityguardian.ui.inspect

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes

data class BehaviorMetricItem(
    val label: String,
    val value: String,
    @DrawableRes val iconRes: Int,
    @ColorRes val accentColorRes: Int,
    val deltaText: String? = null,
    @ColorRes val deltaColorRes: Int = accentColorRes
)

data class RecentEventItem(
    val title: String,
    val subtitle: String,
    val time: String,
    val severity: String,
    @DrawableRes val iconRes: Int,
    @ColorRes val iconTintRes: Int,
    @ColorRes val severityTextColorRes: Int,
    @DrawableRes val severityBackgroundRes: Int
)

data class TimelinePoint(
    val score: Int,
    val label: String,
    val timestamp: Long,
    val severity: String
)
