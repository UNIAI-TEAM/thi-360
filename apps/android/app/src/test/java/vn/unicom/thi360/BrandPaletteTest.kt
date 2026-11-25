package vn.unicom.thi360

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.BrandPalette
import vn.unicom.thi360.core.theme.contrastRatio
import vn.unicom.thi360.core.theme.toMaterialColorScheme

class BrandPaletteTest {

    @Test
    fun defaultPaletteMatchesThi360GreenLightAndDarkTokens() {
        val palette = BrandPalette.Default
        assertEquals("thi360-green", palette.presetId)
        assertEquals(ThemeMode.LIGHT, palette.defaultMode)

        val light = palette.colorsFor(ThemeMode.LIGHT)
        assertEquals(Color(0xFF047857), light.primaryFill)
        assertEquals(Color(0xFFFFFFFF), light.onPrimary)
        assertEquals(Color(0xFF047857), light.primaryText)
        assertEquals(Color(0xFFECFDF5), light.primaryContainer)
        assertEquals(Color(0xFFDCFCE7), light.primaryContainerStrong)
        assertEquals(Color(0xFFF59223), light.accent)
        assertEquals(Color(0xFF1A1A1F), light.onAccent)
        assertEquals(Color(0xFFF7F8F9), light.bgPage)
        assertEquals(Color(0xFFFFFFFF), light.bgSurface)
        assertEquals(Color(0xFFF1F2F4), light.bgSubtle)
        assertEquals(Color(0xFF11181C), light.text)
        assertEquals(Color(0xFF3A3A42), light.textSecondary)
        assertEquals(Color(0xFF6B6B72), light.textMuted)
        assertEquals(Color(0xFFE8E8EC), light.border)
        assertEquals(Color(0xFFD4D4D8), light.borderStrong)
        assertEquals(Color(0xFFB91C1C), light.dangerText)
        assertEquals(Color(0xFFFEF2F2), light.dangerBg)

        val dark = palette.colorsFor(ThemeMode.DARK)
        assertEquals(Color(0xFF55B38F), dark.primaryFill)
        assertEquals(Color(0xFF11181C), dark.onPrimary)
        assertEquals(Color(0xFF10B981), dark.primaryText)
        assertEquals(Color(0xFF0A1F19), dark.primaryContainer)
        assertEquals(Color(0xFF0C1F18), dark.primaryContainerStrong)
        assertEquals(Color(0xFFF59223), dark.accent)
        assertEquals(Color(0xFF1A1A1F), dark.onAccent)
        assertEquals(Color(0xFF0F1418), dark.bgPage)
        assertEquals(Color(0xFF171D22), dark.bgSurface)
        assertEquals(Color(0xFF1E262C), dark.bgSubtle)
        assertEquals(Color(0xFFE6EAED), dark.text)
        assertEquals(Color(0xFFC5CCD2), dark.textSecondary)
        assertEquals(Color(0xFFA9B2BA), dark.textMuted)
        assertEquals(Color(0xFF2A3238), dark.border)
        assertEquals(Color(0xFF3A444C), dark.borderStrong)
        assertEquals(Color(0xFFF87171), dark.dangerText)
        assertEquals(Color(0xFF2E1517), dark.dangerBg)
    }

    @Test
    fun materialColorSchemeMeetsWcagContrastForErrorAndPrimaryRoles() {
        val lightScheme = BrandPalette.Default.colorsFor(ThemeMode.LIGHT)
            .toMaterialColorScheme(ThemeMode.LIGHT)
        assertEquals(Color(0xFFFFFFFF), lightScheme.onError)
        assertTrue(contrastRatio(lightScheme.onError, lightScheme.error) >= 4.5f)
        assertTrue(contrastRatio(lightScheme.onErrorContainer, lightScheme.errorContainer) >= 4.5f)
        assertTrue(contrastRatio(lightScheme.onPrimary, lightScheme.primary) >= 4.5f)
        assertTrue(contrastRatio(lightScheme.onSecondaryContainer, lightScheme.secondaryContainer) >= 4.5f)

        val darkScheme = BrandPalette.Default.colorsFor(ThemeMode.DARK)
            .toMaterialColorScheme(ThemeMode.DARK)
        assertEquals(Color(0xFF11181C), darkScheme.onError)
        assertTrue(contrastRatio(darkScheme.onError, darkScheme.error) >= 4.5f)
        assertTrue(contrastRatio(darkScheme.onErrorContainer, darkScheme.errorContainer) >= 4.5f)
        assertTrue(contrastRatio(darkScheme.onPrimary, darkScheme.primary) >= 4.5f)
        assertTrue(contrastRatio(darkScheme.onSecondaryContainer, darkScheme.secondaryContainer) >= 4.5f)
    }
}
