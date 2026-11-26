package vn.unicom.thi360.core.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.BrandColors
import vn.unicom.thi360.core.theme.BrandPalette
import vn.unicom.thi360.core.theme.Thi360Theme

@RunWith(RobolectricTestRunner::class)
class PaletteApplicationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contractFixtureAndAlternatePaletteApplyExactBrandAndNeutralColorsInBothModes() {
        val contractRaw = ContractFixtureLoader.readAppearanceFixture("tokens-thi360-green.json")
        val parsedContract = AppearanceTokensParser.parse(contractRaw)
        assertNotNull("Contract fixture tokens-thi360-green.json must parse", parsedContract)
        val greenPalette = checkNotNull(parsedContract).palette
        assertEquals(0, parsedContract.version)
        assertEquals("thi360-green", greenPalette.presetId)
        assertEquals(ThemeMode.LIGHT, greenPalette.defaultMode)
        assertEquals(BrandPalette.DefaultLightColors, greenPalette.light)
        assertEquals(BrandPalette.DefaultDarkColors, greenPalette.dark)

        val navyParsed = AppearanceTokensParser.parse(NAVY_TEAL_JSON)
        assertNotNull("Alternate navy-teal palette must parse", navyParsed)
        val navyPalette = checkNotNull(navyParsed).palette
        assertEquals(3, navyParsed.version)
        assertEquals("navy-teal", navyPalette.presetId)
        assertEquals(ThemeMode.DARK, navyPalette.defaultMode)
        assertEquals(Color(0xFF0F3C6E), navyPalette.light.primaryFill)
        assertEquals(Color(0xFF00B4A6), navyPalette.light.accent)
        assertEquals(Color(0xFFF7F8F9), navyPalette.light.bgPage)
        assertEquals(Color(0xFF6DA4E0), navyPalette.dark.primaryFill)
        assertEquals(Color(0xFF0F1418), navyPalette.dark.bgPage)

        var activePalette by mutableStateOf(greenPalette)
        var activeMode by mutableStateOf(ThemeMode.LIGHT)
        var observed: BrandColors? = null

        composeRule.setContent {
            Thi360Theme(
                preferences = AppPreferences(locale = AppLocale.VI, themeMode = activeMode),
                brandPalette = activePalette,
            ) {
                observed = Thi360Theme.colors
            }
        }
        composeRule.waitForIdle()
        assertEquals(greenPalette.light, observed)

        composeRule.runOnIdle { activeMode = ThemeMode.DARK }
        composeRule.waitForIdle()
        assertEquals(greenPalette.dark, observed)

        composeRule.runOnIdle {
            activePalette = navyPalette
            activeMode = ThemeMode.LIGHT
        }
        composeRule.waitForIdle()
        assertEquals(navyPalette.light, observed)

        composeRule.runOnIdle { activeMode = ThemeMode.DARK }
        composeRule.waitForIdle()
        assertEquals(navyPalette.dark, observed)
    }

    companion object {
        val NAVY_TEAL_JSON = """
            {
              "version": 3,
              "mode": "dark",
              "presetId": "navy-teal",
              "light": {
                "primaryFill": "#0f3c6e",
                "onPrimary": "#ffffff",
                "primaryText": "#0f3c6e",
                "primaryContainer": "#eff6ff",
                "primaryContainerStrong": "#dbeafe",
                "accent": "#00b4a6",
                "onAccent": "#11181c",
                "bgPage": "#f7f8f9",
                "bgSurface": "#ffffff",
                "bgSubtle": "#f1f2f4",
                "text": "#11181c",
                "textSecondary": "#3a3a42",
                "textMuted": "#6b6b72",
                "border": "#e8e8ec",
                "borderStrong": "#d4d4d8",
                "dangerText": "#b91c1c",
                "dangerBg": "#fef2f2"
              },
              "dark": {
                "primaryFill": "#6da4e0",
                "onPrimary": "#11181c",
                "primaryText": "#7eb3eb",
                "primaryContainer": "#0e1d30",
                "primaryContainerStrong": "#132944",
                "accent": "#00b4a6",
                "onAccent": "#11181c",
                "bgPage": "#0f1418",
                "bgSurface": "#171d22",
                "bgSubtle": "#1e262c",
                "text": "#e6eaed",
                "textSecondary": "#c5ccd2",
                "textMuted": "#a9b2ba",
                "border": "#2a3238",
                "borderStrong": "#3a444c",
                "dangerText": "#f87171",
                "dangerBg": "#2e1517"
              }
            }
        """.trimIndent()
    }
}
