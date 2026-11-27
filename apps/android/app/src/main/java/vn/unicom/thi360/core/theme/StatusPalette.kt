package vn.unicom.thi360.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import vn.unicom.thi360.core.preferences.ThemeMode

@Immutable
data class StatusColors(
    val success: Color,
    val successBg: Color,
    val warning: Color,
    val warningBg: Color,
    val info: Color,
    val infoBg: Color,
    val dangerSolid: Color,
    val onDanger: Color,
)

object StatusPalette {
    val Light = StatusColors(
        success = Color(0xFF047857),
        successBg = Color(0xFFECFDF5),
        warning = Color(0xFFB45309),
        warningBg = Color(0xFFFFFBEB),
        info = Color(0xFF0E7490),
        infoBg = Color(0xFFECFEFF),
        dangerSolid = Color(0xFFDC2626),
        onDanger = Color(0xFFFFFFFF),
    )

    val Dark = StatusColors(
        success = Color(0xFF34D399),
        successBg = Color(0xFF0F2A21),
        warning = Color(0xFFFBBF24),
        warningBg = Color(0xFF2B2210),
        info = Color(0xFF22D3EE),
        infoBg = Color(0xFF0F2A30),
        dangerSolid = Color(0xFFDC2626),
        onDanger = Color(0xFFFFFFFF),
    )

    fun forMode(mode: ThemeMode): StatusColors = when (mode) {
        ThemeMode.LIGHT -> Light
        ThemeMode.DARK -> Dark
    }
}
