package vn.unicom.thi360.core.appearance

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.BrandColors
import vn.unicom.thi360.core.theme.BrandPalette

@Serializable
internal data class AppearancePaletteDto(
    val primaryFill: String,
    val onPrimary: String,
    val primaryText: String,
    val primaryContainer: String,
    val primaryContainerStrong: String,
    val accent: String,
    val onAccent: String,
    val bgPage: String,
    val bgSurface: String,
    val bgSubtle: String,
    val text: String,
    val textSecondary: String,
    val textMuted: String,
    val border: String,
    val borderStrong: String,
    val dangerText: String,
    val dangerBg: String,
)

@Serializable
internal data class AppearanceTokensDto(
    val version: Int,
    val mode: String,
    val presetId: String,
    val light: AppearancePaletteDto,
    val dark: AppearancePaletteDto,
)

data class ParsedAppearance(
    val version: Int,
    val palette: BrandPalette,
)

object AppearanceTokensParser {
    private val hexColorRegex = Regex("^#[0-9A-Fa-f]{6}$")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(rawJson: String): ParsedAppearance? {
        if (rawJson.isBlank()) return null
        val dto = runCatching {
            json.decodeFromString(AppearanceTokensDto.serializer(), rawJson)
        }.getOrNull() ?: return null

        if (dto.version < 0) return null
        val presetId = dto.presetId.trim()
        if (presetId.isEmpty()) return null
        val defaultMode = when (dto.mode) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> return null
        }

        val lightColors = dto.light.toBrandColorsOrNull() ?: return null
        val darkColors = dto.dark.toBrandColorsOrNull() ?: return null

        return ParsedAppearance(
            version = dto.version,
            palette = BrandPalette(
                presetId = presetId,
                defaultMode = defaultMode,
                light = lightColors,
                dark = darkColors,
            ),
        )
    }

    internal fun parseHexColorOrNull(hex: String): Color? {
        val trimmed = hex.trim()
        if (!hexColorRegex.matches(trimmed)) return null
        val rgb = trimmed.substring(1).toLong(radix = 16)
        return Color(0xFF000000L or rgb)
    }

    private fun AppearancePaletteDto.toBrandColorsOrNull(): BrandColors? = BrandColors(
        primaryFill = parseHexColorOrNull(primaryFill) ?: return null,
        onPrimary = parseHexColorOrNull(onPrimary) ?: return null,
        primaryText = parseHexColorOrNull(primaryText) ?: return null,
        primaryContainer = parseHexColorOrNull(primaryContainer) ?: return null,
        primaryContainerStrong = parseHexColorOrNull(primaryContainerStrong) ?: return null,
        accent = parseHexColorOrNull(accent) ?: return null,
        onAccent = parseHexColorOrNull(onAccent) ?: return null,
        bgPage = parseHexColorOrNull(bgPage) ?: return null,
        bgSurface = parseHexColorOrNull(bgSurface) ?: return null,
        bgSubtle = parseHexColorOrNull(bgSubtle) ?: return null,
        text = parseHexColorOrNull(text) ?: return null,
        textSecondary = parseHexColorOrNull(textSecondary) ?: return null,
        textMuted = parseHexColorOrNull(textMuted) ?: return null,
        border = parseHexColorOrNull(border) ?: return null,
        borderStrong = parseHexColorOrNull(borderStrong) ?: return null,
        dangerText = parseHexColorOrNull(dangerText) ?: return null,
        dangerBg = parseHexColorOrNull(dangerBg) ?: return null,
    )
}
