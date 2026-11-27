package vn.unicom.thi360.feature.prepare

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object PrepareIcons {
    private val stroke = SolidColor(Color.Black)

    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowBack",
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
                moveTo(20f, 12f)
                horizontalLineTo(4f)
                moveTo(10f, 6f)
                lineTo(4f, 12f)
                lineTo(10f, 18f)
            }
        }.build()
    }

    val Clock: ImageVector by lazy {
        ImageVector.Builder(
            name = "Clock",
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
                moveTo(20f, 13f)
                arcTo(8f, 8f, 0f, true, true, 4f, 13f)
                arcTo(8f, 8f, 0f, true, true, 20f, 13f)
                moveTo(12f, 9f)
                verticalLineTo(13f)
                lineTo(14.5f, 15f)
                moveTo(9f, 2f)
                horizontalLineTo(15f)
            }
        }.build()
    }

    val PauseCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "PauseCircle",
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
                moveTo(10f, 9f)
                verticalLineTo(15f)
                moveTo(14f, 9f)
                verticalLineTo(15f)
            }
        }.build()
    }

    val Info: ImageVector by lazy {
        ImageVector.Builder(
            name = "Info",
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
                moveTo(12f, 11f)
                verticalLineTo(16f)
                moveTo(12f, 8f)
                horizontalLineTo(12.01f)
            }
        }.build()
    }

    val AlertTriangle: ImageVector by lazy {
        ImageVector.Builder(
            name = "AlertTriangle",
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
                moveTo(10.29f, 3.86f)
                lineTo(1.82f, 18f)
                arcTo(2f, 2f, 0f, false, false, 3.53f, 21f)
                horizontalLineTo(20.47f)
                arcTo(2f, 2f, 0f, false, false, 22.18f, 18f)
                lineTo(13.71f, 3.86f)
                arcTo(2f, 2f, 0f, false, false, 10.29f, 3.86f)
                close()
                moveTo(12f, 9f)
                verticalLineTo(13f)
                moveTo(12f, 17f)
                horizontalLineTo(12.01f)
            }
        }.build()
    }

    val Headphones: ImageVector by lazy {
        ImageVector.Builder(
            name = "Headphones",
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
                moveTo(3f, 18f)
                verticalLineTo(12f)
                arcTo(9f, 9f, 0f, false, true, 21f, 12f)
                verticalLineTo(18f)
                moveTo(21f, 19f)
                arcTo(2f, 2f, 0f, false, true, 19f, 21f)
                horizontalLineTo(18f)
                arcTo(2f, 2f, 0f, false, true, 16f, 19f)
                verticalLineTo(16f)
                arcTo(2f, 2f, 0f, false, true, 18f, 14f)
                horizontalLineTo(21f)
                close()
                moveTo(3f, 19f)
                arcTo(2f, 2f, 0f, false, false, 5f, 21f)
                horizontalLineTo(6f)
                arcTo(2f, 2f, 0f, false, false, 8f, 19f)
                verticalLineTo(16f)
                arcTo(2f, 2f, 0f, false, false, 6f, 14f)
                horizontalLineTo(3f)
                close()
            }
        }.build()
    }

    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "Play",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke,
                stroke = stroke,
                strokeLineWidth = 1.5f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7f, 4f)
                verticalLineTo(20f)
                lineTo(20f, 12f)
                close()
            }
        }.build()
    }

    val Refresh: ImageVector by lazy {
        ImageVector.Builder(
            name = "Refresh",
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
                arcTo(9f, 9f, 0f, true, true, 18.36f, 5.64f)
                lineTo(21f, 8f)
                moveTo(21f, 3f)
                verticalLineTo(8f)
                horizontalLineTo(16f)
            }
        }.build()
    }
}
