package com.sliit.isp.accessibilityguardian.ui

import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sliit.isp.accessibilityguardian.R
import com.sliit.isp.accessibilityguardian.SuspiciousAppAdapter
import kotlinx.coroutines.launch

class MonitorFragment : Fragment(R.layout.fragment_monitor) {

    private val viewModel: MonitorViewModel by viewModels()
    private val monitoringStateViewModel: MonitoringStateViewModel by activityViewModels()

    private lateinit var suspiciousAppsRecycler: RecyclerView
    private lateinit var swMonitoring: SwitchCompat
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvRiskSub: TextView
    private lateinit var viewServiceDot: View
    private lateinit var tvSuspCount: TextView
    private lateinit var cardScanned: View
    private lateinit var cardThreats: View
    private lateinit var cardBlocked: View
    private lateinit var riskGauge: SemiGaugeView
    private lateinit var btnRefreshRisk: ImageButton
    private lateinit var btnAlerts: View
    private lateinit var chipAll: TextView
    private lateinit var chipCritical: TextView
    private lateinit var chipHigh: TextView
    private lateinit var chipMedium: TextView
    private lateinit var chipLow: TextView

    private lateinit var suspiciousAppAdapter: SuspiciousAppAdapter
    private var monitoringSwitchListener: CompoundButton.OnCheckedChangeListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val initialTopPadding = view.paddingTop
        val initialBottomPadding = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            target.setPadding(
                target.paddingLeft,
                initialTopPadding + systemBars.top,
                target.paddingRight,
                initialBottomPadding + systemBars.bottom
            )
            insets
        }

        suspiciousAppsRecycler = view.findViewById(R.id.recyclerApps)
        swMonitoring = view.findViewById(R.id.swMonitoring)
        tvServiceStatus = view.findViewById(R.id.tvServiceStatus)
        tvRiskSub = view.findViewById(R.id.tvRiskSub)
        viewServiceDot = view.findViewById(R.id.viewServiceDot)
        tvSuspCount = view.findViewById(R.id.tvSuspCount)
        cardScanned = view.findViewById(R.id.cardScanned)
        cardThreats = view.findViewById(R.id.cardThreats)
        cardBlocked = view.findViewById(R.id.cardBlocked)
        riskGauge = view.findViewById(R.id.gauge)
        btnRefreshRisk = view.findViewById(R.id.btnRefreshRisk)
        btnAlerts = view.findViewById(R.id.btnAlerts)
        chipAll = view.findViewById(R.id.chipAll)
        chipCritical = view.findViewById(R.id.chipCritical)
        chipHigh = view.findViewById(R.id.chipHigh)
        chipMedium = view.findViewById(R.id.chipMedium)
        chipLow = view.findViewById(R.id.chipLow)

        suspiciousAppAdapter = SuspiciousAppAdapter(emptyList()) { app ->
            val bundle = Bundle().apply {
                putString("package_name", app.packageName)
            }
            parentFragmentManager.setFragmentResult(
                "inspect_request",
                bundle
            )
            if (findNavController().currentDestination?.id != R.id.navigation_inspect) {
                findNavController().navigate(
                    R.id.navigation_inspect,
                    bundle,
                    navOptions {
                        launchSingleTop = true
                        restoreState = true
                    }
                )
            }
        }

        suspiciousAppsRecycler.layoutManager = LinearLayoutManager(requireContext())
        suspiciousAppsRecycler.adapter = suspiciousAppAdapter

        bindStatCard(cardScanned, R.string.stat_scanned, 0, R.drawable.ic_target_small)
        bindStatCard(cardThreats, R.string.stat_threats, 0, R.drawable.ic_alert_small)
        bindStatCard(cardBlocked, R.string.stat_blocked, 0, R.drawable.ic_block_small)

        setupActions()
        collectUi()
    }

    override fun onResume() {
        super.onResume()
        monitoringStateViewModel.refreshMonitoringState()
        monitoringStateViewModel.refreshServiceState()
        viewModel.refreshPermissionState()
    }

    private fun setupActions() {
        monitoringSwitchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            monitoringStateViewModel.setMonitoringEnabled(isChecked)
        }
        swMonitoring.setOnCheckedChangeListener(monitoringSwitchListener)

        btnRefreshRisk.setOnClickListener { refreshButton ->
            if (!refreshButton.isEnabled) {
                return@setOnClickListener
            }

            refreshButton.isEnabled = false
            refreshButton.animate().rotationBy(360f).setDuration(400).start()
            viewModel.refreshRiskScore()
        }

        btnAlerts.setOnClickListener {
            if (parentFragmentManager.findFragmentByTag(RecentAlertsDialogFragment.TAG) == null) {
                RecentAlertsDialogFragment().show(
                    parentFragmentManager,
                    RecentAlertsDialogFragment.TAG
                )
            }
        }

        chipAll.setOnClickListener { viewModel.setSelectedFilter(MonitorSeverityFilter.ALL) }
        chipCritical.setOnClickListener { viewModel.setSelectedFilter(MonitorSeverityFilter.CRITICAL) }
        chipHigh.setOnClickListener { viewModel.setSelectedFilter(MonitorSeverityFilter.HIGH) }
        chipMedium.setOnClickListener { viewModel.setSelectedFilter(MonitorSeverityFilter.MEDIUM) }
        chipLow.setOnClickListener { viewModel.setSelectedFilter(MonitorSeverityFilter.LOW) }
    }

    private fun collectUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    monitoringStateViewModel.uiState.collect { state ->
                        swMonitoring.setOnCheckedChangeListener(null)
                        swMonitoring.isChecked = state.monitoringEnabled
                        swMonitoring.setOnCheckedChangeListener(monitoringSwitchListener)
                        updateMonitoringState(state.serviceActive)
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        riskGauge.value = state.overallRiskScore.coerceIn(0, 100)
                        tvRiskSub.text = state.overallRiskSubtitle
                        btnRefreshRisk.isEnabled = !state.isRefreshingRisk

                        bindStatCard(
                            root = cardScanned,
                            labelRes = R.string.stat_scanned,
                            value = state.scannedCount,
                            iconRes = R.drawable.ic_target_small
                        )
                        bindStatCard(
                            root = cardThreats,
                            labelRes = R.string.stat_threats,
                            value = state.alertCount,
                            iconRes = R.drawable.ic_alert_small
                        )
                        bindStatCard(
                            root = cardBlocked,
                            labelRes = R.string.stat_blocked,
                            value = state.blockedCount,
                            iconRes = R.drawable.ic_block_small
                        )

                        tvSuspCount.text = "${state.suspiciousApps.size} detected"
                        bindFilterState(state.selectedFilter)
                        suspiciousAppAdapter.updateItems(state.suspiciousApps)
                    }
                }
            }
        }
    }

    private fun updateMonitoringState(isEnabled: Boolean) {
        if (isEnabled) {
            tvServiceStatus.text = getString(R.string.service_active)
            tvServiceStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_green))
            viewServiceDot.setBackgroundResource(R.drawable.bg_status_green)
            viewServiceDot.backgroundTintList = null
        } else {
            tvServiceStatus.text = getString(R.string.service_inactive)
            tvServiceStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_red))
            viewServiceDot.setBackgroundResource(R.drawable.bg_status_red)
            viewServiceDot.backgroundTintList = null
        }
    }

    private fun bindStatCard(root: View, labelRes: Int, value: Int, iconRes: Int) {
        root.findViewById<TextView>(R.id.tvStatLabel).setText(labelRes)
        root.findViewById<TextView>(R.id.tvStatValue).text = value.toString()
        val iconView = root.findViewById<ImageView>(R.id.ivStatBadge)
        iconView.setImageResource(iconRes)
        ImageViewCompat.setImageTintList(iconView, null)
    }

    private fun bindFilterState(selectedFilter: MonitorSeverityFilter) {
        updateChipSelection(chipAll, selectedFilter == MonitorSeverityFilter.ALL)
        updateChipSelection(chipCritical, selectedFilter == MonitorSeverityFilter.CRITICAL)
        updateChipSelection(chipHigh, selectedFilter == MonitorSeverityFilter.HIGH)
        updateChipSelection(chipMedium, selectedFilter == MonitorSeverityFilter.MEDIUM)
        updateChipSelection(chipLow, selectedFilter == MonitorSeverityFilter.LOW)
    }

    private fun updateChipSelection(chip: TextView, isSelected: Boolean) {
        chip.setBackgroundResource(
            if (isSelected) R.drawable.bg_chip_selected else R.drawable.bg_card_soft
        )
        chip.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isSelected) R.color.text_white else R.color.text_secondary_gray
            )
        )
        chip.isSelected = isSelected
    }
}
