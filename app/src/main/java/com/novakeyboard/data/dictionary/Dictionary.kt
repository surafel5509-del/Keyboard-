package com.novakeyboard.data.dictionary

import java.io.InputStream

/**
 * Bundled, frequency-ranked English word list.
 *
 * The asset ships as `word<TAB>count` lines sorted by count during asset
 * generation; this class re-sorts them alphabetically so that prefix lookups and
 * exact-match checks are simple binary searches. Loading happens on a background
 * thread (see `KeyboardController`), never on the UI thread.
 */
class Dictionary private constructor(
    private val words: Array<String>,
    private val frequencies: IntArray,
) : WordFrequencySource {

    override val isReady: Boolean = true

    val size: Int get() = words.size

    override fun frequency(word: String): Int {
        val index = indexOf(word.lowercase())
        return if (index >= 0) frequencies[index] else 0
    }

    override fun prefixMatches(prefix: String, limit: Int): List<WordScore> {
        val query = prefix.lowercase()
        if (query.isEmpty() || words.isEmpty()) return emptyList()
        var low = 0
        var high = words.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (words[mid] < query) low = mid + 1 else high = mid
        }
        val best = ArrayList<WordScore>(limit)
        var index = low
        var scanned = 0
        while (index < words.size && scanned < MAX_PREFIX_SCAN) {
            val word = words[index]
            if (!word.startsWith(query)) break
            if (word.length != query.length) {
                insertRanked(best, WordScore(word, frequencies[index]), limit)
            }
            index++
            scanned++
        }
        return best
    }

    override fun oneEditMatches(word: String, limit: Int): List<WordScore> {
        if (word.isEmpty()) return emptyList()
        val query = word.lowercase()
        val best = ArrayList<WordScore>(limit)
        for (candidate in edits1(query, SEARCH_ALPHABET)) {
            val index = indexOf(candidate)
            if (index < 0) continue
            val item = WordScore(candidate, frequencies[index])
            if (best.none { it.word == item.word }) insertRanked(best, item, limit)
        }
        return best
    }

    private fun insertRanked(target: MutableList<WordScore>, candidate: WordScore, limit: Int) {
        var position = 0
        while (position < target.size && target[position].frequency >= candidate.frequency) position++
        if (position >= limit) return
        target.add(position, candidate)
        if (target.size > limit) target.removeAt(target.size - 1)
    }

    private fun indexOf(word: String): Int {
        var low = 0
        var high = words.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val comparison = words[mid].compareTo(word)
            when {
                comparison < 0 -> low = mid + 1
                comparison > 0 -> high = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    companion object {
        private const val MAX_PREFIX_SCAN = 4000
        private const val MAX_WORD_LENGTH = 24
        private val SEARCH_ALPHABET = "abcdefghijklmnopqrstuvwxyz'".toCharArray()

        val EMPTY = Dictionary(emptyArray(), IntArray(0))

        fun parse(input: InputStream): Dictionary =
            input.bufferedReader().useLines { parseLines(it) }

        fun load(input: InputStream): Dictionary = parse(input)

        fun parseLines(lines: Sequence<String>): Dictionary {
            val counts = HashMap<String, Int>(70000)
            for (line in lines) {
                if (line.isEmpty()) continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val word = line.substring(0, tab).lowercase()
                if (!isValidWord(word)) continue
                val count = line.substring(tab + 1).trim().toIntOrNull() ?: continue
                if (count <= 0) continue
                val existing = counts[word]
                if (existing == null || count > existing) counts[word] = count
            }
            if (counts.isEmpty()) return EMPTY
            val sorted = counts.entries.sortedBy { it.key }
            val words = Array(sorted.size) { sorted[it].key }
            val frequencies = IntArray(sorted.size) { sorted[it].value }
            return Dictionary(words, frequencies)
        }

        private fun isValidWord(word: String): Boolean {
            if (word.isEmpty() || word.length > MAX_WORD_LENGTH) return false
            if (word.first() == '\'' || word.last() == '\'') return false
            if (word.count { it == '\'' } > 1) return false
            for (c in word) {
                if (c == '\'') continue
                if (c !in 'a'..'z') return false
            }
            return true
        }

        /** All strings one edit (insert/delete/replace/transpose) away from [word]. */
        private fun edits1(word: String, alphabet: CharArray): Sequence<String> = sequence {
            val splits = ArrayList<Pair<String, String>>(word.length + 1)
            for (i in 0..word.length) splits.add(word.substring(0, i) to word.substring(i))
            for ((left, right) in splits) {
                if (right.length > 1) yield(left + right[1] + right[0] + right.substring(2))
            }
            for ((left, right) in splits) {
                if (right.isNotEmpty()) yield(left + right.substring(1))
            }
            for ((left, right) in splits) {
                for (c in alphabet) yield(left + c + right)
            }
            for ((left, right) in splits) {
                if (right.isNotEmpty()) {
                    for (c in alphabet) {
                        if (c != right[0]) yield(left + c + right.substring(1))
                    }
                }
            }
        }
    }
}
