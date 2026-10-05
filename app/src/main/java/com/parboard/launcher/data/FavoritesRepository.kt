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

        fun create(context: Context): FavoritesRepository {
            return FavoritesRepository(context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE))
        }
    }

    /**
     * Returns the ordered list of pinned favorites as Pair(packageName, activityName).
     */
    fun getFavorites(): List<Pair<String, String>> {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()

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

    /**
     * Adds an app to pinned favorites. Returns true if added, false if already exists.
     */
    fun addFavorite(packageName: String, activityName: String): Boolean {
        val current = getFavorites().toMutableList()
        val exists = current.any { it.first == packageName && it.second == activityName }
        if (exists) return false

        current.add(Pair(packageName, activityName))
        saveFavorites(current)
        return true
    }

    /**
     * Batch adds multiple apps to pinned favorites. Returns the number of newly added items.
     */
    fun addFavorites(items: List<Pair<String, String>>): Int {
        val current = getFavorites().toMutableList()
        var addedCount = 0

        for (item in items) {
            val exists = current.any { it.first == item.first && it.second == item.second }
            if (!exists) {
                current.add(item)
                addedCount++
            }
        }

        if (addedCount > 0) {
            saveFavorites(current)
        }
        return addedCount
    }

    /**
     * Removes an app from pinned favorites. Returns true if removed, false if not found.
     */
    fun removeFavorite(packageName: String, activityName: String): Boolean {
        val current = getFavorites().toMutableList()
        val index = current.indexOfFirst { it.first == packageName && it.second == activityName }
        if (index == -1) return false

        current.removeAt(index)
        saveFavorites(current)
        return true
    }

    /**
     * Checks if an app is currently in pinned favorites.
     */
    fun isFavorite(packageName: String, activityName: String): Boolean {
        val current = getFavorites()
        return current.any { it.first == packageName && it.second == activityName }
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

    private fun saveFavorites(list: List<Pair<String, String>>) {
        val serialized = list.joinToString(ITEM_SEPARATOR) { "${it.first}$COMPONENT_SEPARATOR${it.second}" }
        prefs.edit().putString(KEY_FAVORITES, serialized).apply()
    }
}
