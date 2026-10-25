package com.novakeyboard.ui.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.novakeyboard.ime.ShiftState
import com.novakeyboard.ui.theme.KeyboardTheme
import com.novakeyboard.ui.theme.ThemePresets
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The keyboard surface. Everything is drawn on a single Canvas with pre-built
 * Paints and key rectangles, so a frame costs a few dozen draw calls and no
 * allocations — comfortably inside the 16 ms frame budget while typing.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        /** Fired on touch-down so the host can play a sound / haptic tick. */
        fun onKeyPressed(key: Key)

        /** Fired when a key action should be executed. */
        fun onKeyCommitted(key: Key)

        /** A long-press alternate character was chosen. */
        fun onAlternateSelected(text: String)

        /** Spacebar cursor swipe produced [steps] cursor moves (negative = left). */
        fun onCursorStep(steps: Int)

        /** Long-press on the spacebar (used to switch language). */
        fun onSpaceLongPressed()
    }

    private class PlacedKey(
        val key: Key,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
        fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom
    }

    private class PopupState(val anchor: PlacedKey, val items: List<String>) {
        var selected: Int = 0
        var left: Float = 0f
        var top: Float = 0f
        var cellWidth: Float = 0f
        var height: Float = 0f
    }

    private val density = resources.displayMetrics.density

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000 }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val keyRect = RectF()
    private val popupRect = RectF()
    private val cursorRect = RectF()
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var theme: KeyboardTheme = ThemePresets.midnight
    private var layout: KeyLayout? = null
    private var placedRows: List<List<PlacedKey>> = emptyList()

    private var rowHeightPx: Float = 54f * density
    private var keyGapPx: Float = 3f * density
    private var radiusPx: Float = 11f * density
    private val horizontalPaddingPx: Float = 3f * density
    private val verticalPaddingPx: Float = 5f * density

    var uppercase: Boolean = false
        private set
    var shiftState: ShiftState = ShiftState.OFF
        private set
    var spaceCursorEnabled: Boolean = true
    var longPressAlternatesEnabled: Boolean = true
    var showBorders: Boolean = false
    var interactive: Boolean = true

    var listener: Listener? = null

    private var pressedKey: PlacedKey? = null
    private var popup: PopupState? = null
    private var activePointerId = -1
    private var downX = 0f
    private var downY = 0f
    private var longPressConsumed = false
    private var cursorMode = false
    private var cursorAccumulated = 0f
    private var cursorSteps = 0
    private var repeatCount = 0

    private val longPressRunnable = Runnable { handleLongPress() }
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val key = pressedKey ?: return
            if (key.key.type != KeyType.BACKSPACE) return
            listener?.onKeyCommitted(key.key)
            repeatCount++
            handler.postDelayed(this, if (repeatCount > 18) 38L else 62L)
        }
    }

    init {
        isClickable = true
        isFocusable = false
        contentDescription = context.getString(com.novakeyboard.R.string.ime_label)
    }

    // ---------------------------------------------------------------- appearance

    fun applyAppearance(newTheme: KeyboardTheme, keyHeightDp: Float, borders: Boolean) {
        theme = newTheme
        rowHeightPx = keyHeightDp * density
        keyGapPx = newTheme.keyGapDp * density
        radiusPx = newTheme.keyRadiusDp * density
        showBorders = borders
        rebuildBackgroundShader()
        requestLayout()
        rebuild()
        invalidate()
    }

    fun setLayout(newLayout: KeyLayout) {
        cancelPress()
        layout = newLayout
        requestLayout()
        rebuild()
        invalidate()
    }

    fun setShift(state: ShiftState) {
        shiftState = state
        uppercase = state != ShiftState.OFF
        invalidate()
    }

    fun currentLayout(): KeyLayout? = layout

    // ------------------------------------------------------------------ geometry

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = layout?.rows?.size ?: 4
        val desiredHeight = (rows * rowHeightPx + 2 * verticalPaddingPx).toInt()
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), desiredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildBackgroundShader()
        rebuild()
    }

    private fun rebuild() {
        val current = layout ?: return
        val width = width.toFloat()
        if (width <= 0f) return
        val available = width - horizontalPaddingPx * 2
        val rows = ArrayList<List<PlacedKey>>(current.rows.size)
        for ((rowIndex, row) in current.rows.withIndex()) {
            val totalWeight = row.keys.sumOf { it.weight.toDouble() }.toFloat()
            val gaps = keyGapPx * max(0, row.keys.size - 1)
            val unit = if (totalWeight > 0f) (available - gaps) / totalWeight else 0f
            var x = horizontalPaddingPx
            val top = verticalPaddingPx + rowIndex * rowHeightPx
            val bottom = top + rowHeightPx
            val placed = ArrayList<PlacedKey>(row.keys.size)
            for (key in row.keys) {
                val keyWidth = unit * key.weight
                placed.add(PlacedKey(key, x, top, x + keyWidth, bottom))
                x += keyWidth + keyGapPx
            }
            rows.add(placed)
        }
        placedRows = rows
    }

    private fun rebuildBackgroundShader() {
        val h = max(height, 1).toFloat()
        backgroundPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            theme.backgroundTop,
            theme.backgroundBottom,
            Shader.TileMode.CLAMP,
        )
    }

    // -------------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        drawBackground(canvas)
        for (row in placedRows) {
            for (placed in row) {
                if (placed.key.type == KeyType.SPACER) continue
                drawKey(canvas, placed)
            }
        }
        drawPopup(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        backgroundPaint.alpha = theme.backgroundAlpha
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
    }

    private fun drawKey(canvas: Canvas, placed: PlacedKey) {
        val key = placed.key
        val inset = keyGapPx / 2f
        keyRect.set(placed.left + inset, placed.top + inset, placed.right - inset, placed.bottom - inset)
        val pressed = placed === pressedKey
        val specialized = key.type != KeyType.CHAR && key.type != KeyType.SPACE
        var background = if (specialized) {
            KeyboardTheme.blend(theme.keyBackground, 0.45f, theme.backgroundBottom)
        } else {
            theme.keyBackground
        }
        var foreground = if (specialized) theme.textSecondary else theme.textPrimary
        if (key.type == KeyType.ENTER) {
            background = theme.accent
            foreground = theme.onAccent
        }
        if (key.type == KeyType.SHIFT && shiftState != ShiftState.OFF) {
            background = theme.accent
            foreground = theme.onAccent
        }
        if (pressed) {
            background = if (background == theme.accent) {
                KeyboardTheme.blend(theme.accent, 0.18f, 0xFFFFFFFF.toInt())
            } else {
                theme.keyPressed
            }
        }
        if (key.type == KeyType.SPACE && cursorMode) {
            background = KeyboardTheme.blend(theme.keyBackground, 0.35f, theme.accent)
        }

        if (!pressed) {
            shadowPaint.color = 0x22000000
            canvas.drawRoundRect(
                keyRect.left, keyRect.top + 1.5f * density, keyRect.right, keyRect.bottom + 1.5f * density,
                radiusPx, radiusPx, shadowPaint,
            )
        }
        keyPaint.color = background
        canvas.drawRoundRect(keyRect, radiusPx, radiusPx, keyPaint)

        if (showBorders) {
            borderPaint.color = theme.keyBorder
            borderPaint.strokeWidth = 1f * density
            canvas.drawRoundRect(keyRect, radiusPx, radiusPx, borderPaint)
        }

        when {
            key.type == KeyType.SPACE -> drawSpaceAffordance(canvas, keyRect)
            key.icon != KeyIcon.NONE -> {
                iconPaint.color = foreground
                Icons.draw(
                    canvas, key.icon, keyRect.centerX(), keyRect.centerY(),
                    min(keyRect.height() * 0.52f, keyRect.width() * 0.5f), iconPaint,
                    filled = key.type == KeyType.SHIFT && shiftState == ShiftState.CAPS,
                )
            }
            key.label.isNotEmpty() -> {
                labelPaint.color = foreground
                val size = labelSizeFor(key, keyRect)
                labelPaint.textSize = size
                val baseline = keyRect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f
                canvas.drawText(key.labelFor(uppercase), keyRect.centerX(), baseline, labelPaint)
                key.topLabel?.let { top ->
                    secondaryPaint.color = theme.textSecondary
                    secondaryPaint.textSize = min(rowHeightPx * 0.26f, keyRect.width() * 0.3f)
                    canvas.drawText(
                        top,
                        keyRect.right - keyRect.width() * 0.14f,
                        keyRect.top + keyRect.height() * 0.3f,
                        secondaryPaint,
                    )
                }
            }
        }
    }

    private fun labelSizeFor(key: Key, rect: RectF): Float {
        val base = min(rowHeightPx * 0.42f, rect.width() * 0.66f)
        return when (key.type) {
            KeyType.CHAR -> if (key.label.length > 1) base * 0.7f else base
            else -> min(rowHeightPx * 0.3f, rect.width() * 0.42f)
        }
    }

    private fun drawSpaceAffordance(canvas: Canvas, rect: RectF) {
        secondaryPaint.color = KeyboardTheme.blend(theme.textSecondary, 0.55f, 0x00000000)
        val width = min(rect.width() * 0.34f, 120f * density)
        val height = 2.6f * density
        cursorRect.set(rect.centerX() - width / 2, rect.centerY() - height / 2, rect.centerX() + width / 2, rect.centerY() + height / 2)
        val paint = if (cursorMode) popupHighlightPaint.apply { color = theme.accent } else popupHighlightPaint.apply { color = theme.textSecondary }
        canvas.drawRoundRect(cursorRect, height, height, paint)
        if (cursorMode) {
            secondaryPaint.color = theme.accent
            secondaryPaint.textSize = min(rowHeightPx * 0.3f, 16f * density)
            val y = rect.centerY() - (secondaryPaint.descent() + secondaryPaint.ascent()) / 2f
            canvas.drawText("◀", rect.left + rect.width() * 0.18f, y, secondaryPaint)
            canvas.drawText("▶", rect.right - rect.width() * 0.18f, y, secondaryPaint)
        }
    }

    private fun drawPopup(canvas: Canvas) {
        val state = popup ?: return
        popupPaint.color = theme.popupBackground
        popupRect.set(state.left, state.top, state.left + state.cellWidth * state.items.size, state.top + state.height)
        shadowPaint.color = 0x55000000
        canvas.drawRoundRect(
            popupRect.left, popupRect.top + 2f * density, popupRect.right, popupRect.bottom + 3f * density,
            radiusPx * 1.2f, radiusPx * 1.2f, shadowPaint,
        )
        canvas.drawRoundRect(popupRect, radiusPx * 1.2f, radiusPx * 1.2f, popupPaint)
        val cell = RectF()
        popupTextPaint.textSize = min(state.height * 0.46f, state.cellWidth * 0.6f)
        for ((index, item) in state.items.withIndex()) {
            val left = state.left + index * state.cellWidth
            cell.set(left + 3f * density, state.top + 3f * density, left + state.cellWidth - 3f * density, state.top + state.height - 3f * density)
            if (index == state.selected) {
                popupHighlightPaint.color = theme.popupSelected
                canvas.drawRoundRect(cell, radiusPx, radiusPx, popupHighlightPaint)
                popupTextPaint.color = theme.onAccent
            } else {
                popupTextPaint.color = theme.textPrimary
            }
            val baseline = cell.centerY() - (popupTextPaint.descent() + popupTextPaint.ascent()) / 2f
            canvas.drawText(item, cell.centerX(), baseline, popupTextPaint)
        }
    }

    // --------------------------------------------------------------------- input

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                longPressConsumed = false
                cursorMode = false
                cursorAccumulated = 0f
                cursorSteps = 0
                pressedKey = keyAt(event.x, event.y)
                pressedKey?.let {
                    listener?.onKeyPressed(it.key)
                    handler.postDelayed(longPressRunnable, LONG_PRESS_DELAY)
                }
                invalidate()
                return pressedKey != null
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.getPointerId(event.actionIndex) != activePointerId) return true
                val state = popup
                if (state != null) {
                    val index = ((event.x - state.left) / state.cellWidth).toInt().coerceIn(0, state.items.size - 1)
                    if (index != state.selected) {
                        state.selected = index
                        listener?.onKeyPressed(pressedKey?.key ?: return true)
                        invalidate()
                    }
                    return true
                }
                val dx = event.x - downX
                val dy = event.y - downY
                val current = pressedKey
                if (current != null && current.key.type == KeyType.SPACE && spaceCursorEnabled && !cursorMode && abs(dx) > 10f * density) {
                    cursorMode = true
                    handler.removeCallbacks(longPressRunnable)
                    downX = event.x
                    invalidate()
                    return true
                }
                if (cursorMode) {
                    cursorAccumulated += event.x - downX
                    downX = event.x
                    val stepPx = 11f * density
                    val steps = (cursorAccumulated / stepPx).toInt()
                    if (steps != cursorSteps) {
                        listener?.onCursorStep(steps - cursorSteps)
                        cursorSteps = steps
                    }
                    return true
                }
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    handler.removeCallbacks(longPressRunnable)
                    val under = keyAt(event.x, event.y)
                    if (under !== current) {
                        pressedKey = under
                        under?.let { listener?.onKeyPressed(it.key) }
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                val state = popup
                if (state != null) {
                    // Releasing inside the alternate popup commits the highlighted character.
                    val selected = state.items.getOrNull(state.selected)
                    popup = null
                    if (selected != null) listener?.onAlternateSelected(selected)
                } else if (cursorMode) {
                    cursorMode = false
                    cursorAccumulated = 0f
                    cursorSteps = 0
                } else if (pressedKey != null) {
                    val under = keyAt(event.x, event.y)
                    val target = under ?: pressedKey
                    if (!longPressConsumed && target != null) {
                        listener?.onKeyCommitted(target.key)
                    }
                }
                cancelPress()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelPress()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleLongPress() {
        val placed = pressedKey ?: return
        longPressConsumed = true
        when (placed.key.type) {
            KeyType.BACKSPACE -> {
                listener?.onKeyCommitted(placed.key)
                repeatCount = 0
                handler.postDelayed(repeatRunnable, 70)
            }
            KeyType.SPACE -> listener?.onSpaceLongPressed()
            KeyType.CHAR -> {
                val items = placed.key.alternatesFor(uppercase)
                if (items.isNotEmpty() && longPressAlternatesEnabled) {
                    showPopup(placed, items)
                } else {
                    longPressConsumed = false
                }
            }
            else -> longPressConsumed = false
        }
        invalidate()
    }

    private fun showPopup(anchor: PlacedKey, items: List<String>) {
        val state = PopupState(anchor, items)
        val cell = max(rowHeightPx * 0.92f, 30f * density)
        state.cellWidth = cell
        state.height = rowHeightPx * 1.02f
        val totalWidth = cell * items.size
        state.left = (anchor.centerX - totalWidth / 2f).coerceIn(2f * density, width - totalWidth - 2f * density)
        var top = anchor.top - state.height - 6f * density
        if (top < 2f * density) top = anchor.bottom + 6f * density
        state.top = top
        state.selected = 0
        popup = state
        listener?.onKeyPressed(anchor.key)
    }

    private fun cancelPress() {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
        popup = null
        pressedKey = null
        repeatCount = 0
        activePointerId = -1
        invalidate()
    }

    private fun keyAt(x: Float, y: Float): PlacedKey? {
        for (row in placedRows) {
            if (row.isEmpty()) continue
            val first = row.first()
            val last = row.last()
            if (y < first.top || y > first.bottom) continue
            var best: PlacedKey? = null
            var bestDistance = Float.MAX_VALUE
            for (placed in row) {
                if (placed.key.type == KeyType.SPACER) continue
                if (placed.contains(x, y)) return placed
                val distance = abs(placed.centerX - x)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = placed
                }
            }
            // Fall back to the closest key in the same row so taps near the
            // edges still register on the intended key.
            if (best != null && x >= first.left - 6f * density && x <= last.right + 6f * density) return best
        }
        return null
    }

    /** Releases handlers when the IME view is torn down. */
    fun release() {
        cancelPress()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
    }

    companion object {
        const val LONG_PRESS_DELAY = 400L
    }
}
