package vn.unicom.thi360.core.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode

val LocalBrandColors = staticCompositionLocalOf { BrandPalette.DefaultLightColors }
val LocalStatusColors = staticCompositionLocalOf { StatusPalette.Light }
val LocalAppLocale = staticCompositionLocalOf { AppLocale.Default }
val LocalThemeMode = staticCompositionLocalOf { ThemeMode.LIGHT }

object Thi360Theme {
    val colors: BrandColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandColors.current

    val statusColors: StatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStatusColors.current

    val themeMode: ThemeMode
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeMode.current
}

private class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: Resources,
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}

private tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}

internal fun contrastRatio(foreground: Color, background: Color): Float {
    val fgLum = foreground.luminance()
    val bgLum = background.luminance()
    val lighter = maxOf(fgLum, bgLum)
    val darker = minOf(fgLum, bgLum)
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun pickReadableOnColor(background: Color): Color {
    val white = Color(0xFFFFFFFF)
    val darkInk = Color(0xFF11181C)
    return if (contrastRatio(white, background) >= 4.5f) white else darkInk
}

internal fun BrandColors.toMaterialColorScheme(mode: ThemeMode): ColorScheme {
    val onErrorColor = pickReadableOnColor(dangerText)
    return when (mode) {
        ThemeMode.LIGHT -> lightColorScheme(
            primary = primaryFill,
            onPrimary = onPrimary,
            primaryContainer = primaryContainerStrong,
            onPrimaryContainer = primaryText,
            secondary = primaryText,
            onSecondary = onPrimary,
            secondaryContainer = primaryContainer,
            onSecondaryContainer = primaryText,
            tertiary = accent,
            onTertiary = onAccent,
            background = bgPage,
            onBackground = text,
            surface = bgSurface,
            onSurface = text,
            surfaceVariant = bgSubtle,
            onSurfaceVariant = textMuted,
            surfaceContainerLowest = bgSurface,
            surfaceContainerLow = bgPage,
            surfaceContainer = bgSubtle,
            surfaceContainerHigh = bgSubtle,
            surfaceContainerHighest = border,
            outline = borderStrong,
            outlineVariant = border,
            error = dangerText,
            onError = onErrorColor,
            errorContainer = dangerBg,
            onErrorContainer = dangerText,
        )
        ThemeMode.DARK -> darkColorScheme(
            primary = primaryFill,
            onPrimary = onPrimary,
            primaryContainer = primaryContainerStrong,
            onPrimaryContainer = primaryText,
            secondary = primaryText,
            onSecondary = onPrimary,
            secondaryContainer = primaryContainer,
            onSecondaryContainer = primaryText,
            tertiary = accent,
            onTertiary = onAccent,
            background = bgPage,
            onBackground = text,
            surface = bgSurface,
            onSurface = text,
            surfaceVariant = bgSubtle,
            onSurfaceVariant = textMuted,
            surfaceContainerLowest = bgSurface,
            surfaceContainerLow = bgPage,
            surfaceContainer = bgSubtle,
            surfaceContainerHigh = bgSubtle,
            surfaceContainerHighest = border,
            outline = borderStrong,
            outlineVariant = border,
            error = dangerText,
            onError = onErrorColor,
            errorContainer = dangerBg,
            onErrorContainer = dangerText,
        )
    }
}

@Composable
fun Thi360Theme(
    preferences: AppPreferences = AppPreferences(),
    brandPalette: BrandPalette = BrandPalette.Default,
    content: @Composable () -> Unit,
) {
    val effectiveMode = preferences.effectiveThemeMode(brandPalette.defaultMode)
    val effectiveLocale = preferences.effectiveLocale
    val brandColors = brandPalette.colorsFor(effectiveMode)
    val statusColors = StatusPalette.forMode(effectiveMode)
    val colorScheme = remember(brandColors, effectiveMode) {
        brandColors.toMaterialColorScheme(effectiveMode)
    }

    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val view = LocalView.current

    SideEffect {
        val window = baseContext.findActivityOrNull()?.window
        if (window != null && !view.isInEditMode) {
            val controller = WindowCompat.getInsetsController(window, view)
            val useLightBars = effectiveMode == ThemeMode.LIGHT
            controller.isAppearanceLightStatusBars = useLightBars
            controller.isAppearanceLightNavigationBars = useLightBars
        }
    }

    val localizedConfiguration = remember(baseConfiguration, effectiveLocale) {
        Configuration(baseConfiguration).apply {
            // Single-element LocaleList prevents Android from falling back to values-en on en-US devices when vi is selected.
            setLocales(LocaleList(effectiveLocale.toJavaLocale()))
        }
    }
    val localizedContext = remember(baseContext, localizedConfiguration) {
        val configContext = baseContext.createConfigurationContext(localizedConfiguration)
        LocalizedContextWrapper(baseContext, configContext.resources)
    }

    CompositionLocalProvider(
        LocalBrandColors provides brandColors,
        LocalStatusColors provides statusColors,
        LocalAppLocale provides effectiveLocale,
        LocalThemeMode provides effectiveMode,
        LocalConfiguration provides localizedConfiguration,
        LocalContext provides localizedContext,
        LocalResources provides localizedContext.resources,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
