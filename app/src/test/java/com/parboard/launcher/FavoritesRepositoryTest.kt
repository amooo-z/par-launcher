package com.parboard.launcher

import android.content.SharedPreferences
import com.parboard.launcher.data.FavoritesRepository
import com.parboard.launcher.data.FavoritesRepository.LayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FavoritesRepositoryTest {

    private lateinit var mockPrefs: FakeSharedPreferences
    private lateinit var repository: FavoritesRepository

    @Before
    fun setUp() {
        mockPrefs = FakeSharedPreferences()
        repository = FavoritesRepository(mockPrefs)
    }

    @Test
    fun testEmptyFavoritesByDefault() {
        val favs = repository.getFavorites()
        assertTrue(favs.isEmpty())
    }

    @Test
    fun testAddAndRetrieveFavorite() {
        val added = repository.addFavorite("com.android.settings", ".Settings")
        assertTrue(added)
        assertTrue(repository.isFavorite("com.android.settings", ".Settings"))

        val favs = repository.getFavorites()
        assertEquals(1, favs.size)
        assertEquals("com.android.settings", favs[0].first)
        assertEquals(".Settings", favs[0].second)
    }

    @Test
    fun testPreventDuplicateFavorites() {
        repository.addFavorite("com.android.settings", ".Settings")
        val addedAgain = repository.addFavorite("com.android.settings", ".Settings")
        assertFalse(addedAgain)
        assertEquals(1, repository.getFavorites().size)
    }

    @Test
    fun testRemoveFavorite() {
        repository.addFavorite("com.android.settings", ".Settings")
        val removed = repository.removeFavorite("com.android.settings", ".Settings")
        assertTrue(removed)
        assertFalse(repository.isFavorite("com.android.settings", ".Settings"))
        assertTrue(repository.getFavorites().isEmpty())
    }

    @Test
    fun testUnlimitedFavorites() {
        // Can add more than 7 favorites (unlimited)
        for (i in 1..25) {
            val added = repository.addFavorite("com.app$i", ".Main")
            assertTrue("App $i should be added", added)
        }
        assertEquals(25, repository.getFavorites().size)
    }

    @Test
    fun testBatchAddFavorites() {
        val batch = listOf(
            Pair("com.app1", ".Main"),
            Pair("com.app2", ".Main"),
            Pair("com.app3", ".Main")
        )
        val addedCount = repository.addFavorites(batch)
        assertEquals(3, addedCount)
        assertEquals(3, repository.getFavorites().size)

        // Adding again with duplicates
        val duplicateBatch = listOf(
            Pair("com.app3", ".Main"),
            Pair("com.app4", ".Main")
        )
        val secondAdded = repository.addFavorites(duplicateBatch)
        assertEquals(1, secondAdded) // only app4 was added
        assertEquals(4, repository.getFavorites().size)
    }

    @Test
    fun testLayoutModePersistence() {
        assertEquals(LayoutMode.DRAWER, repository.getLayoutMode())
        repository.setLayoutMode(LayoutMode.ALL_APPS)
        assertEquals(LayoutMode.ALL_APPS, repository.getLayoutMode())
    }
}

class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor(values)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val backingMap: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { temp[key ?: ""] = values; return this }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
        override fun remove(key: String?): SharedPreferences.Editor { temp[key ?: ""] = null; return this }
        override fun clear(): SharedPreferences.Editor { clear = true; return this }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) backingMap.clear()
            for ((k, v) in temp) {
                if (v == null) backingMap.remove(k) else backingMap[k] = v
            }
        }
    }
}
