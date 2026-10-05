package com.parboard.launcher

import com.parboard.launcher.util.DateFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DateFormatterTest {

    @Test
    fun testGregorianToJalaliConversion() {
        val (year, month, day) = DateFormatter.toJalali(2026, 10, 5)
        assertEquals(1405, year)
        assertEquals(7, month) // Mehr
        assertEquals(13, day)
    }

    @Test
    fun testFormattedPersianDate() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")).apply {
            set(2026, Calendar.OCTOBER, 5, 12, 0, 0)
        }
        val formatted = DateFormatter.formatPersianDate(cal)
        // Expected: "دوشنبه، ۱۳ مهر ۱۴۰۵"
        assertTrue("Formatted date should contain 'مهر'", formatted.contains("مهر"))
        assertTrue("Formatted date should contain 'دوشنبه'", formatted.contains("دوشنبه"))
        assertTrue("Formatted date should contain '۱۴۰۵'", formatted.contains("۱۴۰۵"))
    }

    @Test
    fun testNowDateProducesNonEmptyString() {
        val now = DateFormatter.getCurrentPersianDate()
        assertTrue(now.isNotEmpty())
    }

    @Test
    fun testToPersianDigits() {
        assertEquals("۱۲۳۴۵۶۷۸۹۰", DateFormatter.toPersianDigits(1234567890))
    }
}
