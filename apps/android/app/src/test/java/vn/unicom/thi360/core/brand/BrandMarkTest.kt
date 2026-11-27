package vn.unicom.thi360.core.brand

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.appearance.AppearanceTokensParser
import vn.unicom.thi360.core.appearance.PaletteApplicationTest
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.BrandPalette
import vn.unicom.thi360.core.theme.Thi360Theme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandMarkTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun resolveResDir(): File {
        val candidates = listOf(File("src/main/res"), File("app/src/main/res"))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot locate src/main/res")
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = fg.luminance().toDouble()
        val l2 = bg.luminance().toDouble()
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    @Test
    fun nameContrast() {
        val lightRatio = contrastRatio(BrandPalette.Default.light.text, BrandPalette.Default.light.bgPage)
        val darkRatio = contrastRatio(BrandPalette.Default.dark.text, BrandPalette.Default.dark.bgPage)
        assertTrue("Light mode BrandMark name contrast was $lightRatio, expected >= 4.5", lightRatio >= 4.5)
        assertTrue("Dark mode BrandMark name contrast was $darkRatio, expected >= 4.5", darkRatio >= 4.5)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun neverUpscaled() {
        // 1. Every drawable-*/ic_brand_mark.png has exact 96dp * density dimensions (never upscaled beyond 700px source).
        val resDir = resolveResDir()
        val densities = linkedMapOf(
            "mdpi" to 96,
            "hdpi" to 144,
            "xhdpi" to 192,
            "xxhdpi" to 288,
            "xxxhdpi" to 384,
        )
        for ((qualifier, expectedPx) in densities) {
            val file = File(resDir, "drawable-$qualifier/ic_brand_mark.png")
            assertTrue("Missing drawable-$qualifier/ic_brand_mark.png", file.isFile)
            val img = ImageIO.read(file)
            assertEquals("Width mismatch for drawable-$qualifier/ic_brand_mark.png", expectedPx, img.width)
            assertEquals("Height mismatch for drawable-$qualifier/ic_brand_mark.png", expectedPx, img.height)
            assertTrue("BrandMark bitmap must never exceed source 700px", img.width <= 700 && img.height <= 700)
        }

        // 2. Even if caller passes markSize > 96.dp, BrandMark clamps rendered icon size to <= 96.dp.
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                BrandMark(markSize = 180.dp)
            }
        }
        val iconBounds = composeRule.onNodeWithTag(BrandMarkTestTags.ICON).getUnclippedBoundsInRoot()
        assertTrue("BrandMark width must be clamped <= 96.dp, got ${iconBounds.width}", iconBounds.width <= 96.5.dp)
        assertTrue("BrandMark height must be clamped <= 96.dp, got ${iconBounds.height}", iconBounds.height <= 96.5.dp)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun markIgnoresBrandPalette() {
        val navy = checkNotNull(AppearanceTokensParser.parse(PaletteApplicationTest.NAVY_TEAL_JSON)).palette
        var palette by mutableStateOf(BrandPalette.Default)

        composeRule.setContent {
            Thi360Theme(
                preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT),
                brandPalette = palette,
            ) {
                Box(
                    modifier = Modifier
                        .background(Thi360Theme.colors.bgPage)
                        .padding(16.dp),
                ) {
                    BrandMark(markSize = 56.dp)
                }
            }
        }

        composeRule.waitForIdle()
        val defaultIconBitmap = composeRule.onNodeWithTag(BrandMarkTestTags.ICON).captureToImage().asAndroidBitmap()
        val defaultPixels = IntArray(defaultIconBitmap.width * defaultIconBitmap.height)
        defaultIconBitmap.getPixels(defaultPixels, 0, defaultIconBitmap.width, 0, 0, defaultIconBitmap.width, defaultIconBitmap.height)

        composeRule.runOnIdle { palette = navy }
        composeRule.waitForIdle()
        val navyIconBitmap = composeRule.onNodeWithTag(BrandMarkTestTags.ICON).captureToImage().asAndroidBitmap()
        val navyPixels = IntArray(navyIconBitmap.width * navyIconBitmap.height)
        navyIconBitmap.getPixels(navyPixels, 0, navyIconBitmap.width, 0, 0, navyIconBitmap.width, navyIconBitmap.height)

        assertEquals(defaultIconBitmap.width, navyIconBitmap.width)
        assertEquals(defaultIconBitmap.height, navyIconBitmap.height)
        assertArrayEquals(
            "BrandMark icon pixels must stay identical when BrandPalette changes from default to navy",
            defaultPixels,
            navyPixels,
        )
    }
}
