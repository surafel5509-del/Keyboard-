package com.novakeyboard.ui.clipboard

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.novakeyboard.R
import com.novakeyboard.data.clipboard.ClipEntry
import com.novakeyboard.data.clipboard.ClipboardRepository
import com.novakeyboard.ui.Ui
import com.novakeyboard.ui.Ui.dp
import com.novakeyboard.ui.Ui.dpF
import com.novakeyboard.ui.theme.KeyboardTheme
import com.novakeyboard.ui.theme.ThemePresets
import com.novakeyboard.utils.TimeFormat

/** Local clipboard history panel: insert, pin, delete, clear. */
class ClipboardPanelView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onInsert(text: String)
        fun onPin(id: Long, pinned: Boolean)
        fun onDelete(id: Long)
        fun onClearAll()
        fun onSaveCurrentClipboard()
        fun onClosePanel()
    }

    private var theme: KeyboardTheme = ThemePresets.midnight
    private var repository: ClipboardRepository? = null
    private var enabled = true
    private var maxEntries = ClipboardRepository.DEFAULT_MAX
    private var status: String? = null

    private val header = Ui.linearRow(context)
    private val titleView: TextView
    private val backButton: TextView
    private val saveButton: TextView
    private val content = Ui.linearColumn(context)
    private val scrollView = ScrollView(context)
    private val footer = Ui.linearRow(context)
    private val clearButton: TextView

    var listener: Listener? = null

    init {
        orientation = VERTICAL
        background = Ui.roundedBackground(theme.backgroundTop, context.dpF(16f))

        titleView = Ui.textView(context, context.getString(R.string.clipboard_title), 14f, theme.textPrimary, bold = true).apply {
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }
        backButton = Ui.textView(context, "ABC", 13f, theme.accent, bold = true).apply {
            gravity = Gravity.CENTER
            contentDescription = context.getString(R.string.clipboard_back)
            layoutParams = LayoutParams(context.dp(56f), LayoutParams.MATCH_PARENT)
            setOnClickListener { listener?.onClosePanel() }
        }
        saveButton = Ui.textView(context, context.getString(R.string.clipboard_save_current), 12f, theme.accent).apply {
            gravity = Gravity.CENTER
            setPadding(context.dp(10f), 0, context.dp(10f), 0)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            setOnClickListener { listener?.onSaveCurrentClipboard() }
        }
        header.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, context.dp(38f))
        header.addView(backButton)
        header.addView(titleView)
        header.addView(saveButton)
        addView(header)

        scrollView.isVerticalScrollBarEnabled = false
        scrollView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        content.orientation = VERTICAL
        content.setPadding(context.dp(10f), context.dp(4f), context.dp(10f), context.dp(4f))
        scrollView.addView(content)
        addView(scrollView)

        footer.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, context.dp(34f))
        footer.setPadding(context.dp(12f), 0, context.dp(12f), 0)
        clearButton = Ui.textView(context, context.getString(R.string.clipboard_clear), 12f, theme.accent).apply {
            gravity = Gravity.CENTER
            setPadding(context.dp(8f), 0, context.dp(8f), 0)
            setOnClickListener { listener?.onClearAll() }
        }
        footer.addView(clearButton)
        val spacer = View(context)
        spacer.layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        footer.addView(spacer)
        addView(footer)
    }

    fun applyTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        background = Ui.roundedBackground(theme.backgroundTop, context.dpF(16f))
        titleView.setTextColor(theme.textPrimary)
        backButton.setTextColor(theme.accent)
        saveButton.setTextColor(theme.accent)
        clearButton.setTextColor(theme.accent)
        render()
    }

    fun bind(repo: ClipboardRepository, maxEntries: Int, enabled: Boolean, statusMessage: String? = null) {
        repository = repo
        this.maxEntries = maxEntries
        this.enabled = enabled
        status = statusMessage
        render()
    }

    fun setStatus(message: String?) {
        status = message
        render()
    }

    private fun render() {
        content.removeAllViews()
        if (!enabled) {
            content.addView(hintText(context.getString(R.string.clipboard_disabled)))
            return
        }
        val entries = repository?.entries().orEmpty()
        if (entries.isEmpty()) {
            content.addView(hintText(context.getString(R.string.clipboard_empty)))
            return
        }
        val now = System.currentTimeMillis()
        for (entry in entries.take(maxEntries)) {
            content.addView(buildRow(entry, now))
        }
        status?.let { content.addView(hintText(it)) }
    }

    private fun hintText(text: String): TextView = Ui.textView(context, text, 13f, theme.textSecondary).apply {
        gravity = Gravity.CENTER
        setPadding(context.dp(12f), context.dp(22f), context.dp(12f), context.dp(12f))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun buildRow(entry: ClipEntry, now: Long): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(12f), context.dp(8f), context.dp(12f), context.dp(6f))
            background = Ui.roundedBackground(
                KeyboardTheme.blend(theme.keyBackground, 0.35f, theme.backgroundTop),
                context.dpF(10f),
            )
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(6f)
            }
            setOnClickListener { listener?.onInsert(entry.text) }
        }
        val preview = entry.text.replace('\n', ' ').let {
            if (it.length > 160) it.substring(0, 160) + "…" else it
        }
        val text = Ui.textView(context, preview, 13f, theme.textPrimary).apply {
            maxLines = 3
            setOnLongClickListener {
                listener?.onInsert(entry.text)
                true
            }
        }
        card.addView(text)

        val meta = Ui.linearRow(context)
        meta.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, context.dp(24f))
        val time = Ui.textView(context, TimeFormat.relative(now, entry.createdAt), 11f, theme.textSecondary)
        time.layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        meta.addView(time)
        if (entry.pinned) {
            val pinned = Ui.textView(context, context.getString(R.string.clipboard_pinned), 11f, theme.accent, bold = true)
            pinned.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            meta.addView(pinned)
        }
        val pin = Ui.textView(
            context,
            if (entry.pinned) context.getString(R.string.clipboard_unpin) else context.getString(R.string.clipboard_pin),
            11f,
            theme.accent,
        ).apply {
            gravity = Gravity.CENTER
            setPadding(context.dp(8f), 0, context.dp(8f), 0)
            setOnClickListener { listener?.onPin(entry.id, !entry.pinned) }
        }
        meta.addView(pin)
        val delete = Ui.textView(context, "✕", 12f, theme.textSecondary).apply {
            gravity = Gravity.CENTER
            contentDescription = context.getString(R.string.clipboard_delete)
            setPadding(context.dp(8f), 0, context.dp(4f), 0)
            setOnClickListener { listener?.onDelete(entry.id) }
        }
        meta.addView(delete)
        card.addView(meta)
        return card
    }
}
