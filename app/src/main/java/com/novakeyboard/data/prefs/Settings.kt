package com.novakeyboard.data.prefs

/** Every persisted preference key. */
object SettingsKeys {
    const val THEME = "theme.preset"
    const val CUSTOM_BACKGROUND = "theme.custom.background"
    const val CUSTOM_KEY = "theme.custom.key"
    const val CUSTOM_TEXT = "theme.custom.text"
    const val CUSTOM_ACCENT = "theme.custom.accent"
    const val KEY_RADIUS = "appearance.key.radius"
    const val KEY_GAP = "appearance.key.gap"
    const val KEY_HEIGHT = "appearance.key.height"
    const val OPACITY = "appearance.opacity"
    const val BORDERS = "appearance.borders"
    const val NUMBER_ROW = "appearance.number.row"
    const val SUGGESTIONS = "appearance.suggestions"
    const val TOOLBAR = "appearance.toolbar"
    const val SOUND = "typing.sound"
    const val SOUND_VOLUME = "typing.sound.volume"
    const val HAPTICS = "typing.haptics"
    const val AUTO_CAPITALIZE = "typing.auto.capitalize"
    const val AUTO_CORRECT = "typing.auto.correct"
    const val PREDICTION = "typing.prediction"
    const val LONG_PRESS = "typing.long.press"
    const val SPACE_CURSOR = "typing.space.cursor"
    const val LEARNING = "typing.learning"
    const val EMOJI_SUGGESTIONS = "typing.emoji.suggestions"
    const val CLIPBOARD_ENABLED = "privacy.clipboard.enabled"
    const val CLIPBOARD_CAPTURE = "privacy.clipboard.capture"
    const val CLIPBOARD_MAX = "privacy.clipboard.max"
    const val LANGUAGES = "language.enabled"
    const val AMHARIC_MODE = "language.amharic.mode"
    const val SETUP_DONE = "setup.done"

    val all: Set<String> = setOf(
        THEME, CUSTOM_BACKGROUND, CUSTOM_KEY, CUSTOM_TEXT, CUSTOM_ACCENT,
        KEY_RADIUS, KEY_GAP, KEY_HEIGHT, OPACITY, BORDERS, NUMBER_ROW, SUGGESTIONS, TOOLBAR,
        SOUND, SOUND_VOLUME, HAPTICS, AUTO_CAPITALIZE, AUTO_CORRECT, PREDICTION, LONG_PRESS,
        SPACE_CURSOR, LEARNING, EMOJI_SUGGESTIONS, CLIPBOARD_ENABLED, CLIPBOARD_CAPTURE,
        CLIPBOARD_MAX, LANGUAGES, AMHARIC_MODE, SETUP_DONE,
    )
}

object ThemeIds {
    const val MIDNIGHT = "midnight"
    const val OCEAN = "ocean"
    const val VIOLET = "violet"
    const val WHITE = "pure_white"
    const val GRAY = "minimal_gray"
    const val FOREST = "forest"
}

object LanguageIds {
    const val ENGLISH = "en"
    const val AMHARIC = "am"
}

/** Immutable snapshot of all user preferences. */
data class Settings(
    val themePresetId: String = ThemeIds.MIDNIGHT,
    val customBackground: Int? = null,
    val customKey: Int? = null,
    val customText: Int? = null,
    val customAccent: Int? = null,
    val keyRadiusDp: Float = 12f,
    val keyGapDp: Float = 3f,
    val keyHeightScale: Float = 1f,
    val backgroundOpacity: Float = 1f,
    val keyBorders: Boolean = false,
    val numberRow: Boolean = false,
    val showSuggestions: Boolean = true,
    val showToolbar: Boolean = true,
    val soundEnabled: Boolean = true,
    val soundVolume: Float = 0.6f,
    val hapticsEnabled: Boolean = true,
    val autoCapitalize: Boolean = true,
    val autoCorrect: Boolean = true,
    val nextWordPrediction: Boolean = true,
    val longPressAlternates: Boolean = true,
    val spacebarCursor: Boolean = true,
    val learnWords: Boolean = true,
    val emojiSuggestions: Boolean = true,
    val clipboardEnabled: Boolean = true,
    val clipboardCapture: Boolean = false,
    val clipboardMaxEntries: Int = 20,
    val languages: List<String> = listOf(LanguageIds.ENGLISH),
    val amharicMode: String = AMHARIC_TRANSLITERATION,
    val setupDone: Boolean = false,
) {
    val clipboardLimit: Int get() = clipboardMaxEntries.coerceIn(MIN_CLIPBOARD, MAX_CLIPBOARD)

    companion object {
        const val AMHARIC_TRANSLITERATION = "transliteration"
        const val AMHARIC_GEEZ = "geez"

        /** Alias kept for readability at call sites. */
        const val AMHARIC_PHONETIC = AMHARIC_TRANSLITERATION

        const val MIN_CLIPBOARD = 5
        const val MAX_CLIPBOARD = 100
    }
}

/** Reads and writes [Settings] through a [KeyValueStore]. */
class SettingsRepository(private val store: KeyValueStore) {

    fun current(): Settings = load()

    fun load(): Settings {
        val defaults = Settings()
        val languageList = store.getString(SettingsKeys.LANGUAGES, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: defaults.languages
        return Settings(
            themePresetId = store.getString(SettingsKeys.THEME, defaults.themePresetId)
                ?: defaults.themePresetId,
            customBackground = readColor(SettingsKeys.CUSTOM_BACKGROUND),
            customKey = readColor(SettingsKeys.CUSTOM_KEY),
            customText = readColor(SettingsKeys.CUSTOM_TEXT),
            customAccent = readColor(SettingsKeys.CUSTOM_ACCENT),
            keyRadiusDp = store.getFloat(SettingsKeys.KEY_RADIUS, defaults.keyRadiusDp),
            keyGapDp = store.getFloat(SettingsKeys.KEY_GAP, defaults.keyGapDp),
            keyHeightScale = store.getFloat(SettingsKeys.KEY_HEIGHT, defaults.keyHeightScale),
            backgroundOpacity = store.getFloat(SettingsKeys.OPACITY, defaults.backgroundOpacity),
            keyBorders = store.getBoolean(SettingsKeys.BORDERS, defaults.keyBorders),
            numberRow = store.getBoolean(SettingsKeys.NUMBER_ROW, defaults.numberRow),
            showSuggestions = store.getBoolean(SettingsKeys.SUGGESTIONS, defaults.showSuggestions),
            showToolbar = store.getBoolean(SettingsKeys.TOOLBAR, defaults.showToolbar),
            soundEnabled = store.getBoolean(SettingsKeys.SOUND, defaults.soundEnabled),
            soundVolume = store.getFloat(SettingsKeys.SOUND_VOLUME, defaults.soundVolume),
            hapticsEnabled = store.getBoolean(SettingsKeys.HAPTICS, defaults.hapticsEnabled),
            autoCapitalize = store.getBoolean(SettingsKeys.AUTO_CAPITALIZE, defaults.autoCapitalize),
            autoCorrect = store.getBoolean(SettingsKeys.AUTO_CORRECT, defaults.autoCorrect),
            nextWordPrediction = store.getBoolean(SettingsKeys.PREDICTION, defaults.nextWordPrediction),
            longPressAlternates = store.getBoolean(SettingsKeys.LONG_PRESS, defaults.longPressAlternates),
            spacebarCursor = store.getBoolean(SettingsKeys.SPACE_CURSOR, defaults.spacebarCursor),
            learnWords = store.getBoolean(SettingsKeys.LEARNING, defaults.learnWords),
            emojiSuggestions = store.getBoolean(SettingsKeys.EMOJI_SUGGESTIONS, defaults.emojiSuggestions),
            clipboardEnabled = store.getBoolean(SettingsKeys.CLIPBOARD_ENABLED, defaults.clipboardEnabled),
            clipboardCapture = store.getBoolean(SettingsKeys.CLIPBOARD_CAPTURE, defaults.clipboardCapture),
            clipboardMaxEntries = store.getInt(SettingsKeys.CLIPBOARD_MAX, defaults.clipboardMaxEntries)
                .coerceIn(Settings.MIN_CLIPBOARD, Settings.MAX_CLIPBOARD),
            languages = languageList,
            amharicMode = store.getString(SettingsKeys.AMHARIC_MODE, defaults.amharicMode)
                ?.takeIf { it == Settings.AMHARIC_GEEZ || it == Settings.AMHARIC_TRANSLITERATION }
                ?: defaults.amharicMode,
            setupDone = store.getBoolean(SettingsKeys.SETUP_DONE, defaults.setupDone),
        )
    }

    // ------------------------------------------------------------- appearance

    fun setThemePreset(id: String) {
        store.putString(SettingsKeys.THEME, id)
        store.remove(SettingsKeys.CUSTOM_BACKGROUND)
        store.remove(SettingsKeys.CUSTOM_KEY)
        store.remove(SettingsKeys.CUSTOM_TEXT)
        store.remove(SettingsKeys.CUSTOM_ACCENT)
    }

    fun setCustomBackground(color: Int?) = writeColor(SettingsKeys.CUSTOM_BACKGROUND, color)
    fun setCustomKey(color: Int?) = writeColor(SettingsKeys.CUSTOM_KEY, color)
    fun setCustomText(color: Int?) = writeColor(SettingsKeys.CUSTOM_TEXT, color)
    fun setCustomAccent(color: Int?) = writeColor(SettingsKeys.CUSTOM_ACCENT, color)

    fun setKeyRadius(value: Float) = store.putFloat(SettingsKeys.KEY_RADIUS, value.coerceIn(0f, 24f))
    fun setKeyGap(value: Float) = store.putFloat(SettingsKeys.KEY_GAP, value.coerceIn(0f, 8f))
    fun setKeyHeightScale(value: Float) = store.putFloat(SettingsKeys.KEY_HEIGHT, value.coerceIn(0.75f, 1.4f))
    fun setOpacity(value: Float) = store.putFloat(SettingsKeys.OPACITY, value.coerceIn(0.55f, 1f))
    fun setKeyBorders(value: Boolean) = store.putBoolean(SettingsKeys.BORDERS, value)
    fun setNumberRow(value: Boolean) = store.putBoolean(SettingsKeys.NUMBER_ROW, value)
    fun setShowSuggestions(value: Boolean) = store.putBoolean(SettingsKeys.SUGGESTIONS, value)
    fun setShowToolbar(value: Boolean) = store.putBoolean(SettingsKeys.TOOLBAR, value)

    // ----------------------------------------------------------------- typing

    fun setSound(value: Boolean) = store.putBoolean(SettingsKeys.SOUND, value)
    fun setSoundVolume(value: Float) = store.putFloat(SettingsKeys.SOUND_VOLUME, value.coerceIn(0f, 1f))
    fun setHaptics(value: Boolean) = store.putBoolean(SettingsKeys.HAPTICS, value)
    fun setAutoCapitalize(value: Boolean) = store.putBoolean(SettingsKeys.AUTO_CAPITALIZE, value)
    fun setAutoCorrect(value: Boolean) = store.putBoolean(SettingsKeys.AUTO_CORRECT, value)
    fun setNextWord(value: Boolean) = store.putBoolean(SettingsKeys.PREDICTION, value)
    fun setPrediction(value: Boolean) = setNextWord(value)
    fun setLongPress(value: Boolean) = store.putBoolean(SettingsKeys.LONG_PRESS, value)
    fun setSpacebarCursor(value: Boolean) = store.putBoolean(SettingsKeys.SPACE_CURSOR, value)
    fun setLearnWords(value: Boolean) = store.putBoolean(SettingsKeys.LEARNING, value)
    fun setEmojiSuggestions(value: Boolean) = store.putBoolean(SettingsKeys.EMOJI_SUGGESTIONS, value)

    // ---------------------------------------------------------------- privacy

    fun setClipboardEnabled(value: Boolean) = store.putBoolean(SettingsKeys.CLIPBOARD_ENABLED, value)
    fun setClipboardCapture(value: Boolean) = store.putBoolean(SettingsKeys.CLIPBOARD_CAPTURE, value)
    fun setClipboardMax(value: Int) = store.putInt(
        SettingsKeys.CLIPBOARD_MAX,
        value.coerceIn(Settings.MIN_CLIPBOARD, Settings.MAX_CLIPBOARD),
    )

    // -------------------------------------------------------------- languages

    /** Keeps the user's order, drops unknown ids and always keeps one language. */
    fun setLanguages(languages: List<String>) {
        val cleaned = LinkedHashSet<String>()
        for (id in languages) {
            if (id == LanguageIds.ENGLISH || id == LanguageIds.AMHARIC) cleaned.add(id)
        }
        val safe = if (cleaned.isEmpty()) listOf(LanguageIds.ENGLISH) else cleaned.toList()
        store.putString(SettingsKeys.LANGUAGES, safe.joinToString(","))
    }

    fun setAmharicMode(mode: String) {
        store.putString(
            SettingsKeys.AMHARIC_MODE,
            if (mode == Settings.AMHARIC_GEEZ) Settings.AMHARIC_GEEZ else Settings.AMHARIC_TRANSLITERATION,
        )
    }

    fun setSetupDone(value: Boolean) = store.putBoolean(SettingsKeys.SETUP_DONE, value)

    fun restoreDefaults() {
        for (key in SettingsKeys.all) {
            if (key == SettingsKeys.SETUP_DONE) continue
            store.remove(key)
        }
    }

    fun addListener(listener: KeyValueStore.Listener) = store.addListener(listener)

    fun removeListener(listener: KeyValueStore.Listener) = store.removeListener(listener)

    private fun readColor(key: String): Int? {
        val stored = store.getInt(key, Int.MIN_VALUE)
        return if (stored == Int.MIN_VALUE) null else stored
    }

    private fun writeColor(key: String, color: Int?) {
        if (color == null) store.remove(key) else store.putInt(key, color)
    }
}
