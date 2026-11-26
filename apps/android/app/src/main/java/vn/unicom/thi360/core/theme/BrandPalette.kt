package vn.unicom.thi360.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import vn.unicom.thi360.core.preferences.ThemeMode

@Immutable
data class BrandColors(
    val primaryFill: Color,
    val onPrimary: Color,
    val primaryText: Color,
    val primaryContainer: Color,
    val primaryContainerStrong: Color,
    val accent: Color,
    val onAccent: Color,
    val bgPage: Color,
    val bgSurface: Color,
    val bgSubtle: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val borderStrong: Color,
    val dangerText: Color,
    val dangerBg: Color,
)

@Immutable
data class BrandPalette(
    val presetId: String,
    val defaultMode: ThemeMode,
    val light: BrandColors,
    val dark: BrandColors,
) {
    fun colorsFor(mode: ThemeMode): BrandColors = when (mode) {
        ThemeMode.LIGHT -> light
        ThemeMode.DARK -> dark
    }

    companion object {
        val DefaultLightColors = BrandColors(
            primaryFill = Color(0xFF047857),
            onPrimary = Color(0xFFFFFFFF),
            primaryText = Color(0xFF047857),
            primaryContainer = Color(0xFFECFDF5),
            primaryContainerStrong = Color(0xFFDCFCE7),
            accent = Color(0xFFF59223),
            onAccent = Color(0xFF1A1A1F),
            bgPage = Color(0xFFF7F8F9),
            bgSurface = Color(0xFFFFFFFF),
            bgSubtle = Color(0xFFF1F2F4),
            text = Color(0xFF11181C),
            textSecondary = Color(0xFF3A3A42),
            textMuted = Color(0xFF6B6B72),
            border = Color(0xFFE8E8EC),
            borderStrong = Color(0xFFD4D4D8),
            dangerText = Color(0xFFB91C1C),
            dangerBg = Color(0xFFFEF2F2),
        )

        val DefaultDarkColors = BrandColors(
            primaryFill = Color(0xFF55B38F),
            onPrimary = Color(0xFF11181C),
            primaryText = Color(0xFF10B981),
            primaryContainer = Color(0xFF0A1F19),
            primaryContainerStrong = Color(0xFF0C1F18),
            accent = Color(0xFFF59223),
            onAccent = Color(0xFF1A1A1F),
            bgPage = Color(0xFF0F1418),
            bgSurface = Color(0xFF171D22),
            bgSubtle = Color(0xFF1E262C),
            text = Color(0xFFE6EAED),
            textSecondary = Color(0xFFC5CCD2),
            textMuted = Color(0xFFA9B2BA),
            border = Color(0xFF2A3238),
            borderStrong = Color(0xFF3A444C),
            dangerText = Color(0xFFF87171),
            dangerBg = Color(0xFF2E1517),
        )

        val Default = BrandPalette(
            presetId = "thi360-green",
            defaultMode = ThemeMode.LIGHT,
            light = DefaultLightColors,
            dark = DefaultDarkColors,
        )
    }
}
