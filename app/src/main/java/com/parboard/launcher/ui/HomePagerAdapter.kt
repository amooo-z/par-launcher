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
                onItemLongClick = null // No dialog popup - pure drag & drop!
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

                override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        viewHolder?.itemView?.animate()
                            ?.scaleX(1.15f)
                            ?.scaleY(1.15f)
                            ?.alpha(0.85f)
                            ?.setDuration(150)
                            ?.start()
                    }
                }

                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.animate()
                        ?.scaleX(1.0f)
                        ?.scaleY(1.0f)
                        ?.alpha(1.0f)
                        ?.setDuration(150)
                        ?.start()
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
