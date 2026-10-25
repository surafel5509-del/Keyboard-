package com.novakeyboard.ime

import android.content.ClipboardManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.inputmethodservice.InputMethodService
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.novakeyboard.R
import com.novakeyboard.data.clipboard.ClipboardCapturePolicy
import com.novakeyboard.data.clipboard.ClipboardRepository
import com.novakeyboard.data.dictionary.BigramModel
import com.novakeyboard.data.dictionary.CompositeWordSource
import com.novakeyboard.data.dictionary.Dictionary
import com.novakeyboard.data.dictionary.UserDictionary
import com.novakeyboard.data.prefs.KeyValueStore
import com.novakeyboard.data.prefs.Settings
import com.novakeyboard.data.prefs.SettingsKeys
import com.novakeyboard.data.prefs.SettingsRepository
import com.novakeyboard.data.prefs.SharedPrefsStore
import com.novakeyboard.domain.autocorrect.AutoCorrector
import com.novakeyboard.domain.language.GeezTransliterator
import com.novakeyboard.domain.language.Language
import com.novakeyboard.domain.prediction.PredictionEngine
import com.novakeyboard.security.CapMode
import com.novakeyboard.security.EditorState
import com.novakeyboard.security.EnterAction
import com.novakeyboard.security.KeyboardKind
import com.novakeyboard.ui.Ui
import com.novakeyboard.ui.Ui.dp
import com.novakeyboard.ui.Ui.dpF
import com.novakeyboard.ui.clipboard.ClipboardPanelView
import com.novakeyboard.ui.emoji.EmojiIndex
import com.novakeyboard.ui.emoji.EmojiPanelView
import com.novakeyboard.ui.emoji.EmojiRecentsStore
import com.novakeyboard.ui.keyboard.Key
import com.novakeyboard.ui.keyboard.KeyLayout
import com.novakeyboard.ui.keyboard.KeyType
import com.novakeyboard.ui.keyboard.KeyboardLayouts
import com.novakeyboard.ui.keyboard.KeyboardView
import com.novakeyboard.ui.keyboard.LayoutKind
import com.novakeyboard.ui.keyboard.SuggestionStripView
import com.novakeyboard.ui.settings.SettingsActivity
import com.novakeyboard.ui.theme.KeyboardTheme
import com.novakeyboard.ui.theme.ThemePresets
import com.novakeyboard.ui.toolbar.ToolbarView
import com.novakeyboard.utils.Feedback
import com.novakeyboard.utils.KeySoundPlayer
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Owns the keyboard view tree and translates key events into InputConnection
 * edits, suggestions, themes, panels and language switching.
 */
class KeyboardController(private val service: InputMethodService) :
    KeyboardView.Listener,
    ToolbarView.Listener,
    SuggestionStripView.Listener,
    EmojiPanelView.Listener,
    ClipboardPanelView.Listener {

    private enum class Panel { NONE, EMOJI, CLIPBOARD, EMOJI_SEARCH }

    private val context: Context = service
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private lateinit var preferences: SharedPrefsStore
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var userDictionary: UserDictionary
    private lateinit var clipboardRepository: ClipboardRepository
    private lateinit var emojiRecents: EmojiRecentsStore

    private var settings: Settings = Settings()
    private var theme: KeyboardTheme = ThemePresets.midnight
    private var editor: EditorState = EditorState()
    private var language: Language = Language.ENGLISH
    private var layoutKind: LayoutKind = LayoutKind.LETTERS
    private var panel: Panel = Panel.NONE

    private val shift = ShiftStateMachine()
    private val session = InputSession()

    private var dictionary: Dictionary = Dictionary.EMPTY
    private var bigrams: BigramModel = BigramModel.EMPTY
    private var emojiIndex: EmojiIndex? = null
    private var predictionEngine: PredictionEngine
    private var autoCorrector: AutoCorrector
    private var wordSource: CompositeWordSource

    private val sound = KeySoundPlayer(context)

    private lateinit var rootView: LinearLayout
    private lateinit var toolbar: ToolbarView
    private lateinit var strip: SuggestionStripView
    private lateinit var frame: FrameLayout
    private lateinit var keyboardView: KeyboardView
    private lateinit var emojiPanel: EmojiPanelView
    private lateinit var clipboardPanel: ClipboardPanelView

    private var suggestionToken = 0
    private var lastCommitAt = 0L
    private var lastSuggestionKey: String = ""
    private var phoneticBuffer = ""
    private var committedPhoneticChars = 0
    private var emojiQuery = ""
    private var correctionTrailing = ""
    private var emojiResults: List<com.novakeyboard.ui.emoji.EmojiEntry> = emptyList()
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var languageInitialised = false
    private var assetsLoaded = false
    private var destroyed = false

    private val preferenceListener = KeyValueStore.Listener { key ->
        if (SettingsKeys.all.contains(key)) {
            mainHandler.post { applySettings(fromPreferenceChange = true) }
        }
    }

    init {
        val store = service.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preferences = SharedPrefsStore(store)
        settingsRepository = SettingsRepository(preferences)
        userDictionary = UserDictionary(preferences)
        clipboardRepository = ClipboardRepository(preferences)
        emojiRecents = EmojiRecentsStore(preferences)
        wordSource = CompositeWordSource(dictionary, userDictionary)
        predictionEngine = PredictionEngine(wordSource, bigrams, emojiIndex)
        autoCorrector = AutoCorrector(wordSource)
    }

    // ------------------------------------------------------------------ lifecycle

    fun onCreate() {
        settings = settingsRepository.current()
        theme = ThemePresets.resolve(settings)
        language = Language.fromId(settings.languages.firstOrNull()) ?: Language.ENGLISH
        settingsRepository.addListener(preferenceListener)
        sound.configure(settings.soundEnabled, settings.soundVolume)
        sound.load()
        loadAssetsAsync()
    }

    fun createInputView(): View {
        if (this::rootView.isInitialized) return rootView
        rootView = Ui.linearColumn(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }
        toolbar = ToolbarView(context).apply {
            listener = this@KeyboardController
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(40f))
        }
        strip = SuggestionStripView(context).apply {
            listener = this@KeyboardController
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(38f))
        }
        frame = FrameLayout(context)
        keyboardView = KeyboardView(context).apply {
            listener = this@KeyboardController
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        emojiPanel = EmojiPanelView(context).apply {
            listener = this@KeyboardController
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        clipboardPanel = ClipboardPanelView(context).apply {
            listener = this@KeyboardController
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        frame.addView(keyboardView)
        frame.addView(emojiPanel)
        frame.addView(clipboardPanel)
        frame.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        rootView.addView(toolbar)
        rootView.addView(strip)
        rootView.addView(frame)

        applySettings()
        updateLayout()
        updateShiftVisual()
        applyPanelVisibility()
        if (emojiIndex != null) emojiPanel.bind(emojiIndex!!, emojiRecents)
        return rootView
    }

    fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        editor = if (attribute != null) {
            EditorState.from(attribute.inputType, attribute.imeOptions, attribute.privateImeOptions)
        } else {
            EditorState.from(InputType.TYPE_CLASS_TEXT, 0)
        }
        session.reset()
        shift.reset()
        phoneticBuffer = ""
        committedPhoneticChars = 0
        correctionTrailing = ""
        lastCorrectionVisualClear()
        val enabledLanguages = settings.languages.mapNotNull { Language.fromId(it) }
            .ifEmpty { listOf(Language.ENGLISH) }
        if (!languageInitialised) {
            language = enabledLanguages.first()
            languageInitialised = true
        } else if (!enabledLanguages.contains(language)) {
            language = enabledLanguages.first()
        }
        layoutKind = lettersKind()
        if (attribute != null) {
            applyInputTypeHints(attribute)
        }
        registerClipboardCapture()
        if (this::strip.isInitialized) {
            updateLayout()
            updateShiftVisual()
            requestSuggestions()
            applyPanelVisibility()
        }
    }

    fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        if (!this::keyboardView.isInitialized) return
        closePanels()
        applySettings()
        updateLayout()
        updateShiftVisual()
        requestSuggestions()
        applyPanelVisibility()
        sound.configure(settings.soundEnabled, settings.soundVolume)
        keyboardView.visibility = View.VISIBLE
        keyboardView.requestFocus()
    }

    fun onSelectionChanged(selectionStart: Int, selectionEnd: Int) {
        if (!this::keyboardView.isInitialized) return
        if (SystemClock.uptimeMillis() - lastCommitAt < 120L) return
        resyncFromEditor()
    }

    fun onFinishInput() {
        session.reset()
        phoneticBuffer = ""
        committedPhoneticChars = 0
        closePanels()
    }

    fun onFinishInputView() {
        closePanels()
        if (this::keyboardView.isInitialized) keyboardView.release()
    }

    fun onConfigurationChanged() {
        if (!this::keyboardView.isInitialized) return
        applySettings()
        updateLayout()
    }

    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // Suggestions are re-computed on demand; nothing large is cached.
            lastSuggestionKey = ""
        }
    }

    fun onDestroy() {
        destroyed = true
        settingsRepository.removeListener(preferenceListener)
        mainHandler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        sound.release()
        clipboardListener?.let { listener ->
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.removePrimaryClipChangedListener(listener)
        }
        clipboardListener = null
        if (this::keyboardView.isInitialized) keyboardView.release()
    }

    // -------------------------------------------------------------------- settings

    private fun applySettings(fromPreferenceChange: Boolean = false) {
        if (!this::rootView.isInitialized) return
        settings = settingsRepository.current()
        theme = ThemePresets.resolve(settings)
        sound.configure(settings.soundEnabled, settings.soundVolume)
        applyThemeToViews()
        toolbar.visibility = if (settings.showToolbar) View.VISIBLE else View.GONE
        keyboardView.applyAppearance(theme, keyHeightDp(), settings.keyBorders)
        keyboardView.spaceCursorEnabled = settings.spacebarCursor
        keyboardView.longPressAlternatesEnabled = settings.longPressAlternates
        if (fromPreferenceChange) {
            updateLayout()
            updateShiftVisual()
            requestSuggestions()
            applyPanelVisibility()
            if (settings.clipboardEnabled) clipboardRepository.trimTo(settings.clipboardMaxEntries)
            if (settings.clipboardEnabled && settings.clipboardCapture) registerClipboardCapture() else unregisterClipboardCapture()
            if (panel == Panel.CLIPBOARD) refreshClipboardPanel()
        }
    }

    private fun applyThemeToViews() {
        if (!this::rootView.isInitialized) return
        val background = Ui.roundedBackground(
            withAlpha(theme.backgroundTop, theme.backgroundAlpha),
            context.dpF(16f),
        )
        rootView.background = background
        toolbar.applyTheme(theme)
        strip.applyTheme(theme)
        emojiPanel.applyTheme(theme)
        clipboardPanel.applyTheme(theme)
        toolbar.setLanguageLabel(if (language == Language.AMHARIC) "አማ" else "EN")
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb((alpha * 0.98f).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun keyHeightDp(): Float {
        val landscape = service.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val base = if (landscape) 44f else 54f
        return base * settings.keyHeightScale.coerceIn(0.8f, 1.35f)
    }

    // ---------------------------------------------------------------------- layout

    private fun lettersKind(): LayoutKind {
        if (language == Language.AMHARIC && settings.amharicMode == Settings.AMHARIC_GEEZ &&
            editor.kind == KeyboardKind.TEXT
        ) {
            return LayoutKind.GEEZ_LETTERS
        }
        return when (editor.kind) {
            KeyboardKind.NUMBER, KeyboardKind.DATETIME -> LayoutKind.NUMBER
            KeyboardKind.PHONE -> LayoutKind.PHONE
            KeyboardKind.EMAIL -> LayoutKind.EMAIL
            KeyboardKind.URI -> LayoutKind.URI
            else -> LayoutKind.LETTERS
        }
    }

    private fun updateLayout() {
        if (!this::keyboardView.isInitialized) return
        val layout = KeyboardLayouts.build(
            language = language,
            kind = layoutKind,
            uppercase = shift.isUppercase,
            enterAction = editor.enterAction,
            numberRow = settings.numberRow && layoutKind == LayoutKind.LETTERS,
        )
        keyboardView.setLayout(layout)
        keyboardView.setShift(shift.state)
        toolbar.setLanguageLabel(if (language == Language.AMHARIC) "አማ" else "EN")
    }

    private fun updateShiftVisual() {
        if (!this::keyboardView.isInitialized) return
        if (language == Language.ENGLISH) {
            val shouldCapitalise = settings.autoCapitalize && editor.kind == KeyboardKind.TEXT &&
                session.autoCapitalise
            shift.applyAutoCapitalisation(shouldCapitalise)
        }
        val wasUppercase = keyboardView.uppercase
        keyboardView.setShift(shift.state)
        if (wasUppercase != shift.isUppercase) updateLayout()
    }

    private fun switchLayout(kind: LayoutKind) {
        layoutKind = kind
        updateLayout()
    }

    // -------------------------------------------------------------------- key input

    override fun onKeyPressed(key: Key) {
        if (settings.hapticsEnabled) Feedback.tick(keyboardView)
        if (settings.soundEnabled) sound.click()
    }

    override fun onKeyCommitted(key: Key) {
        when (key.type) {
            KeyType.CHAR -> handleCharacter(key)
            KeyType.SHIFT -> handleShiftTap()
            KeyType.BACKSPACE -> handleBackspace()
            KeyType.ENTER -> handleEnter()
            KeyType.SPACE -> handleSpace()
            KeyType.TO_SYMBOLS -> switchLayout(LayoutKind.SYMBOLS)
            KeyType.TO_SYMBOLS_2 -> switchLayout(LayoutKind.SYMBOLS_2)
            KeyType.TO_LETTERS -> switchLayout(lettersKind())
            KeyType.LANGUAGE -> cycleLanguage()
            KeyType.EMOJI -> showEmojiPanel()
            KeyType.CLIPBOARD -> showClipboardPanel()
            KeyType.SETTINGS -> openSettings()
            KeyType.SPACER -> Unit
        }
    }

    override fun onAlternateSelected(text: String) {
        if (panel == Panel.EMOJI_SEARCH) {
            emojiQuery = (emojiQuery + text).take(24)
            updateEmojiSearch()
            return
        }
        val connection = ic() ?: return
        connection.commitText(text, 1)
        lastCommitAt = SystemClock.uptimeMillis()
        session.onCharacterCommitted(text)
        shift.onCharacterTyped()
        updateShiftVisual()
        requestSuggestions()
    }

    override fun onCursorStep(steps: Int) {
        if (steps == 0) return
        val connection = ic() ?: return
        val keyCode = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(abs(steps)) {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        resyncFromEditor()
    }

    override fun onSpaceLongPressed() {
        cycleLanguage()
    }

    private fun handleShiftTap() {
        shift.onShiftTapped(SystemClock.uptimeMillis())
        updateLayout()
    }

    private fun handleCharacter(key: Key) {
        if (panel == Panel.EMOJI_SEARCH) {
            val text = key.outputFor(false)
            if (text.length == 1 && text[0].isLetter()) {
                emojiQuery = (emojiQuery + text.lowercase()).take(24)
                updateEmojiSearch()
            }
            return
        }
        if (panel != Panel.NONE) return
        val text = key.outputFor(shift.isUppercase && language == Language.ENGLISH)
        if (language == Language.AMHARIC && settings.amharicMode == Settings.AMHARIC_PHONETIC) {
            commitPhonetic(text)
            return
        }
        val connection = ic() ?: return
        val finalText = if (language == Language.AMHARIC) GeezTransliterator.transliterate(text) else text
        connection.commitText(finalText, 1)
        lastCommitAt = SystemClock.uptimeMillis()
        session.onCharacterCommitted(finalText)
        shift.onCharacterTyped()
        updateShiftVisual()
        requestSuggestions()
    }

    private fun handleBackspace() {
        if (panel == Panel.EMOJI_SEARCH) {
            if (emojiQuery.isNotEmpty()) {
                emojiQuery = emojiQuery.dropLast(1)
                updateEmojiSearch()
            } else {
                exitEmojiSearch()
            }
            return
        }
        if (panel != Panel.NONE) return
        val correction = session.lastCorrection
        if (correction != null) {
            revertCorrection()
            return
        }
        if (language == Language.AMHARIC && settings.amharicMode == Settings.AMHARIC_PHONETIC && phoneticBuffer.isNotEmpty()) {
            backspacePhonetic()
            return
        }
        deleteOneCharacter()
        session.onBackspace()
        requestSuggestions()
    }

    private fun handleSpace() {
        if (panel == Panel.EMOJI_SEARCH) {
            insertFirstEmojiResult()
            return
        }
        if (panel != Panel.NONE) return
        if (language == Language.AMHARIC && settings.amharicMode == Settings.AMHARIC_PHONETIC) finalisePhonetic()
        maybeAutocorrect()
        learnCurrentWord()
        commitPlain(" ")
        if (session.lastCorrection != null) correctionTrailing = " "
        session.onSpaceCommitted()
        shift.applyAutoCapitalisation(false)
        updateShiftVisual()
        requestSuggestions()
    }

    private fun handleEnter() {
        if (panel == Panel.EMOJI_SEARCH) {
            insertFirstEmojiResult()
            return
        }
        if (panel != Panel.NONE) return
        if (language == Language.AMHARIC && settings.amharicMode == Settings.AMHARIC_PHONETIC) finalisePhonetic()
        maybeAutocorrect()
        learnCurrentWord()
        val connection = ic() ?: return
        val trailing = if (editor.enterAction == EnterAction.NEWLINE || editor.enterAction == EnterAction.NONE) "\n" else ""
        when (editor.enterAction) {
            EnterAction.NEWLINE, EnterAction.NONE -> commitPlain("\n")
            else -> {
                val actionCode = InputSession.enterActionCode(editor.enterAction)
                if (actionCode != 0) {
                    if (!connection.performEditorAction(actionCode)) commitPlain("\n")
                } else {
                    commitPlain("\n")
                }
            }
        }
        if (session.lastCorrection != null) correctionTrailing = trailing
        session.onEnterCommitted()
        updateShiftVisual()
        requestSuggestions()
    }

    private fun commitPlain(text: String) {
        val connection = ic() ?: return
        connection.commitText(text, 1)
        lastCommitAt = SystemClock.uptimeMillis()
    }

    private fun deleteOneCharacter() {
        val connection = ic() ?: return
        val selection = try {
            connection.getSelectedText(0)
        } catch (e: Exception) {
            null
        }
        if (!selection.isNullOrEmpty()) {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            lastCommitAt = SystemClock.uptimeMillis()
            return
        }
        val deleted = connection.deleteSurroundingText(1, 0)
        lastCommitAt = SystemClock.uptimeMillis()
        if (!deleted) {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
    }

    // ------------------------------------------------------------------ phonetic

    private fun commitPhonetic(text: String) {
        if (text.length != 1 || !text[0].isLetter()) {
            finalisePhonetic()
            commitPlain(text)
            session.onCharacterCommitted(text)
            requestSuggestions()
            return
        }
        phoneticBuffer += text
        renderPhonetic()
    }

    private fun renderPhonetic() {
        val connection = ic() ?: return
        val geez = GeezTransliterator.transliterate(phoneticBuffer)
        connection.beginBatchEdit()
        if (committedPhoneticChars > 0) connection.deleteSurroundingText(committedPhoneticChars, 0)
        if (geez.isNotEmpty()) connection.commitText(geez, 1)
        connection.endBatchEdit()
        committedPhoneticChars = geez.length
        lastCommitAt = SystemClock.uptimeMillis()
        updateShiftVisual()
        requestSuggestions()
    }

    private fun backspacePhonetic() {
        val units = GeezTransliterator.units(phoneticBuffer)
        if (units.isEmpty()) {
            finalisePhonetic()
            deleteOneCharacter()
            session.onBackspace()
            requestSuggestions()
            return
        }
        phoneticBuffer = units.dropLast(1).joinToString("") { it.first }
        renderPhonetic()
    }

    private fun finalisePhonetic() {
        phoneticBuffer = ""
        committedPhoneticChars = 0
    }

    // ------------------------------------------------------------------ suggestions

    private fun requestSuggestions() {
        if (!this::strip.isInitialized) return
        val current = session.currentWord
        val previous = session.previousWord
        if (!settings.showSuggestions || !editor.allowsSuggestions || panel != Panel.NONE) {
            strip.setItems(emptyList())
            return
        }
        val cacheKey = "${current.lowercase()}\u0000${previous?.lowercase()}\u0000${settings.nextWordPrediction}\u0000${session.lastCorrection != null}"
        if (cacheKey == lastSuggestionKey) {
            applySuggestionItems(currentSuggestions)
            return
        }
        val token = ++suggestionToken
        val predictionEnabled = settings.nextWordPrediction
        val emojiEnabled = settings.emojiSuggestions && emojiIndex != null
        worker.execute {
            val suggestions = predictionEngine.suggestions(
                currentWord = current,
                previousWord = previous,
                limit = 3,
                predictionEnabled = predictionEnabled,
                emojiEnabled = emojiEnabled,
            )
            val items = suggestions.map {
                SuggestionStripView.Item(
                    label = it.text,
                    payload = it.text,
                    isEmoji = it.isEmoji,
                )
            }
            mainHandler.post {
                if (destroyed) return@post
                if (token != suggestionToken) return@post
                lastSuggestionKey = cacheKey
                currentSuggestions = items
                if (settings.showSuggestions && editor.allowsSuggestions && panel == Panel.NONE) {
                    applySuggestionItems(items)
                }
            }
        }
    }

    private var currentSuggestions: List<SuggestionStripView.Item> = emptyList()

    private fun applySuggestionItems(items: List<SuggestionStripView.Item>) {
        if (!this::strip.isInitialized) return
        val list = ArrayList<SuggestionStripView.Item>(items.size + 1)
        if (session.lastCorrection != null) {
            list.add(SuggestionStripView.Item(context.getString(R.string.undo_autocorrect), "undo", isAction = true))
        }
        list.addAll(items)
        strip.setItems(list)
        applyPanelVisibility()
    }

    override fun onSuggestionSelected(item: SuggestionStripView.Item) {
        if (panel == Panel.EMOJI_SEARCH) {
            if (item.isAction) exitEmojiSearch() else insertEmoji(item.payload)
            return
        }
        if (item.isAction) {
            revertCorrection()
            return
        }
        val connection = ic() ?: return
        val word = session.currentWord
        connection.beginBatchEdit()
        if (word.isNotEmpty()) connection.deleteSurroundingText(word.length, 0)
        connection.commitText(item.payload + " ", 1)
        connection.endBatchEdit()
        lastCommitAt = SystemClock.uptimeMillis()
        session.finishWord()
        session.clearCorrection()
        session.setPreviousWord(item.payload)
        session.setAutoCapitalise(false)
        lastSuggestionKey = ""
        shift.applyAutoCapitalisation(false)
        updateShiftVisual()
        requestSuggestions()
    }

    override fun onSuggestionLongPressed(item: SuggestionStripView.Item) {
        if (item.isAction || item.isEmoji) return
        val word = item.payload.lowercase()
        if (word.isBlank()) return
        if (userDictionary.isPersonal(word)) {
            userDictionary.unlearn(word)
            toast(context.getString(R.string.suggestion_remove_word, word))
        } else {
            userDictionary.addPersonal(word)
            toast(context.getString(R.string.suggestion_added, word))
        }
        lastSuggestionKey = ""
        requestSuggestions()
    }

    private fun maybeAutocorrect() {
        if (!settings.autoCorrect || !editor.allowsSuggestions || session.lastCorrection != null) return
        if (language != Language.ENGLISH) return
        val word = session.currentWord
        if (word.length < 3) return
        val connection = ic() ?: return
        val before = try {
            connection.getTextBeforeCursor(word.length, 0)?.toString()
        } catch (e: Exception) {
            null
        }
        if (before != word) return
        val correction = autoCorrector.correctionFor(word) ?: return
        connection.beginBatchEdit()
        connection.deleteSurroundingText(word.length, 0)
        connection.commitText(correction.replacement, 1)
        connection.endBatchEdit()
        lastCommitAt = SystemClock.uptimeMillis()
        session.markCorrection(correction.typed, correction.replacement)
        correctionTrailing = ""
        lastSuggestionKey = ""
    }

    private fun revertCorrection() {
        val correction = session.clearCorrection() ?: return
        val connection = ic() ?: return
        val trailing = correctionTrailing
        correctionTrailing = ""
        connection.beginBatchEdit()
        connection.deleteSurroundingText(correction.second.length + trailing.length, 0)
        connection.commitText(correction.first + trailing, 1)
        connection.endBatchEdit()
        lastCommitAt = SystemClock.uptimeMillis()
        lastSuggestionKey = ""
        updateShiftVisual()
        requestSuggestions()
    }

    private fun learnCurrentWord() {
        if (!settings.learnWords || !editor.allowsPersonalization) return
        val word = session.currentWord
        if (word.length < 3) return
        if (word.any { !it.isLetter() && it != '\'' }) return
        if (dictionary.contains(word)) return
        userDictionary.learn(word)
    }

    private fun lastCorrectionVisualClear() {
        lastSuggestionKey = ""
    }

    private fun resyncFromEditor() {
        val connection = ic() ?: return
        val text = try {
            connection.getTextBeforeCursor(64, 0)
        } catch (e: Exception) {
            null
        }
        session.syncFromText(text)
        phoneticBuffer = ""
        committedPhoneticChars = 0
        updateShiftVisual()
        requestSuggestions()
    }

    // ---------------------------------------------------------------------- panels

    private fun showEmojiPanel() {
        if (panel == Panel.EMOJI) {
            closePanels()
            return
        }
        if (panel == Panel.EMOJI_SEARCH) {
            panel = Panel.EMOJI
            updateEmojiSearch()
            applyPanelVisibility()
            return
        }
        panel = Panel.EMOJI
        if (emojiIndex != null) emojiPanel.bind(emojiIndex!!, emojiRecents)
        applyPanelVisibility()
    }

    private fun showClipboardPanel() {
        if (panel == Panel.CLIPBOARD) {
            closePanels()
            return
        }
        panel = Panel.CLIPBOARD
        refreshClipboardPanel()
        applyPanelVisibility()
    }

    private fun refreshClipboardPanel() {
        clipboardPanel.bind(
            repo = clipboardRepository,
            maxEntries = settings.clipboardMaxEntries,
            enabled = settings.clipboardEnabled,
        )
    }

    private fun closePanels() {
        panel = Panel.NONE
        applyPanelVisibility()
    }

    private fun applyPanelVisibility() {
        if (!this::frame.isInitialized) return
        val panelOpen = panel != Panel.NONE
        keyboardView.visibility = if (panel == Panel.NONE || panel == Panel.EMOJI_SEARCH) View.VISIBLE else View.INVISIBLE
        emojiPanel.visibility = if (panel == Panel.EMOJI) View.VISIBLE else View.GONE
        clipboardPanel.visibility = if (panel == Panel.CLIPBOARD) View.VISIBLE else View.GONE
        toolbar.setActive(
            when (panel) {
                Panel.EMOJI, Panel.EMOJI_SEARCH -> ToolbarView.Action.EMOJI
                Panel.CLIPBOARD -> ToolbarView.Action.CLIPBOARD
                Panel.NONE -> null
            },
        )
        val showStrip = panel == Panel.EMOJI_SEARCH ||
            (settings.showSuggestions && editor.allowsSuggestions && panel == Panel.NONE)
        strip.visibility = if (showStrip) View.VISIBLE else View.GONE
        if (panel == Panel.EMOJI_SEARCH) renderEmojiSearchStrip() else if (panel == Panel.NONE) {
            strip.setItems(if (session.lastCorrection != null) {
                listOf(SuggestionStripView.Item(context.getString(R.string.undo_autocorrect), "undo", isAction = true)) + currentSuggestions
            } else {
                currentSuggestions
            })
        } else {
            strip.setItems(emptyList())
        }
        if (panelOpen && this::strip.isInitialized) keyboardView.release()
    }

    override fun onToolbarAction(action: ToolbarView.Action) {
        when (action) {
            ToolbarView.Action.EMOJI -> showEmojiPanel()
            ToolbarView.Action.CLIPBOARD -> showClipboardPanel()
            ToolbarView.Action.THEME -> openSettings(appearanceSection = true)
            ToolbarView.Action.SETTINGS -> openSettings(appearanceSection = false)
            ToolbarView.Action.LANGUAGE -> cycleLanguage()
        }
    }

    private fun openSettings(appearanceSection: Boolean = false) {
        val intent = Intent(context, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (appearanceSection) putExtra(SettingsActivity.EXTRA_SECTION, SettingsActivity.SECTION_APPEARANCE)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            toast(context.getString(R.string.settings_title))
        }
    }

    // -------------------------------------------------------------------- languages

    private fun cycleLanguage() {
        val enabled = settings.languages.mapNotNull { Language.fromId(it) }.ifEmpty { listOf(Language.ENGLISH) }
        if (enabled.size <= 1) {
            val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            manager?.showInputMethodPicker()
            return
        }
        val index = enabled.indexOf(language).coerceAtLeast(0)
        language = enabled[(index + 1) % enabled.size]
        shift.reset()
        session.reset()
        phoneticBuffer = ""
        committedPhoneticChars = 0
        layoutKind = lettersKind()
        updateLayout()
        updateShiftVisual()
        lastSuggestionKey = ""
        requestSuggestions()
        toolbar.setLanguageLabel(if (language == Language.AMHARIC) "አማ" else "EN")
    }

    // ------------------------------------------------------------------------ emoji

    override fun onEmojiSelected(emoji: String) {
        insertEmoji(emoji)
    }

    override fun onEmojiLongPressed(emoji: String) {
        insertEmoji(emoji)
    }

    override fun onClosePanel() {
        closePanels()
    }

    override fun onSearchRequested() {
        panel = Panel.EMOJI_SEARCH
        emojiQuery = ""
        emojiResults = emptyList()
        layoutKind = LayoutKind.LETTERS
        updateLayout()
        applyPanelVisibility()
        updateEmojiSearch()
    }

    private fun insertEmoji(emoji: String) {
        val connection = ic() ?: return
        connection.commitText(emoji, 1)
        emojiRecents.markUsed(emoji)
        lastCommitAt = SystemClock.uptimeMillis()
    }

    private fun updateEmojiSearch() {
        val index = emojiIndex
        emojiResults = if (index == null || emojiQuery.isBlank()) emptyList() else index.search(emojiQuery, 24)
        renderEmojiSearchStrip()
    }

    private fun renderEmojiSearchStrip() {
        val items = ArrayList<SuggestionStripView.Item>()
        items.add(SuggestionStripView.Item("✕", "close", isAction = true))
        if (emojiQuery.isNotEmpty()) {
            items.add(SuggestionStripView.Item(emojiQuery, "query", isAction = false))
        }
        for (entry in emojiResults.take(8)) {
            items.add(SuggestionStripView.Item(entry.emoji, entry.emoji, isEmoji = true))
        }
        strip.setItems(items)
    }

    private fun insertFirstEmojiResult() {
        val first = emojiResults.firstOrNull() ?: return
        insertEmoji(first.emoji)
        emojiQuery = ""
        updateEmojiSearch()
    }

    private fun exitEmojiSearch() {
        emojiQuery = ""
        emojiResults = emptyList()
        panel = Panel.EMOJI
        applyPanelVisibility()
    }

    // --------------------------------------------------------------------- clipboard

    override fun onInsert(text: String) {
        val connection = ic() ?: return
        connection.commitText(text, 1)
        lastCommitAt = SystemClock.uptimeMillis()
        closePanels()
    }

    override fun onPin(id: Long, pinned: Boolean) {
        clipboardRepository.setPinned(id, pinned)
        refreshClipboardPanel()
    }

    override fun onDelete(id: Long) {
        clipboardRepository.remove(id)
        refreshClipboardPanel()
    }

    override fun onClearAll() {
        clipboardRepository.clear()
        refreshClipboardPanel()
    }

    override fun onSaveCurrentClipboard() {
        val text = readClipboardText()
        if (text == null) {
            clipboardPanel.setStatus(context.getString(R.string.clipboard_unavailable))
            return
        }
        val saved = clipboardRepository.save(text, maxEntries = settings.clipboardMaxEntries)
        if (saved) {
            clipboardPanel.setStatus(context.getString(R.string.clipboard_saved))
        } else {
            clipboardPanel.setStatus(context.getString(R.string.clipboard_unavailable))
        }
        refreshClipboardPanel()
    }

    private fun readClipboardText(): String? {
        return try {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
            if (!manager.hasPrimaryClip()) return null
            val clip = manager.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            val text = clip.getItemAt(0)?.coerceToText(context)?.toString()
            text?.takeIf { it.isNotBlank() }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun registerClipboardCapture() {
        val enabled = settings.clipboardEnabled && settings.clipboardCapture
        if (!enabled) {
            unregisterClipboardCapture()
            return
        }
        if (clipboardListener != null) return
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            val text = readClipboardText() ?: return@OnPrimaryClipChangedListener
            if (ClipboardCapturePolicy.shouldCapture(
                    enabled = settings.clipboardEnabled,
                    enabledByUser = settings.clipboardCapture,
                    isSensitiveField = editor.isSensitive,
                    text = text,
                )
            ) {
                clipboardRepository.save(text, maxEntries = settings.clipboardMaxEntries)
                if (panel == Panel.CLIPBOARD) mainHandler.post { refreshClipboardPanel() }
            }
        }
        clipboardListener = listener
        manager.addPrimaryClipChangedListener(listener)
    }

    private fun unregisterClipboardCapture() {
        val listener = clipboardListener ?: return
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.removePrimaryClipChangedListener(listener)
        clipboardListener = null
    }

    // ------------------------------------------------------------------- helpers

    private fun ic(): InputConnection? = service.currentInputConnection

    private fun toast(message: String) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } catch (ignored: Exception) {
        }
    }

    private fun applyInputTypeHints(attribute: EditorInfo) {
        // Nothing beyond EditorState.from() is required, but this is the hook
        // where per-field customisation (e.g. numeric hints) would go.
        @Suppress("UNUSED_PARAMETER") val ignored = attribute
    }

    private fun loadAssetsAsync() {
        worker.execute {
            val dictionary = try {
                context.assets.open("dictionaries/en_words.txt").use { Dictionary.parse(it) }
            } catch (e: Exception) {
                Dictionary.EMPTY
            }
            val bigrams = try {
                context.assets.open("dictionaries/en_bigrams.txt").use { BigramModel.parse(it) }
            } catch (e: Exception) {
                BigramModel.EMPTY
            }
            val emojiIndex = try {
                context.assets.open("emoji/emoji.tsv").bufferedReader().useLines { lines ->
                    EmojiIndex.parse(lines)
                }
            } catch (e: Exception) {
                EmojiIndex(emptyList())
            }
            mainHandler.post {
                if (destroyed) return@post
                this.dictionary = dictionary
                this.bigrams = bigrams
                this.emojiIndex = emojiIndex
                wordSource = CompositeWordSource(dictionary, userDictionary)
                predictionEngine = PredictionEngine(wordSource, bigrams, emojiIndex)
                autoCorrector = AutoCorrector(wordSource)
                assetsLoaded = true
                if (this::emojiPanel.isInitialized) emojiPanel.bind(emojiIndex, emojiRecents)
                lastSuggestionKey = ""
                requestSuggestions()
            }
        }
    }

    companion object {
        const val PREFS_NAME = "my_keyboard_prefs"
    }
}
