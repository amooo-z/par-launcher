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
    fun testEmptyPagesByDefault() {
        val pages = repository.getPages()
        assertEquals(1, pages.size)
        assertTrue(pages[0].isEmpty())
    }

    @Test
    fun testAddAndRetrieveMultiPages() {
        val page0 = listOf(Pair("com.app1", ".A1"), Pair("com.app2", ".A2"))
        val page1 = listOf(Pair("com.app3", ".A3"))

        repository.savePages(listOf(page0, page1))

        val loadedPages = repository.getPages()
        assertEquals(2, loadedPages.size)
        assertEquals(2, loadedPages[0].size)
        assertEquals(1, loadedPages[1].size)
        assertEquals("com.app1", loadedPages[0][0].first)
        assertEquals("com.app3", loadedPages[1][0].first)
    }

    @Test
    fun testAutoPruneEmptyPages() {
        val page0 = listOf(Pair("com.app1", ".A1"))
        val page1 = emptyList<Pair<String, String>>()
        val page2 = listOf(Pair("com.app2", ".A2"))

        repository.savePages(listOf(page0, page1, page2))

        val loaded = repository.getPages()
        assertEquals(2, loaded.size)
        assertEquals("com.app1", loaded[0][0].first)
        assertEquals("com.app2", loaded[1][0].first)
    }

    @Test
    fun testMoveFavoriteBetweenPages() {
        val page0 = listOf(Pair("com.app1", ".A1"), Pair("com.app2", ".A2"))
        val page1 = listOf(Pair("com.app3", ".A3"))
        repository.savePages(listOf(page0, page1))

        // Move app2 to page 1
        repository.moveFavorite(fromPage = 0, fromPos = 1, toPage = 1, toPos = 1)

        val loaded = repository.getPages()
        assertEquals(2, loaded.size)
        assertEquals(1, loaded[0].size)
        assertEquals("com.app1", loaded[0][0].first)
        assertEquals(2, loaded[1].size)
        assertEquals("com.app3", loaded[1][0].first)
        assertEquals("com.app2", loaded[1][1].first)
    }

    @Test
    fun testMoveToNewPageAtEnd() {
        val page0 = listOf(Pair("com.app1", ".A1"), Pair("com.app2", ".A2"))
        repository.savePages(listOf(page0))

        // Move app2 to a newly created page 1
        repository.moveFavorite(fromPage = 0, fromPos = 1, toPage = 1, toPos = 0)

        val loaded = repository.getPages()
        assertEquals(2, loaded.size)
        assertEquals("com.app1", loaded[0][0].first)
        assertEquals("com.app2", loaded[1][0].first)
    }

    @Test
    fun testFlatFavoritesListMaintainsAllItems() {
        val page0 = listOf(Pair("com.app1", ".A1"))
        val page1 = listOf(Pair("com.app2", ".A2"))
        repository.savePages(listOf(page0, page1))

        val flat = repository.getFavorites()
        assertEquals(2, flat.size)
        assertTrue(repository.isFavorite("com.app1", ".A1"))
        assertTrue(repository.isFavorite("com.app2", ".A2"))
    }

    @Test
    fun testSwapFavoritesWithinPage() {
        val page0 = listOf(Pair("com.app1", ".A1"), Pair("com.app2", ".A2"), Pair("com.app3", ".A3"))
        repository.savePages(listOf(page0))

        repository.swapFavorites(page = 0, fromPos = 0, toPos = 2)

        val loaded = repository.getPages()
        assertEquals(1, loaded.size)
        assertEquals("com.app3", loaded[0][0].first)
        assertEquals("com.app2", loaded[0][1].first)
        assertEquals("com.app1", loaded[0][2].first)
    }

    @Test
    fun testMoveFavoriteToNewPageMethod() {
        val page0 = listOf(Pair("com.app1", ".A1"), Pair("com.app2", ".A2"))
        repository.savePages(listOf(page0))

        val result = repository.moveFavoriteToNewPage("com.app1", ".A1")
        assertTrue(result)

        val loaded = repository.getPages()
        assertEquals(2, loaded.size)
        assertEquals("com.app2", loaded[0][0].first)
        assertEquals("com.app1", loaded[1][0].first)
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
