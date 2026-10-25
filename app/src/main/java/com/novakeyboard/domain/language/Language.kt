package com.novakeyboard.domain.language

import com.novakeyboard.data.prefs.LanguageIds

/** Languages the keyboard can type. Adding a language only needs a new entry. */
enum class Language(
    val id: String,
    val displayName: String,
    val localeTag: String,
) {
    ENGLISH(LanguageIds.ENGLISH, "English", "en-US"),
    AMHARIC(LanguageIds.AMHARIC, "አማርኛ", "am-ET"),
    ;

    val isRightToLeft: Boolean get() = false

    companion object {
        fun fromId(id: String?): Language? = entries.firstOrNull { it.id == id }

        fun enabled(ids: Set<String>): List<Language> {
            val result = entries.filter { ids.contains(it.id) }
            return if (result.isEmpty()) listOf(ENGLISH) else result
        }
    }
}

/** How Amharic text is produced. */
enum class AmharicInputMode(val id: String) {
    /** Latin keys are transliterated into Geez syllables live. */
    TRANSLITERATION("transliteration"),

    /** A direct Geez keyboard where each key shows an Ethiopic character. */
    GEEZ("geez"),
    ;

    companion object {
        fun fromId(id: String?): AmharicInputMode = entries.firstOrNull { it.id == id } ?: TRANSLITERATION
    }
}
