package com.novakeyboard.utils

import kotlin.math.abs

/**
 * Bounded optimal-string-alignment (Damerau-Levenshtein with adjacent
 * transpositions) used by the autocorrect engine. Returns [max] + 1 as soon as
 * the distance is known to exceed [max], so long candidate lists stay cheap.
 */
object EditDistance {

    fun distance(a: String, b: String, max: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        if (a.isEmpty()) return if (b.length > max) max + 1 else b.length
        if (b.isEmpty()) return if (a.length > max) max + 1 else a.length
        if (abs(a.length - b.length) > max) return max + 1

        val short: CharArray
        val long: CharArray
        if (a.length <= b.length) {
            short = a.toCharArray()
            long = b.toCharArray()
        } else {
            short = b.toCharArray()
            long = a.toCharArray()
        }

        val n = short.size
        var twoRowsBack = IntArray(n + 1)
        var previous = IntArray(n + 1) { it }
        var current = IntArray(n + 1)

        for (j in 1..long.size) {
            current[0] = j
            var rowMinimum = current[0]
            for (i in 1..n) {
                val substitution = if (short[i - 1] == long[j - 1]) 0 else 1
                var value = minOf(
                    current[i - 1] + 1,
                    previous[i] + 1,
                    previous[i - 1] + substitution,
                )
                if (i > 1 && j > 1 &&
                    short[i - 1] == long[j - 2] &&
                    short[i - 2] == long[j - 1]
                ) {
                    value = minOf(value, twoRowsBack[i - 2] + 1)
                }
                current[i] = value
                if (value < rowMinimum) rowMinimum = value
            }
            if (rowMinimum > max) return max + 1
            val recycled = twoRowsBack
            twoRowsBack = previous
            previous = current
            current = recycled
        }
        return previous[n].let { if (it > max) max + 1 else it }
    }

    fun within(a: String, b: String, max: Int): Boolean = distance(a, b, max) <= max
}
