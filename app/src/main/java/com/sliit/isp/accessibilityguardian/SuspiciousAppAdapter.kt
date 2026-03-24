package com.sliit.isp.accessibilityguardian

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sliit.isp.accessibilityguardian.ui.SuspiciousApp

class SuspiciousAppAdapter(
    private var items: List<SuspiciousApp>,
    private val onItemClick: (SuspiciousApp) -> Unit
) : RecyclerView.Adapter<SuspiciousAppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appName: TextView = view.findViewById(R.id.appName)
        val riskLabel: TextView = view.findViewById(R.id.riskLabel)
        val riskScore: TextView = view.findViewById(R.id.appRiskScore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suspicious_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = items[position]

        holder.appName.text = app.appName
        holder.riskLabel.text = app.riskLevel
        holder.riskScore.text = app.riskScore.toString()

        holder.itemView.setOnClickListener { onItemClick(app) }

        // Use Real Risk Colors from theme
        when (app.riskLevel.uppercase()) {
            "CRITICAL" -> holder.riskLabel.setTextColor(holder.itemView.context.getColor(R.color.risk_critical))
            "HIGH" -> holder.riskLabel.setTextColor(holder.itemView.context.getColor(R.color.risk_high))
            "MEDIUM" -> holder.riskLabel.setTextColor(holder.itemView.context.getColor(R.color.risk_medium))
            else -> holder.riskLabel.setTextColor(holder.itemView.context.getColor(R.color.risk_low))
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<SuspiciousApp>) {
        items = newItems
        notifyDataSetChanged()
    }
}
