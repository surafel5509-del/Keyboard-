package com.novakeyboard.data.dictionary

/** Merges the bundled dictionary with the user's personal/learned words. */
class CompositeWordSource(
    private val base: WordFrequencySource,
    private val user: WordFrequencySource,
) : WordFrequencySource {

    override val isReady: Boolean get() = base.isReady

    override fun frequency(word: String): Int {
        val userFrequency = user.frequency(word)
        return if (userFrequency > 0) userFrequency else base.frequency(word)
    }

    override fun prefixMatches(prefix: String, limit: Int): List<WordScore> {
        val combined = ArrayList<WordScore>(limit * 2)
        for (item in user.prefixMatches(prefix, limit)) if (combined.none { it.word == item.word }) combined.add(item)
        for (item in base.prefixMatches(prefix, limit)) if (combined.none { it.word == item.word }) combined.add(item)
        return combined.sortedByDescending { it.frequency }.take(limit)
    }

    override fun oneEditMatches(word: String, limit: Int): List<WordScore> {
        val combined = ArrayList<WordScore>(limit * 2)
        for (item in user.oneEditMatches(word, limit)) if (combined.none { it.word == item.word }) combined.add(item)
        for (item in base.oneEditMatches(word, limit)) if (combined.none { it.word == item.word }) combined.add(item)
        return combined.sortedByDescending { it.frequency }.take(limit)
    }
}
