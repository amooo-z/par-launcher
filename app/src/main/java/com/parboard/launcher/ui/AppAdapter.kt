package com.parboard.launcher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.parboard.launcher.R
import com.parboard.launcher.model.AppItem

class AppAdapter(
    private val onAppClick: (AppItem) -> Unit,
    private val onAppLongClick: (AppItem) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private var items: List<AppItem> = emptyList()
    val selectedItems: MutableSet<AppItem> = LinkedHashSet()
    var isSelectionMode: Boolean = false
        private set

    fun submitList(newItems: List<AppItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun startSelectionMode(initialItem: AppItem? = null) {
        isSelectionMode = true
        selectedItems.clear()
        initialItem?.let { selectedItems.add(it) }
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
    }

    fun endSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun toggleSelection(item: AppItem) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
        } else {
            selectedItems.add(item)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
    }

    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(items)
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_app_label)
        private val tvCheck: TextView = itemView.findViewById(R.id.tv_check_indicator)

        fun bind(item: AppItem) {
            tvLabel.text = item.label

            if (isSelectionMode) {
                tvCheck.visibility = View.VISIBLE
                val isSelected = selectedItems.contains(item)
                tvCheck.text = if (isSelected) "✓" else "○"
                tvCheck.alpha = if (isSelected) 1.0f else 0.4f

                itemView.setOnClickListener {
                    toggleSelection(item)
                }
                itemView.setOnLongClickListener(null)
            } else {
                tvCheck.visibility = View.GONE
                itemView.setOnClickListener {
                    onAppClick(item)
                }
                itemView.setOnLongClickListener {
                    onAppLongClick(item)
                    true
                }
            }
        }
    }
}
