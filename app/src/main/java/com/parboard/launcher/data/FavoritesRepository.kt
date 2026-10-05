package com.parboard.launcher.data

import android.content.Context
import android.content.SharedPreferences

class FavoritesRepository(private val prefs: SharedPreferences) {

    enum class LayoutMode {
        DRAWER,     // Home favorites + App Drawer & Search
        ALL_APPS    // All apps on home screen grid, no search/drawer
    }

    companion object {
        const val PREF_NAME = "parlauncher_prefs"
        private const val KEY_FAVORITES = "pinned_favorites"
        private const val KEY_LAYOUT_MODE = "layout_mode"
        private const val ITEM_SEPARATOR = "\n"
        private const val COMPONENT_SEPARATOR = "/"
        private const val PAGE_SEPARATOR = "\n===PAGE===\n"

        fun create(context: Context): FavoritesRepository {
            return FavoritesRepository(context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE))
        }
    }

    /**
     * Returns all pages of pinned favorites. Each page is a List<Pair<packageName, activityName>>.
     * Guaranteed to return at least 1 page.
     */
    fun getPages(): List<List<Pair<String, String>>> {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return listOf(emptyList())
        if (raw.isBlank()) return listOf(emptyList())

        val rawPages = raw.split(PAGE_SEPARATOR)
        val pages = ArrayList<List<Pair<String, String>>>()

        for (rawPage in rawPages) {
            val pageItems = parseItems(rawPage)
            if (pageItems.isNotEmpty()) {
                pages.add(pageItems)
            }
        }

        return if (pages.isEmpty()) listOf(emptyList()) else pages
    }

    /**
     * Saves all pages, automatically pruning empty pages unless it is the only page.
     */
    fun savePages(pages: List<List<Pair<String, String>>>) {
        val nonEmpty = pages.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) {
            prefs.edit().putString(KEY_FAVORITES, "").apply()
            return
        }

        val serialized = nonEmpty.joinToString(PAGE_SEPARATOR) { page ->
            page.joinToString(ITEM_SEPARATOR) { "${it.first}$COMPONENT_SEPARATOR${it.second}" }
        }
        prefs.edit().putString(KEY_FAVORITES, serialized).apply()
    }

    /**
     * Returns a flat list of all favorites across all pages.
     */
    fun getFavorites(): List<Pair<String, String>> {
        val pages = getPages()
        val flat = ArrayList<Pair<String, String>>()
        for (page in pages) {
            flat.addAll(page)
        }
        return flat
    }

    /**
     * Adds an app to the first page (or creates it).
     */
    fun addFavorite(packageName: String, activityName: String): Boolean {
        if (isFavorite(packageName, activityName)) return false
        val pages = getPages().map { it.toMutableList() }.toMutableList()
        if (pages.isEmpty()) {
            pages.add(mutableListOf())
        }
        pages[0].add(Pair(packageName, activityName))
        savePages(pages)
        return true
    }

    /**
     * Batch adds multiple apps to the last page (or first page).
     */
    fun addFavorites(items: List<Pair<String, String>>): Int {
        val pages = getPages().map { it.toMutableList() }.toMutableList()
        if (pages.isEmpty()) {
            pages.add(mutableListOf())
        }
        var addedCount = 0

        for (item in items) {
            if (!isFavorite(item.first, item.second)) {
                // Add to last page
                pages[pages.lastIndex].add(item)
                addedCount++
            }
        }

        if (addedCount > 0) {
            savePages(pages)
        }
        return addedCount
    }

    /**
     * Removes an app from whatever page it is on.
     */
    fun removeFavorite(packageName: String, activityName: String): Boolean {
        val pages = getPages().map { it.toMutableList() }.toMutableList()
        var found = false

        for (page in pages) {
            val index = page.indexOfFirst { it.first == packageName && it.second == activityName }
            if (index != -1) {
                page.removeAt(index)
                found = true
                break
            }
        }

        if (found) {
            savePages(pages)
        }
        return found
    }

    /**
     * Moves a favorite item between pages or within the same page.
     */
    fun moveFavorite(fromPage: Int, fromPos: Int, toPage: Int, toPos: Int) {
        val pages = getPages().map { it.toMutableList() }.toMutableList()
        if (fromPage !in 0..pages.lastIndex) return
        val sourcePage = pages[fromPage]
        if (fromPos !in 0..sourcePage.lastIndex) return

        val item = sourcePage.removeAt(fromPos)

        // If toPage is beyond current pages, create new page
        while (pages.size <= toPage) {
            pages.add(mutableListOf())
        }

        val targetPage = pages[toPage]
        val clampedPos = toPos.coerceIn(0, targetPage.size)
        targetPage.add(clampedPos, item)

        savePages(pages)
    }

    /**
     * Swaps two items within a page during drag & drop reordering.
     */
    fun swapFavorites(page: Int, fromPos: Int, toPos: Int) {
        val pages = getPages().map { it.toMutableList() }.toMutableList()
        if (page !in 0..pages.lastIndex) return
        val p = pages[page]
        if (fromPos in 0..p.lastIndex && toPos in 0..p.lastIndex) {
            java.util.Collections.swap(p, fromPos, toPos)
            savePages(pages)
        }
    }

    /**
     * Moves an app to the next page (creating it if needed).
     */
    fun moveFavoriteToNextPage(packageName: String, activityName: String): Boolean {
        val pages = getPages()
        for (i in pages.indices) {
            val pos = pages[i].indexOfFirst { it.first == packageName && it.second == activityName }
            if (pos != -1) {
                moveFavorite(fromPage = i, fromPos = pos, toPage = i + 1, toPos = 0)
                return true
            }
        }
        return false
    }

    /**
     * Moves an app to a brand new page at the end.
     */
    fun moveFavoriteToNewPage(packageName: String, activityName: String): Boolean {
        val pages = getPages()
        for (i in pages.indices) {
            val pos = pages[i].indexOfFirst { it.first == packageName && it.second == activityName }
            if (pos != -1) {
                moveFavorite(fromPage = i, fromPos = pos, toPage = pages.size, toPos = 0)
                return true
            }
        }
        return false
    }

    /**
     * Moves an app to the previous page.
     */
    fun moveFavoriteToPrevPage(packageName: String, activityName: String): Boolean {
        val pages = getPages()
        for (i in pages.indices) {
            if (i == 0) continue
            val pos = pages[i].indexOfFirst { it.first == packageName && it.second == activityName }
            if (pos != -1) {
                moveFavorite(fromPage = i, fromPos = pos, toPage = i - 1, toPos = pages[i - 1].size)
                return true
            }
        }
        return false
    }

    fun isFavorite(packageName: String, activityName: String): Boolean {
        return getFavorites().any { it.first == packageName && it.second == activityName }
    }

    fun getLayoutMode(): LayoutMode {
        val name = prefs.getString(KEY_LAYOUT_MODE, LayoutMode.DRAWER.name)
        return try {
            LayoutMode.valueOf(name ?: LayoutMode.DRAWER.name)
        } catch (e: Exception) {
            LayoutMode.DRAWER
        }
    }

    fun setLayoutMode(mode: LayoutMode) {
        prefs.edit().putString(KEY_LAYOUT_MODE, mode.name).apply()
    }

    private fun parseItems(raw: String): List<Pair<String, String>> {
        val tokens = raw.split(ITEM_SEPARATOR)
        val result = ArrayList<Pair<String, String>>(tokens.size)
        for (token in tokens) {
            val trimmed = token.trim()
            if (trimmed.isNotEmpty()) {
                val parts = trimmed.split(COMPONENT_SEPARATOR, limit = 2)
                if (parts.size == 2) {
                    result.add(Pair(parts[0], parts[1]))
                }
            }
        }
        return result
    }
}
