package com.parboard.launcher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.parboard.launcher.R
import com.parboard.launcher.model.AppItem
import java.util.Collections

class HomePagerAdapter(
    private val onAppClick: (AppItem) -> Unit,
    private val onAppLongClick: (AppItem, pageIndex: Int, totalPages: Int) -> Unit,
    private val onItemMovedWithinPage: (pageIndex: Int, fromPos: Int, toPos: Int) -> Unit
) : RecyclerView.Adapter<HomePagerAdapter.PageViewHolder>() {

    private var pages: MutableList<MutableList<AppItem>> = mutableListOf()

    fun submitPages(newPages: List<List<AppItem>>) {
        pages = newPages.map { it.toMutableList() }.toMutableList()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    inner class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rvGrid: RecyclerView = itemView.findViewById(R.id.rv_page_grid)
        private val gridAdapter: FavoritesAdapter

        init {
            rvGrid.layoutManager = GridLayoutManager(itemView.context, 5)
            rvGrid.setHasFixedSize(true)
            gridAdapter = FavoritesAdapter(
                onItemClick = { item -> onAppClick(item) },
                onItemLongClick = { item ->
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onAppLongClick(item, pos, pages.size)
                    }
                }
            )
            rvGrid.adapter = gridAdapter

            val callback = object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                0
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val pagePos = bindingAdapterPosition
                    if (pagePos == RecyclerView.NO_POSITION || pagePos !in pages.indices) return false
                    val pageList = pages[pagePos]
                    val fromPos = viewHolder.bindingAdapterPosition
                    val toPos = target.bindingAdapterPosition
                    if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                        Collections.swap(pageList, fromPos, toPos)
                        gridAdapter.notifyItemMoved(fromPos, toPos)
                        onItemMovedWithinPage(pagePos, fromPos, toPos)
                        return true
                    }
                    return false
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
                override fun isLongPressDragEnabled(): Boolean = true
            }
            ItemTouchHelper(callback).attachToRecyclerView(rvGrid)
        }

        fun bind(pageItems: MutableList<AppItem>) {
            gridAdapter.submitList(pageItems)
        }
    }
}
