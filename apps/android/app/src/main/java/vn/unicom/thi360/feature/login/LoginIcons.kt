package vn.unicom.thi360.feature.login

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object LoginIcons {
    private val stroke = SolidColor(Color.Black)

    val Globe: ImageVector by lazy {
        ImageVector.Builder(
            name = "Globe",
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
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, true, true, 3f, 12f)
                arcTo(9f, 9f, 0f, true, true, 21f, 12f)
                moveTo(3f, 12f)
                horizontalLineTo(21f)
                moveTo(12f, 3f)
                arcTo(14f, 14f, 0f, false, true, 12f, 21f)
                moveTo(12f, 3f)
                arcTo(14f, 14f, 0f, false, false, 12f, 21f)
            }
        }.build()
    }

    val Visibility: ImageVector by lazy {
        ImageVector.Builder(
            name = "Visibility",
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
                moveTo(2f, 12f)
                curveTo(2f, 12f, 5.6f, 5f, 12f, 5f)
                curveTo(18.4f, 5f, 22f, 12f, 22f, 12f)
                curveTo(22f, 12f, 18.4f, 19f, 12f, 19f)
                curveTo(5.6f, 19f, 2f, 12f, 2f, 12f)
                close()
                moveTo(15f, 12f)
                arcTo(3f, 3f, 0f, true, true, 9f, 12f)
                arcTo(3f, 3f, 0f, true, true, 15f, 12f)
            }
        }.build()
    }

    val VisibilityOff: ImageVector by lazy {
        ImageVector.Builder(
            name = "VisibilityOff",
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
                moveTo(3f, 3f)
                lineTo(21f, 21f)
                moveTo(10.6f, 10.6f)
                arcTo(3f, 3f, 0f, false, false, 13.4f, 13.4f)
                moveTo(9.9f, 5.1f)
                arcTo(10.5f, 10.5f, 0f, false, true, 12f, 5f)
                curveTo(18.4f, 5f, 22f, 12f, 22f, 12f)
                curveTo(21.2f, 13.5f, 20f, 15.1f, 18.4f, 16.4f)
                moveTo(6.6f, 6.6f)
                curveTo(4.2f, 8.2f, 2f, 12f, 2f, 12f)
                curveTo(2f, 12f, 5.6f, 19f, 12f, 19f)
                curveTo(13.8f, 19f, 15.4f, 18.5f, 16.8f, 17.7f)
            }
        }.build()
    }

    val AlertCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "AlertCircle",
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
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, true, true, 3f, 12f)
                arcTo(9f, 9f, 0f, true, true, 21f, 12f)
                moveTo(12f, 8f)
                verticalLineTo(12f)
                moveTo(12f, 16f)
                horizontalLineTo(12.01f)
            }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 12f)
                lineTo(10f, 17f)
                lineTo(19f, 7f)
            }
        }.build()
    }

    val UserBlocked: ImageVector by lazy {
        ImageVector.Builder(
            name = "UserBlocked",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(14f, 8f)
                arcTo(4f, 4f, 0f, true, true, 6f, 8f)
                arcTo(4f, 4f, 0f, true, true, 14f, 8f)
                moveTo(3f, 20f)
                arcTo(7f, 7f, 0f, false, true, 15.5f, 15.7f)
                moveTo(17f, 16f)
                lineTo(21f, 20f)
                moveTo(21f, 16f)
                lineTo(17f, 20f)
            }
        }.build()
    }

    val WifiOff: ImageVector by lazy {
        ImageVector.Builder(
            name = "WifiOff",
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
                moveTo(2f, 2f)
                lineTo(22f, 22f)
                moveTo(8.5f, 16.5f)
                arcTo(5f, 5f, 0f, false, true, 15.5f, 16.5f)
                moveTo(5f, 12.6f)
                arcTo(10f, 10f, 0f, false, true, 10.2f, 10.2f)
                moveTo(14.5f, 10.3f)
                arcTo(10f, 10f, 0f, false, true, 19f, 12.6f)
                moveTo(12f, 20f)
                horizontalLineTo(12.01f)
            }
        }.build()
    }
}
