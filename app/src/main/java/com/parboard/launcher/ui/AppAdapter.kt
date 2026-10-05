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
    private val onAppLongClick: (AppItem) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private var items: List<AppItem> = emptyList()

    fun submitList(newItems: List<AppItem>) {
        items = newItems
        notifyDataSetChanged()
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

        fun bind(item: AppItem) {
            tvLabel.text = item.label
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
