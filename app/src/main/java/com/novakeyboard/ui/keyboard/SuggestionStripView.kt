package com.novakeyboard.ui.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import android.view.View
import com.novakeyboard.ui.theme.KeyboardTheme
import com.novakeyboard.ui.theme.ThemePresets
import kotlin.math.min

/**
 * The suggestion strip. Shows up to three word suggestions plus an optional
 * emoji chip and the "Undo" chip that appears after an autocorrection.
 */
class SuggestionStripView(context: Context) : View(context) {

    data class Item(
        val label: String,
        val payload: String,
        val isEmoji: Boolean = false,
        val isAction: Boolean = false,
    )

    interface Listener {
        fun onSuggestionSelected(item: Item)
        fun onSuggestionLongPressed(item: Item)
    }

    private val density = resources.displayMetrics.density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hitRect = RectF()

    private var theme: KeyboardTheme = ThemePresets.midnight
    private var items: List<Item> = emptyList()
    private var layoutItems: List<Pair<Item, RectF>> = emptyList()
    private var pressedIndex = -1
    private var downTime = 0L
    private val longPressRunnable = Runnable {
        val index = pressedIndex
        if (index in items.indices) {
            listener?.onSuggestionLongPressed(items[index])
            pressedIndex = -1
            invalidate()
        }
    }

    var listener: Listener? = null

    init {
        isClickable = true
        contentDescription = context.getString(com.novakeyboard.R.string.settings_suggestions)
    }

    fun applyTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        invalidate()
    }

    fun setItems(newItems: List<Item>) {
        items = newItems
        rebuildLayout()
        invalidate()
    }

    fun hasItems(): Boolean = items.isNotEmpty()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (38f * density).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), desired)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLayout()
    }

    private fun rebuildLayout() {
        layoutItems = emptyList()
        if (width == 0 || items.isEmpty()) return
        val padding = 14f * density
        val gap = 16f * density
        val maxItemWidth = width * 0.40f
        textPaint.textSize = 15.5f * density
        var x = padding
        val result = ArrayList<Pair<Item, RectF>>(items.size)
        for (item in items) {
            val textWidth = min(textPaint.measureText(item.label) + 6f * density, maxItemWidth)
            hitRect.set(x, 0f, x + textWidth, height.toFloat())
            result.add(item to RectF(hitRect))
            x += textWidth + gap
        }
        layoutItems = result
    }

    override fun onDraw(canvas: Canvas) {
        backgroundPaint.color = theme.backgroundTop
        backgroundPaint.alpha = theme.backgroundAlpha
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        if (items.isEmpty()) return
        val centerY = height / 2f
        dividerPaint.color = theme.keyBorder
        for ((index, pair) in layoutItems.withIndex()) {
            val item = pair.first
            val rect = pair.second
            if (index == pressedIndex) {
                chipPaint.color = KeyboardTheme.blend(theme.keyBackground, 0.55f, theme.accent)
                canvas.drawRoundRect(
                    rect.left - 6f * density, centerY - 13f * density,
                    rect.right + 6f * density, centerY + 13f * density,
                    13f * density, 13f * density, chipPaint,
                )
            }
            textPaint.textSize = if (item.isEmoji) 20f * density else 15.5f * density
            textPaint.color = when {
                item.isAction -> theme.accent
                item.isEmoji -> theme.textPrimary
                else -> theme.textPrimary
            }
            val text = ellipsize(item.label, rect.width())
            val baseline = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(text, rect.left, baseline, textPaint)
            // divider
            if (index != layoutItems.lastIndex) {
                dividerPaint.alpha = 90
                val dividerX = rect.right + 8f * density
                canvas.drawRect(dividerX, centerY - 8f * density, dividerX + 1f * density, centerY + 8f * density, dividerPaint)
            }
        }
    }

    private fun ellipsize(text: String, maxWidth: Float): String {
        if (textPaint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && textPaint.measureText(text, 0, end) + 8f * density > maxWidth) end--
        return text.substring(0, end).trimEnd() + "…"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = indexAt(event.x, event.y)
                downTime = System.currentTimeMillis()
                if (pressedIndex >= 0) {
                    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                    if (manager?.isEnabled == true && manager.isTouchExplorationEnabled) {
                        items.getOrNull(pressedIndex)?.let { announceForAccessibility(it.label) }
                    }
                    postDelayed(longPressRunnable, 420)
                    invalidate()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val index = indexAt(event.x, event.y)
                if (index != pressedIndex) {
                    removeCallbacks(longPressRunnable)
                    pressedIndex = index
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                val index = pressedIndex
                pressedIndex = -1
                invalidate()
                if (index in items.indices && System.currentTimeMillis() - downTime < 420) {
                    listener?.onSuggestionSelected(items[index])
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                pressedIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun indexAt(x: Float, y: Float): Int {
        for ((index, pair) in layoutItems.withIndex()) {
            if (pair.second.contains(x, y)) return index
        }
        return -1
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(longPressRunnable)
    }
}
