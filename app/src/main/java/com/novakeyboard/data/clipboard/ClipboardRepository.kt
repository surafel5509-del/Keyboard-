package com.novakeyboard.data.clipboard

import com.novakeyboard.data.prefs.KeyValueStore
import java.util.Base64

/** One saved clipboard item. Text never leaves this list. */
data class ClipEntry(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val pinned: Boolean,
)

/**
 * Local clipboard history.
 *
 * Privacy rules enforced here:
 *  - the history is empty until the user saves something (or opts into capture),
 *  - items are capped by count and by text length,
 *  - nothing is written to the system clipboard; entries can only be inserted,
 *  - storage is the app's private SharedPreferences file.
 */
class ClipboardRepository(private val store: KeyValueStore) {

    fun entries(): List<ClipEntry> = decode(store.getString(KEY, null))

    fun count(): Int = entries().size

    /**
     * Adds [text] to the top of the history. Returns false when the text is
     * empty, too long, or when clipboard history is disabled by the caller.
     */
    fun save(text: String, maxEntries: Int, now: Long = System.currentTimeMillis()): Boolean {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return false
        if (cleaned.length > MAX_TEXT_LENGTH) return false
        val current = entries().toMutableList()
        val duplicate = current.firstOrNull { it.text == cleaned }
        if (duplicate != null) {
            current.removeAll { it.text == cleaned }
            current.add(0, duplicate.copy(createdAt = now))
        } else {
            current.add(0, ClipEntry(id = nextId(current), text = cleaned, createdAt = now, pinned = false))
        }
        val trimmed = trim(current, maxEntries)
        store.putString(KEY, encode(trimmed))
        return true
    }

    fun remove(id: Long) {
        val current = entries().filterNot { it.id == id }
        store.putString(KEY, encode(current))
    }

    fun setPinned(id: Long, pinned: Boolean) {
        val current = entries().map { if (it.id == id) it.copy(pinned = pinned) else it }
        store.putString(KEY, encode(current))
    }

    fun clear() {
        store.remove(KEY)
    }

    /** Enforces the configured maximum after the user changed the setting. */
    fun trimTo(maxEntries: Int) {
        val current = entries()
        val trimmed = trim(current, maxEntries)
        if (trimmed.size != current.size) store.putString(KEY, encode(trimmed))
    }

    /** Keeps the list within [maxEntries], dropping the oldest unpinned entries. */
    private fun trim(entries: List<ClipEntry>, maxEntries: Int): List<ClipEntry> {
        val limit = maxEntries.coerceIn(1, 100)
        if (entries.size <= limit) return entries
        val result = ArrayList<ClipEntry>(entries)
        while (result.size > limit) {
            val index = result.indexOfLast { !it.pinned }
            if (index < 0) break
            result.removeAt(index)
        }
        return result
    }

    private fun nextId(entries: List<ClipEntry>): Long =
        (entries.maxOfOrNull { it.id } ?: 0L) + 1L

    companion object {
        const val KEY = "clipboard.entries"
        const val MAX_TEXT_LENGTH = 2000
        const val DEFAULT_MAX = 20

        fun encode(entries: List<ClipEntry>): String {
            val builder = StringBuilder(entries.size * 64)
            for (entry in entries) {
                val text = Base64.getEncoder().encodeToString(entry.text.toByteArray(Charsets.UTF_8))
                builder.append(entry.id).append('|')
                    .append(entry.createdAt).append('|')
                    .append(if (entry.pinned) '1' else '0').append('|')
                    .append(text).append('\n')
            }
            return builder.toString()
        }

        fun decode(raw: String?): List<ClipEntry> {
            if (raw.isNullOrEmpty()) return emptyList()
            val result = ArrayList<ClipEntry>()
            for (line in raw.split('\n')) {
                if (line.isEmpty()) continue
                val first = line.indexOf('|')
                if (first <= 0) continue
                val second = line.indexOf('|', first + 1)
                if (second <= first) continue
                val third = line.indexOf('|', second + 1)
                if (third <= second) continue
                val id = line.substring(0, first).toLongOrNull() ?: continue
                val createdAt = line.substring(first + 1, second).toLongOrNull() ?: continue
                val pinned = line.substring(second + 1, third) == "1"
                val text = try {
                    String(Base64.getDecoder().decode(line.substring(third + 1)), Charsets.UTF_8)
                } catch (e: IllegalArgumentException) {
                    continue
                }
                if (text.isNotEmpty()) result.add(ClipEntry(id, text, createdAt, pinned))
            }
            return result
        }
    }
}
