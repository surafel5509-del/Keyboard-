package com.novakeyboard.data.dictionary

/** A word with its corpus frequency. */
data class WordScore(val word: String, val frequency: Int)

/**
 * Anything the prediction engine can ask for word frequencies, prefix
 * completions and one-edit neighbours. Implemented by the bundled dictionary
 * and by the user dictionary (learned + personal words).
 */
interface WordFrequencySource {
    val isReady: Boolean

    fun frequency(word: String): Int

    fun contains(word: String): Boolean = frequency(word) > 0

    /** Words starting with [prefix], best first. */
    fun prefixMatches(prefix: String, limit: Int): List<WordScore>

    /** Known words that are at most one edit away from [word]. */
    fun oneEditMatches(word: String, limit: Int): List<WordScore>

    companion object {
        val EMPTY: WordFrequencySource = object : WordFrequencySource {
            override val isReady: Boolean = true
            override fun frequency(word: String): Int = 0
            override fun prefixMatches(prefix: String, limit: Int): List<WordScore> = emptyList()
            override fun oneEditMatches(word: String, limit: Int): List<WordScore> = emptyList()
        }
    }
}
