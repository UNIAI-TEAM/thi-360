package vn.unicom.thi360

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.feature.catalog.CatalogTestFixtures
import vn.unicom.thi360.feature.catalog.CatalogTestTags
import vn.unicom.thi360.feature.catalog.LocalCatalogDependencies
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.Thi360App

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun captureShell(
        name: String,
        themeMode: ThemeMode,
        destination: Destination = Destination.Home,
    ) {
        val catalogDeps = CatalogTestFixtures.createDeterministicFixtureDependencies()
        composeRule.setContent {
            CompositionLocalProvider(LocalCatalogDependencies provides catalogDeps) {
                Thi360App(
                    preferences = AppPreferences(
                        locale = AppLocale.VI,
                        themeMode = themeMode,
                    ),
                    startDestination = destination.route,
                )
            }
        }
        if (destination == Destination.Home) {
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(CatalogTestTags.productPrimaryAction("EXAM-DEMO-01"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun shell_360x640_light() = captureShell("shell_360x640_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun shell_360x640_dark() = captureShell("shell_360x640_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun shell_412x915_light() = captureShell("shell_412x915_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun shell_412x915_dark() = captureShell("shell_412x915_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun shell_840x720_light() = captureShell("shell_840x720_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun shell_840x720_dark() = captureShell("shell_840x720_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun shell_840x720_account_light() =
        captureShell("shell_840x720_account_light", ThemeMode.LIGHT, Destination.Account)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun shell_840x720_account_dark() =
        captureShell("shell_840x720_account_dark", ThemeMode.DARK, Destination.Account)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun shell_1280x800_light() = captureShell("shell_1280x800_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun shell_1280x800_dark() = captureShell("shell_1280x800_dark", ThemeMode.DARK)
}
