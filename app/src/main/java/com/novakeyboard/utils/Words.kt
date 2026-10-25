package com.novakeyboard.utils

/** Word-boundary helpers shared by the IME and the autocorrect engine. */
object Words {

    private const val MAX_WORD_LOOKBACK = 48

    fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\'' || c == '’'

    /** The word directly before the cursor, or an empty string. */
    fun wordBefore(text: CharSequence?): String {
        if (text.isNullOrEmpty()) return ""
        var end = text.length
        while (end > 0 && !isWordChar(text[end - 1])) {
            // Allow a single trailing apostrophe inside a word ("don't").
            if (text[end - 1] == '\'' || text[end - 1] == '’') break
            end--
        }
        var start = end
        while (start > 0 && isWordChar(text[start - 1]) && end - start < MAX_WORD_LOOKBACK) start--
        return text.subSequence(start, end).toString()
    }

    /** The word before [wordBefore] (used for next-word prediction). */
    fun previousWord(text: CharSequence?): String? {
        if (text.isNullOrEmpty()) return null
        var index = text.length
        // Skip the current word and the separator before it.
        while (index > 0 && isWordChar(text[index - 1])) index--
        while (index > 0 && !isWordChar(text[index - 1])) index--
        if (index == 0) return null
        val current = text.subSequence(0, index).toString()
        val word = wordBefore(current)
        return word.ifEmpty { null }
    }

    fun capitalize(word: String): String =
        if (word.isEmpty()) word else word[0].uppercaseChar() + word.substring(1)

    fun isCapitalized(word: String): Boolean = word.isNotEmpty() && word[0].isUpperCase()

    /** Applies the capitalisation of [reference] to [word]. */
    fun matchCase(word: String, reference: String): String =
        if (reference.isNotEmpty() && reference[0].isUpperCase()) capitalize(word) else word

    /** True when the text looks like a URL, e-mail address or handle. */
    fun looksLikeUrlOrEmail(word: String): Boolean {
        if (word.contains('@') || word.contains("://") || word.contains('.')) return true
        if (word.startsWith("www", ignoreCase = true)) return true
        return false
    }

    /** True when every character is a letter or an apostrophe. */
    fun isPlainWord(word: String): Boolean =
        word.isNotEmpty() && word.all { isWordChar(it) }

    /**
     * Whether the next character should be upper case, based on the text before
     * the cursor (start of field or after sentence punctuation).
     */
    fun shouldCapitalize(text: CharSequence?): Boolean {
        if (text.isNullOrEmpty()) return true
        var index = text.length - 1
        while (index >= 0 && text[index].isWhitespace()) index--
        if (index < 0) return true
        val c = text[index]
        return c == '.' || c == '!' || c == '?' || c == '።' || c == '፧' || c == '\n'
    }

    /** Splits a text into words for learning and personal-dictionary bookkeeping. */
    fun splitWords(text: CharSequence): List<String> {
        val result = ArrayList<String>()
        val builder = StringBuilder()
        for (c in text) {
            if (isWordChar(c)) {
                builder.append(c)
            } else if (builder.isNotEmpty()) {
                result.add(builder.toString())
                builder.setLength(0)
            }
        }
        if (builder.isNotEmpty()) result.add(builder.toString())
        return result
    }
}
