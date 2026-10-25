package com.novakeyboard.ui.emoji

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.novakeyboard.R
import com.novakeyboard.ui.Ui
import com.novakeyboard.ui.Ui.dp
import com.novakeyboard.ui.Ui.dpF
import com.novakeyboard.ui.theme.KeyboardTheme
import com.novakeyboard.ui.theme.ThemePresets

/**
 * Fully offline emoji picker: category tabs, recents and a live search mode
 * (search is driven by the main keyboard, see KeyboardController).
 */
class EmojiPanelView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onEmojiSelected(emoji: String)
        fun onEmojiLongPressed(emoji: String)
        fun onClosePanel()
        fun onSearchRequested()
    }

    private val density = resources.displayMetrics.density
    private var theme: KeyboardTheme = ThemePresets.midnight
    private var index: EmojiIndex? = null
    private var recentsStore: EmojiRecentsStore? = null
    private var currentCategory = "recent"

    private val header = Ui.linearRow(context)
    private val tabsContainer = Ui.linearRow(context)
    private val content = Ui.linearColumn(context)
    private val scrollView = ScrollView(context)
    private val tabsScroll = HorizontalScrollView(context)
    private val backButton: TextView
    private val searchButton: TextView
    private val titleView: TextView

    var listener: Listener? = null

    init {
        orientation = VERTICAL
        setPadding(0, context.dp(4f), 0, 0)
        background = Ui.roundedBackground(theme.backgroundTop, context.dpF(16f))

        titleView = Ui.textView(context, context.getString(R.string.emoji_title), 14f, theme.textPrimary, bold = true)
        titleView.gravity = Gravity.CENTER
        backButton = Ui.textView(context, "ABC", 13f, theme.accent, bold = true).apply {
            gravity = Gravity.CENTER
            contentDescription = context.getString(R.string.emoji_back)
            layoutParams = LayoutParams(context.dp(56f), LayoutParams.MATCH_PARENT)
            setOnClickListener { listener?.onClosePanel() }
        }
        searchButton = Ui.textView(context, context.getString(R.string.emoji_search), 13f, theme.accent).apply {
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            setPadding(context.dp(12f), 0, context.dp(12f), 0)
            setOnClickListener { listener?.onSearchRequested() }
        }
        header.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, context.dp(36f))
        titleView.layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        header.addView(backButton)
        header.addView(titleView)
        header.addView(searchButton)
        addView(header)

        tabsScroll.isHorizontalScrollBarEnabled = false
        tabsScroll.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, context.dp(38f))
        tabsContainer.orientation = HORIZONTAL
        tabsContainer.setPadding(context.dp(6f), 0, context.dp(6f), 0)
        tabsScroll.addView(tabsContainer)
        addView(tabsScroll)

        scrollView.isVerticalScrollBarEnabled = false
        scrollView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        content.orientation = VERTICAL
        content.setPadding(context.dp(6f), context.dp(2f), context.dp(6f), context.dp(6f))
        scrollView.addView(content)
        addView(scrollView)
    }

    fun bind(emojiIndex: EmojiIndex, recents: EmojiRecentsStore) {
        index = emojiIndex
        recentsStore = recents
        buildTabs()
        showCategory(currentCategory)
    }

    fun applyTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        background = Ui.roundedBackground(theme.backgroundTop, context.dpF(16f))
        titleView.setTextColor(theme.textPrimary)
        backButton.setTextColor(theme.accent)
        searchButton.setTextColor(theme.accent)
        buildTabs()
        showCategory(currentCategory)
    }

    private fun buildTabs() {
        tabsContainer.removeAllViews()
        val allTabs = listOf(EmojiCategory("recent", "Recently used", "recent", "\uD83D\uDD52")) +
            EmojiIndex.CATEGORIES.filter { it.id != "recent" }
        for (category in allTabs) {
            val selected = category.id == currentCategory
            val chip = Ui.textView(
                context,
                category.sample,
                18f,
                if (selected) theme.onAccent else theme.textSecondary,
            ).apply {
                gravity = Gravity.CENTER
                contentDescription = category.title
                background = if (selected) {
                    Ui.roundedBackground(theme.accent, context.dpF(14f))
                } else {
                    Ui.roundedBackground(Color.TRANSPARENT, context.dpF(14f))
                }
                layoutParams = LayoutParams(context.dp(40f), context.dp(30f)).apply {
                    topMargin = context.dp(4f)
                    rightMargin = context.dp(6f)
                }
                setOnClickListener {
                    currentCategory = category.id
                    buildTabs()
                    showCategory(category.id)
                }
            }
            tabsContainer.addView(chip)
        }
    }

    private fun showCategory(categoryId: String) {
        val idx = index ?: return
        content.removeAllViews()
        currentCategory = categoryId
        val entries = when (categoryId) {
            "recent" -> {
                val stored = recentsStore?.recents().orEmpty()
                if (stored.isEmpty()) emptyList() else {
                    val map = idx.entries.associateBy { it.emoji }
                    stored.mapNotNull { map[it] }
                }
            }
            else -> idx.categoryEntries(categoryId)
        }
        if (entries.isEmpty()) {
            val hint = Ui.textView(
                context,
                if (categoryId == "recent") context.getString(R.string.emoji_recent) else context.getString(R.string.emoji_no_results),
                13f,
                theme.textSecondary,
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, context.dp(28f), 0, 0)
            }
            content.addView(hint)
            return
        }
        val columns = 8
        var row: LinearLayout? = null
        for ((i, entry) in entries.withIndex()) {
            if (i % columns == 0) {
                row = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, context.dp(42f))
                }
                content.addView(row)
            }
            val cell = Ui.textView(context, entry.emoji, 23f, theme.textPrimary).apply {
                gravity = Gravity.CENTER
                contentDescription = entry.name
                isClickable = true
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener { listener?.onEmojiSelected(entry.emoji) }
                setOnLongClickListener {
                    listener?.onEmojiLongPressed(entry.emoji)
                    true
                }
                background = Ui.ripple(context, Color.TRANSPARENT, context.dpF(8f), adjustAlpha(theme.accent, 0.25f))
            }
            row?.addView(cell)
        }
    }

    private fun adjustAlpha(color: Int, alpha: Float): Int =
        Color.argb((255 * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        content.removeAllViews()
    }

    /** Keeps the typeface reference alive for the IDE. */
    @Suppress("unused")
    private val typeface: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
}
