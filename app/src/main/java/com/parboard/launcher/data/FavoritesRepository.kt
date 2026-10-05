package com.parboard.launcher.data

import android.content.Context
import android.content.SharedPreferences

class FavoritesRepository(private val prefs: SharedPreferences) {

    companion object {
        const val PREF_NAME = "parlauncher_prefs"
        private const val KEY_FAVORITES = "pinned_favorites"
        const val MAX_FAVORITES = 7
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
     * Adds an app to pinned favorites. Returns true if added, false if already exists or capacity reached.
     */
    fun addFavorite(packageName: String, activityName: String): Boolean {
        val current = getFavorites().toMutableList()
        val exists = current.any { it.first == packageName && it.second == activityName }
        if (exists) return false
        if (current.size >= MAX_FAVORITES) return false

        current.add(Pair(packageName, activityName))
        saveFavorites(current)
        return true
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

    private fun saveFavorites(list: List<Pair<String, String>>) {
        val serialized = list.joinToString(ITEM_SEPARATOR) { "${it.first}$COMPONENT_SEPARATOR${it.second}" }
        prefs.edit().putString(KEY_FAVORITES, serialized).apply()
    }
}
