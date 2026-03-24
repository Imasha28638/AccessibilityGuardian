package com.sliit.isp.accessibilityguardian.ui.inspect

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.sliit.isp.accessibilityguardian.R
import com.sliit.isp.accessibilityguardian.ui.inspect.views.ActivityTimelineView
import com.sliit.isp.accessibilityguardian.ui.inspect.views.RiskGaugeView
import kotlinx.coroutines.launch

class InspectFragment : Fragment(R.layout.fragment_inspect) {

    companion object {
        private const val ARG_PACKAGE_NAME = "package_name"
        private const val ARG_SELECTED_LOG_ID = "selected_log_id"
        private const val REQUEST_KEY_INSPECT = "inspect_request"
    }

    private val viewModel: InspectViewModel by viewModels()

    private lateinit var contentGroup: View
    private lateinit var emptyStateGroup: View
    private lateinit var ivAppIcon: ImageView
    private lateinit var tvAppName: TextView
    private lateinit var tvPackageName: TextView
    private lateinit var tvVersion: TextView
    private lateinit var tvInstalled: TextView
    private lateinit var tvRiskLevel: TextView
    private lateinit var tvRecommendation: TextView
    private lateinit var tvPeakRisk: TextView
    private lateinit var tvAvgRisk: TextView
    private lateinit var tvSpikes: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvEventCount: TextView
    private lateinit var riskGaugeView: RiskGaugeView
    private lateinit var timelineView: ActivityTimelineView
    private lateinit var metricsRecycler: RecyclerView
    private lateinit var eventsRecycler: RecyclerView
    private lateinit var btnTrustApp: Button
    private lateinit var btnIgnoreOnce: Button
    private lateinit var btnViewLogs: MaterialButton

    private lateinit var behaviorMetricAdapter: BehaviorMetricAdapter
    private lateinit var recentEventsAdapter: RecentEventsAdapter

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

        contentGroup = view.findViewById(R.id.contentGroup)
        emptyStateGroup = view.findViewById(R.id.emptyStateGroup)
        ivAppIcon = view.findViewById(R.id.ivAppIcon)
        tvAppName = view.findViewById(R.id.tvAppName)
        tvPackageName = view.findViewById(R.id.tvPackage)
        tvVersion = view.findViewById(R.id.tvVersion)
        tvInstalled = view.findViewById(R.id.tvInstalled)
        tvRiskLevel = view.findViewById(R.id.tvWarning)
        tvRecommendation = view.findViewById(R.id.tvRiskRecommendation)
        tvPeakRisk = view.findViewById(R.id.tvPeakRisk)
        tvAvgRisk = view.findViewById(R.id.tvAvgRisk)
        tvSpikes = view.findViewById(R.id.tvSpikes)
        tvDuration = view.findViewById(R.id.tvDuration)
        tvEventCount = view.findViewById(R.id.tvEventCount)
        riskGaugeView = view.findViewById(R.id.riskGauge)
        timelineView = view.findViewById(R.id.timelineChart)
        metricsRecycler = view.findViewById(R.id.rvBehavior)
        eventsRecycler = view.findViewById(R.id.rvRecentEvents)
        btnTrustApp = view.findViewById(R.id.btnSafe)
        btnIgnoreOnce = view.findViewById(R.id.btnBlock)
        btnViewLogs = view.findViewById(R.id.btnViewLogs)

        behaviorMetricAdapter = BehaviorMetricAdapter(emptyList())
        recentEventsAdapter = RecentEventsAdapter(emptyList())

        metricsRecycler.layoutManager = GridLayoutManager(requireContext(), 2)
        metricsRecycler.adapter = behaviorMetricAdapter
        metricsRecycler.isNestedScrollingEnabled = false

        eventsRecycler.layoutManager = LinearLayoutManager(requireContext())
        eventsRecycler.adapter = recentEventsAdapter
        eventsRecycler.isNestedScrollingEnabled = false

        handleInspectRequest(arguments)

        parentFragmentManager.setFragmentResultListener(
            REQUEST_KEY_INSPECT,
            viewLifecycleOwner
        ) { _, bundle ->
            handleInspectRequest(bundle)
        }

        btnTrustApp.setOnClickListener { viewModel.onMarkAsSafe() }
        btnIgnoreOnce.setOnClickListener { viewModel.onBlockApp() }
        btnViewLogs.setOnClickListener { findNavController().navigate(R.id.navigation_logs) }

        collectUi()
    }

    private fun handleInspectRequest(bundle: Bundle?) {
        val selectedLogId = bundle?.getLong(ARG_SELECTED_LOG_ID, -1L) ?: -1L
        val packageName = bundle?.getString(ARG_PACKAGE_NAME)

        when {
            selectedLogId > 0L -> viewModel.loadInspectionForLog(selectedLogId)
            !packageName.isNullOrBlank() -> viewModel.selectPackage(packageName)
            else -> viewModel.loadLatestInspection()
        }
    }

    private fun collectUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is InspectUiEvent.Message -> {
                                Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        contentGroup.visibility = if (state.hasDetection) View.VISIBLE else View.GONE
                        emptyStateGroup.visibility = if (state.hasDetection) View.GONE else View.VISIBLE

                        if (!state.hasDetection) {
                            return@collect
                        }

                        tvAppName.text = state.appName
                        tvPackageName.text = state.packageName
                        tvVersion.text = state.versionName
                        tvInstalled.text = state.installedAgeLabel
                        tvRecommendation.text = state.recommendation
                        tvPeakRisk.text = state.peakRiskLabel
                        tvAvgRisk.text = state.avgRiskLabel
                        tvSpikes.text = state.spikesLabel
                        tvDuration.text = state.durationLabel
                        tvEventCount.text = state.recentEventsCountLabel

                        bindSeverityBadge(state.riskLevel)
                        bindAppIcon(state.packageName)

                        riskGaugeView.setScore(state.riskScore.toFloat())
                        riskGaugeView.setRiskLabel(state.riskLevel)
                        timelineView.setData(
                            state.timelineItems.map { it.score.toFloat() },
                            state.timelineItems.map { it.label },
                            state.timelineThreshold.toFloat()
                        )

                        behaviorMetricAdapter.updateItems(state.behaviorMetrics)
                        recentEventsAdapter.updateItems(state.recentEvents)
                    }
                }
            }
        }
    }

    private fun bindAppIcon(packageName: String) {
        val drawable = try {
            requireContext().packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            ContextCompat.getDrawable(requireContext(), android.R.drawable.sym_def_app_icon)
        }
        ivAppIcon.setImageDrawable(drawable)
    }

    private fun bindSeverityBadge(severity: String) {
        val (textColorRes, backgroundRes, label) = when (severity.uppercase()) {
            "CRITICAL" -> Triple(R.color.inspect_red, R.drawable.bg_severity_chip_critical, "CRITICAL RISK")
            "HIGH" -> Triple(R.color.inspect_orange, R.drawable.bg_severity_chip_high, "HIGH RISK")
            "MEDIUM" -> Triple(R.color.inspect_yellow, R.drawable.bg_severity_chip_medium, "MEDIUM RISK")
            else -> Triple(R.color.inspect_low, R.drawable.bg_severity_chip_low, "LOW RISK")
        }
        tvRiskLevel.text = label
        tvRiskLevel.setTextColor(ContextCompat.getColor(requireContext(), textColorRes))
        tvRiskLevel.setBackgroundResource(backgroundRes)
    }
}
