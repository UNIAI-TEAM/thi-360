package vn.unicom.thi360.feature.richcontent.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine
import vn.unicom.thi360.feature.richcontent.formula.RaTeXFormulaEngine

@Immutable
data class RichContentPalette(
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primaryText: Color,
    val surface: Color,
    val quoteBackground: Color,
    val border: Color,
    val borderStrong: Color,
    val tableHead: Color,
    val blankFg: Color,
    val blankBg: Color,
    val warningFg: Color,
    val warningBg: Color,
) {
    companion object {
        // Spec A-50: fixed neutral tableHead token from packages/ui/src/theme/scale.ts:90,119.
        val LightTableHead = Color(0xFFFAFAFB)
        val DarkTableHead = Color(0xFF1A2126)

        @Composable
        @ReadOnlyComposable
        fun current(): RichContentPalette {
            val brand = Thi360Theme.colors
            val status = Thi360Theme.statusColors
            val isDark = Thi360Theme.themeMode == ThemeMode.DARK
            return RichContentPalette(
                text = brand.text,
                textSecondary = brand.textSecondary,
                textMuted = brand.textMuted,
                primaryText = brand.primaryText,
                surface = brand.bgSurface,
                quoteBackground = brand.bgSubtle,
                border = brand.border,
                borderStrong = brand.borderStrong,
                tableHead = if (isDark) DarkTableHead else LightTableHead,
                blankFg = status.info,
                blankBg = status.infoBg,
                warningFg = status.warning,
                warningBg = status.warningBg,
            )
        }
    }
}

val LocalFormulaEngine = staticCompositionLocalOf<FormulaEngine?> { null }

@Composable
fun rememberDefaultFormulaEngine(): FormulaEngine {
    val override = LocalFormulaEngine.current
    if (override != null) return override
    val context = LocalContext.current
    return remember(context.applicationContext) {
        RaTeXFormulaEngine(context.applicationContext)
    }
}

object RichContentTestTags {
    const val ROOT = "richcontent_root"
    const val PARAGRAPH = "richcontent_paragraph"
    const val FORMULA_BLOCK = "richcontent_formula_block"
    const val FORMULA_FALLBACK = "richcontent_formula_fallback"
    const val FORMULA_SCROLL = "richcontent_formula_scroll"
    const val TABLE_SCROLL = "richcontent_table_scroll"
    const val TABLE_FIGURE = "richcontent_table_figure"
    const val CROSS_FIGURE = "richcontent_cross_figure"
    const val IMAGE_FIGURE = "richcontent_image_figure"
    const val IMAGE_CONTENT = "richcontent_image_content"
    const val IMAGE_LOADING = "richcontent_image_loading"
    const val IMAGE_ERROR = "richcontent_image_error"
    const val IMAGE_ZOOM_DIALOG = "richcontent_image_zoom_dialog"
    const val IMAGE_ZOOM_CLOSE = "richcontent_image_zoom_close"
    const val IMAGE_ZOOM_SURFACE = "richcontent_image_zoom_surface"
    const val IMAGE_ZOOM_BADGE = "richcontent_image_zoom_badge"
    const val QUOTE_BLOCK = "richcontent_quote_block"
    const val QUOTE_LINE_NUMBER = "richcontent_quote_line_number"
    const val SLOT_HOST = "richcontent_slot_host"
}
