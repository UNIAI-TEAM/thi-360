package vn.unicom.thi360.feature.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object PlayerIcons {

    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayerArrowBack",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
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

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayerCheck",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 12.5f)
                lineTo(10f, 17.5f)
                lineTo(19f, 6.5f)
            }
        }.build()
    }

    val CheckCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayerCheckCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(22f, 11.08f)
                verticalLineTo(12f)
                arcToRelative(10f, 10f, 0f, true, true, -5.93f, -9.14f)
                moveTo(22f, 4f)
                lineTo(12f, 14.01f)
                lineTo(9f, 11.01f)
            }
        }.build()
    }

    val Lock: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayerLock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 11f)
                horizontalLineTo(19f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineTo(19f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(5f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(13f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
                moveTo(7f, 11f)
                verticalLineTo(7f)
                arcToRelative(5f, 5f, 0f, false, true, 10f, 0f)
                verticalLineTo(11f)
            }
        }.build()
    }

    val Clock: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayerClock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 2f)
                arcToRelative(10f, 10f, 0f, true, false, 0f, 20f)
                arcToRelative(10f, 10f, 0f, true, false, 0f, -20f)
                close()
                moveTo(12f, 6f)
                verticalLineTo(12f)
                lineTo(16f, 14f)
            }
        }.build()
    }

    val Flag: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayerFlag",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 3f)
                horizontalLineTo(7f)
                verticalLineTo(21f)
                horizontalLineTo(5f)
                close()
                moveTo(7f, 4f)
                horizontalLineTo(19f)
                lineTo(16.2f, 9f)
                lineTo(19f, 14f)
                horizontalLineTo(7f)
                close()
            }
        }.build()
    }

    val Grid: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayerGrid",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 4f)
                horizontalLineTo(10f)
                verticalLineTo(10f)
                horizontalLineTo(4f)
                close()
                moveTo(14f, 4f)
                horizontalLineTo(20f)
                verticalLineTo(10f)
                horizontalLineTo(14f)
                close()
                moveTo(4f, 14f)
                horizontalLineTo(10f)
                verticalLineTo(20f)
                horizontalLineTo(4f)
                close()
                moveTo(14f, 14f)
                horizontalLineTo(20f)
                verticalLineTo(20f)
                horizontalLineTo(14f)
                close()
            }
        }.build()
    }

    val ChevronUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayerChevronUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(18f, 15f)
                lineTo(12f, 9f)
                lineTo(6f, 15f)
            }
        }.build()
    }

    val ChevronDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayerChevronDown",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 9f)
                lineTo(12f, 15f)
                lineTo(18f, 9f)
            }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayerClose",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
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

    val Warning: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayerWarning",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10.29f, 3.86f)
                lineTo(1.82f, 18f)
                arcToRelative(2f, 2f, 0f, false, false, 1.71f, 3f)
                horizontalLineTo(20.47f)
                arcToRelative(2f, 2f, 0f, false, false, 1.71f, -3f)
                lineTo(13.71f, 3.86f)
                arcToRelative(2f, 2f, 0f, false, false, -3.42f, 0f)
                close()
                moveTo(12f, 9f)
                verticalLineTo(13f)
                moveTo(12f, 17f)
                horizontalLineTo(12.01f)
            }
        }.build()
    }

    val WifiOff: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayerWifiOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(2f, 2f)
                lineTo(22f, 22f)
                moveTo(8.5f, 16.5f)
                arcToRelative(5f, 5f, 0f, false, true, 7f, 0f)
                moveTo(12f, 20f)
                horizontalLineTo(12.01f)
                moveTo(5f, 12.86f)
                arcToRelative(10f, 10f, 0f, false, true, 5.17f, -2.69f)
                moveTo(19f, 12.86f)
                arcToRelative(10f, 10f, 0f, false, false, -2.07f, -1.69f)
            }
        }.build()
    }

    val Info: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayerInfo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 2f)
                arcToRelative(10f, 10f, 0f, true, false, 0f, 20f)
                arcToRelative(10f, 10f, 0f, true, false, 0f, -20f)
                close()
                moveTo(12f, 16f)
                verticalLineTo(12f)
                moveTo(12f, 8f)
                horizontalLineTo(12.01f)
            }
        }.build()
    }
}
