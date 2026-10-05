package com.parboard.launcher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.parboard.launcher.R
import com.parboard.launcher.model.AppItem

class FavoritesAdapter(
    private val onItemClick: (AppItem) -> Unit,
    private val onItemLongClick: (AppItem) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.FavoriteViewHolder>() {

    private var items: List<AppItem> = emptyList()

    fun submitList(newItems: List<AppItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_favorite_grid, parent, false)
        return FavoriteViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    inner class FavoriteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_app_label)

        fun bind(item: AppItem) {
            tvLabel.text = item.label
            itemView.setOnClickListener {
                onItemClick(item)
            }
            itemView.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }
    }
}
