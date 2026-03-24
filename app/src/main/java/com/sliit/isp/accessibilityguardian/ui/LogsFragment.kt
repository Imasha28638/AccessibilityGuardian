package com.sliit.isp.accessibilityguardian.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.sliit.isp.accessibilityguardian.R
import com.sliit.isp.accessibilityguardian.SecurityLogAdapter
import kotlinx.coroutines.launch

class LogsFragment : Fragment(R.layout.fragment_logs) {

    private val viewModel: LogsViewModel by viewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipAll: Chip
    private lateinit var chipCritical: Chip
    private lateinit var chipHigh: Chip
    private lateinit var chipMedium: Chip
    private lateinit var chipLow: Chip
    private lateinit var countView: TextView
    private lateinit var criticalCard: View
    private lateinit var highCard: View
    private lateinit var mediumCard: View
    private lateinit var lowCard: View
    private lateinit var adapter: SecurityLogAdapter

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

        recyclerView = view.findViewById(R.id.recyclerLogs)
        chipGroup = view.findViewById(R.id.chipGroup)
        chipAll = view.findViewById(R.id.chipAll)
        chipCritical = view.findViewById(R.id.chipCritical)
        chipHigh = view.findViewById(R.id.chipHigh)
        chipMedium = view.findViewById(R.id.chipMedium)
        chipLow = view.findViewById(R.id.chipLow)
        countView = view.findViewById(R.id.tvCount)
        criticalCard = view.findViewById(R.id.cardCritical)
        highCard = view.findViewById(R.id.cardHigh)
        mediumCard = view.findViewById(R.id.cardMedium)
        lowCard = view.findViewById(R.id.cardLow)

        adapter = SecurityLogAdapter(emptyList(), ::openInspectionForLog)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        bindFilters()
        collectUi()
    }

    private fun bindFilters() {
        chipAll.setOnClickListener { viewModel.setFilter(LogsFilter.ALL) }
        chipCritical.setOnClickListener { viewModel.setFilter(LogsFilter.CRITICAL) }
        chipHigh.setOnClickListener { viewModel.setFilter(LogsFilter.HIGH) }
        chipMedium.setOnClickListener { viewModel.setFilter(LogsFilter.MEDIUM) }
        chipLow.setOnClickListener { viewModel.setFilter(LogsFilter.LOW) }
    }

    private fun collectUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.updateItems(state.entries)
                    countView.text = getString(R.string.events_found, state.entries.size)
                    bindFilterState(state.selectedFilter)
                    bindStatCard(criticalCard, R.string.critical, state.criticalCount, android.R.drawable.ic_dialog_alert, R.color.risk_critical)
                    bindStatCard(highCard, R.string.high, state.highCount, android.R.drawable.presence_away, R.color.risk_high)
                    bindStatCard(mediumCard, R.string.medium, state.mediumCount, android.R.drawable.ic_menu_info_details, R.color.risk_medium)
                    bindStatCard(lowCard, R.string.low, state.lowCount, android.R.drawable.presence_online, R.color.risk_low)
                }
            }
        }
    }

    private fun bindFilterState(filter: LogsFilter) {
        val targetChipId = when (filter) {
            LogsFilter.ALL -> R.id.chipAll
            LogsFilter.CRITICAL -> R.id.chipCritical
            LogsFilter.HIGH -> R.id.chipHigh
            LogsFilter.MEDIUM -> R.id.chipMedium
            LogsFilter.LOW -> R.id.chipLow
        }

        if (chipGroup.checkedChipId != targetChipId) {
            chipGroup.check(targetChipId)
        }
    }

    private fun bindStatCard(
        root: View,
        labelRes: Int,
        value: Int,
        iconRes: Int,
        colorRes: Int
    ) {
        root.findViewById<TextView>(R.id.tvStatLabel).setText(labelRes)
        root.findViewById<TextView>(R.id.tvStatValue).apply {
            text = value.toString()
            setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        }
        val iconView = root.findViewById<ImageView>(R.id.ivStatBadge)
        iconView.setImageResource(iconRes)
        ImageViewCompat.setImageTintList(
            iconView,
            ContextCompat.getColorStateList(requireContext(), colorRes)
        )
    }

    private fun openInspectionForLog(log: SecurityLog) {
        if (log.logId <= 0L) {
            return
        }

        findNavController().navigate(
            R.id.navigation_inspect,
            Bundle().apply {
                putLong("selected_log_id", log.logId)
            },
            navOptions {
                launchSingleTop = true
                restoreState = true
            }
        )
    }
}
