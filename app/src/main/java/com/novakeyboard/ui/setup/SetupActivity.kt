package com.novakeyboard.ui.setup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.novakeyboard.R
import com.novakeyboard.ui.Ui
import com.novakeyboard.ui.Ui.dp
import com.novakeyboard.ui.privacy.PrivacyActivity
import com.novakeyboard.ui.settings.SettingsActivity
import com.novakeyboard.ui.theme.ThemePresets

/**
 * First-run screen: enable the IME, select it, and try it in a real text field.
 * The enabled/selected state is re-read in onResume so it is always accurate.
 */
class SetupActivity : Activity() {

    private val theme = ThemePresets.midnight

    private lateinit var statusEnabled: TextView
    private lateinit var statusSelected: TextView
    private lateinit var selectButton: View
    private lateinit var testField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.statusBarColor = theme.backgroundTop
        window?.navigationBarColor = theme.backgroundTop
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(theme.backgroundTop)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24f), dp(40f), dp(24f), dp(32f))
        }
        scroll.addView(column)

        column.addView(
            Ui.textView(this, getString(R.string.app_name), 30f, theme.textPrimary, bold = true).apply {
                letterSpacing = 0.04f
            },
        )
        column.addView(
            Ui.textView(this, getString(R.string.setup_tagline), 14f, theme.accent).apply {
                setPadding(0, dp(4f), 0, dp(14f))
            },
        )
        column.addView(
            Ui.textView(this, getString(R.string.setup_subtitle), 14f, theme.textSecondary).apply {
                setLineSpacing(dp(3f).toFloat(), 1f)
                setPadding(0, 0, 0, dp(18f))
            },
        )

        // ---- step 1
        val step1 = card()
        step1.addView(stepHeader(getString(R.string.setup_step_enable_title)))
        statusEnabled = Ui.textView(this, "", 13f, theme.textSecondary).apply {
            setPadding(0, dp(4f), 0, dp(10f))
        }
        step1.addView(statusEnabled)
        step1.addView(
            primaryButton(getString(R.string.setup_open_ime_settings)) {
                openImeSettings()
            },
        )
        column.addView(step1)

        // ---- step 2
        val step2 = card()
        step2.addView(stepHeader(getString(R.string.setup_step_select_title)))
        statusSelected = Ui.textView(this, "", 13f, theme.textSecondary).apply {
            setPadding(0, dp(4f), 0, dp(10f))
        }
        step2.addView(statusSelected)
        selectButton = primaryButton(getString(R.string.setup_select_keyboard)) {
            showPicker()
        }
        step2.addView(selectButton)
        column.addView(step2)

        // ---- step 3
        val step3 = card()
        step3.addView(stepHeader(getString(R.string.setup_step_try_title)))
        step3.addView(
            Ui.textView(this, getString(R.string.setup_step_try_body), 13f, theme.textSecondary).apply {
                setPadding(0, dp(2f), 0, dp(10f))
            },
        )
        testField = EditText(this).apply {
            hint = getString(R.string.setup_test_hint)
            setTextColor(theme.textPrimary)
            setHintTextColor(theme.textSecondary)
            background = Ui.roundedBackground(theme.keyBackground, dp(10f).toFloat(), theme.keyBorder, dp(1f))
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            minLines = 2
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        step3.addView(
            testField,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(84f)),
        )
        column.addView(step3)

        // ---- secondary actions
        column.addView(
            secondaryButton(getString(R.string.setup_customize)) {
                startActivity(Intent(this, SettingsActivity::class.java))
            },
        )
        column.addView(
            secondaryButton(getString(R.string.setup_privacy)) {
                startActivity(Intent(this, PrivacyActivity::class.java))
            },
        )

        column.addView(
            Ui.textView(this, getString(R.string.setup_offline_note), 12f, theme.textSecondary).apply {
                setPadding(0, dp(16f), 0, dp(6f))
                setLineSpacing(dp(3f).toFloat(), 1f)
            },
        )
        return scroll
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.roundedBackground(theme.keyBackground, dp(18f).toFloat(), theme.keyBorder, dp(1f))
        setPadding(dp(16f), dp(14f), dp(16f), dp(16f))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14f)
        }
    }

    private fun stepHeader(text: String): TextView = Ui.textView(this, text, 16f, theme.textPrimary, bold = true)

    private fun primaryButton(text: String, onClick: () -> Unit): View {
        val button = Ui.textView(this, text, 14f, theme.onAccent, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16f), dp(13f), dp(16f), dp(13f))
            background = Ui.ripple(this@SetupActivity, theme.accent, dp(12f).toFloat(), 0x66FFFFFF)
            minimumHeight = dp(46f)
            setOnClickListener { onClick() }
        }
        button.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return button
    }

    private fun secondaryButton(text: String, onClick: () -> Unit): View {
        val button = Ui.textView(this, text, 14f, theme.textPrimary).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            background = Ui.ripple(this@SetupActivity, theme.keyBackground, dp(12f).toFloat(), theme.accent)
            minimumHeight = dp(46f)
            setOnClickListener { onClick() }
        }
        button.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(10f)
        }
        return button
    }

    private fun refreshStatus() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val enabled = manager?.enabledInputMethodList?.any { it.packageName == packageName } == true
        val selected = manager?.currentInputMethodInfo?.packageName == packageName
        statusEnabled.text = getString(if (enabled) R.string.setup_enabled else R.string.setup_not_enabled)
        statusEnabled.setTextColor(if (enabled) theme.accent else theme.textSecondary)
        statusSelected.text = getString(if (selected) R.string.setup_selected else R.string.setup_not_selected)
        statusSelected.setTextColor(if (selected) theme.accent else theme.textSecondary)
        selectButton.visibility = if (enabled && !selected) View.VISIBLE else if (selected) View.GONE else View.VISIBLE
    }

    private fun openImeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (ignored: Exception) {
            }
        }
    }

    private fun showPicker() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (manager == null) {
            openImeSettings()
            return
        }
        try {
            manager.showInputMethodPicker()
        } catch (e: Exception) {
            openImeSettings()
        }
    }
}
