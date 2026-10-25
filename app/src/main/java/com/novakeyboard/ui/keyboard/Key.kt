package com.novakeyboard.ui.keyboard

import com.novakeyboard.domain.language.Language

enum class KeyType {
    CHAR,
    SHIFT,
    BACKSPACE,
    ENTER,
    SPACE,
    TO_SYMBOLS,
    TO_SYMBOLS_2,
    TO_LETTERS,
    LANGUAGE,
    EMOJI,
    CLIPBOARD,
    SETTINGS,
    SPACER,
}

enum class KeyIcon {
    NONE,
    SHIFT,
    SHIFT_CAPS,
    BACKSPACE,
    ENTER,
    SEARCH,
    SEND,
    NEXT,
    DONE,
    GO,
    GLOBE,
    EMOJI,
    CLIPBOARD,
    GEAR,
    PALETTE,
}

enum class LayoutKind {
    LETTERS,
    SYMBOLS,
    SYMBOLS_2,
    NUMBER,
    PHONE,
    EMAIL,
    URI,
    GEEZ_LETTERS,
}

/** One key on the on-screen keyboard. */
data class Key(
    val type: KeyType,
    val label: String = "",
    val output: String = "",
    val shiftedOutput: String? = null,
    val alternates: List<String> = emptyList(),
    val weight: Float = 1f,
    val icon: KeyIcon = KeyIcon.NONE,
    val topLabel: String? = null,
    val repeatable: Boolean = false,
    val gestureHint: String? = null,
) {
    val isCharacter: Boolean get() = type == KeyType.CHAR

    // Uppercase forms are precomputed once per layout so that drawing a frame
    // allocates nothing (the keyboard redraws on every press).
    private val upperOutput: String = shiftedOutput ?: if (output.isEmpty()) output else output.uppercase()
    private val upperLabel: String = if (label.isEmpty()) label else label.uppercase()
    private val upperAlternates: List<String> by lazy {
        if (alternates.isEmpty()) alternates else alternates.map { it.uppercase() }
    }

    fun outputFor(uppercase: Boolean): String = if (uppercase) upperOutput else output

    fun labelFor(uppercase: Boolean): String {
        if (!uppercase || !isCharacter) return label
        return upperLabel
    }

    fun alternatesFor(uppercase: Boolean): List<String> =
        if (uppercase) upperAlternates else alternates

    companion object {
        fun char(value: String, alternates: List<String> = emptyList(), topLabel: String? = null): Key =
            Key(KeyType.CHAR, label = value, output = value, alternates = alternates, topLabel = topLabel)

        fun shift(): Key = Key(KeyType.SHIFT, label = "", icon = KeyIcon.SHIFT, weight = 1.5f)
        fun backspace(): Key = Key(KeyType.BACKSPACE, icon = KeyIcon.BACKSPACE, weight = 1.5f, repeatable = true)
        fun enter(icon: KeyIcon = KeyIcon.ENTER): Key = Key(KeyType.ENTER, icon = icon, weight = 1.6f)
        fun space(): Key = Key(KeyType.SPACE, label = "", weight = 4.4f)
        fun toSymbols(): Key = Key(KeyType.TO_SYMBOLS, label = "123")
        fun toSymbols2(): Key = Key(KeyType.TO_SYMBOLS_2, label = "=\\<")
        fun toLetters(): Key = Key(KeyType.TO_LETTERS, label = "ABC")
        fun language(): Key = Key(KeyType.LANGUAGE, icon = KeyIcon.GLOBE, weight = 1.2f)
        fun emoji(): Key = Key(KeyType.EMOJI, icon = KeyIcon.EMOJI, weight = 1.2f)
        fun clipboard(): Key = Key(KeyType.CLIPBOARD, icon = KeyIcon.CLIPBOARD, weight = 1.2f)
        fun settings(): Key = Key(KeyType.SETTINGS, icon = KeyIcon.GEAR, weight = 1.2f)
        fun spacer(weight: Float = 0.5f): Key = Key(KeyType.SPACER, weight = weight)
    }
}

data class KeyRow(val keys: List<Key>)

data class KeyLayout(
    val kind: LayoutKind,
    val language: Language,
    val rows: List<KeyRow>,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}
