package com.novakeyboard.utils

/**
 * Pure colour maths used by the keyboard renderer and the settings screens.
 * No Android dependencies so the theme logic can be unit tested.
 */
object Colors {

    fun red(color: Int): Int = (color shr 16) and 0xFF
    fun green(color: Int): Int = (color shr 8) and 0xFF
    fun blue(color: Int): Int = color and 0xFF
    fun alpha(color: Int): Int = (color ushr 24) and 0xFF

    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        ((alpha and 0xFF) shl 24) or ((red and 0xFF) shl 16) or ((green and 0xFF) shl 8) or (blue and 0xFF)

    fun withAlpha(color: Int, alpha: Int): Int = argb(alpha, red(color), green(color), blue(color))

    /** Multiplies the RGB channels, keeping the alpha channel. */
    fun scale(color: Int, factor: Float): Int = argb(
        alpha(color),
        (red(color) * factor).toInt().coerceIn(0, 255),
        (green(color) * factor).toInt().coerceIn(0, 255),
        (blue(color) * factor).toInt().coerceIn(0, 255),
    )

    fun mix(first: Int, second: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        return argb(
            alpha(first),
            (red(first) + (red(second) - red(first)) * r).toInt().coerceIn(0, 255),
            (green(first) + (green(second) - green(first)) * r).toInt().coerceIn(0, 255),
            (blue(first) + (blue(second) - blue(first)) * r).toInt().coerceIn(0, 255),
        )
    }

    /** Relative luminance as defined by WCAG 2.0 (gamma corrected). */
    fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val v = value / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(red(color)) + 0.7152 * channel(green(color)) + 0.0722 * channel(blue(color))
    }

    fun isDark(color: Int): Boolean = luminance(color) < 0.5

    /**
     * Picks black or white for text/icon colour on top of [background],
     * guaranteeing at least a 4.5:1 WCAG AA contrast ratio where possible.
     */
    fun readableOn(background: Int): Int {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF0B0D12.toInt()
        val onWhite = contrastRatio(white, background)
        val onBlack = contrastRatio(black, background)
        return if (onWhite >= onBlack) white else black
    }

    fun contrastRatio(first: Int, second: Int): Double {
        val l1 = luminance(first)
        val l2 = luminance(second)
        val lighter = if (l1 > l2) l1 else l2
        val darker = if (l1 > l2) l2 else l1
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** Ensures [foreground] is readable on [background]; falls back to black/white. */
    fun ensureReadable(foreground: Int, background: Int, minimum: Double = 4.5): Int {
        if (contrastRatio(foreground, background) >= minimum) return foreground
        return readableOn(background)
    }

    fun toHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)
}
