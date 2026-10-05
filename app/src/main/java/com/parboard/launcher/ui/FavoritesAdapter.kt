package com.parboard.launcher.ui

import android.content.ClipData
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.parboard.launcher.R
import com.parboard.launcher.model.AppItem
import com.parboard.launcher.model.DraggedAppData

class FavoritesAdapter(
    private val onItemClick: (AppItem) -> Unit,
    var pageIndex: Int = 0,
    var isDock: Boolean = false,
    private val colorProvider: ((packageName: String) -> Int?)? = null,
    private val onColorPickerClick: ((AppItem) -> Unit)? = null,
    private val onDragStarted: ((DraggedAppData) -> Unit)? = null
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
        holder.bind(items[position])
    }

    inner class FavoriteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val viewIconSquare: View = itemView.findViewById(R.id.view_icon_square)
        private val btnColorPicker: TextView = itemView.findViewById(R.id.btn_icon_color_picker)
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_app_label)

        fun bind(item: AppItem) {
            // Guarantee view scale and alpha are always reset cleanly
            itemView.animate().cancel()
            itemView.scaleX = 1.0f
            itemView.scaleY = 1.0f
            itemView.alpha = 1.0f

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

            // Floating color badge visibility
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

            val touchSlop = ViewConfiguration.get(itemView.context).scaledTouchSlop
            var downX = 0f
            var downY = 0f
            var isLongPressed = false
            var isDragging = false

            val longPressRunnable = Runnable {
                isLongPressed = true
                itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showColorBadgeFor(item.packageName)
            }

            itemView.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        isLongPressed = false
                        isDragging = false
                        v.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        val dist = Math.hypot(dx.toDouble(), dy.toDouble())
                        if (dist > touchSlop) {
                            if (isLongPressed && !isDragging) {
                                val pos = bindingAdapterPosition
                                if (pos == RecyclerView.NO_POSITION) {
                                    v.removeCallbacks(longPressRunnable)
                                    return@setOnTouchListener false
                                }
                                isDragging = true
                                hideAllColorBadges()
                                val clipData = ClipData.newPlainText("app_drag", "${item.packageName}/${item.activityName}")
                                val shadow = View.DragShadowBuilder(viewIconSquare)
                                val dragData = DraggedAppData(
                                    packageName = item.packageName,
                                    activityName = item.activityName,
                                    source = if (isDock) "DOCK" else "PAGE",
                                    sourcePageIndex = pageIndex,
                                    sourcePos = pos
                                )
                                v.startDragAndDrop(clipData, shadow, dragData, 0)
                                v.alpha = 0.4f
                                onDragStarted?.invoke(dragData)
                                true
                            } else if (!isLongPressed) {
                                v.removeCallbacks(longPressRunnable)
                                false
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        v.removeCallbacks(longPressRunnable)
                        if (isLongPressed) {
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.removeCallbacks(longPressRunnable)
                        isLongPressed = false
                        isDragging = false
                        false
                    }
                    else -> false
                }
            }
        }
    }
}
