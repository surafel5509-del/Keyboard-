package com.novakeyboard.domain.autocorrect

import com.novakeyboard.data.dictionary.WordFrequencySource
import com.novakeyboard.utils.Words

/**
 * Deliberately conservative autocorrect.
 *
 * A word is only replaced when
 *  - it is not itself a known word,
 *  - an edit-distance-1 neighbour exists in the dictionary,
 *  - that neighbour is frequent enough for the word's length,
 *  - and the word is not an acronym, URL or e-mail fragment.
 *
 * The caller keeps the original word so the correction can be undone with one
 * tap ("Undo" in the suggestion strip) and by pressing backspace immediately.
 */
class AutoCorrector(private val source: WordFrequencySource) {

    data class Correction(val typed: String, val replacement: String)

    fun correctionFor(word: String): Correction? {
        val typed = word.trim()
        if (typed.length < MIN_LENGTH || typed.length > MAX_LENGTH) return null
        if (!Words.isPlainWord(typed)) return null
        if (Words.looksLikeUrlOrEmail(typed)) return null
        if (isAcronym(typed)) return null
        if (!source.isReady) return null
        if (source.contains(typed.lowercase())) return null

        val minimumFrequency = minimumFrequencyFor(typed.length)
        var best: String? = null
        var bestFrequency = 0
        for (candidate in source.oneEditMatches(typed.lowercase(), MAX_CANDIDATES)) {
            if (candidate.word == typed.lowercase()) continue
            if (candidate.word.length < MIN_LENGTH) continue
            if (candidate.frequency < minimumFrequency) continue
            if (candidate.frequency > bestFrequency) {
                best = candidate.word
                bestFrequency = candidate.frequency
            }
        }
        val replacement = best ?: return null
        return Correction(typed, Words.matchCase(replacement, typed))
    }

    /** Thresholds chosen so that rare words are never silently rewritten. */
    private fun minimumFrequencyFor(length: Int): Int = when (length) {
        3 -> 6000
        4 -> 3000
        in 5..7 -> 900
        else -> 400
    }

    private fun isAcronym(word: String): Boolean {
        if (word.length < 3) return false
        val hasUpper = word.any { it.isUpperCase() }
        if (!hasUpper) return false
        // Mixed-case acronyms such as "NASA" or "iOS" are left alone.
        return word.count { it.isUpperCase() } >= 2
    }

    companion object {
        private const val MIN_LENGTH = 3
        private const val MAX_LENGTH = 32
        private const val MAX_CANDIDATES = 6
    }
}
