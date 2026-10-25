package com.novakeyboard.data.dictionary

import com.novakeyboard.data.prefs.KeyValueStore
import com.novakeyboard.utils.EditDistance
import com.novakeyboard.utils.Words

/**
 * Words the user explicitly added plus words learned from their typing.
 * Both live in private app storage and are never synced anywhere.
 */
class UserDictionary(private val store: KeyValueStore) : WordFrequencySource {

    private var cachedLearned: LinkedHashMap<String, Int>? = null
    private var cachedPersonal: LinkedHashSet<String>? = null

    override val isReady: Boolean = true

    // ---------------------------------------------------------------- reading

    fun learnedWords(): Map<String, Int> = learned().toMap()

    fun personalWords(): List<String> = personal().toList()

    fun learnedCount(): Int = learned().size

    fun isPersonal(word: String): Boolean = personal().contains(word.lowercase())

    fun isLearned(word: String): Boolean = learned().containsKey(word.lowercase())

    override fun frequency(word: String): Int {
        val key = word.lowercase()
        if (personal().contains(key)) return PERSONAL_FREQUENCY
        val learned = learned()[key] ?: return 0
        return LEARNED_BASE_FREQUENCY + learned * 40
    }

    override fun prefixMatches(prefix: String, limit: Int): List<WordScore> {
        if (prefix.isEmpty()) return emptyList()
        val query = prefix.lowercase()
        val matches = ArrayList<WordScore>()
        for (word in personal()) {
            if (word.startsWith(query) && word != query) matches.add(WordScore(word, PERSONAL_FREQUENCY))
        }
        for ((word, count) in learned()) {
            if (word.startsWith(query) && word != query) {
                matches.add(WordScore(word, LEARNED_BASE_FREQUENCY + count * 40))
            }
        }
        return matches.sortedByDescending { it.frequency }.take(limit)
    }

    override fun oneEditMatches(word: String, limit: Int): List<WordScore> {
        if (word.isEmpty()) return emptyList()
        val query = word.lowercase()
        val matches = ArrayList<WordScore>()
        for (candidate in personal()) {
            if (EditDistance.within(query, candidate, 1)) matches.add(WordScore(candidate, PERSONAL_FREQUENCY))
        }
        for ((candidate, count) in learned()) {
            if (EditDistance.within(query, candidate, 1)) {
                matches.add(WordScore(candidate, LEARNED_BASE_FREQUENCY + count * 40))
            }
        }
        return matches.sortedByDescending { it.frequency }.take(limit)
    }

    // ---------------------------------------------------------------- writing

    /** Adds a word to the personal dictionary, optionally remembering its case. */
    fun addPersonal(word: String) {
        val cleaned = sanitize(word) ?: return
        val personal = personal()
        if (personal.add(cleaned)) {
            persistPersonal(personal)
            unlearn(cleaned)
        }
    }

    fun removeWord(word: String) {
        val cleaned = word.lowercase()
        val personal = personal()
        if (personal.remove(cleaned)) persistPersonal(personal)
        val learned = learned()
        if (learned.remove(cleaned) != null) persistLearned(learned)
    }

    /** Records that the user typed [word]; stops after a sensible dictionary size. */
    fun learn(word: String) {
        val cleaned = sanitize(word) ?: return
        if (cleaned.length < 3) return
        if (personal().contains(cleaned)) return
        val learned = learned()
        val current = learned[cleaned] ?: 0
        learned[cleaned] = (current + 1).coerceAtMost(99)
        while (learned.size > MAX_LEARNED) {
            var weakest: String? = null
            var weakestCount = Int.MAX_VALUE
            for ((candidate, count) in learned) {
                if (count < weakestCount) {
                    weakest = candidate
                    weakestCount = count
                }
            }
            if (weakest == null) break
            learned.remove(weakest)
        }
        persistLearned(learned)
    }

    fun unlearn(word: String) {
        val cleaned = word.lowercase()
        val learned = learned()
        if (learned.remove(cleaned) != null) persistLearned(learned)
    }

    fun clearLearned() {
        cachedLearned = LinkedHashMap()
        store.remove(KEY_LEARNED)
    }

    fun clearAll() {
        cachedLearned = LinkedHashMap()
        cachedPersonal = LinkedHashSet()
        store.remove(KEY_LEARNED)
        store.remove(KEY_PERSONAL)
    }

    // ------------------------------------------------------------- internals

    private fun learned(): LinkedHashMap<String, Int> {
        cachedLearned?.let { return it }
        val map = LinkedHashMap<String, Int>()
        val raw = store.getString(KEY_LEARNED, null)
        if (!raw.isNullOrEmpty()) {
            for (line in raw.split('\n')) {
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val word = line.substring(0, tab)
                val count = line.substring(tab + 1).toIntOrNull() ?: 1
                if (word.isNotEmpty()) map[word] = count
            }
        }
        cachedLearned = map
        return map
    }

    private fun personal(): LinkedHashSet<String> {
        cachedPersonal?.let { return it }
        val set = LinkedHashSet<String>()
        val raw = store.getString(KEY_PERSONAL, null)
        if (!raw.isNullOrEmpty()) {
            for (line in raw.split('\n')) {
                val word = line.trim()
                if (word.isNotEmpty()) set.add(word)
            }
        }
        cachedPersonal = set
        return set
    }

    private fun persistLearned(map: Map<String, Int>) {
        val builder = StringBuilder(map.size * 12)
        for ((word, count) in map) {
            builder.append(word).append('\t').append(count).append('\n')
        }
        store.putString(KEY_LEARNED, builder.toString())
        cachedLearned = LinkedHashMap(map)
    }

    private fun persistPersonal(set: Set<String>) {
        store.putString(KEY_PERSONAL, set.joinToString("\n"))
        cachedPersonal = LinkedHashSet(set)
    }

    private fun sanitize(word: String): String? {
        val trimmed = word.trim()
        if (trimmed.length < 2 || trimmed.length > 32) return null
        if (trimmed.any { !Words.isWordChar(it) }) return null
        return trimmed.lowercase()
    }

    companion object {
        const val KEY_LEARNED = "user_dictionary.learned"
        const val KEY_PERSONAL = "user_dictionary.personal"
        const val MAX_LEARNED = 1200
        private const val PERSONAL_FREQUENCY = 200000
        private const val LEARNED_BASE_FREQUENCY = 2000
    }
}
