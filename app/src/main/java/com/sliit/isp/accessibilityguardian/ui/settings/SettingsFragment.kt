package com.sliit.isp.accessibilityguardian.ui.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.sliit.isp.accessibilityguardian.R
import com.sliit.isp.accessibilityguardian.util.AlertNotifier
import com.sliit.isp.accessibilityguardian.util.PermissionUtils
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    private lateinit var tvSensitivityValue: TextView
    private lateinit var tvServiceStatus: TextView
    private lateinit var seekSensitivity: SeekBar

    private lateinit var switchMonitoring: SwitchMaterial
    private lateinit var switchRealtimeAlerts: SwitchMaterial
    private lateinit var switchThreatNotifications: SwitchMaterial

    private lateinit var btnAccessibilitySettings: MaterialButton
    private lateinit var btnPrivacyPolicy: MaterialButton
    private lateinit var btnLicenses: MaterialButton
    private lateinit var btnSupport: MaterialButton
    private lateinit var tvDeveloperLabel: TextView
    private lateinit var tvDeveloperValue: TextView
    private var monitoringSwitchListener: CompoundButton.OnCheckedChangeListener? = null
    private var realtimeAlertsSwitchListener: CompoundButton.OnCheckedChangeListener? = null
    private var threatNotificationsSwitchListener: CompoundButton.OnCheckedChangeListener? = null
    private var sensitivityChangeListener: SeekBar.OnSeekBarChangeListener? = null

    private val postNotificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            settingsViewModel.setThreatNotificationsEnabled(true)
            toast("Threat notifications enabled")
        } else {
            settingsViewModel.refreshThreatNotificationCapability()
            toast("Notification permission is required for threat alerts")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                systemBars.top,
                v.paddingRight,
                systemBars.bottom
            )
            insets
        }

        bindViews(view)
        setupButtons()
        setupDebugHarnessEntry()
        setupListeners()
        observeUiState()
    }

    private fun bindViews(view: View) {
        tvSensitivityValue = view.findViewById(R.id.tvSensitivityValue)
        seekSensitivity = view.findViewById(R.id.seekSensitivity)

        switchMonitoring = view.findViewById(R.id.switchMonitoring)
        switchRealtimeAlerts = view.findViewById(R.id.switchRealtimeAlerts)
        switchThreatNotifications = view.findViewById(R.id.switchThreatNotifications)
        tvServiceStatus = view.findViewById(R.id.tvServiceStatus)

        btnAccessibilitySettings = view.findViewById(R.id.btnAccessibilitySettings)
        btnPrivacyPolicy = view.findViewById(R.id.btnPrivacyPolicy)
        btnLicenses = view.findViewById(R.id.btnLicenses)
        btnSupport = view.findViewById(R.id.btnSupport)
        tvDeveloperLabel = view.findViewById(R.id.tvDeveloperLabel)
        tvDeveloperValue = view.findViewById(R.id.tvDeveloperValue)
    }

    private fun setupListeners() {
        monitoringSwitchListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) {
                return@OnCheckedChangeListener
            }
            settingsViewModel.setMonitoringEnabled(isChecked)
            toast(if (isChecked) "Monitoring service enabled" else "Monitoring service disabled")
        }
        switchMonitoring.setOnCheckedChangeListener(monitoringSwitchListener)

        realtimeAlertsSwitchListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) {
                return@OnCheckedChangeListener
            }
            settingsViewModel.setRealtimeAlertsEnabled(isChecked)
            toast(if (isChecked) "Real-time alerts enabled" else "Real-time alerts disabled")
        }
        switchRealtimeAlerts.setOnCheckedChangeListener(realtimeAlertsSwitchListener)

        threatNotificationsSwitchListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) {
                return@OnCheckedChangeListener
            }
            if (isChecked) {
                enableThreatNotifications()
            } else {
                settingsViewModel.setThreatNotificationsEnabled(false)
                toast("Threat notifications disabled")
            }
        }
        switchThreatNotifications.setOnCheckedChangeListener(threatNotificationsSwitchListener)

        sensitivityChangeListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateSensitivityLabel(progress)
                if (fromUser) {
                    settingsViewModel.setRiskSensitivity(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        seekSensitivity.setOnSeekBarChangeListener(sensitivityChangeListener)
    }

    private fun updateSensitivityLabel(value: Int) {
        val level = when {
            value <= 33 -> "Low"
            value <= 66 -> "Medium"
            else -> "High"
        }
        tvSensitivityValue.text = "$level - $value%"
    }

    private fun enableThreatNotifications() {
        val context = requireContext()
        val state = settingsViewModel.uiState.value

        if (!state.notificationCapability.permissionGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                postNotificationsPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                settingsViewModel.refreshThreatNotificationCapability()
            }
            return
        }

        if (!state.notificationCapability.appNotificationsEnabled) {
            PermissionUtils.openAppNotificationSettings(context)
            settingsViewModel.refreshThreatNotificationCapability()
            toast("Enable app notifications to receive threat alerts")
            return
        }

        if (!state.notificationCapability.channelEnabled) {
            PermissionUtils.openNotificationChannelSettings(context, AlertNotifier.CHANNEL_ID)
            settingsViewModel.refreshThreatNotificationCapability()
            toast("Enable the threat alerts channel to receive notifications")
            return
        }

        settingsViewModel.setThreatNotificationsEnabled(true)
        toast("Threat notifications enabled")
    }

    override fun onResume() {
        super.onResume()
        settingsViewModel.refreshSettingsState()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: SettingsUiState) {
        switchMonitoring.setOnCheckedChangeListener(null)
        switchRealtimeAlerts.setOnCheckedChangeListener(null)
        switchThreatNotifications.setOnCheckedChangeListener(null)
        seekSensitivity.setOnSeekBarChangeListener(null)

        switchMonitoring.isChecked = state.monitoringEnabled
        switchRealtimeAlerts.isChecked = state.realtimeAlertsEnabled
        switchThreatNotifications.isChecked = state.threatNotificationsEnabled
        if (seekSensitivity.progress != state.riskSensitivity) {
            seekSensitivity.progress = state.riskSensitivity
        }
        updateSensitivityLabel(state.riskSensitivity)
        updateServiceStatus(state.serviceActive)

        switchMonitoring.setOnCheckedChangeListener(monitoringSwitchListener)
        switchRealtimeAlerts.setOnCheckedChangeListener(realtimeAlertsSwitchListener)
        switchThreatNotifications.setOnCheckedChangeListener(threatNotificationsSwitchListener)
        seekSensitivity.setOnSeekBarChangeListener(sensitivityChangeListener)
    }

    private fun setupButtons() {
        btnAccessibilitySettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                toast("Unable to open Accessibility Settings")
            }
        }

        btnPrivacyPolicy.setOnClickListener {
            toast("Open Privacy Policy (connect your screen/activity here)")
        }

        btnLicenses.setOnClickListener {
            toast("Open Licenses (connect your screen/activity here)")
        }

        btnSupport.setOnClickListener {
            toast("Open Support (connect your screen/activity here)")
        }
    }

    private fun setupDebugHarnessEntry() {
        val debugEntryListener = View.OnLongClickListener {
            launchDebugHarnessIfAvailable()
        }
        tvDeveloperLabel.setOnLongClickListener(debugEntryListener)
        tvDeveloperValue.setOnLongClickListener(debugEntryListener)
    }

    private fun launchDebugHarnessIfAvailable(): Boolean {
        val context = context ?: return false
        return try {
            val activityClass = Class.forName(
                "com.sliit.isp.accessibilityguardian.debug.DetectionTestHarnessActivity"
            )
            startActivity(Intent(context, activityClass))
            toast("Opening Detection Test Harness")
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun updateServiceStatus(serviceActive: Boolean) {
        val context = requireContext()
        if (serviceActive) {
            tvServiceStatus.text = "Active"
            tvServiceStatus.setBackgroundResource(R.drawable.bg_pill_green)
            tvServiceStatus.setTextColor(resources.getColor(R.color.pill_green_text, context.theme))
        } else {
            tvServiceStatus.text = "Inactive"
            tvServiceStatus.setBackgroundResource(R.drawable.bg_pill_orange)
            tvServiceStatus.setTextColor(resources.getColor(R.color.pill_orange_text, context.theme))
        }
    }
}
