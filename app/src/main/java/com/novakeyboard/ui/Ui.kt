package com.novakeyboard.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Small helpers for the programmatic (framework-only) UI. */
object Ui {

    fun Context.dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()

    fun Context.dpF(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    fun roundedBackground(fillColor: Int, radiusPx: Float, strokeColor: Int? = null, strokeWidthPx: Int = 0): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.cornerRadius = radiusPx
        drawable.setColor(fillColor)
        if (strokeColor != null && strokeWidthPx > 0) drawable.setStroke(strokeWidthPx, strokeColor)
        return drawable
    }

    fun ripple(context: Context, fillColor: Int, radiusPx: Float, rippleColor: Int): RippleDrawable {
        val mask = roundedBackground(Color.WHITE, radiusPx)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), roundedBackground(fillColor, radiusPx), mask)
    }

    fun textView(
        context: Context,
        text: CharSequence,
        textSizeSp: Float,
        color: Int,
        bold: Boolean = false,
    ): TextView = TextView(context).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
    }

    fun linearRow(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun linearColumn(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    fun divider(context: Context, color: Int): View = View(context).apply {
        setBackgroundColor(color)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(1f))
    }

    fun marginParams(
        context: Context,
        width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        left: Float = 0f,
        top: Float = 0f,
        right: Float = 0f,
        bottom: Float = 0f,
    ): LinearLayout.LayoutParams = LinearLayout.LayoutParams(width, height).apply {
        setMargins(context.dp(left), context.dp(top), context.dp(right), context.dp(bottom))
    }
}
