package com.sliit.isp.accessibilityguardian.ui.inspect

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sliit.isp.accessibilityguardian.databinding.ItemBehaviorMetricBinding

class BehaviorMetricAdapter(
    private var items: List<BehaviorMetricItem>
) : RecyclerView.Adapter<BehaviorMetricAdapter.VH>() {

    inner class VH(val binding: ItemBehaviorMetricBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemBehaviorMetricBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val context = holder.binding.root.context
        with(holder.binding) {
            tvMetricTitle.text = item.label
            tvMetricValue.text = item.value
            ivMetricIcon.setImageResource(item.iconRes)
            ivMetricIcon.setColorFilter(ContextCompat.getColor(context, item.accentColorRes))

            if (item.deltaText.isNullOrBlank()) {
                tvMetricDelta.visibility = View.GONE
            } else {
                tvMetricDelta.visibility = View.VISIBLE
                tvMetricDelta.text = item.deltaText
                tvMetricDelta.setTextColor(ContextCompat.getColor(context, item.deltaColorRes))
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<BehaviorMetricItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
