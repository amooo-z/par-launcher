package com.parboard.launcher.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.parboard.launcher.R
import com.parboard.launcher.model.AppItem

class FavoritesAdapter(
    private val onItemClick: (AppItem) -> Unit,
    private val onItemLongClick: ((AppItem) -> Unit)? = null,
    private val colorProvider: ((packageName: String) -> Int?)? = null,
    private val onColorPickerClick: ((AppItem) -> Unit)? = null
) : RecyclerView.Adapter<FavoritesAdapter.FavoriteViewHolder>() {

    private var items: List<AppItem> = emptyList()
    var activeBadgePackage: String? = null
        private set

    fun submitList(newItems: List<AppItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun showColorBadgeFor(packageName: String) {
        val prev = activeBadgePackage
        activeBadgePackage = packageName
        if (prev != null && prev != packageName) {
            val prevIdx = items.indexOfFirst { it.packageName == prev }
            if (prevIdx != -1) notifyItemChanged(prevIdx)
        }
        val curIdx = items.indexOfFirst { it.packageName == packageName }
        if (curIdx != -1) notifyItemChanged(curIdx)
    }

    fun hideAllColorBadges() {
        val prev = activeBadgePackage ?: return
        activeBadgePackage = null
        val prevIdx = items.indexOfFirst { it.packageName == prev }
        if (prevIdx != -1) notifyItemChanged(prevIdx)
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
        private val viewIconSquare: View = itemView.findViewById(R.id.view_icon_square)
        private val btnColorPicker: TextView = itemView.findViewById(R.id.btn_icon_color_picker)
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_app_label)

        fun bind(item: AppItem) {
            tvLabel.text = item.label

            // Apply custom RGBA/Hex color or default
            val density = itemView.context.resources.displayMetrics.density
            val customColor = colorProvider?.invoke(item.packageName)
            val strokeColor = customColor ?: 0x80FFFFFF.toInt()

            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * density
                setColor(Color.TRANSPARENT)
                setStroke((1.5f * density).toInt(), strokeColor)
            }
            viewIconSquare.background = shape

            if (customColor != null) {
                tvLabel.setTextColor(customColor)
            } else {
                tvLabel.setTextColor(0xF0FFFFFF.toInt())
            }

            // Floating color picker badge visibility
            val isBadgeVisible = (item.packageName == activeBadgePackage)
            btnColorPicker.visibility = if (isBadgeVisible) View.VISIBLE else View.GONE
            btnColorPicker.setOnClickListener {
                onColorPickerClick?.invoke(item)
            }

            itemView.setOnClickListener {
                if (activeBadgePackage != null) {
                    hideAllColorBadges()
                } else {
                    onItemClick(item)
                }
            }

            if (onItemLongClick != null) {
                itemView.setOnLongClickListener {
                    onItemLongClick.invoke(item)
                    true
                }
            } else {
                itemView.setOnLongClickListener(null)
            }
        }
    }
}
