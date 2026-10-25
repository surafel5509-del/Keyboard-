package com.novakeyboard.ui.emoji

import com.novakeyboard.data.prefs.KeyValueStore
import com.novakeyboard.domain.prediction.EmojiSuggester

data class EmojiEntry(
    val emoji: String,
    val name: String,
    val keywords: List<String>,
    val category: String,
)

data class EmojiCategory(
    val id: String,
    val title: String,
    val sourceName: String,
    val sample: String,
)

/**
 * Fully offline emoji index parsed from the bundled `emoji/emoji.tsv` asset
 * (`emoji<TAB>name<TAB>keywords<TAB>category`). Search is a simple
 * case-insensitive match over the name and keywords — no network involved.
 */
class EmojiIndex(val entries: List<EmojiEntry>) : EmojiSuggester {

    val size: Int get() = entries.size

    fun categoryEntries(categoryId: String): List<EmojiEntry> = byCategory(categoryId)

    fun byCategory(categoryId: String): List<EmojiEntry> {
        val source = CATEGORIES.firstOrNull { it.id == categoryId }?.sourceName ?: return emptyList()
        return entries.filter { it.category == source }
    }

    fun search(query: String, limit: Int = 60): List<EmojiEntry> {
        val cleaned = query.trim().lowercase()
        if (cleaned.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<EmojiEntry, Int>>()
        for (entry in entries) {
            val score = score(entry, cleaned)
            if (score > 0) scored.add(entry to score)
        }
        scored.sortByDescending { it.second }
        return scored.take(limit).map { it.first }
    }

    private fun score(entry: EmojiEntry, query: String): Int {
        val name = entry.name.lowercase()
        if (name == query) return 100
        if (name.startsWith(query)) return 80
        if (name.contains(query)) return 60
        for (keyword in entry.keywords) {
            val lower = keyword.lowercase()
            if (lower == query) return 50
            if (lower.startsWith(query)) return 40
            if (lower.contains(query)) return 20
        }
        return 0
    }

    override fun emojiForWord(word: String): String? {
        val cleaned = word.trim().lowercase()
        if (cleaned.length < 2) return null
        for (entry in entries) {
            if (entry.name.equals(cleaned, ignoreCase = true)) return entry.emoji
        }
        for (entry in entries) {
            if (entry.keywords.any { it.equals(cleaned, ignoreCase = true) }) return entry.emoji
        }
        return null
    }

    companion object {
        val CATEGORIES: List<EmojiCategory> = listOf(
            EmojiCategory("smileys", "Smileys & emotion", "Smileys & Emotion", "\uD83D\uDE00"),
            EmojiCategory("people", "People & gestures", "People & Body", "\uD83D\uDC4D"),
            EmojiCategory("animals", "Animals & nature", "Animals & Nature", "\uD83D\uDC36"),
            EmojiCategory("food", "Food & drink", "Food & Drink", "\uD83C\uDF55"),
            EmojiCategory("activities", "Activities & sports", "Activities", "\u26BD"),
            EmojiCategory("travel", "Travel & places", "Travel & Places", "\u2708"),
            EmojiCategory("objects", "Objects", "Objects", "\uD83D\uDCA1"),
            EmojiCategory("symbols", "Symbols", "Symbols", "\u2764"),
            EmojiCategory("flags", "Flags", "Flags", "\uD83C\uDFF3"),
        )

        val EMPTY = EmojiIndex(emptyList())

        fun parse(lines: Sequence<String>): EmojiIndex {
            val entries = ArrayList<EmojiEntry>(2048)
            for (line in lines) {
                if (line.isEmpty()) continue
                val parts = line.split('\t')
                if (parts.size < 4) continue
                val emoji = parts[0].trim()
                if (emoji.isEmpty()) continue
                // Bundled layout: emoji, short name, name, category, keywords
                val shortName = parts[1].trim()
                val name = parts[2].trim()
                val category = parts[3].trim()
                val keywords = if (parts.size >= 5) parts[4].trim() else ""
                val keywordList = (shortName + " " + keywords)
                    .split(' ', ',')
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                entries.add(
                    EmojiEntry(
                        emoji = emoji,
                        name = name.ifEmpty { shortName },
                        keywords = keywordList,
                        category = category,
                    ),
                )
            }
            return EmojiIndex(entries)
        }
    }
}

/** Recently used emoji, capped and persisted in private storage. */
class EmojiRecentsStore(private val store: KeyValueStore) {

    fun recents(): List<String> {
        val raw = store.getString(KEY, null) ?: return emptyList()
        return raw.split('\u0001').filter { it.isNotEmpty() }.take(MAX)
    }

    fun markUsed(emoji: String) {
        if (emoji.isEmpty()) return
        val current = recents().toMutableList()
        current.remove(emoji)
        current.add(0, emoji)
        while (current.size > MAX) current.removeAt(current.size - 1)
        store.putString(KEY, current.joinToString("\u0001"))
    }

    fun clear() = store.remove(KEY)

    companion object {
        const val KEY = "emoji.recents"
        const val MAX = 36
    }
}
