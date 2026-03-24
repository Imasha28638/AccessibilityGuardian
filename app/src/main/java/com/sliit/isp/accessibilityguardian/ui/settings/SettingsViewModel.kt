package com.sliit.isp.accessibilityguardian.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sliit.isp.accessibilityguardian.data.repository.SecurityRepository
import com.sliit.isp.accessibilityguardian.util.AlertNotifier
import com.sliit.isp.accessibilityguardian.util.PermissionUtils
import com.sliit.isp.accessibilityguardian.util.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsNotificationCapability(
    val permissionGranted: Boolean = true,
    val appNotificationsEnabled: Boolean = true,
    val channelEnabled: Boolean = true
) {
    val canPostThreatNotifications: Boolean
        get() = permissionGranted && appNotificationsEnabled && channelEnabled
}

data class SettingsUiState(
    val monitoringEnabled: Boolean = true,
    val serviceActive: Boolean = false,
    val realtimeAlertsEnabled: Boolean = true,
    val threatNotificationsEnabled: Boolean = false,
    val riskSensitivity: Int = 65,
    val notificationCapability: SettingsNotificationCapability = SettingsNotificationCapability()
)

class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: SecurityRepository =
        ServiceLocator.repository(application.applicationContext)
    private val accessibilityEnabled = MutableStateFlow(false)
    private val notificationCapability = MutableStateFlow(SettingsNotificationCapability())

    private val baseSettingsState = combine(
        repository.observeMonitoringEnabled(),
        repository.observeRealtimeAlertsEnabled(),
        repository.observeThreatNotificationsEnabled(),
        repository.observeRiskSensitivity(),
        accessibilityEnabled
    ) { monitoringEnabled, realtimeAlertsEnabled, threatNotificationsEnabled, riskSensitivity, isAccessibilityEnabled ->
        SettingsUiState(
            monitoringEnabled = monitoringEnabled,
            serviceActive = monitoringEnabled && isAccessibilityEnabled,
            realtimeAlertsEnabled = realtimeAlertsEnabled,
            threatNotificationsEnabled = threatNotificationsEnabled,
            riskSensitivity = riskSensitivity
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        baseSettingsState,
        notificationCapability
    ) { baseState, notifications ->
        baseState.copy(
            threatNotificationsEnabled = baseState.threatNotificationsEnabled && notifications.canPostThreatNotifications,
            notificationCapability = notifications
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState(
            monitoringEnabled = repository.getMonitoringEnabled(),
            serviceActive = repository.getMonitoringEnabled() && accessibilityEnabled.value,
            realtimeAlertsEnabled = repository.getRealtimeAlertsEnabled(),
            threatNotificationsEnabled = repository.getThreatNotificationsEnabled(),
            riskSensitivity = repository.getRiskSensitivity(),
            notificationCapability = notificationCapability.value
        )
    )

    init {
        refreshSettingsState()
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        repository.setMonitoringEnabled(enabled)
    }

    fun setRealtimeAlertsEnabled(enabled: Boolean) {
        repository.setRealtimeAlertsEnabled(enabled)
    }

    fun setRiskSensitivity(value: Int) {
        repository.setRiskSensitivity(value)
    }

    fun setThreatNotificationsEnabled(enabled: Boolean) {
        repository.setThreatNotificationsEnabled(enabled)
        refreshThreatNotificationCapability()
    }

    fun refreshSettingsState() {
        repository.refreshMonitoringEnabled()
        repository.refreshRealtimeAlertsEnabled()
        repository.refreshThreatNotificationsEnabled()
        repository.refreshRiskSensitivity()
        refreshServiceState()
        refreshThreatNotificationCapability()
    }

    fun refreshServiceState() {
        val context = getApplication<Application>().applicationContext
        accessibilityEnabled.value = PermissionUtils.isGuardianAccessibilityEnabled(context)
    }

    fun refreshThreatNotificationCapability() {
        val context = getApplication<Application>().applicationContext
        val capability = SettingsNotificationCapability(
            permissionGranted = PermissionUtils.hasPostNotificationsPermission(context),
            appNotificationsEnabled = PermissionUtils.areAppNotificationsEnabled(context),
            channelEnabled = PermissionUtils.isNotificationChannelEnabled(
                context,
                AlertNotifier.CHANNEL_ID
            )
        )
        notificationCapability.value = capability

        if (!capability.canPostThreatNotifications && repository.getThreatNotificationsEnabled()) {
            repository.setThreatNotificationsEnabled(false)
        }
    }
}
