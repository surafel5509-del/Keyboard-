package com.novakeyboard.utils

import android.content.Context
import android.util.TypedValue
import kotlin.math.roundToInt

fun Context.dp(value: Float): Float = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    value,
    resources.displayMetrics,
)

fun Context.dpInt(value: Float): Int = dp(value).roundToInt()

fun Context.spToPx(value: Float): Float = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_SP,
    value,
    resources.displayMetrics,
)
