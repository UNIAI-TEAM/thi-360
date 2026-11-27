package vn.unicom.thi360.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import vn.unicom.thi360.shell.COMPACT_WIDTH_BREAKPOINT_DP
import vn.unicom.thi360.shell.TWO_PANE_WIDTH_BREAKPOINT_DP

enum class ShellWidth {
    Narrow,
    Wide,
    TwoColumn,
}

fun shellWidthForDp(widthDp: Int): ShellWidth = when {
    widthDp < COMPACT_WIDTH_BREAKPOINT_DP -> ShellWidth.Narrow
    widthDp < TWO_PANE_WIDTH_BREAKPOINT_DP -> ShellWidth.Wide
    else -> ShellWidth.TwoColumn
}

val LocalShellWidth = staticCompositionLocalOf { ShellWidth.Narrow }
