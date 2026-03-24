package com.sliit.isp.accessibilityguardian

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sliit.isp.accessibilityguardian.ui.SuspiciousApp

class SuspiciousAppAdapter(
    private var items: List<SuspiciousApp>,
    private val onItemClick: (SuspiciousApp) -> Unit
) : RecyclerView.Adapter<SuspiciousAppAdapter.ViewHolder>() {

    private val iconCache = mutableMapOf<String, Drawable?>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIcon)
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
        val context = holder.itemView.context

        holder.appIcon.setImageDrawable(
            resolveAppIcon(
                packageManager = context.packageManager,
                packageName = app.packageName
            ) ?: ContextCompat.getDrawable(context, android.R.drawable.ic_menu_report_image)
        )
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

    private fun resolveAppIcon(
        packageManager: PackageManager,
        packageName: String?
    ): Drawable? {
        val normalizedPackageName = packageName?.trim().orEmpty()
        if (normalizedPackageName.isBlank()) {
            return null
        }

        val cached = iconCache[normalizedPackageName]
        if (cached != null) {
            return cached.constantState?.newDrawable()?.mutate() ?: cached
        }
        if (iconCache.containsKey(normalizedPackageName)) {
            return null
        }

        val resolvedIcon = try {
            val applicationInfo = packageManager.getApplicationInfo(normalizedPackageName, 0)
            packageManager.getApplicationIcon(applicationInfo)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: RuntimeException) {
            null
        }

        iconCache[normalizedPackageName] = resolvedIcon
        return resolvedIcon?.constantState?.newDrawable()?.mutate() ?: resolvedIcon
    }
}
