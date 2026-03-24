package com.sliit.isp.accessibilityguardian

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sliit.isp.accessibilityguardian.databinding.ItemSecurityLogBinding
import com.sliit.isp.accessibilityguardian.ui.SecurityLog

class SecurityLogAdapter(
    private var items: List<SecurityLog>,
    private val onItemClick: (SecurityLog) -> Unit
) :
    RecyclerView.Adapter<SecurityLogAdapter.LogViewHolder>() {

    class LogViewHolder(val binding: ItemSecurityLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemSecurityLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = items[position]
        val binding = holder.binding
        val context = binding.root.context
        val (colorRes, badgeBackgroundRes) = severityStyle(log.severity)
        val color = ContextCompat.getColor(context, colorRes)

        binding.tvAppName.text = log.appName
        binding.tvPackageName.text = log.packageName
        binding.tvLogMessage.text = log.message
        binding.tvTime.text = log.timestampLabel
        binding.tvSeverityBadge.text = log.severity
        binding.tvSeverityBadge.setTextColor(color)
        binding.tvSeverityBadge.setBackgroundResource(badgeBackgroundRes)
        binding.viewSeverityAccent.setBackgroundColor(color)

        val count = log.eventCount
        if (count != null && count > 0) {
            binding.tvEventCount.text = context.getString(R.string.event_count, count)
            binding.tvEventCount.visibility = View.VISIBLE
        } else {
            binding.tvEventCount.visibility = View.GONE
        }

        bindAppIcon(binding.ivAppIcon, log.packageName)
        binding.root.setOnClickListener { onItemClick(log) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<SecurityLog>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun bindAppIcon(iconView: ImageView, packageName: String) {
        val context = iconView.context
        val drawable = try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
        }
        iconView.setImageDrawable(drawable)
    }

    private fun severityStyle(severity: String): Pair<Int, Int> {
        return when (severity.uppercase()) {
            "CRITICAL" -> R.color.risk_critical to R.drawable.bg_severity_chip_critical
            "HIGH" -> R.color.risk_high to R.drawable.bg_severity_chip_high
            "MEDIUM" -> R.color.risk_medium to R.drawable.bg_severity_chip_medium
            else -> R.color.risk_low to R.drawable.bg_severity_chip_low
        }
    }
}
