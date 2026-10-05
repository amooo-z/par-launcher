package com.parboard.launcher

import com.parboard.launcher.model.AppItem
import com.parboard.launcher.util.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    @Test
    fun testNormalizeArabicCharsAndHalfSpace() {
        // Arabic yeh ('ي' \u064A) -> Persian yeh ('ی' \u06CC)
        // Arabic kaf ('ك' \u0643) -> Persian kaf ('ک' \u06A9)
        // ZWNJ ('\u200C') -> stripped
        val input = "فایل\u200cهای صوتی كلاسيك"
        val normalized = SearchEngine.normalize(input)
        assertEquals("فایلهای صوتی کلاسیک", normalized)
    }

    @Test
    fun testInstantFilterMatchesSubstring() {
        val items = listOf(
            AppItem("تنظیمات", "com.android.settings", ".Settings", SearchEngine.normalize("تنظیمات")),
            AppItem("دوربین", "com.android.camera", ".Camera", SearchEngine.normalize("دوربین")),
            AppItem("تلگرام", "org.telegram.messenger", ".MainActivity", SearchEngine.normalize("تلگرام")),
            AppItem("Chrome", "com.android.chrome", ".Main", SearchEngine.normalize("Chrome"))
        )

        val result1 = SearchEngine.filter(items, "تنظیم")
        assertEquals(1, result1.size)
        assertEquals("تنظیمات", result1[0].label)

        val result2 = SearchEngine.filter(items, "ch")
        assertEquals(1, result2.size)
        assertEquals("Chrome", result2[0].label)

        val resultEmpty = SearchEngine.filter(items, "")
        assertEquals(4, resultEmpty.size)
    }

    @Test
    fun testFilterWithArabicInputMatchesPersianApp() {
        val items = listOf(
            AppItem("کتابخانه", "com.example.books", ".Books", SearchEngine.normalize("کتابخانه"))
        )
        // User typed using Arabic keyboard with 'ك'
        val result = SearchEngine.filter(items, "كتاب")
        assertEquals(1, result.size)
        assertEquals("کتابخانه", result[0].label)
    }
}
