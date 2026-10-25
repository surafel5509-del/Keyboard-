package com.novakeyboard.domain.language

/**
 * Latin → Geez transliteration.
 *
 * Scheme (documented in the app):
 *  - Consonants: h l H m s r S q b v t c x n N a k K w A z Z y d j g G T C P Y F f p
 *    where capitals pick the "other" consonant of a pair (S = ሸ, c = ቸ, T = ጠ,
 *    C = ጨ, P = ጰ, Y = ጸ, F = ፀ, N = ኘ, G = ጘ, K = ኸ, H = ሐ, x = ኀ).
 *  - Digraphs win over single letters: sh, ch, ts, ny, ng, kh, zh.
 *  - Vowels: e (1st order), u (2nd), i (3rd), a (4th), o (7th), nothing (6th);
 *    "ie" after a consonant gives the 5th order (ሴ) and capital A is an alias.
 *  - "w" after q/k/g/x produces the labialised syllable: kwa → ኳ.
 *  - Punctuation: , ; : ? → ፣ ፤ ፥ ፧.
 *
 * [transliterate] is a pure function: the IME keeps the Latin word it is
 * composing and re-renders the whole word on every keystroke, which keeps the
 * on-screen text and the buffer in sync without a fragile state machine.
 */
object GeezTransliterator {

    private val vowels = charArrayOf('a', 'e', 'i', 'o', 'u', 'A')
    private val consonantKeys: List<String> = (
        GeezAlphabet.digraphs.keys + GeezAlphabet.series.map { it.key }
        ).sortedWith(compareByDescending<String> { it.length }.thenBy { it })

    /** The parsed latin → geez units of [latin], in order. */
    fun units(latin: CharSequence): List<Pair<String, String>> {
        val result = ArrayList<Pair<String, String>>()
        var index = 0
        while (index < latin.length) {
            val matched = matchConsonant(latin, index)
            if (matched != null) {
                val start = index
                var cursor = index + matched.second.length
                var labialized = false
                if (cursor < latin.length && latin[cursor] == 'w' && matched.first.hasLabialized) {
                    labialized = true
                    cursor++
                }
                var order = GeezAlphabet.ORDER_6
                if (cursor + 1 < latin.length && latin[cursor] == 'i' && latin[cursor + 1] == 'e') {
                    order = GeezAlphabet.ORDER_5
                    cursor += 2
                } else if (cursor < latin.length && isVowel(latin[cursor])) {
                    order = GeezAlphabet.orderForVowel(latin[cursor].toString())
                    cursor++
                }
                val syllable = if (labialized) {
                    matched.first.labializedLetter(order) ?: matched.first.letter(order)
                } else {
                    matched.first.letter(order)
                }
                result.add(latin.substring(start, cursor) to syllable)
                index = cursor
                continue
            }
            val c = latin[index]
            val geez = when {
                isVowel(c) -> GeezAlphabet.vowelLetter(c.toString())
                c == '\'' || c == '\u2019' -> GeezAlphabet.find("A")?.letter(GeezAlphabet.ORDER_6) ?: "ዕ"
                else -> GeezAlphabet.punctuation[c] ?: c.toString()
            }
            result.add(c.toString() to geez)
            index++
        }
        return result
    }

    fun transliterate(latin: CharSequence): String {
        if (latin.isEmpty()) return ""
        val out = StringBuilder(latin.length * 2)
        var index = 0
        while (index < latin.length) {
            val matched = matchConsonant(latin, index)
            if (matched != null) {
                val series = matched.first
                var cursor = index + matched.second.length
                var labialized = false
                if (cursor < latin.length && latin[cursor] == 'w' && series.hasLabialized) {
                    labialized = true
                    cursor++
                }
                var order = GeezAlphabet.ORDER_6
                if (cursor + 1 < latin.length && latin[cursor] == 'i' && latin[cursor + 1] == 'e') {
                    order = GeezAlphabet.ORDER_5
                    cursor += 2
                } else if (cursor < latin.length && isVowel(latin[cursor])) {
                    order = GeezAlphabet.orderForVowel(latin[cursor].toString())
                    cursor++
                }
                val syllable = if (labialized) {
                    series.labializedLetter(order) ?: series.letter(order)
                } else {
                    series.letter(order)
                }
                out.append(syllable)
                index = cursor
                continue
            }
            val c = latin[index]
            if (isVowel(c)) {
                out.append(GeezAlphabet.vowelLetter(c.toString()))
                index++
                continue
            }
            if (c == '\'' || c == '’') {
                // Apostrophe is the ʿayn consonant.
                out.append(GeezAlphabet.find("A")?.letter(GeezAlphabet.ORDER_6) ?: "ዕ")
                index++
                continue
            }
            out.append(GeezAlphabet.punctuation[c] ?: c.toString())
            index++
        }
        return out.toString()
    }

    private fun isVowel(c: Char): Boolean = vowels.contains(c)

    private fun matchConsonant(text: CharSequence, index: Int): Pair<GeezAlphabet.Series, String>? {
        for (key in consonantKeys) {
            if (key == "a") continue // "a" is the vowel/alef key, handled separately
            if (!text.regionMatches(index, key, 0, key.length)) continue
            val series = if (key.length == 1) {
                GeezAlphabet.find(key)
            } else {
                GeezAlphabet.digraphs[key]?.let { GeezAlphabet.find(it) }
            }
            if (series != null) return series to key
        }
        return null
    }

    /** True when the text contains only characters the transliterator understands. */
    fun isTransliterable(text: CharSequence): Boolean = text.all {
        it.isLetter() || it == '\'' || it == '’' || it in GeezAlphabet.punctuation.keys
    }
}
