package vn.unicom.thi360.feature.richcontent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
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
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.model.RichParseOptions
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RichContentScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(
        name: String,
        html: String,
        themeMode: ThemeMode,
        blankNumberStart: Int = 1,
        unsupportedPredicate: (vn.unicom.thi360.feature.richcontent.model.RichInline.Formula) -> Boolean = { false },
    ) {
        val doc = RichHtmlParser.parse(html, RichParseOptions(blankNumberStart = blankNumberStart))
        composeRule.setContent {
            Thi360Theme(
                preferences = AppPreferences(
                    locale = AppLocale.VI,
                    themeMode = themeMode,
                ),
            ) {
                CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine(unsupportedPredicate)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Thi360Theme.colors.bgPage)
                            .padding(16.dp),
                    ) {
                        RichContentView(
                            document = doc,
                            assets = null,
                            blankNumberStart = blankNumberStart,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun richcontent_styles_list_360x640_light() = capture(
        name = "richcontent_styles_list_360x640_light",
        html = """<p>H<sub>2</sub>O và x<sup>2</sup>, <strong>đậm</strong> <em>nghiêng</em> <u>gạch chân</u> <s>gạch ngang</s></p><ol><li>một</li><li>hai</li></ol>""",
        themeMode = ThemeMode.LIGHT,
    )

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun richcontent_data_table_360x640_light() = capture(
        name = "richcontent_data_table_360x640_light",
        html = """<figure class="data-table"><figcaption>TRỊ GIÁ XUẤT, NHẬP KHẨU CỦA HOA KÌ</figcaption><table><thead><tr><th scope="col">Năm</th><th scope="col">2000</th><th scope="col">2020</th></tr></thead><tbody><tr><th scope="row">Xuất khẩu</th><td class="num">1096,1</td><td class="num">2148,6</td></tr><tr><th scope="row">Nhập khẩu</th><td class="num">1477,2</td><td class="num">2776,1</td></tr></tbody></table><p class="figure-note">Đơn vị: Tỉ USD</p><p class="figure-source">Nguồn: World Bank, 2022</p></figure>""",
        themeMode = ThemeMode.LIGHT,
    )

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun richcontent_data_table_360x640_dark() = capture(
        name = "richcontent_data_table_360x640_dark",
        html = """<figure class="data-table"><figcaption>TRỊ GIÁ XUẤT, NHẬP KHẨU CỦA HOA KÌ</figcaption><table><thead><tr><th scope="col">Năm</th><th scope="col">2000</th><th scope="col">2020</th></tr></thead><tbody><tr><th scope="row">Xuất khẩu</th><td class="num">1096,1</td><td class="num">2148,6</td></tr><tr><th scope="row">Nhập khẩu</th><td class="num">1477,2</td><td class="num">2776,1</td></tr></tbody></table><p class="figure-note">Đơn vị: Tỉ USD</p><p class="figure-source">Nguồn: World Bank, 2022</p></figure>""",
        themeMode = ThemeMode.DARK,
    )

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun richcontent_quote_underline_blank_360x640_light() = capture(
        name = "richcontent_quote_underline_blank_360x640_light",
        html = """<blockquote class="quote" data-kind="poem" data-line-numbers="5"><p>Buồn trông cửa bể chiều hôm,<br>Thuyền ai thấp thoáng cánh buồm xa xa?<br>Buồn trông ngọn nước mới sa,<br>Hoa trôi man mác biết là về đâu?</p><p>Buồn trông nội cỏ rầu rầu,<br>Chân mây mặt đất một màu xanh xanh.</p><footer>(Nguyễn Du, <cite>Truyện Kiều</cite>)</footer></blockquote><p>For millennia, <u data-label="A">has been</u> central <u data-label="B">to</u> lives. Blank: <span class="blank" data-blank="2"></span></p>""",
        themeMode = ThemeMode.LIGHT,
        blankNumberStart = 31,
    )

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun richcontent_math_fallback_360x640_dark() = capture(
        name = "richcontent_math_fallback_360x640_dark",
        html = """<p>Biết <span class="math-inline">\(x^2 + 1\)</span> và <span class="math-inline">\(\broken{syntax}\)</span> thì</p><span class="math-block">\[a^2 + b^2 = c^2\]</span><p>Chỗ trống <span class="blank" data-blank="1"></span></p>""",
        themeMode = ThemeMode.DARK,
        unsupportedPredicate = { it.source.contains("\\broken") },
    )
}
