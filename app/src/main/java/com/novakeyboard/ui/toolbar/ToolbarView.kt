package com.novakeyboard.ui.toolbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.novakeyboard.R
import com.novakeyboard.ui.keyboard.Icons
import com.novakeyboard.ui.keyboard.KeyIcon
import com.novakeyboard.ui.theme.KeyboardTheme
import com.novakeyboard.ui.theme.ThemePresets
import kotlin.math.min

/**
 * Compact toolbar above the suggestion strip: emoji, clipboard, themes and
 * settings on the left, the language chip on the right. Every button performs a
 * real action — there are no decorative controls.
 */
class ToolbarView(context: Context) : View(context) {

    enum class Action { EMOJI, CLIPBOARD, THEME, SETTINGS, LANGUAGE }

    interface Listener {
        fun onToolbarAction(action: Action)
    }

    private class Button(val action: Action, val icon: KeyIcon?, val label: String?, var rect: RectF = RectF())

    private val density = resources.displayMetrics.density
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var theme: KeyboardTheme = ThemePresets.midnight
    private var languageLabel: String = "EN"
    private var activeAction: Action? = null
    private var pressedAction: Action? = null
    private var buttons: List<Button> = emptyList()
    private val backgroundPath = Path()
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundRect = RectF()

    var listener: Listener? = null

    init {
        contentDescription = context.getString(R.string.toolbar_emoji)
        isClickable = true
    }

    fun applyTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        rebuild()
        invalidate()
    }

    fun setLanguageLabel(label: String) {
        languageLabel = label
        rebuild()
        invalidate()
    }

    fun setActive(action: Action?) {
        activeAction = action
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (40f * density).toInt().coerceAtLeast(1))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun rebuild() {
        if (width == 0) return
        val size = 40f * density
        val startX = 10f * density
        val list = mutableListOf(
            Button(Action.EMOJI, KeyIcon.EMOJI, context.getString(R.string.toolbar_emoji)),
            Button(Action.CLIPBOARD, KeyIcon.CLIPBOARD, context.getString(R.string.toolbar_clipboard)),
            Button(Action.THEME, KeyIcon.PALETTE, context.getString(R.string.toolbar_theme)),
            Button(Action.SETTINGS, KeyIcon.GEAR, context.getString(R.string.toolbar_settings)),
        )
        var x = startX
        for (button in list) {
            button.rect.set(x, 4f * density, x + size * 0.86f, height - 4f * density)
            x += size * 0.86f
        }
        val chipWidth = 60f * density
        list.add(
            Button(Action.LANGUAGE, null, languageLabel).also {
                it.rect.set((width - chipWidth - 10f * density), 6f * density, width - 10f * density, height - 6f * density)
            },
        )
        buttons = list
    }

    override fun onDraw(canvas: Canvas) {
        val radius = 16f * density
        backgroundRect.set(0f, 0f, width.toFloat(), height + radius)
        backgroundPath.reset()
        backgroundPath.addRoundRect(
            backgroundRect,
            floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f),
            Path.Direction.CW,
        )
        backgroundPaint.color = theme.backgroundTop
        backgroundPaint.alpha = theme.backgroundAlpha
        canvas.drawPath(backgroundPath, backgroundPaint)
        val centerY = height / 2f
        for (button in buttons) {
            val active = activeAction == button.action || pressedAction == button.action
            if (button.action == Action.LANGUAGE) {
                chipPaint.color = if (active) {
                    KeyboardTheme.blend(theme.accent, 0.75f, theme.backgroundBottom)
                } else {
                    KeyboardTheme.blend(theme.keyBackground, 0.35f, theme.backgroundBottom)
                }
                canvas.drawRoundRect(button.rect, button.rect.height() / 2f, button.rect.height() / 2f, chipPaint)
                textPaint.color = if (active) theme.accent else theme.textSecondary
                textPaint.textSize = min(button.rect.height() * 0.38f, 13f * density)
                val baseline = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(button.label ?: "", button.rect.centerX(), baseline, textPaint)
            } else {
                if (active) {
                    chipPaint.color = KeyboardTheme.blend(theme.keyBackground, 0.5f, theme.accent)
                    val chip = RectF(button.rect)
                    chip.inset(-2f * density, 2f * density)
                    canvas.drawRoundRect(chip, 10f * density, 10f * density, chipPaint)
                }
                iconPaint.color = if (active) theme.accent else theme.textSecondary
                Icons.draw(
                    canvas,
                    button.icon ?: KeyIcon.NONE,
                    button.rect.centerX(),
                    centerY,
                    min(button.rect.width() * 0.52f, 19f * density),
                    iconPaint,
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedAction = buttonAt(event.x, event.y)
                invalidate()
                return pressedAction != null
            }
            MotionEvent.ACTION_MOVE -> {
                val action = buttonAt(event.x, event.y)
                if (action != pressedAction) {
                    pressedAction = action
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val action = buttonAt(event.x, event.y)
                pressedAction = null
                invalidate()
                if (action != null) listener?.onToolbarAction(action)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedAction = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun buttonAt(x: Float, y: Float): Action? {
        for (button in buttons) {
            if (button.rect.contains(x, y)) return button.action
        }
        return null
    }
}
