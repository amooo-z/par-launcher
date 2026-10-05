package com.parboard.launcher.util

import java.util.Calendar

object DateFormatter {

    private val PERSIAN_MONTHS = arrayOf(
        "فروردین", "اردیبهشت", "خرداد",
        "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر",
        "دی", "بهمن", "اسفند"
    )

    private val PERSIAN_WEEKDAYS = arrayOf(
        "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه"
    )

    private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    /**
     * Converts Gregorian year, month (1-12), and day (1-31) to Jalali (Solar Hijri) Triple(year, month, day).
     * Zero-allocation standard astronomical conversion.
     */
    fun toJalali(gYear: Int, gMonth: Int, gDay: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

        var gy = gYear - 1600
        var gm = gMonth - 1
        var gd = gDay - 1

        var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400

        for (i in 0 until gm) {
            gDayNo += gDaysInMonth[i + 1]
        }
        if (gm > 1 && ((gYear % 4 == 0 && gYear % 100 != 0) || (gYear % 400 == 0))) {
            gDayNo++
        }
        gDayNo += gd

        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var jm = 0
        var jd = 0
        for (i in 0 until 11) {
            val days = if (i < 6) 31 else 30
            if (jDayNo < days) {
                jm = i + 1
                jd = jDayNo + 1
                break
            }
            jDayNo -= days
        }
        if (jm == 0) {
            jm = 12
            jd = jDayNo + 1
        }

        return Triple(jy, jm, jd)
    }

    fun toPersianDigits(number: Int): String {
        val s = number.toString()
        val sb = java.lang.StringBuilder(s.length)
        for (i in 0 until s.length) {
            val c = s[i]
            if (c in '0'..'9') {
                sb.append(PERSIAN_DIGITS[c - '0'])
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Formats the given Calendar into a Persian date string like:
     * "دوشنبه، ۱۳ مهر ۱۴۰۵"
     */
    fun formatPersianDate(cal: Calendar): String {
        val gYear = cal.get(Calendar.YEAR)
        val gMonth = cal.get(Calendar.MONTH) + 1
        val gDay = cal.get(Calendar.DAY_OF_MONTH)

        val (jYear, jMonth, jDay) = toJalali(gYear, gMonth, gDay)

        // Calendar.DAY_OF_WEEK: Sunday=1, Monday=2, ..., Saturday=7
        val dayOfWeekIndex = cal.get(Calendar.DAY_OF_WEEK) - 1
        val weekDayName = PERSIAN_WEEKDAYS[dayOfWeekIndex]
        val monthName = PERSIAN_MONTHS[jMonth - 1]

        val dayStr = toPersianDigits(jDay)
        val yearStr = toPersianDigits(jYear)

        return "$weekDayName، $dayStr $monthName $yearStr"
    }

    /**
     * Convenience function to get the current formatted Persian date.
     */
    fun getCurrentPersianDate(): String {
        return formatPersianDate(Calendar.getInstance())
    }

    /**
     * Formats current time into HH:mm.
     */
    fun formatTime(cal: Calendar = Calendar.getInstance()): String {
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute)
    }
}
