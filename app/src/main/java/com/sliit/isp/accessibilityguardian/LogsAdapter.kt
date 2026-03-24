package com.sliit.isp.accessibilityguardian

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sliit.isp.accessibilityguardian.ui.LogEntry

class LogsAdapter(
    private var items: List<LogEntry>
) : RecyclerView.Adapter<LogsAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvAppName: TextView = v.findViewById(R.id.tvAppName)
        val tvPackage: TextView = v.findViewById(R.id.tvPackage)
        val tvRiskPill: TextView = v.findViewById(R.id.tvRiskPill)
        val tvBullet1: TextView = v.findViewById(R.id.tvBullet1)
        val tvTime: TextView = v.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        val context = h.itemView.context

        h.tvAppName.text = item.title
        h.tvPackage.text = item.packageName
        h.tvBullet1.text = "• ${item.description}"
        h.tvTime.text = item.timestamp
        h.tvRiskPill.text = item.severity

        val color = when (item.severity.uppercase()) {
            "CRITICAL" -> context.getColor(R.color.risk_critical)
            "HIGH" -> context.getColor(R.color.risk_high)
            "MEDIUM" -> context.getColor(R.color.risk_medium)
            else -> context.getColor(R.color.risk_low)
        }
        h.tvRiskPill.setTextColor(color)
    }

    override fun getItemCount(): Int = items.size

    fun submit(newItems: List<LogEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun updateItems(newItems: List<LogEntry>) {
        submit(newItems)
    }
}
