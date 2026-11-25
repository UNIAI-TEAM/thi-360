package vn.unicom.thi360.core.theme

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode

val LocalBrandColors = staticCompositionLocalOf { BrandPalette.DefaultLightColors }
val LocalAppLocale = staticCompositionLocalOf { AppLocale.Default }

object Thi360Theme {
    val colors: BrandColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandColors.current
}

private class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: Resources,
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}

private fun BrandColors.toMaterialColorScheme(mode: ThemeMode): ColorScheme = when (mode) {
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
        onError = Color(0xFFFFFFFF),
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
        onError = Color(0xFFFFFFFF),
        errorContainer = dangerBg,
        onErrorContainer = dangerText,
    )
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
    val colorScheme = remember(brandColors, effectiveMode) {
        brandColors.toMaterialColorScheme(effectiveMode)
    }

    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
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
        LocalAppLocale provides effectiveLocale,
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
