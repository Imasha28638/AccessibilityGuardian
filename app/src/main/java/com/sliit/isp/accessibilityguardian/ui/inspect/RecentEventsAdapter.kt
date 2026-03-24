package com.sliit.isp.accessibilityguardian.ui.inspect

import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sliit.isp.accessibilityguardian.databinding.ItemRecentEventBinding

class RecentEventsAdapter(
    private var items: List<RecentEventItem>
) : RecyclerView.Adapter<RecentEventsAdapter.VH>() {

    inner class VH(val binding: ItemRecentEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRecentEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val context = holder.binding.root.context
        with(holder.binding) {
            tvEventTitle.text = item.title
            tvEventSubtitle.text = item.subtitle
            tvTime.text = item.time
            tvSeverity.text = item.severity

            ivEventIcon.setImageResource(item.iconRes)
            ivEventIcon.setColorFilter(ContextCompat.getColor(context, item.iconTintRes))
            tvSeverity.setTextColor(ContextCompat.getColor(context, item.severityTextColorRes))
            tvSeverity.setBackgroundResource(item.severityBackgroundRes)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<RecentEventItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
