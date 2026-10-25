package com.novakeyboard.domain

import com.novakeyboard.data.clipboard.ClipboardCapturePolicy
import com.novakeyboard.data.clipboard.ClipboardRepository
import com.novakeyboard.data.dictionary.BigramModel
import com.novakeyboard.data.dictionary.Dictionary
import com.novakeyboard.data.dictionary.UserDictionary
import com.novakeyboard.data.prefs.MemoryKeyValueStore
import com.novakeyboard.data.prefs.Settings
import com.novakeyboard.data.prefs.SettingsRepository
import com.novakeyboard.domain.autocorrect.AutoCorrector
import com.novakeyboard.domain.language.GeezAlphabet
import com.novakeyboard.domain.language.GeezTransliterator
import com.novakeyboard.domain.language.Language
import com.novakeyboard.domain.prediction.PredictionEngine
import com.novakeyboard.domain.prediction.Suggestion
import com.novakeyboard.ime.InputSession
import com.novakeyboard.ime.ShiftState
import com.novakeyboard.ime.ShiftStateMachine
import com.novakeyboard.security.EditorState
import com.novakeyboard.security.EnterAction
import com.novakeyboard.security.InputFlags
import com.novakeyboard.security.KeyboardKind
import com.novakeyboard.ui.emoji.EmojiIndex
import com.novakeyboard.utils.Words
import java.io.File
import kotlin.system.exitProcess

/**
 * Plain-JVM test suite for everything that does not need a device: shift/caps,
 * editing state, suggestions, autocorrect, ranking, preferences, language
 * switching, clipboard limits and sensitive-field restrictions.
 *
 * It deliberately avoids JUnit so it can run with nothing but the Kotlin
 * compiler and a JVM (`./gradlew :app:runDomainTests` wires this `main` into
 * Gradle). Assertions print PASS/FAIL and the process exits non-zero on failure.
 */

internal var checks = 0
internal var failures = 0
internal val failedTests = ArrayList<String>()

/** Clears the counters so a single scenario can be reported as one JUnit test. */
internal fun resetStats() {
    checks = 0
    failures = 0
    failedTests.clear()
}

private fun check(condition: Boolean, message: String) {
    checks++
    if (!condition) {
        failures++
        println("    FAIL: $message")
    }
}

private fun <T> expectEquals(actual: T, expected: T, message: String) {
    check(actual == expected, "$message (expected <$expected>, got <$actual>)")
}

internal fun test(name: String, body: () -> Unit) {
    val before = failures
    try {
        body()
    } catch (t: Throwable) {
        failures++
        println("    ERROR: ${t::class.java.simpleName}: ${t.message}")
        t.printStackTrace(System.out)
    }
    if (failures == before) {
        println("PASS  $name")
    } else {
        failedTests.add(name)
        println("FAIL  $name")
    }
}

private fun assetsDir(): File {
    val candidates = listOfNotNull(
        System.getProperty("nova.assets")?.let(::File),
        File("app/src/main/assets"),
        File("src/main/assets"),
        File("../app/src/main/assets"),
        File("../../app/src/main/assets"),
    )
    return candidates.firstOrNull { File(it, "dictionaries/en_words.txt").isFile }
        ?: error("Bundled assets not found; pass -Dnova.assets=<path to app/src/main/assets>")
}

private fun hex(vararg codePoints: Int): String =
    codePoints.joinToString("") { String(Character.toChars(it)) }

private fun loadDictionary(): Dictionary =
    Dictionary.load(File(assetsDir(), "dictionaries/en_words.txt").inputStream())

private fun loadBigrams(): BigramModel =
    BigramModel.load(File(assetsDir(), "dictionaries/en_bigrams.txt").inputStream())

private fun loadEmoji(): EmojiIndex =
    File(assetsDir(), "emoji/emoji.tsv").bufferedReader().useLines { EmojiIndex.parse(it) }

// -------------------------------------------------------------------- shift

fun shiftAndCaps() {
    val shift = ShiftStateMachine(doubleTapMs = 380L)

    expectEquals(shift.state, ShiftState.OFF, "initial state is off")
    shift.onShiftTapped(1_000L)
    expectEquals(shift.state, ShiftState.ON, "single tap enables one-shot shift")
    shift.onCharacterTyped()
    expectEquals(shift.state, ShiftState.OFF, "typing a character releases one-shot shift")

    shift.onShiftTapped(2_000L)
    shift.onShiftTapped(2_100L)
    expectEquals(shift.state, ShiftState.CAPS, "double tap locks caps")
    shift.onCharacterTyped()
    expectEquals(shift.state, ShiftState.CAPS, "caps lock survives typing")

    shift.onShiftTapped(3_000L)
    expectEquals(shift.state, ShiftState.OFF, "tapping while locked releases caps")

    shift.onShiftTapped(4_000L)
    shift.onShiftTapped(4_600L)
    expectEquals(shift.state, ShiftState.OFF, "two slow taps do not lock caps")

    shift.applyAutoCapitalisation(true)
    expectEquals(shift.state, ShiftState.ON, "auto-capitalisation enables shift")
    shift.setCapsLock()
    shift.applyAutoCapitalisation(false)
    expectEquals(shift.state, ShiftState.CAPS, "auto-capitalisation never clears caps lock")

    shift.reset()
    expectEquals(shift.state, ShiftState.OFF, "reset clears shift")
}

// --------------------------------------------------------- editing session

fun insertionAndBackspace() {
    val session = InputSession()
    for (letter in "hello") session.onCharacterCommitted(letter.toString())
    expectEquals(session.currentWord, "hello", "typed characters build the current word")
    check(!session.autoCapitalise, "mid-word typing disables auto-capitalisation")

    session.onBackspace()
    expectEquals(session.currentWord, "hell", "backspace removes one character")
    session.onBackspace()
    session.onBackspace()
    session.onBackspace()
    expectEquals(session.currentWord, "h", "backspace stops at the first character")
    session.onBackspace()
    expectEquals(session.currentWord, "", "backspace on an empty word stays empty")

    for (letter in "world") session.onCharacterCommitted(letter.toString())
    session.onSpaceCommitted()
    expectEquals(session.currentWord, "", "space finishes the word")
    expectEquals(session.previousWord, "world", "space records the previous word")

    session.onCharacterCommitted(".")
    expectEquals(session.currentWord, "", "punctuation is not part of a word")
    check(session.autoCapitalise, "a full stop starts a new sentence")
    session.onSpaceCommitted()
    check(session.autoCapitalise, "space after a full stop keeps the sentence flag")

    session.onEnterCommitted()
    check(session.autoCapitalise, "enter starts a new sentence")
    expectEquals(session.previousWord, "world", "enter keeps the last finished word")

    session.syncFromText("hello grea")
    expectEquals(session.currentWord, "grea", "sync reads the word being edited")
    expectEquals(session.previousWord, "hello", "sync reads the previous word")

    session.syncFromText("Hello. ")
    check(session.autoCapitalise, "sync detects a sentence start")

    session.markCorrection("teh", "the")
    expectEquals(session.lastCorrection, "teh" to "the", "autocorrection can be undone")
    expectEquals(session.currentWord, "the", "corrected word becomes the current word")
    val undo = session.clearCorrection()
    expectEquals(undo, "teh" to "the", "undo returns the original spelling")
    expectEquals(session.currentWord, "teh", "undo restores the typed word")
    expectEquals(session.clearCorrection(), null, "undo is only offered once")

    expectEquals(Words.matchCase("hello", "HEL"), "Hello", "all-caps input produces a capitalised word")
    expectEquals(Words.matchCase("hello", "Hel"), "Hello", "title case is preserved")
    expectEquals(Words.matchCase("hello", "hel"), "hello", "lower-case input stays lower case")
}

// ------------------------------------------------------- suggestions/ranking

fun suggestionsAndRanking() {
    val dictionary = loadDictionary()
    val bigrams = loadBigrams()
    check(dictionary.size > 30_000, "bundled dictionary loaded (${dictionary.size} words)")
    check(bigrams.size > 10_000, "bundled bigram table loaded (${bigrams.size} pairs)")

    check(dictionary.contains("the"), "dictionary contains 'the'")
    check(dictionary.contains("hello"), "dictionary contains 'hello'")
    check(dictionary.frequency("the") > dictionary.frequency("hello"), "common words rank higher")

    val prefixes = dictionary.prefixMatches("hel", 3)
    check(prefixes.isNotEmpty(), "prefix completion returns candidates")
    check(prefixes.all { it.word.startsWith("hel") }, "prefix matches share the prefix")
    check(prefixes.size <= 3, "prefix limit is respected")
    expectEquals(prefixes[0].word, "help", "highest-frequency prefix match comes first")
    expectEquals(prefixes[1].word, "hello", "ranking descends by frequency")
    check(prefixes.zipWithNext().all { it.first.frequency >= it.second.frequency }, "prefix results are ranked by frequency")

    val predictor = PredictionEngine(dictionary, bigrams)
    val forPrefix = predictor.suggestions("hel", null, limit = 3)
    check(forPrefix.isNotEmpty(), "typing a prefix yields suggestions")
    check(forPrefix.size <= 3, "the strip shows at most three suggestions")
    check(forPrefix.all { it.kind == Suggestion.Kind.PREFIX }, "prefix suggestions come from the dictionary")
    check(forPrefix.first().text.lowercase().startsWith("hel"), "first suggestion extends the prefix")
    check(forPrefix.none { it.text.equals("hel", ignoreCase = true) }, "the exact typed word is not suggested")

    val next = predictor.suggestions("", "thank", limit = 3, predictionEnabled = true)
    check(next.isNotEmpty(), "next-word prediction returns candidates for 'thank'")
    check(next.any { it.text.equals("you", ignoreCase = true) }, "bigram prediction offers 'you' after 'thank'")
    check(next.all { it.kind == Suggestion.Kind.NEXT_WORD }, "empty input yields next-word suggestions only")

    val withoutPrediction = predictor.suggestions("", "thank", limit = 3, predictionEnabled = false)
    check(withoutPrediction.isEmpty(), "next-word prediction can be disabled")

    val emojiIndex = loadEmoji()
    val emojiPredictor = PredictionEngine(dictionary, bigrams, emojiIndex)
    val withEmoji = emojiPredictor.suggestions("pizza", null, limit = 3)
    check(withEmoji.isNotEmpty() && withEmoji.first().isEmoji, "emoji suggestion is offered for an exact keyword")
    check(withEmoji.size <= 3, "emoji suggestions respect the limit")
    val emojiOff = emojiPredictor.suggestions("pizza", null, limit = 3, emojiEnabled = false)
    check(emojiOff.none { it.isEmoji }, "emoji suggestions can be disabled")

    val user = UserDictionary(MemoryKeyValueStore())
    val personal = PredictionEngine(user, BigramModel.EMPTY)
    user.addPersonal("zebrinsky")
    val personalResults = personal.suggestions("zebr", null)
    check(personalResults.any { it.text == "zebrinsky" }, "personal dictionary words are suggested")
    check(user.isPersonal("zebrinsky"), "personal word is stored")
    user.removeWord("zebrinsky")
    check(!user.isPersonal("zebrinsky"), "personal word can be removed")

    user.learn("novakeyboard")
    check(user.isLearned("novakeyboard"), "learning stores a word")
    check(user.frequency("novakeyboard") > 0, "learned words have a frequency")
    user.unlearn("novakeyboard")
    check(!user.isLearned("novakeyboard"), "learned word can be forgotten")

    // Ranking must not leak allocations or crash on unusual input.
    check(predictor.suggestions("", null).isEmpty(), "no context yields no suggestions")
    check(predictor.suggestions("", null, limit = 0).isEmpty(), "zero limit yields no suggestions")
    check(predictor.suggestions("qzx", null).isEmpty(), "unknown prefix yields no suggestions")
}

// ------------------------------------------------------------- autocorrect

fun autoCorrect() {
    val corrector = AutoCorrector(loadDictionary())

    val teh = corrector.correctionFor("teh")
    check(teh != null, "'teh' is corrected")
    expectEquals(teh?.replacement, "the", "'teh' becomes 'the'")
    expectEquals(teh?.typed, "teh", "the original spelling is kept for undo")

    val titleCase = corrector.correctionFor("Teh")
    expectEquals(titleCase?.replacement, "The", "correction preserves title case")

    check(corrector.correctionFor("hello") == null, "correct words are never rewritten")
    check(corrector.correctionFor("ur") == null, "words shorter than three letters are left alone")
    check(corrector.correctionFor("NASA") == null, "acronyms are left alone")
    check(corrector.correctionFor("https://example.com") == null, "URLs are left alone")
    check(corrector.correctionFor("someone@example.com") == null, "email addresses are left alone")
    check(corrector.correctionFor("qzxqzx") == null, "unknown words without a nearby match are untouched")
    check(corrector.correctionFor("") == null, "empty input is ignored")

    val emptyCorrector = AutoCorrector(Dictionary.EMPTY)
    check(emptyCorrector.correctionFor("teh") == null, "autocorrect stays quiet until the dictionary is loaded")
}

// ------------------------------------------------------------- preferences

fun preferences() {
    val store = MemoryKeyValueStore()
    val repository = SettingsRepository(store)
    val defaults = repository.load()
    expectEquals(defaults.themePresetId, "midnight", "default theme is Midnight Black")
    expectEquals(defaults.keyRadiusDp, 12f, "default key radius")
    expectEquals(defaults.keyGapDp, 3f, "default key gap")
    expectEquals(defaults.clipboardMaxEntries, 20, "default clipboard size")
    expectEquals(defaults.languages, listOf("en"), "default language is English")
    expectEquals(defaults.amharicMode, Settings.AMHARIC_TRANSLITERATION, "default Amharic mode is transliteration")
    check(defaults.autoCorrect && defaults.nextWordPrediction, "prediction is on by default")
    check(!defaults.clipboardCapture, "clipboard capture is opt-in")
    check(!defaults.setupDone, "setup starts incomplete")

    repository.setThemePreset("ocean")
    expectEquals(repository.load().themePresetId, "ocean", "theme change persists")

    repository.setCustomAccent(0xFF112233.toInt())
    repository.setThemePreset("violet")
    check(repository.load().customAccent == null, "choosing a preset clears custom colours")

    repository.setKeyRadius(99f)
    expectEquals(repository.load().keyRadiusDp, 24f, "key radius is clamped")
    repository.setKeyGap(-3f)
    expectEquals(repository.load().keyGapDp, 0f, "key gap is clamped")
    repository.setKeyHeightScale(3f)
    expectEquals(repository.load().keyHeightScale, 1.4f, "key height is clamped")
    repository.setOpacity(0.1f)
    expectEquals(repository.load().backgroundOpacity, 0.55f, "opacity is clamped")
    repository.setClipboardMax(1)
    expectEquals(repository.load().clipboardMaxEntries, 5, "clipboard size is clamped")
    repository.setSoundVolume(4f)
    expectEquals(repository.load().soundVolume, 1f, "sound volume is clamped")

    repository.setHaptics(false)
    repository.setNumberRow(true)
    val changed = repository.load()
    check(!changed.hapticsEnabled, "haptics toggle persists")
    check(changed.numberRow, "number row toggle persists")

    repository.setLanguages(listOf("am", "en", "xx"))
    expectEquals(repository.load().languages, listOf("am", "en"), "unknown languages are dropped and order is kept")
    repository.setLanguages(emptyList())
    expectEquals(repository.load().languages, listOf("en"), "at least one language always stays enabled")
    repository.setAmharicMode("nonsense")
    expectEquals(repository.load().amharicMode, Settings.AMHARIC_TRANSLITERATION, "invalid Amharic mode falls back")

    repository.setSetupDone(true)
    repository.restoreDefaults()
    val restored = repository.load()
    expectEquals(restored.themePresetId, "midnight", "restore defaults resets the theme")
    expectEquals(restored.keyRadiusDp, 12f, "restore defaults resets the radius")
    check(restored.setupDone, "restore defaults keeps the setup flag")

    var notified = 0
    val listener = object : com.novakeyboard.data.prefs.KeyValueStore.Listener {
        override fun onKeyChanged(key: String) { notified++ }
    }
    repository.addListener(listener)
    repository.setSound(true)
    repository.removeListener(listener)
    repository.setSound(false)
    check(notified >= 1, "settings listeners are notified about changes")
}

// --------------------------------------------------------- language switch

fun languagesAndGeez() {
    expectEquals(Language.fromId("en"), Language.ENGLISH, "English id maps to English")
    expectEquals(Language.fromId("am"), Language.AMHARIC, "Amharic id maps to Amharic")
    expectEquals(Language.fromId("fr"), null, "unknown ids are rejected")

    expectEquals(GeezTransliterator.transliterate("selam"), hex(0x1230, 0x120B, 0x121D), "selam → ሰላም")
    expectEquals(GeezTransliterator.transliterate("enat"), hex(0x12A0, 0x1293, 0x1275), "enat → አናት")
    expectEquals(GeezTransliterator.transliterate("biet"), hex(0x1264, 0x1275), "biet → ቤት")
    expectEquals(GeezTransliterator.transliterate("bet"), hex(0x1260, 0x1275), "bet → በት (first order)")
    expectEquals(GeezTransliterator.transliterate("s"), hex(0x1235), "bare consonant → 6th order")
    expectEquals(GeezTransliterator.transliterate("sa"), hex(0x1233), "sa → ሳ")
    expectEquals(GeezTransliterator.transliterate("sie"), hex(0x1234), "sie → ሴ")
    expectEquals(GeezTransliterator.transliterate("she"), hex(0x1238), "the sh digraph → ሸ")
    expectEquals(GeezTransliterator.transliterate("sh"), hex(0x123D), "bare sh → ሽ")
    expectEquals(GeezTransliterator.transliterate("kwa"), hex(0x12B3), "kwa → ኳ (labialised)")
    expectEquals(GeezTransliterator.transliterate(","), "፣", "Latin comma becomes the Ethiopic comma")
    expectEquals(GeezTransliterator.transliterate("?"), "፧", "question mark becomes ፧")
    expectEquals(GeezTransliterator.transliterate(""), "", "empty input produces empty output")

    val units = GeezTransliterator.units("selam")
    expectEquals(units.size, 3, "units groups syllables")
    expectEquals(units[0].first, "se", "first unit keeps its Latin spelling")
    expectEquals(units[0].second, "ሰ", "first unit maps to ሰ")
    expectEquals(units[1].first, "la", "second unit keeps its Latin spelling")
    expectEquals(units[2].first, "m", "trailing consonant is its own unit")
    expectEquals(units.map { it.first }.joinToString(""), "selam", "units can be re-joined for backspace")

    val orders = GeezAlphabet.ordersFor("ሰ")
    expectEquals(orders.size, 7, "each consonant has seven orders")
    expectEquals(orders[0], "ሰ", "first order is the base letter")
    expectEquals(orders[3], "ሳ", "fourth order is -aa")
    expectEquals(orders[6], "ሶ", "seventh order is -o")
    check(GeezAlphabet.find("s")?.base == 0x1230, "Latin s maps to ሰ (U+1230)")
    check(GeezAlphabet.find("S")?.base == 0x1238, "capital S maps to ሸ (U+1238)")
    check(GeezAlphabet.find("sz")?.base == 0x1220, "sz maps to ሠ (U+1220)")
    check(GeezAlphabet.extraRow.isNotEmpty(), "the Geez keyboard has a punctuation row")
    check(GeezTransliterator.isTransliterable("selam"), "plain Latin is transliterable")
    check(!GeezTransliterator.isTransliterable("hello 123"), "digits cannot be transliterated")
}

// ------------------------------------------------------------ clipboard

fun clipboardLimits() {
    val store = MemoryKeyValueStore()
    val repository = ClipboardRepository(store)

    check(repository.save("first", maxEntries = 5, now = 1L), "clipboard text is saved")
    check(repository.save("second", maxEntries = 5, now = 2L), "second entry is saved")
    expectEquals(repository.entries().first().text, "second", "newest entry is first")
    expectEquals(repository.count(), 2, "both entries are stored")

    repository.save("second", maxEntries = 5, now = 3L)
    expectEquals(repository.count(), 2, "duplicate text is not stored twice")
    expectEquals(repository.entries().count { it.text == "second" }, 1, "the duplicate entry is replaced")
    expectEquals(repository.entries().first().createdAt, 3L, "duplicate moves to the top with a new timestamp")

    for (index in 0 until 12) repository.save("entry-$index", maxEntries = 5, now = 100L + index)
    expectEquals(repository.count(), 5, "history never exceeds the configured maximum")
    expectEquals(repository.entries().first().text, "entry-11", "the newest entry survives trimming")

    val pinned = repository.entries().first()
    repository.setPinned(pinned.id, true)
    for (index in 0 until 8) repository.save("later-$index", maxEntries = 3, now = 200L + index)
    check(repository.entries().any { it.text == "entry-11" }, "pinned entries survive trimming")
    check(repository.count() <= 4, "pinned entry does not grow the list past the limit unnecessarily")

    repository.trimTo(2)
    check(repository.count() <= 3, "lowering the limit trims the history")
    check(repository.entries().any { it.text == "entry-11" }, "trimming keeps pinned entries")

    repository.remove(repository.entries().first { it.text == "entry-11" }.id)
    check(repository.entries().none { it.text == "entry-11" }, "entries can be deleted")
    repository.clear()
    expectEquals(repository.count(), 0, "history can be cleared")

    check(!repository.save("", maxEntries = 5), "empty text is ignored")
    check(!repository.save("   ", maxEntries = 5), "blank text is ignored")
    check(!repository.save("x".repeat(4000), maxEntries = 5), "over-long text is ignored")
    check(repository.save("x".repeat(2000), maxEntries = 5), "text at the size limit is accepted")

    check(ClipboardCapturePolicy.shouldCapture(true, true, false, "hello"), "ordinary clipboard text is captured when enabled")
    check(!ClipboardCapturePolicy.shouldCapture(false, true, false, "hello"), "capture is off when history is disabled")
    check(!ClipboardCapturePolicy.shouldCapture(true, false, false, "hello"), "capture requires the user opt-in")
    check(!ClipboardCapturePolicy.shouldCapture(true, true, true, "secret"), "password fields are never captured")
    check(!ClipboardCapturePolicy.shouldCapture(true, true, false, ""), "empty clipboard is ignored")
    check(!ClipboardCapturePolicy.shouldCapture(true, true, false, "x".repeat(4001)), "over-long clipboard text is ignored")
    check(!ClipboardCapturePolicy.shouldCapture(true, true, false, "   "), "blank clipboard text is ignored")
}

// -------------------------------------------------------- sensitive fields

fun sensitiveFields() {
    val text = InputFlags.TYPE_CLASS_TEXT
    val password = EditorState.from(text or InputFlags.TYPE_TEXT_VARIATION_PASSWORD, 0)
    check(password.isPassword, "password variation is detected")
    check(password.isSensitive, "passwords are sensitive")
    check(!password.allowsSuggestions, "suggestions are disabled in passwords")
    check(!password.allowsPersonalization, "learning is disabled in passwords")
    expectEquals(password.enterAction, EnterAction.DONE, "password fields use Done")

    val visible = EditorState.from(text or InputFlags.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, 0)
    check(visible.isSensitive, "visible passwords are still sensitive")

    val web = EditorState.from(text or InputFlags.TYPE_TEXT_VARIATION_WEB_PASSWORD, 0)
    check(web.isPassword, "web password fields are detected")

    val pin = EditorState.from(InputFlags.TYPE_CLASS_NUMBER or InputFlags.TYPE_NUMBER_VARIATION_PASSWORD, 0)
    check(pin.isPassword && pin.isSensitive, "numeric PIN fields are sensitive")
    expectEquals(pin.kind, KeyboardKind.NUMBER, "PIN fields use the number layout")

    val noLearning = EditorState.from(
        text or InputFlags.TYPE_TEXT_FLAG_MULTI_LINE,
        InputFlags.IME_FLAG_NO_PERSONALIZED_LEARNING or 6,
    )
    check(noLearning.isSensitive, "IME_FLAG_NO_PERSONALIZED_LEARNING marks the field sensitive")
    check(!noLearning.allowsPersonalization, "no-personalized-learning disables learning")
    check(noLearning.isMultiline, "multiline flag is read")
    expectEquals(noLearning.enterAction, EnterAction.DONE, "explicit ime action wins in multiline fields")

    val privateIme = EditorState.from(text, 0, "noPersonalizedLearning")
    check(privateIme.isSensitive, "private IME options are honoured")

    val email = EditorState.from(text or InputFlags.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 3)
    expectEquals(email.kind, KeyboardKind.EMAIL, "email variation selects the email layout")
    expectEquals(email.enterAction, EnterAction.SEARCH, "search action is read")
    check(!email.isSensitive, "email fields are not sensitive")

    val uri = EditorState.from(text or InputFlags.TYPE_TEXT_VARIATION_URI, 2)
    expectEquals(uri.kind, KeyboardKind.URI, "URI variation selects the URI layout")
    expectEquals(uri.enterAction, EnterAction.GO, "go action is read")

    val phone = EditorState.from(InputFlags.TYPE_CLASS_PHONE, 0)
    expectEquals(phone.kind, KeyboardKind.PHONE, "phone fields use the phone layout")

    val multiline = EditorState.from(text or InputFlags.TYPE_TEXT_FLAG_MULTI_LINE, 0)
    check(multiline.isMultiline, "multiline text is detected")
    expectEquals(multiline.enterAction, EnterAction.NEWLINE, "multiline fields default to a newline key")

    val search = EditorState.from(text, 3)
    expectEquals(search.enterAction, EnterAction.SEARCH, "search action maps to Search")
    expectEquals(search.enterKeyLabel, "Search", "the enter key is labelled for the action")

    val suggestionless = EditorState.from(text or InputFlags.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 0)
    check(!suggestionless.allowsSuggestions, "TYPE_TEXT_FLAG_NO_SUGGESTIONS disables suggestions")
}

// ----------------------------------------------------------------- emoji

fun emojiIndex() {
    val index = loadEmoji()
    check(index.size > 1_500, "bundled emoji index loaded (${index.size} entries)")
    check(index.entries.all { it.category.isNotEmpty() }, "every emoji has a category")
    check(index.entries.all { it.emoji.isNotEmpty() }, "every emoji has a glyph")
    check(index.entries.all { it.name.isNotBlank() }, "every emoji has a name")

    for (category in EmojiIndex.CATEGORIES) {
        val entries = index.categoryEntries(category.id)
        check(entries.isNotEmpty(), "category '${category.id}' has entries")
        check(entries.all { it.category == category.sourceName }, "category '${category.id}' only contains its own emoji")
    }

    val smile = index.search("smile", 20)
    check(smile.isNotEmpty(), "searching 'smile' finds emoji")
    check(smile.size <= 20, "emoji search respects its limit")
    check(index.search("zzzznotanemoji").isEmpty(), "unknown emoji queries return nothing")
    check(index.search("", 20).isEmpty(), "empty emoji queries return nothing")

    val exact = index.emojiForWord("pizza")
    check(exact != null, "emoji suggestion finds an exact keyword")
    check(index.emojiForWord("x") == null, "one-letter words never trigger emoji suggestions")
}

// ------------------------------------------------------------------- main

fun main() {
    println("NOVA KEYBOARD — domain test suite")
    println("assets: ${assetsDir().absolutePath}")
    val started = System.currentTimeMillis()
    test("shift and caps lock", ::shiftAndCaps)
    test("insertion and backspace", ::insertionAndBackspace)
    test("suggestions and ranking", ::suggestionsAndRanking)
    test("autocorrect", ::autoCorrect)
    test("preferences", ::preferences)
    test("language switching and Geez input", ::languagesAndGeez)
    test("clipboard limits and capture policy", ::clipboardLimits)
    test("sensitive fields", ::sensitiveFields)
    test("emoji index", ::emojiIndex)
    val elapsed = System.currentTimeMillis() - started
    println()
    println("$checks checks, $failures failure(s), ${elapsed} ms")
    if (failures > 0) {
        println("Failed tests: ${failedTests.joinToString(", ")}")
        exitProcess(1)
    }
    println("ALL TESTS PASSED")
}
