package com.novakeyboard.data.dictionary

import java.io.InputStream

/**
 * Offline next-word model. The asset is a compact bigram count list produced
 * from a public-domain English corpus: `previous<TAB>next<TAB>count`.
 *
 * It is a statistical n-gram table, not an "AI" model: it only knows which word
 * tended to follow another word in the bundled corpus.
 */
class BigramModel private constructor(
    private val table: HashMap<String, Array<WordScore>>,
    val size: Int,
) {

    fun nextWords(previous: String, limit: Int): List<WordScore> {
        if (limit <= 0) return emptyList()
        val key = previous.lowercase()
        val entries = table[key] ?: return emptyList()
        return if (entries.size <= limit) entries.toList() else entries.take(limit)
    }

    fun count(previous: String, next: String): Int {
        val entries = table[previous.lowercase()] ?: return 0
        val wanted = next.lowercase()
        for (entry in entries) if (entry.word == wanted) return entry.frequency
        return 0
    }

    companion object {
        val EMPTY = BigramModel(HashMap(), 0)
        private const val MAX_FOLLOWERS = 8

        fun parse(input: InputStream): BigramModel =
            input.bufferedReader().useLines { parseLines(it) }

        fun load(input: InputStream): BigramModel = parse(input)

        fun parseLines(lines: Sequence<String>): BigramModel {
            val builders = HashMap<String, MutableList<WordScore>>(16384)
            var total = 0
            for (line in lines) {
                if (line.isEmpty()) continue
                val first = line.indexOf('\t')
                if (first <= 0) continue
                val second = line.indexOf('\t', first + 1)
                if (second <= first) continue
                val previous = line.substring(0, first).lowercase()
                val next = line.substring(first + 1, second).lowercase()
                val count = line.substring(second + 1).trim().toIntOrNull() ?: continue
                if (count <= 0 || previous.isEmpty() || next.isEmpty()) continue
                builders.getOrPut(previous) { ArrayList(4) }.add(WordScore(next, count))
                total++
            }
            if (builders.isEmpty()) return EMPTY
            val table = HashMap<String, Array<WordScore>>(builders.size * 2)
            for ((key, list) in builders) {
                list.sortByDescending { it.frequency }
                val trimmed = if (list.size > MAX_FOLLOWERS) ArrayList(list.subList(0, MAX_FOLLOWERS)) else list
                table[key] = trimmed.toTypedArray()
            }
            return BigramModel(table, total)
        }
    }
}
