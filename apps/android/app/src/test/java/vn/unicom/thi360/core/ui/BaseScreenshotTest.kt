package vn.unicom.thi360.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BaseScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun captureListDetail(
        name: String,
        widthDp: Int,
        themeMode: ThemeMode,
        fontScale: Float = 1.0f,
    ) {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = fontScale),
                LocalShellWidth provides shellWidthForDp(widthDp),
            ) {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode)) {
                    ListDetailSample()
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun captureLoadState(
        name: String,
        themeMode: ThemeMode,
        fontScale: Float = 1.0f,
    ) {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = fontScale),
            ) {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode)) {
                    LoadStateView<String>(
                        state = LoadState.Failed(kind = LoadFailureKind.Offline, canRetry = true),
                        onRetry = {},
                        emptyContent = {},
                        modifier = Modifier.background(Thi360Theme.colors.bgPage),
                        content = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Composable
    private fun ListDetailSample() {
        val colors = Thi360Theme.colors
        ListDetailLayout(
            selected = "Đề thi thử TOEIC 2026 — Bộ số 1",
            modifier = Modifier.background(colors.bgPage),
            list = {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    listOf(
                        "Đề thi thử TOEIC 2026 — Bộ số 1",
                        "Đề thi thử VSTEP B1-C1 — Đợt tháng 11",
                        "Luyện đề Đánh giá năng lực — Toán & Tư duy",
                    ).forEach { title ->
                        Surface(color = colors.bgSurface, modifier = Modifier.fillMaxWidth()) {
                            Text(text = title, color = colors.text, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            },
            detail = { selected ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = selected, color = colors.text)
                    StaleBanner(
                        message = "Dữ liệu chưa cập nhật mới nhất.",
                        onRetry = {},
                    )
                }
            },
            emptyDetail = {
                Text(text = "Chọn một mục để xem chi tiết", color = colors.textMuted)
            },
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun base_list_detail_360x640_light() = captureListDetail("base_list_detail_360x640_light", 360, ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun base_list_detail_360x640_dark() = captureListDetail("base_list_detail_360x640_dark", 360, ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun base_list_detail_360x640_fs130_light() =
        captureListDetail("base_list_detail_360x640_fs130_light", 360, ThemeMode.LIGHT, fontScale = 1.3f)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun base_list_detail_412x915_light() = captureListDetail("base_list_detail_412x915_light", 412, ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun base_list_detail_412x915_dark() = captureListDetail("base_list_detail_412x915_dark", 412, ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun base_list_detail_840x720_light() = captureListDetail("base_list_detail_840x720_light", 840, ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun base_list_detail_840x720_dark() = captureListDetail("base_list_detail_840x720_dark", 840, ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun base_list_detail_1280x800_light() = captureListDetail("base_list_detail_1280x800_light", 1280, ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun base_list_detail_1280x800_dark() = captureListDetail("base_list_detail_1280x800_dark", 1280, ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun base_load_state_360x640_light() = captureLoadState("base_load_state_360x640_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun base_load_state_360x640_dark() = captureLoadState("base_load_state_360x640_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun base_load_state_360x640_fs130_light() =
        captureLoadState("base_load_state_360x640_fs130_light", ThemeMode.LIGHT, fontScale = 1.3f)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun base_load_state_412x915_light() = captureLoadState("base_load_state_412x915_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun base_load_state_412x915_dark() = captureLoadState("base_load_state_412x915_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun base_load_state_840x720_light() = captureLoadState("base_load_state_840x720_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun base_load_state_840x720_dark() = captureLoadState("base_load_state_840x720_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun base_load_state_1280x800_light() = captureLoadState("base_load_state_1280x800_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun base_load_state_1280x800_dark() = captureLoadState("base_load_state_1280x800_dark", ThemeMode.DARK)
}
