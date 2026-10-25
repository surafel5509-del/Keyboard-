package com.novakeyboard.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.novakeyboard.R
import com.novakeyboard.data.dictionary.UserDictionary
import com.novakeyboard.data.prefs.Settings
import com.novakeyboard.data.prefs.SettingsRepository
import com.novakeyboard.data.prefs.SharedPrefsStore
import com.novakeyboard.domain.language.Language
import com.novakeyboard.ime.KeyboardController
import com.novakeyboard.security.EnterAction
import com.novakeyboard.ui.Ui
import com.novakeyboard.ui.Ui.dp
import com.novakeyboard.ui.keyboard.KeyboardLayouts
import com.novakeyboard.ui.keyboard.KeyboardView
import com.novakeyboard.ui.keyboard.LayoutKind
import com.novakeyboard.ui.privacy.PrivacyActivity
import com.novakeyboard.ui.theme.KeyboardTheme
import com.novakeyboard.ui.theme.ThemePresets

/**
 * All user preferences with a live keyboard preview. Every control writes
 * straight to [SettingsRepository], so the running IME updates immediately.
 */
class SettingsActivity : Activity() {

    private lateinit var repository: SettingsRepository
    private lateinit var userDictionary: UserDictionary
    private lateinit var settings: Settings
    private lateinit var theme: KeyboardTheme

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var scrollView: ScrollView
    private var preview: KeyboardView? = null
    private var scrollToAppearance = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SettingsRepository(SharedPrefsStore(getSharedPreferences(KeyboardController.PREFS_NAME, Context.MODE_PRIVATE)))
        userDictionary = UserDictionary(SharedPrefsStore(getSharedPreferences(KeyboardController.PREFS_NAME, Context.MODE_PRIVATE)))
        settings = repository.current()
        theme = ThemePresets.resolve(settings)
        scrollToAppearance = intent?.getStringExtra(EXTRA_SECTION) == SECTION_APPEARANCE
        buildUi()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.backgroundTop)
        }
        root.addView(buildHeader())
        scrollView = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(4f), dp(16f), dp(28f))
        }
        scrollView.addView(content)
        root.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        renderSections()
        if (scrollToAppearance) scrollView.post { scrollView.smoothScrollTo(0, 0) }
    }

    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6f), dp(10f), dp(16f), dp(10f))
            setBackgroundColor(theme.backgroundTop)
        }
        val back = Ui.textView(this, "‹", 26f, theme.accent).apply {
            gravity = Gravity.CENTER
            contentDescription = getString(R.string.done)
            layoutParams = LinearLayout.LayoutParams(dp(46f), dp(46f))
            setOnClickListener { finish() }
        }
        val title = Ui.textView(this, getString(R.string.settings_title), 20f, theme.textPrimary, bold = true)
        title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        header.addView(back)
        header.addView(title)
        return header
    }

    private fun renderSections() {
        content.removeAllViews()
        renderAppearance()
        renderTyping()
        renderLanguages()
        renderPrivacy()
        renderDictionary()
        renderAbout()
    }

    // ------------------------------------------------------------------ appearance

    private fun renderAppearance() {
        content.addView(sectionHeader(getString(R.string.settings_appearance)))

        val previewFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132f))
            background = Ui.roundedBackground(theme.backgroundBottom, dp(14f).toFloat())
            setPadding(dp(6f), dp(8f), dp(6f), dp(8f))
        }
        val previewView = KeyboardView(this).apply {
            interactive = false
            applyAppearance(theme, 30f, settings.keyBorders)
            setLayout(
                KeyboardLayouts.build(
                    Language.ENGLISH,
                    LayoutKind.LETTERS,
                    uppercase = false,
                    enterAction = EnterAction.NEWLINE,
                    numberRow = settings.numberRow,
                ),
            )
        }
        preview = previewView
        previewFrame.addView(
            previewView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            },
        )
        content.addView(previewFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132f)).apply {
            bottomMargin = dp(12f)
        })

        content.addView(
            label(getString(R.string.settings_theme_preset)),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4f)
                bottomMargin = dp(6f)
            },
        )
        content.addView(presetGrid())
        space(10f)

        content.addView(colorRow(getString(R.string.settings_key_color), theme.keyBackground) { color ->
            repository.setCustomKey(color)
            refresh()
        })
        content.addView(colorRow(getString(R.string.settings_background_color), theme.backgroundTop) { color ->
            repository.setCustomBackground(color)
            refresh()
        })
        content.addView(colorRow(getString(R.string.settings_text_color), theme.textPrimary) { color ->
            repository.setCustomText(color)
            refresh()
        })
        content.addView(colorRow(getString(R.string.settings_accent_color), theme.accent) { color ->
            repository.setCustomAccent(color)
            refresh()
        })

        content.addView(
            sliderRow(
                getString(R.string.settings_key_radius),
                settings.keyRadiusDp, 2f, 24f,
            ) { value ->
                repository.setKeyRadius(value)
                refreshPreviewOnly()
            },
        )
        content.addView(
            sliderRow(
                getString(R.string.settings_key_height),
                settings.keyHeightScale, 0.8f, 1.35f,
            ) { value ->
                repository.setKeyHeightScale(value)
                refreshPreviewOnly()
            },
        )
        content.addView(
            sliderRow(
                getString(R.string.settings_opacity),
                settings.backgroundOpacity, 0.6f, 1f,
            ) { value ->
                repository.setOpacity(value)
                refreshPreviewOnly()
            },
        )

        content.addView(
            switchRow(
                getString(R.string.settings_borders),
                null,
                settings.keyBorders,
            ) { enabled ->
                repository.setKeyBorders(enabled)
                refreshPreviewOnly()
            },
        )
        content.addView(
            switchRow(
                getString(R.string.settings_number_row),
                null,
                settings.numberRow,
            ) { enabled -> repository.setNumberRow(enabled); refresh() },
        )
        content.addView(
            switchRow(
                getString(R.string.settings_suggestions),
                null,
                settings.showSuggestions,
            ) { enabled -> repository.setShowSuggestions(enabled); refresh() },
        )
        content.addView(
            switchRow(
                getString(R.string.settings_toolbar),
                null,
                settings.showToolbar,
            ) { enabled -> repository.setShowToolbar(enabled); refresh() },
        )
    }

    private fun presetGrid(): View {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val presets = ThemePresets.all
        var row: LinearLayout? = null
        for ((index, preset) in presets.withIndex()) {
            if (index % 3 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74f))
                }
                grid.addView(row)
            }
            val selected = preset.id == settings.themePresetId
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(3f), dp(4f), dp(3f), dp(4f))
                }
                background = Ui.ripple(
                    this@SettingsActivity,
                    preset.keyBackground,
                    dp(10f).toFloat(),
                    adjustAlpha(preset.accent, 0.35f),
                )
                setOnClickListener {
                    repository.setThemePreset(preset.id)
                    repository.setCustomKey(null)
                    repository.setCustomBackground(null)
                    repository.setCustomText(null)
                    repository.setCustomAccent(null)
                    refresh()
                }
            }
            cell.addView(Ui.textView(this, preset.displayName, 11f, preset.textPrimary).apply {
                gravity = Gravity.CENTER
                maxLines = 2
            })
            if (selected) {
                val check = Ui.textView(this, "●", 10f, preset.accent).apply { gravity = Gravity.CENTER }
                cell.addView(check)
            }
            row?.addView(cell)
        }
        return grid
    }

    // ----------------------------------------------------------------------- typing

    private fun renderTyping() {
        content.addView(sectionHeader(getString(R.string.settings_typing)))
        content.addView(switchRow(getString(R.string.settings_sound), null, settings.soundEnabled) {
            repository.setSound(it); refresh()
        })
        content.addView(
            sliderRow(getString(R.string.settings_sound_volume), settings.soundVolume, 0f, 1f) {
                repository.setSoundVolume(it)
            },
        )
        content.addView(switchRow(getString(R.string.settings_haptics), null, settings.hapticsEnabled) {
            repository.setHaptics(it); refresh()
        })
        content.addView(switchRow(getString(R.string.settings_autocap), null, settings.autoCapitalize) {
            repository.setAutoCapitalize(it); refresh()
        })
        content.addView(switchRow(getString(R.string.settings_autocorrect), null, settings.autoCorrect) {
            repository.setAutoCorrect(it); refresh()
        })
        content.addView(switchRow(getString(R.string.settings_prediction), null, settings.nextWordPrediction) {
            repository.setNextWord(it); refresh()
        })
        content.addView(switchRow(getString(R.string.settings_emoji_suggestions), null, settings.emojiSuggestions) {
            repository.setEmojiSuggestions(it); refresh()
        })
        content.addView(switchRow(getString(R.string.settings_longpress), null, settings.longPressAlternates) {
            repository.setLongPress(it); refresh()
        })
        content.addView(switchRow(getString(R.string.settings_space_cursor), null, settings.spacebarCursor) {
            repository.setSpacebarCursor(it); refresh()
        })
        content.addView(switchRow(getString(R.string.settings_learning), null, settings.learnWords) {
            repository.setLearnWords(it); refresh()
        })
    }

    // -------------------------------------------------------------------- languages

    private fun renderLanguages() {
        content.addView(sectionHeader(getString(R.string.settings_languages)))
        val enabled = settings.languages.toMutableList()
        content.addView(switchRow(getString(R.string.settings_language_en), null, enabled.contains("en")) { on ->
            if (on) {
                if (!enabled.contains("en")) enabled.add("en")
            } else {
                enabled.remove("en")
                if (enabled.isEmpty()) {
                    enabled.add("en")
                    toast(getString(R.string.settings_language_hint))
                }
            }
            repository.setLanguages(enabled)
            refresh()
        })
        content.addView(switchRow(getString(R.string.settings_language_am), null, enabled.contains("am")) { on ->
            if (on) {
                if (!enabled.contains("am")) enabled.add("am")
            } else {
                enabled.remove("am")
            }
            repository.setLanguages(enabled)
            refresh()
        })
        if (enabled.contains("am")) {
            val options = listOf(
                getString(R.string.settings_am_mode_translit),
                getString(R.string.settings_am_mode_geez),
            )
            val selected = if (settings.amharicMode == Settings.AMHARIC_GEEZ) 1 else 0
            content.addView(
                choiceRow(getString(R.string.settings_am_mode), options, selected) { index ->
                    repository.setAmharicMode(if (index == 1) Settings.AMHARIC_GEEZ else Settings.AMHARIC_PHONETIC)
                    refresh()
                },
            )
            content.addView(note(getString(R.string.settings_am_dictionary_note)))
        } else {
            content.addView(note(getString(R.string.settings_no_amharic)))
        }
        content.addView(note(getString(R.string.settings_language_hint)))
    }

    // ---------------------------------------------------------------------- privacy

    private fun renderPrivacy() {
        content.addView(sectionHeader(getString(R.string.settings_privacy)))
        content.addView(switchRow(getString(R.string.settings_clipboard), null, settings.clipboardEnabled) {
            repository.setClipboardEnabled(it); refresh()
        })
        content.addView(switchRow(getString(R.string.settings_clipboard_capture), null, settings.clipboardCapture) {
            repository.setClipboardCapture(it); refresh()
        })
        val sizes = listOf(10, 20, 50)
        val selected = sizes.indexOfFirst { it >= settings.clipboardMaxEntries }.let { if (it < 0) 1 else it }
        content.addView(
            choiceRow(
                getString(R.string.settings_clipboard_max),
                sizes.map { it.toString() },
                selected,
            ) { index ->
                repository.setClipboardMax(sizes[index])
                refresh()
            },
        )
        content.addView(actionRow(getString(R.string.settings_clear_clipboard), null) {
            confirm(getString(R.string.settings_clear_clipboard_confirm)) {
                com.novakeyboard.data.clipboard.ClipboardRepository(
                    SharedPrefsStore(getSharedPreferences(KeyboardController.PREFS_NAME, Context.MODE_PRIVATE)),
                ).clear()
                toast(getString(R.string.settings_clear_clipboard))
            }
        })
        content.addView(actionRow(getString(R.string.settings_clear_learned), null) {
            confirm(getString(R.string.settings_clear_learned_confirm)) {
                userDictionary.clearLearned()
                toast(getString(R.string.settings_clear_learned))
                renderSections()
            }
        })
        content.addView(actionRow(getString(R.string.settings_open_privacy), null) {
            startActivity(Intent(this, PrivacyActivity::class.java))
        })
    }

    // ------------------------------------------------------------------- dictionary

    private fun renderDictionary() {
        content.addView(sectionHeader(getString(R.string.settings_dictionary)))
        val learned = userDictionary.learnedCount()
        content.addView(note(getString(R.string.settings_learned_count, learned)))
        content.addView(actionRow(getString(R.string.settings_add_word), null) { showAddWordDialog() })
        val personal = userDictionary.personalWords().sorted()
        if (personal.isEmpty()) {
            content.addView(note(getString(R.string.settings_remove_word)))
        } else {
            for (word in personal) {
                content.addView(
                    actionRow(word, getString(R.string.settings_remove_word)) {
                        userDictionary.unlearn(word)
                        renderSections()
                    },
                )
            }
        }
    }

    private fun showAddWordDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.settings_add_word_hint)
            setSingleLine()
            setTextColor(theme.textPrimary)
            setHintTextColor(theme.textSecondary)
        }
        val container = LinearLayout(this).apply {
            setPadding(dp(20f), dp(8f), dp(20f), 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_add_word))
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                val word = input.text.toString().trim()
                if (word.isNotEmpty()) {
                    userDictionary.addPersonal(word)
                    renderSections()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------------ about

    private fun renderAbout() {
        content.addView(sectionHeader(getString(R.string.settings_about)))
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
        content.addView(note(getString(R.string.settings_version, version)))
        content.addView(actionRow(getString(R.string.settings_restore_defaults), null) {
            confirm(getString(R.string.settings_restore_confirm)) {
                repository.restoreDefaults()
                refresh()
                toast(getString(R.string.reset))
            }
        })
    }

    // ----------------------------------------------------------------------- widgets

    private fun sectionHeader(title: String): View {
        val view = Ui.textView(this, title.uppercase(), 12f, theme.accent, bold = true).apply {
            letterSpacing = 0.12f
            setPadding(0, dp(22f), 0, dp(8f))
        }
        return view
    }

    private fun label(text: String): TextView = Ui.textView(this, text, 14f, theme.textPrimary)

    private fun note(text: String): TextView = Ui.textView(this, text, 12f, theme.textSecondary).apply {
        setPadding(0, dp(6f), 0, dp(10f))
        setLineSpacing(dp(3f).toFloat(), 1f)
    }

    private fun space(dpValue: Float): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dpValue))
    }

    private fun switchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10f), 0, dp(10f))
            minimumHeight = dp(52f)
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(Ui.textView(this, title, 15f, theme.textPrimary))
        subtitle?.let { texts.addView(Ui.textView(this, it, 12f, theme.textSecondary)) }
        val toggle = Switch(this).apply {
            isChecked = checked
            showText = false
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(theme.onAccent, theme.textSecondary),
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(theme.accent, adjustAlpha(theme.textSecondary, 0.35f)),
            )
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }
        row.addView(texts)
        row.addView(toggle)
        row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
        return row
    }

    private fun sliderRow(
        title: String,
        value: Float,
        min: Float,
        max: Float,
        onChange: (Float) -> Unit,
    ): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8f), 0, dp(4f))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val labelView = Ui.textView(this, title, 15f, theme.textPrimary)
        labelView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val valueView = Ui.textView(this, formatValue(value), 13f, theme.textSecondary)
        header.addView(labelView)
        header.addView(valueView)
        column.addView(header)
        val steps = 100
        val bar = SeekBar(this).apply {
            this.max = steps
            progress = (((value - min) / (max - min)) * steps).toInt().coerceIn(0, steps)
            progressTintList = ColorStateList.valueOf(theme.accent)
            thumbTintList = ColorStateList.valueOf(theme.accent)
        }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val computed = min + (max - min) * (progress.toFloat() / steps)
                valueView.text = formatValue(computed)
                if (fromUser) onChange(computed)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        column.addView(bar)
        return column
    }

    private fun formatValue(value: Float): String = String.format(java.util.Locale.US, "%.2f", value)

    private fun colorRow(title: String, color: Int, onPick: (Int?) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10f), 0, dp(10f))
            minimumHeight = dp(52f)
        }
        val labelView = Ui.textView(this, title, 15f, theme.textPrimary)
        labelView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val swatch = View(this).apply {
            background = Ui.roundedBackground(color, dp(9f).toFloat(), adjustAlpha(theme.textSecondary, 0.5f), dp(1f))
            layoutParams = LinearLayout.LayoutParams(dp(34f), dp(34f))
            contentDescription = title
        }
        row.addView(labelView)
        row.addView(swatch)
        row.setOnClickListener { showColorDialog(title, color, onPick) }
        return row
    }

    private fun showColorDialog(title: String, current: Int, onPick: (Int?) -> Unit) {
        val palette = intArrayOf(
            0xFF10131A.toInt(), 0xFF1D212B.toInt(), 0xFF2B3141.toInt(), 0xFF3C4354.toInt(),
            0xFFFFFFFF.toInt(), 0xFFF2F4F8.toInt(), 0xFFD7DCE5.toInt(), 0xFF9AA3B2.toInt(),
            0xFF7C5CFF.toInt(), 0xFF3D8BFF.toInt(), 0xFF3DD68C.toInt(), 0xFF4ECDC4.toInt(),
            0xFFFF6B8A.toInt(), 0xFFFF9F43.toInt(), 0xFFFFD93D.toInt(), 0xFFC86BFF.toInt(),
            0xFF0B0D12.toInt(), 0xFF071626.toInt(), 0xFF0B2137.toInt(), 0xFF120D22.toInt(),
            0xFF1A1230.toInt(), 0xFF0B1712.toInt(), 0xFF102019.toInt(), 0xFF191C21.toInt(),
        )
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(12f), dp(18f), dp(4f))
        }
        var row: LinearLayout? = null
        for ((index, color) in palette.withIndex()) {
            if (index % 6 == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                grid.addView(row)
            }
            val cell = View(this).apply {
                background = Ui.roundedBackground(color, dp(8f).toFloat(), adjustAlpha(theme.textSecondary, 0.4f), dp(1f))
                layoutParams = LinearLayout.LayoutParams(0, dp(40f), 1f).apply {
                    setMargins(dp(3f), dp(3f), dp(3f), dp(3f))
                }
                contentDescription = String.format("#%06X", 0xFFFFFF and color)
                setOnClickListener {
                    onPick(color)
                    (parent?.parent?.parent as? AlertDialog)?.dismiss()
                }
            }
            row?.addView(cell)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(grid)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.reset) { _, _ ->
                onPick(null)
            }
            .create()
        dialog.setOnShowListener {
            // Allow swatch clicks to close the dialog they live in.
            grid.tag = dialog
        }
        dialog.show()
        for (i in 0 until grid.childCount) {
            val childRow = grid.getChildAt(i) as LinearLayout
            for (j in 0 until childRow.childCount) {
                val cell = childRow.getChildAt(j)
                cell.setOnClickListener {
                    onPick(palette[i * 6 + j])
                    dialog.dismiss()
                }
            }
        }
    }

    private fun choiceRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10f), 0, dp(6f))
        }
        column.addView(Ui.textView(this, title, 15f, theme.textPrimary))
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8f), 0, 0)
        }
        for ((index, option) in options.withIndex()) {
            val active = index == selected
            val chip = Ui.textView(this, option, 12f, if (active) theme.onAccent else theme.textPrimary).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
                background = Ui.ripple(
                    this@SettingsActivity,
                    if (active) theme.accent else adjustAlpha(theme.textSecondary, 0.16f),
                    dp(10f).toFloat(),
                    adjustAlpha(theme.accent, 0.3f),
                )
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dp(8f)
                }
                setOnClickListener { onSelect(index) }
            }
            chips.addView(chip)
        }
        column.addView(chips)
        return column
    }

    private fun actionRow(title: String, subtitle: String?, onClick: () -> Unit): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12f), 0, dp(12f))
            minimumHeight = dp(48f)
            background = Ui.ripple(this@SettingsActivity, Color.TRANSPARENT, dp(10f).toFloat(), adjustAlpha(theme.accent, 0.18f))
        }
        column.addView(Ui.textView(this, title, 15f, theme.textPrimary))
        subtitle?.let { column.addView(Ui.textView(this, it, 12f, theme.textSecondary)) }
        column.setOnClickListener { onClick() }
        return column
    }

    private fun confirm(message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refresh() {
        settings = repository.current()
        theme = ThemePresets.resolve(settings)
        root.setBackgroundColor(theme.backgroundTop)
        renderSections()
    }

    private fun refreshPreviewOnly() {
        settings = repository.current()
        theme = ThemePresets.resolve(settings)
        preview?.applyAppearance(theme, 30f, settings.keyBorders)
        preview?.setLayout(
            KeyboardLayouts.build(
                Language.ENGLISH,
                LayoutKind.LETTERS,
                uppercase = false,
                enterAction = EnterAction.NEWLINE,
                numberRow = settings.numberRow,
            ),
        )
    }

    private fun adjustAlpha(color: Int, alpha: Float): Int =
        Color.argb((255 * alpha).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /** Unused, kept to avoid a warning about the typeface import in older toolchains. */
    @Suppress("unused")
    private val typeface: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    companion object {
        const val EXTRA_SECTION = "section"
        const val SECTION_APPEARANCE = "appearance"
    }
}
