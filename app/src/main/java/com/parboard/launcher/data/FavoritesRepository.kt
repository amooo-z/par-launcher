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
        private const val KEY_DOCK_APPS = "dock_apps"
        private const val KEY_LAYOUT_MODE = "layout_mode"
        private const val ITEM_SEPARATOR = "\n"
        private const val COMPONENT_SEPARATOR = "/"
        private const val PAGE_SEPARATOR = "\n===PAGE===\n"
        private const val PAGE_PREFIX = "PAGE:"

        fun create(context: Context): FavoritesRepository {
            return FavoritesRepository(context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE))
        }
    }

    /**
     * Returns all pages of pinned favorites. Each page is a List<Pair<packageName, activityName>>.
     * Guaranteed to return at least 1 page. Empty pages are preserved.
     */
    fun getPages(): List<List<Pair<String, String>>> {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return listOf(emptyList())
        if (raw.isEmpty()) return listOf(emptyList())

        val rawPages = raw.split(PAGE_SEPARATOR)
        val pages = ArrayList<List<Pair<String, String>>>()

        for (rawPage in rawPages) {
            val content = if (rawPage.startsWith(PAGE_PREFIX)) rawPage.removePrefix(PAGE_PREFIX) else rawPage
            pages.add(parseItems(content))
        }

        return if (pages.isEmpty()) listOf(emptyList()) else pages
    }

    /**
     * Saves all pages preserving empty pages using a prefix marker.
     */
    fun savePages(pages: List<List<Pair<String, String>>>) {
        if (pages.isEmpty()) {
            prefs.edit().putString(KEY_FAVORITES, "").apply()
            return
        }

        val serialized = pages.joinToString(PAGE_SEPARATOR) { page ->
            PAGE_PREFIX + page.joinToString(ITEM_SEPARATOR) { "${it.first}$COMPONENT_SEPARATOR${it.second}" }
        }
        prefs.edit().putString(KEY_FAVORITES, serialized).apply()
    }

    /**
     * Creates an empty page at the end and returns its index.
     */
    fun addEmptyPage(): Int {
        val pages = getPages().map { it.toMutableList() }.toMutableList()
        pages.add(mutableListOf())
        savePages(pages)
        return pages.lastIndex
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

    fun removeFavoriteAt(pageIndex: Int, pos: Int): Pair<String, String>? {
        val pages = getPages().map { it.toMutableList() }.toMutableList()
        if (pageIndex !in 0..pages.lastIndex) return null
        val page = pages[pageIndex]
        if (pos !in 0..page.lastIndex) return null
        val item = page.removeAt(pos)
        savePages(pages)
        return item
    }

    fun addFavoriteAt(pageIndex: Int, pos: Int, item: Pair<String, String>) {
        val pages = getPages().map { it.toMutableList() }.toMutableList()
        // Remove any existing duplicate of this package across all pages
        for (p in pages) {
            p.removeAll { it.first == item.first }
        }
        while (pages.size <= pageIndex) {
            pages.add(mutableListOf())
        }
        val page = pages[pageIndex]
        val clampedPos = pos.coerceIn(0, page.size)
        page.add(clampedPos, item)

        val maxPerPage = if (getLayoutMode() == LayoutMode.ALL_APPS) 20 else 25
        var curIdx = pageIndex
        while (curIdx < pages.size && pages[curIdx].size > maxPerPage) {
            val overflow = pages[curIdx].removeAt(pages[curIdx].lastIndex)
            val nextIdx = curIdx + 1
            if (nextIdx >= pages.size) {
                pages.add(mutableListOf())
            }
            pages[nextIdx].add(0, overflow)
            curIdx++
        }

        savePages(pages)
    }

    /**
     * Moves a favorite item between pages or within the same page.
     */
    fun moveFavorite(fromPage: Int, fromPos: Int, toPage: Int, toPos: Int) {
        val item = removeFavoriteAt(fromPage, fromPos) ?: return
        addFavoriteAt(toPage, toPos, item)
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

    /**
     * Returns bottom dock apps (up to 5).
     */
    fun getDockApps(): List<Pair<String, String>> {
        val raw = prefs.getString(KEY_DOCK_APPS, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return parseItems(raw)
    }

    fun saveDockApps(apps: List<Pair<String, String>>) {
        val capped = apps.take(5)
        val serialized = capped.joinToString(ITEM_SEPARATOR) { "${it.first}$COMPONENT_SEPARATOR${it.second}" }
        prefs.edit().putString(KEY_DOCK_APPS, serialized).apply()
    }

    fun removeDockAppAt(pos: Int): Pair<String, String>? {
        val apps = getDockApps().toMutableList()
        if (pos !in 0..apps.lastIndex) return null
        val item = apps.removeAt(pos)
        saveDockApps(apps)
        return item
    }

    fun addDockAppAt(pos: Int, item: Pair<String, String>): Boolean {
        val apps = getDockApps().toMutableList()
        apps.removeAll { it.first == item.first }
        if (apps.size >= 5) return false
        val clampedPos = pos.coerceIn(0, apps.size)
        apps.add(clampedPos, item)
        saveDockApps(apps)
        return true
    }

    fun swapDockApps(fromPos: Int, toPos: Int) {
        val apps = getDockApps().toMutableList()
        if (fromPos in 0..apps.lastIndex && toPos in 0..apps.lastIndex) {
            java.util.Collections.swap(apps, fromPos, toPos)
            saveDockApps(apps)
        }
    }

    /**
     * Custom icon color (ARGB Int) per package.
     */
    fun getIconColor(packageName: String): Int? {
        val key = "icon_color_$packageName"
        return if (prefs.contains(key)) prefs.getInt(key, 0) else null
    }

    fun setIconColor(packageName: String, color: Int?) {
        val key = "icon_color_$packageName"
        if (color == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putInt(key, color).apply()
        }
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
