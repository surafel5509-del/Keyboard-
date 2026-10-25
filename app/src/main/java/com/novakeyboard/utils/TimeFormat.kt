package com.novakeyboard.utils

/** Short, localisable-enough relative time labels used by the clipboard panel. */
object TimeFormat {

    fun relative(now: Long, timestamp: Long): String {
        val delta = now - timestamp
        if (delta < 0) return "now"
        val minutes = delta / 60_000
        val hours = delta / 3_600_000
        val days = delta / 86_400_000
        return when {
            delta < 60_000 -> "now"
            minutes < 60 -> "${minutes}m"
            hours < 24 -> "${hours}h"
            days < 7 -> "${days}d"
            else -> "${days / 7}w"
        }
    }
}
