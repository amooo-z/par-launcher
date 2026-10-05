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
    fun testPreserveEmptyPages() {
        val page0 = listOf(Pair("com.app1", ".A1"))
        val page1 = emptyList<Pair<String, String>>()
        val page2 = listOf(Pair("com.app2", ".A2"))

        repository.savePages(listOf(page0, page1, page2))

        val loaded = repository.getPages()
        assertEquals(3, loaded.size)
        assertEquals("com.app1", loaded[0][0].first)
        assertTrue(loaded[1].isEmpty())
        assertEquals("com.app2", loaded[2][0].first)
    }

    @Test
    fun testAddEmptyPage() {
        val page0 = listOf(Pair("com.app1", ".A1"))
        repository.savePages(listOf(page0))

        val newIndex = repository.addEmptyPage()
        assertEquals(1, newIndex)

        val loaded = repository.getPages()
        assertEquals(2, loaded.size)
        assertTrue(loaded[1].isEmpty())
    }

    @Test
    fun testDockApps() {
        val dockApps = listOf(
            Pair("com.phone", ".Dialer"),
            Pair("com.sms", ".Messages"),
            Pair("com.browser", ".Browser")
        )
        repository.saveDockApps(dockApps)

        val loaded = repository.getDockApps()
        assertEquals(3, loaded.size)
        assertEquals("com.phone", loaded[0].first)

        repository.swapDockApps(0, 1)
        val swapped = repository.getDockApps()
        assertEquals("com.sms", swapped[0].first)
        assertEquals("com.phone", swapped[1].first)
    }

    @Test
    fun testIconColor() {
        assertEquals(null, repository.getIconColor("com.test.app"))
        repository.setIconColor("com.test.app", 0xFF123456.toInt())
        assertEquals(0xFF123456.toInt(), repository.getIconColor("com.test.app"))
        repository.setIconColor("com.test.app", null)
        assertEquals(null, repository.getIconColor("com.test.app"))
    }

    @Test
    fun testAddFavoriteWithCascadingOverflow() {
        repository.setLayoutMode(LayoutMode.ALL_APPS)
        val fullPage0 = (1..20).map { Pair("com.app$it", ".Act$it") }
        repository.savePages(listOf(fullPage0))

        // Add a 21st item at position 0 of page 0
        repository.addFavoriteAt(0, 0, Pair("com.newapp", ".NewAct"))

        val pages = repository.getPages()
        assertEquals(2, pages.size)
        assertEquals(20, pages[0].size)
        assertEquals("com.newapp", pages[0][0].first)
        assertEquals(1, pages[1].size)
        assertEquals("com.app20", pages[1][0].first)
    }

    @Test
    fun testAddFavoriteDeduplication() {
        val page0 = listOf(Pair("com.app1", ".A1"), Pair("com.app2", ".A2"))
        val page1 = listOf(Pair("com.app3", ".A3"))
        repository.savePages(listOf(page0, page1))

        // Adding com.app1 to page 1 must remove it from page 0
        repository.addFavoriteAt(1, 0, Pair("com.app1", ".A1"))

        val pages = repository.getPages()
        assertEquals(2, pages.size)
        assertEquals(1, pages[0].size)
        assertEquals("com.app2", pages[0][0].first)
        assertEquals(2, pages[1].size)
        assertEquals("com.app1", pages[1][0].first)
        assertEquals("com.app3", pages[1][1].first)
    }

    @Test
    fun testAddDockAppDeduplication() {
        val dockApps = listOf(Pair("com.app1", ".A1"), Pair("com.app2", ".A2"))
        repository.saveDockApps(dockApps)

        repository.addDockAppAt(0, Pair("com.app2", ".A2"))
        val loaded = repository.getDockApps()
        assertEquals(2, loaded.size)
        assertEquals("com.app2", loaded[0].first)
        assertEquals("com.app1", loaded[1].first)
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
