package com.novakeyboard.ime

import com.novakeyboard.security.EnterAction
import com.novakeyboard.utils.Words

/**
 * Pure state for one editing session: the word being typed, the previous word
 * (used for next-word prediction) and the sentence-capitalisation flag. Keeping
 * this free of Android types means the typing behaviour is unit-testable.
 */
class InputSession {

    var currentWord: String = ""
        private set
    var previousWord: String? = null
        private set
    var autoCapitalise: Boolean = true
        private set
    var lastCorrection: Pair<String, String>? = null
        private set

    fun setAutoCapitalise(value: Boolean) {
        autoCapitalise = value
    }

    fun setPreviousWord(word: String?) {
        previousWord = word
    }

    fun reset() {
        currentWord = ""
        previousWord = null
        autoCapitalise = true
        lastCorrection = null
    }

    /** Records a committed character and updates capitalisation. */
    fun onCharacterCommitted(text: String) {
        if (text.isEmpty()) return
        val first = text[0]
        if (isWordChar(first)) {
            currentWord += text
            lastCorrection = null
            autoCapitalise = false
        } else {
            finishWord()
            currentWord = ""
            if (first == '.' || first == '!' || first == '?' || first == '።' || first == '፧') {
                autoCapitalise = true
            }
        }
    }

    /** Called at a word boundary (space / enter / punctuation). */
    fun finishWord() {
        if (currentWord.isNotEmpty()) previousWord = currentWord
        currentWord = ""
    }

    fun onSpaceCommitted() {
        // Keep the sentence-start flag: after "." or "?" the next word still
        // needs a capital even though a space was just committed.
        finishWord()
    }

    fun onEnterCommitted() {
        finishWord()
        currentWord = ""
        autoCapitalise = true
    }

    fun onBackspace() {
        if (currentWord.isNotEmpty()) currentWord = currentWord.dropLast(1)
    }

    fun markCorrection(typed: String, replacement: String) {
        lastCorrection = typed to replacement
        currentWord = replacement
        autoCapitalise = false
    }

    fun clearCorrection(): Pair<String, String>? {
        val correction = lastCorrection
        lastCorrection = null
        if (correction != null) currentWord = correction.first
        return correction
    }

    /** Re-synchronises with the editor after the user moved the cursor. */
    fun syncFromText(textBeforeCursor: CharSequence?) {
        currentWord = Words.wordBefore(textBeforeCursor)
        previousWord = Words.previousWord(textBeforeCursor)
        lastCorrection = null
        autoCapitalise = Words.shouldCapitalize(textBeforeCursor)
    }

    companion object {
        fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\'' || c == '’'

        fun enterActionCode(action: EnterAction): Int = when (action) {
            EnterAction.GO -> 2
            EnterAction.SEARCH -> 3
            EnterAction.SEND -> 4
            EnterAction.NEXT -> 5
            EnterAction.DONE -> 6
            EnterAction.PREVIOUS -> 7
            else -> 0
        }
    }
}
