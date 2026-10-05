package com.parboard.launcher.ui

import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.parboard.launcher.R
import com.parboard.launcher.model.AppItem
import com.parboard.launcher.model.DraggedAppData

class HomePagerAdapter(
    private val onAppClick: (AppItem) -> Unit,
    private val onItemDroppedOnPage: (dragData: DraggedAppData, targetPage: Int, targetPos: Int) -> Unit,
    private val colorProvider: (String) -> Int?,
    private val onColorPickerClick: (AppItem) -> Unit
) : RecyclerView.Adapter<HomePagerAdapter.PageViewHolder>() {

    private var pages: MutableList<MutableList<AppItem>> = mutableListOf()
    private val activeAdapters = mutableListOf<FavoritesAdapter>()

    fun submitPages(newPages: List<List<AppItem>>) {
        pages = newPages.map { it.toMutableList() }.toMutableList()
        notifyDataSetChanged()
    }

    fun hideAllBadges() {
        for (adapter in activeAdapters) {
            adapter.hideAllColorBadges()
        }
    }

    fun showBadgeFor(packageName: String) {
        for (adapter in activeAdapters) {
            adapter.showColorBadgeFor(packageName)
        }
    }

    override fun getItemCount(): Int = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position], position)
    }

    inner class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rvGrid: RecyclerView = itemView.findViewById(R.id.rv_page_grid)
        private val gridAdapter: FavoritesAdapter

        init {
            rvGrid.layoutManager = object : GridLayoutManager(itemView.context, 5) {
                override fun canScrollVertically(): Boolean = false
            }
            rvGrid.isNestedScrollingEnabled = false
            rvGrid.overScrollMode = View.OVER_SCROLL_NEVER
            gridAdapter = FavoritesAdapter(
                onItemClick = { item -> onAppClick(item) },
                pageIndex = 0,
                isDock = false,
                colorProvider = colorProvider,
                onColorPickerClick = onColorPickerClick,
                onDragStarted = { hideAllBadges() }
            )
            rvGrid.adapter = gridAdapter
            activeAdapters.add(gridAdapter)

            // Listen for drops onto this page's grid
            rvGrid.setOnDragListener { _, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> true
                    DragEvent.ACTION_DRAG_LOCATION -> true
                    DragEvent.ACTION_DROP -> {
                        val dragData = event.localState as? DraggedAppData ?: return@setOnDragListener false
                        val pagePos = bindingAdapterPosition
                        if (pagePos != RecyclerView.NO_POSITION && pagePos in pages.indices) {
                            val child = rvGrid.findChildViewUnder(event.x, event.y)
                            val dropPos = if (child != null) {
                                rvGrid.getChildAdapterPosition(child).coerceAtLeast(0)
                            } else {
                                pages[pagePos].size
                            }
                            rvGrid.post {
                                onItemDroppedOnPage(dragData, pagePos, dropPos)
                            }
                            true
                        } else {
                            false
                        }
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        rvGrid.post {
                            gridAdapter.notifyDataSetChanged()
                        }
                        true
                    }
                    else -> true
                }
            }
        }

        fun bind(pageItems: MutableList<AppItem>, pageIndex: Int) {
            gridAdapter.pageIndex = pageIndex
            gridAdapter.submitList(pageItems)
        }
    }
}
