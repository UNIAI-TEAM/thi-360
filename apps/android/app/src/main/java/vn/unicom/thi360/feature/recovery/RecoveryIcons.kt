package vn.unicom.thi360.feature.recovery

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object RecoveryIcons {
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
                moveTo(19f, 12f)
                horizontalLineTo(5f)
                moveTo(12f, 19f)
                lineTo(5f, 12f)
                lineTo(12f, 5f)
            }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "Close",
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
                moveTo(18f, 6f)
                lineTo(6f, 18f)
                moveTo(6f, 6f)
                lineTo(18f, 18f)
            }
        }.build()
    }

    val Mail: ImageVector by lazy {
        ImageVector.Builder(
            name = "Mail",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 5f)
                horizontalLineTo(19f)
                arcTo(2f, 2f, 0f, false, true, 21f, 7f)
                verticalLineTo(17f)
                arcTo(2f, 2f, 0f, false, true, 19f, 19f)
                horizontalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 3f, 17f)
                verticalLineTo(7f)
                arcTo(2f, 2f, 0f, false, true, 5f, 5f)
                close()
                moveTo(3f, 7f)
                lineTo(12f, 13f)
                lineTo(21f, 7f)
            }
        }.build()
    }

    val Link: ImageVector by lazy {
        ImageVector.Builder(
            name = "Link",
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
                moveTo(10f, 13f)
                arcTo(5f, 5f, 0f, false, false, 17.54f, 13.54f)
                lineTo(20.54f, 10.54f)
                arcTo(5f, 5f, 0f, false, false, 13.46f, 3.46f)
                lineTo(11.75f, 5.18f)
                moveTo(14f, 11f)
                arcTo(5f, 5f, 0f, false, false, 6.46f, 10.46f)
                lineTo(3.46f, 13.46f)
                arcTo(5f, 5f, 0f, false, false, 10.54f, 20.54f)
                lineTo(12.24f, 18.83f)
            }
        }.build()
    }

    val LinkOff: ImageVector by lazy {
        ImageVector.Builder(
            name = "LinkOff",
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
                moveTo(9f, 17f)
                horizontalLineTo(7f)
                arcTo(5f, 5f, 0f, false, true, 7f, 7f)
                horizontalLineTo(9f)
                moveTo(15f, 7f)
                horizontalLineTo(17f)
                arcTo(5f, 5f, 0f, false, true, 17f, 17f)
                horizontalLineTo(15f)
                moveTo(2f, 2f)
                lineTo(22f, 22f)
            }
        }.build()
    }

    val CircleOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "CircleOutline",
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
                moveTo(20f, 12f)
                arcTo(8f, 8f, 0f, true, true, 4f, 12f)
                arcTo(8f, 8f, 0f, true, true, 20f, 12f)
            }
        }.build()
    }
}
