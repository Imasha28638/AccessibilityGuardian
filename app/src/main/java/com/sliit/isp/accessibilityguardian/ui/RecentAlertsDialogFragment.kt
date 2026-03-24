package com.sliit.isp.accessibilityguardian.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sliit.isp.accessibilityguardian.SecurityLogAdapter
import com.sliit.isp.accessibilityguardian.databinding.DialogRecentAlertsBinding
import kotlinx.coroutines.launch

class RecentAlertsDialogFragment : DialogFragment() {

    private val viewModel: MonitorViewModel by viewModels(ownerProducer = { requireParentFragment() })

    private var _binding: DialogRecentAlertsBinding? = null
    private val binding get() = checkNotNull(_binding)

    private lateinit var adapter: SecurityLogAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogRecentAlertsBinding.inflate(LayoutInflater.from(requireContext()))
        adapter = SecurityLogAdapter(emptyList()) { }
        isCancelable = true

        binding.recyclerAlerts.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerAlerts.adapter = adapter
        binding.btnClose.setOnClickListener { dismiss() }

        observeAlerts()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    private fun observeAlerts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val alerts = state.securityLogs.sortedByDescending(SecurityLog::timestampMillis)
                    adapter.updateItems(alerts)
                    binding.recyclerAlerts.isVisible = alerts.isNotEmpty()
                    binding.tvEmptyState.isVisible = alerts.isEmpty()
                }
            }
        }
    }

    companion object {
        const val TAG = "RecentAlertsDialog"
    }
}
