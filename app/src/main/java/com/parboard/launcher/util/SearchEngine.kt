package com.parboard.launcher.util

import com.parboard.launcher.model.AppItem
import java.util.Locale

object SearchEngine {

    /**
     * Normalizes text for sub-millisecond search matching:
     * - Maps Arabic Yeh ('ي' \u064A, 'ى' \u0649) to Persian Yeh ('ی' \u06CC)
     * - Maps Arabic Kaf ('ك' \u0643) to Persian Kaf ('ک' \u06A9)
     * - Strips Zero-Width Non-Joiner (ZWNJ \u200C)
     * - Converts Latin letters to lowercase
     */
    fun normalize(text: String): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder(text.length)
        for (i in 0 until text.length) {
            val c = text[i]
            when (c) {
                '\u064A', '\u0649' -> sb.append('\u06CC') // ي, ى -> ی
                '\u0643' -> sb.append('\u06A9')           // ك -> ک
                '\u200C' -> { /* strip ZWNJ */ }
                '\u0640' -> { /* strip Tatweel */ }
                else -> sb.append(c.lowercaseChar())
            }
        }
        return sb.toString().trim()
    }

    /**
     * Filters list of items by query with zero allocation in the search loop.
     */
    fun filter(items: List<AppItem>, query: String): List<AppItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return items
        }
        val normalizedQuery = normalize(trimmed)
        if (normalizedQuery.isEmpty()) {
            return items
        }

        val result = ArrayList<AppItem>(items.size)
        for (i in 0 until items.size) {
            val item = items[i]
            if (item.normalizedToken.contains(normalizedQuery)) {
                result.add(item)
            }
        }
        return result
    }
}
