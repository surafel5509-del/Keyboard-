package com.novakeyboard.ui.privacy

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.novakeyboard.R
import com.novakeyboard.ui.Ui
import com.novakeyboard.ui.Ui.dp
import com.novakeyboard.ui.theme.ThemePresets

/** Static, offline privacy and permissions statement rendered from strings. */
class PrivacyActivity : Activity() {

    private val theme = ThemePresets.midnight

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.statusBarColor = theme.backgroundTop
        window?.navigationBarColor = theme.backgroundTop
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(theme.backgroundTop)
            isFillViewport = true
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(24f), dp(20f), dp(32f))
        }
        scroll.addView(column)

        column.addView(
            Ui.textView(this, getString(R.string.privacy_title), 26f, theme.textPrimary, bold = true),
        )
        column.addView(
            Ui.textView(this, getString(R.string.privacy_intro), 14f, theme.textSecondary).apply {
                setPadding(0, dp(8f), 0, dp(16f))
                setLineSpacing(dp(3f).toFloat(), 1f)
            },
        )

        val sections = listOf(
            R.string.privacy_no_internet_title to R.string.privacy_no_internet_body,
            R.string.privacy_local_title to R.string.privacy_local_body,
            R.string.privacy_fields_title to R.string.privacy_fields_body,
            R.string.privacy_clipboard_title to R.string.privacy_clipboard_body,
            R.string.privacy_backup_title to R.string.privacy_backup_body,
            R.string.privacy_permissions_title to R.string.privacy_permissions_body,
            R.string.privacy_data_title to R.string.privacy_data_body,
        )
        for ((title, body) in sections) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.roundedBackground(theme.keyBackground, dp(16f).toFloat(), theme.keyBorder, dp(1f))
                setPadding(dp(16f), dp(14f), dp(16f), dp(16f))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(12f)
                }
            }
            card.addView(Ui.textView(this, getString(title), 16f, theme.textPrimary, bold = true))
            card.addView(
                Ui.textView(this, getString(body), 13f, theme.textSecondary).apply {
                    setPadding(0, dp(6f), 0, 0)
                    setLineSpacing(dp(3f).toFloat(), 1f)
                },
            )
            column.addView(card)
        }
        return scroll
    }
}
