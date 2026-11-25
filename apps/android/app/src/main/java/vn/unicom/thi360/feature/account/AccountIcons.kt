package vn.unicom.thi360.feature.account

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object AccountIcons {
    private val stroke = SolidColor(Color.Black)

    val Key: ImageVector by lazy {
        ImageVector.Builder(
            name = "Key",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 15f)
                arcTo(4f, 4f, 0f, true, true, 4f, 15f)
                arcTo(4f, 4f, 0f, true, true, 12f, 15f)
                moveTo(11f, 12f)
                lineTo(20f, 3f)
                moveTo(16f, 7f)
                lineTo(19f, 10f)
            }
        }.build()
    }

    val Fingerprint: ImageVector by lazy {
        ImageVector.Builder(
            name = "Fingerprint",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 11f)
                verticalLineTo(15f)
                arcTo(6f, 6f, 0f, false, true, 10.5f, 19f)
                moveTo(8f, 15f)
                verticalLineTo(11f)
                arcTo(4f, 4f, 0f, false, true, 16f, 11f)
                verticalLineTo(14f)
                arcTo(10f, 10f, 0f, false, true, 15f, 18.5f)
                moveTo(5f, 17f)
                arcTo(12f, 12f, 0f, false, false, 5.5f, 13.5f)
                verticalLineTo(11f)
                arcTo(6.5f, 6.5f, 0f, false, true, 16.5f, 6.3f)
                moveTo(19f, 11f)
                verticalLineTo(13f)
                arcTo(14f, 14f, 0f, false, true, 18.6f, 16.4f)
            }
        }.build()
    }

    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 6f)
                lineTo(15f, 12f)
                lineTo(9f, 18f)
            }
        }.build()
    }

    val Sun: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sun",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(16f, 12f)
                arcTo(4f, 4f, 0f, true, true, 8f, 12f)
                arcTo(4f, 4f, 0f, true, true, 16f, 12f)
                moveTo(12f, 2f)
                verticalLineTo(4f)
                moveTo(12f, 20f)
                verticalLineTo(22f)
                moveTo(4.9f, 4.9f)
                lineTo(6.3f, 6.3f)
                moveTo(17.7f, 17.7f)
                lineTo(19.1f, 19.1f)
                moveTo(2f, 12f)
                horizontalLineTo(4f)
                moveTo(20f, 12f)
                horizontalLineTo(22f)
                moveTo(4.9f, 19.1f)
                lineTo(6.3f, 17.7f)
                moveTo(17.7f, 6.3f)
                lineTo(19.1f, 4.9f)
            }
        }.build()
    }

    val Logout: ImageVector by lazy {
        ImageVector.Builder(
            name = "Logout",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 21f)
                horizontalLineTo(6f)
                arcTo(2f, 2f, 0f, false, true, 4f, 19f)
                verticalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 6f, 3f)
                horizontalLineTo(9f)
                moveTo(16f, 17f)
                lineTo(21f, 12f)
                lineTo(16f, 7f)
                moveTo(21f, 12f)
                horizontalLineTo(9f)
            }
        }.build()
    }
}
