package com.novakeyboard.domain.prediction

import com.novakeyboard.data.dictionary.BigramModel
import com.novakeyboard.data.dictionary.WordFrequencySource
import com.novakeyboard.data.dictionary.WordScore
import com.novakeyboard.utils.Words

/** One entry of the suggestion strip. */
data class Suggestion(
    val text: String,
    val kind: Kind,
    val frequency: Int = 0,
) {
    enum class Kind { PREFIX, NEXT_WORD, EMOJI }

    val isEmoji: Boolean get() = kind == Kind.EMOJI
}

/** Optional bridge to the offline emoji index. */
interface EmojiSuggester {
    /** The emoji whose name or keyword equals [word], or null. */
    fun emojiForWord(word: String): String?
}

/**
 * Offline suggestion engine: prefix completions from the bundled dictionary,
 * next-word prediction from the bundled bigram table, and emoji suggestions for
 * exact keyword matches. Runs on a background thread and never touches the
 * network.
 */
class PredictionEngine(
    private val source: WordFrequencySource,
    private val bigrams: BigramModel,
    private val emoji: EmojiSuggester? = null,
) {

    fun suggestions(
        currentWord: String,
        previousWord: String?,
        limit: Int = 3,
        predictionEnabled: Boolean = true,
        emojiEnabled: Boolean = true,
    ): List<Suggestion> {
        if (limit <= 0 || !source.isReady) return emptyList()
        val result = ArrayList<Suggestion>(limit + 1)

        if (currentWord.isNotEmpty()) {
            val prefix = currentWord.lowercase()
            for (item in source.prefixMatches(prefix, limit + 2)) {
                if (item.word == prefix) continue
                result.add(Suggestion(Words.matchCase(item.word, currentWord), Suggestion.Kind.PREFIX, item.frequency))
                if (result.size >= limit) break
            }
        }

        if (predictionEnabled && result.size < limit) {
            val context = if (currentWord.isEmpty()) previousWord else currentWord
            if (!context.isNullOrEmpty()) {
                val seen = HashSet<String>()
                for (suggestion in result) seen.add(suggestion.text.lowercase())
                for (item in bigrams.nextWords(context, limit + 4)) {
                    if (item.word == context.lowercase()) continue
                    if (!seen.add(item.word)) continue
                    result.add(Suggestion(Words.matchCase(item.word, context), Suggestion.Kind.NEXT_WORD, item.frequency))
                    if (result.size >= limit) break
                }
            }
        }

        if (emojiEnabled && currentWord.isNotEmpty()) {
            val glyph = emoji?.emojiForWord(currentWord)
            if (glyph != null) {
                result.add(0, Suggestion(glyph, Suggestion.Kind.EMOJI))
                if (result.size > limit) result.removeAt(result.size - 1)
            }
        }
        return result
    }

    /** Exposed for the suggestion strip long-press action. */
    fun frequencyOf(word: String): Int = source.frequency(word)

    /** Prefix matches only, used when the user taps a partially typed word. */
    fun completionsFor(word: String, limit: Int = 3): List<WordScore> =
        source.prefixMatches(word.lowercase(), limit)
}
