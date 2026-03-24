package com.sliit.isp.accessibilityguardian.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sliit.isp.accessibilityguardian.data.repository.SecurityRepository
import com.sliit.isp.accessibilityguardian.util.PermissionUtils
import com.sliit.isp.accessibilityguardian.util.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class MonitoringStateUi(
    val monitoringEnabled: Boolean = true,
    val serviceActive: Boolean = false
)

class MonitoringStateViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: SecurityRepository =
        ServiceLocator.repository(application.applicationContext)
    private val accessibilityEnabled = MutableStateFlow(false)
    private val _uiState = MutableStateFlow(
        MonitoringStateUi(
            monitoringEnabled = repository.getMonitoringEnabled(),
            serviceActive = false
        )
    )
    val uiState: StateFlow<MonitoringStateUi> = _uiState.asStateFlow()

    init {
        refreshMonitoringState()
        refreshServiceState()
        observeState()
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        repository.setMonitoringEnabled(enabled)
    }

    fun refreshMonitoringState() {
        repository.refreshMonitoringEnabled()
    }

    fun refreshServiceState() {
        val context = getApplication<Application>().applicationContext
        accessibilityEnabled.value = PermissionUtils.isGuardianAccessibilityEnabled(context)
    }

    private fun observeState() {
        viewModelScope.launch {
            combine(
                repository.observeMonitoringEnabled(),
                accessibilityEnabled
            ) { monitoringEnabled, isAccessibilityEnabled ->
                MonitoringStateUi(
                    monitoringEnabled = monitoringEnabled,
                    serviceActive = monitoringEnabled && isAccessibilityEnabled
                )
            }.collect { _uiState.value = it }
        }
    }
}
