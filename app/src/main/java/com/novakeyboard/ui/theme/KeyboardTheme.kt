package com.novakeyboard.ui.theme

import com.novakeyboard.data.prefs.Settings
import com.novakeyboard.data.prefs.ThemeIds
import com.novakeyboard.utils.Colors

/**
 * Colours and geometry for the keyboard surface. All values are ARGB ints so
 * the theme does not depend on the Android framework.
 */
data class KeyboardTheme(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val backgroundTop: Int,
    val backgroundBottom: Int,
    val keyBackground: Int,
    val keyPressed: Int,
    val keyBorder: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accent: Int,
    val onAccent: Int,
    val keyRadiusDp: Float = 12f,
    val keyGapDp: Float = 3f,
    val backgroundAlpha: Int = 255,
    val toolbarBackground: Int = backgroundTop,
    val popupBackground: Int = keyBackground,
    val popupSelected: Int = accent,
) {
    val isLight: Boolean get() = !isDark

    companion object {
        /** Blends [color] towards [other] by [ratio] (0..1). */
        fun blend(color: Int, ratio: Float, other: Int): Int = Colors.mix(color, other, ratio)
    }
}

object ThemePresets {

    val midnight = KeyboardTheme(
        id = ThemeIds.MIDNIGHT,
        displayName = "Midnight Black",
        isDark = true,
        backgroundTop = 0xFF12141A.toInt(),
        backgroundBottom = 0xFF0B0D12.toInt(),
        keyBackground = 0xFF232733.toInt(),
        keyPressed = 0xFF323847.toInt(),
        keyBorder = 0xFF2E3340.toInt(),
        textPrimary = 0xFFF5F7FB.toInt(),
        textSecondary = 0xFF9AA3B5.toInt(),
        accent = 0xFF7C6BFF.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        popupSelected = 0xFF7C6BFF.toInt(),
    )

    val ocean = KeyboardTheme(
        id = ThemeIds.OCEAN,
        displayName = "Ocean Blue",
        isDark = true,
        backgroundTop = 0xFF0A1622.toInt(),
        backgroundBottom = 0xFF061019.toInt(),
        keyBackground = 0xFF17293D.toInt(),
        keyPressed = 0xFF20394F.toInt(),
        keyBorder = 0xFF1F3448.toInt(),
        textPrimary = 0xFFEAF4FF.toInt(),
        textSecondary = 0xFF8FAAC6.toInt(),
        accent = 0xFF2F8BFF.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        popupSelected = 0xFF2F8BFF.toInt(),
    )

    val violet = KeyboardTheme(
        id = ThemeIds.VIOLET,
        displayName = "Violet Glow",
        isDark = true,
        backgroundTop = 0xFF171025.toInt(),
        backgroundBottom = 0xFF100A1B.toInt(),
        keyBackground = 0xFF2A1F42.toInt(),
        keyPressed = 0xFF382A55.toInt(),
        keyBorder = 0xFF35294E.toInt(),
        textPrimary = 0xFFF4EDFF.toInt(),
        textSecondary = 0xFFB4A4D6.toInt(),
        accent = 0xFFC06BFF.toInt(),
        onAccent = 0xFF16091F.toInt(),
        popupSelected = 0xFFC06BFF.toInt(),
    )

    val pureWhite = KeyboardTheme(
        id = ThemeIds.WHITE,
        displayName = "Pure White",
        isDark = false,
        backgroundTop = 0xFFF7F8FA.toInt(),
        backgroundBottom = 0xFFEDEFF4.toInt(),
        keyBackground = 0xFFFFFFFF.toInt(),
        keyPressed = 0xFFE3E7F0.toInt(),
        keyBorder = 0xFFDCE0E8.toInt(),
        textPrimary = 0xFF14161C.toInt(),
        textSecondary = 0xFF6E7686.toInt(),
        accent = 0xFF4F46E5.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        keyRadiusDp = 11f,
        popupSelected = 0xFF4F46E5.toInt(),
    )

    val minimalGray = KeyboardTheme(
        id = ThemeIds.GRAY,
        displayName = "Minimal Gray",
        isDark = true,
        backgroundTop = 0xFF212429.toInt(),
        backgroundBottom = 0xFF191B20.toInt(),
        keyBackground = 0xFF2C3037.toInt(),
        keyPressed = 0xFF3A3F48.toInt(),
        keyBorder = 0xFF353A42.toInt(),
        textPrimary = 0xFFF1F2F4.toInt(),
        textSecondary = 0xFFA3A9B4.toInt(),
        accent = 0xFF7E8BA3.toInt(),
        onAccent = 0xFF14161B.toInt(),
        keyRadiusDp = 8f,
        popupSelected = 0xFF9AA6BC.toInt(),
    )

    val forest = KeyboardTheme(
        id = ThemeIds.FOREST,
        displayName = "Forest Green",
        isDark = true,
        backgroundTop = 0xFF0D1A14.toInt(),
        backgroundBottom = 0xFF08110D.toInt(),
        keyBackground = 0xFF1B3025.toInt(),
        keyPressed = 0xFF26452F.toInt(),
        keyBorder = 0xFF254030.toInt(),
        textPrimary = 0xFFECF7F0.toInt(),
        textSecondary = 0xFF94B5A2.toInt(),
        accent = 0xFF3DDB8A.toInt(),
        onAccent = 0xFF07170E.toInt(),
        popupSelected = 0xFF3DDB8A.toInt(),
    )

    val all: List<KeyboardTheme> = listOf(midnight, ocean, violet, pureWhite, minimalGray, forest)

    fun byId(id: String?): KeyboardTheme = all.firstOrNull { it.id == id } ?: midnight

    /** Applies user overrides on top of the chosen preset. */
    fun resolve(settings: Settings): KeyboardTheme {
        val base = byId(settings.themePresetId)
        val background = settings.customBackground ?: base.backgroundTop
        val key = settings.customKey ?: base.keyBackground
        val text = settings.customText ?: base.textPrimary
        val accent = settings.customAccent ?: base.accent
        val dark = Colors.isDark(background)
        val keyPressed = if (dark) Colors.mix(key, 0xFFFFFFFF.toInt(), 0.10f) else Colors.mix(key, 0xFF000000.toInt(), 0.08f)
        val secondary = Colors.mix(text, background, 0.35f)
        val border = Colors.mix(key, background, 0.45f)
        val onAccent = Colors.ensureReadable(
            if (Colors.isDark(accent)) 0xFFFFFFFF.toInt() else 0xFF10131A.toInt(),
            accent,
            4.0,
        )
        return base.copy(
            displayName = base.displayName,
            isDark = dark,
            backgroundTop = background,
            backgroundBottom = Colors.mix(background, 0xFF000000.toInt(), 0.18f),
            keyBackground = key,
            keyPressed = keyPressed,
            keyBorder = border,
            textPrimary = Colors.ensureReadable(text, key, 4.5),
            textSecondary = Colors.ensureReadable(secondary, key, 3.0),
            accent = accent,
            onAccent = onAccent,
            keyRadiusDp = settings.keyRadiusDp,
            keyGapDp = settings.keyGapDp,
            backgroundAlpha = (settings.backgroundOpacity.coerceIn(0.55f, 1f) * 255).toInt(),
            toolbarBackground = background,
            popupBackground = if (dark) {
                Colors.mix(key, 0xFFFFFFFF.toInt(), 0.06f)
            } else {
                Colors.mix(key, 0xFF000000.toInt(), 0.04f)
            },
            popupSelected = accent,
        )
    }
}
