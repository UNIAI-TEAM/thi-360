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

    val UserCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "UserCircle",
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
                moveTo(15f, 10f)
                arcTo(3f, 3f, 0f, true, true, 9f, 10f)
                arcTo(3f, 3f, 0f, true, true, 15f, 10f)
                moveTo(6.5f, 18.5f)
                arcTo(6.5f, 6.5f, 0f, false, true, 17.5f, 18.5f)
            }
        }.build()
    }

    val Receipt: ImageVector by lazy {
        ImageVector.Builder(
            name = "Receipt",
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
                moveTo(6f, 3f)
                horizontalLineTo(18f)
                verticalLineTo(21f)
                lineTo(15f, 19f)
                lineTo(12f, 21f)
                lineTo(9f, 19f)
                lineTo(6f, 21f)
                close()
                moveTo(9f, 8f)
                horizontalLineTo(15f)
                moveTo(9f, 12f)
                horizontalLineTo(15f)
                moveTo(9f, 16f)
                horizontalLineTo(12f)
            }
        }.build()
    }

    val Document: ImageVector by lazy {
        ImageVector.Builder(
            name = "Document",
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
                moveTo(14f, 3f)
                horizontalLineTo(7f)
                arcTo(2f, 2f, 0f, false, false, 5f, 5f)
                verticalLineTo(19f)
                arcTo(2f, 2f, 0f, false, false, 7f, 21f)
                horizontalLineTo(17f)
                arcTo(2f, 2f, 0f, false, false, 19f, 19f)
                verticalLineTo(8f)
                close()
                moveTo(14f, 3f)
                verticalLineTo(8f)
                horizontalLineTo(19f)
                moveTo(9f, 13f)
                horizontalLineTo(15f)
                moveTo(9f, 17f)
                horizontalLineTo(13f)
            }
        }.build()
    }

    val Shield: ImageVector by lazy {
        ImageVector.Builder(
            name = "Shield",
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
                moveTo(12f, 3f)
                lineTo(19f, 6f)
                verticalLineTo(11f)
                curveTo(19f, 16f, 16f, 19.5f, 12f, 21f)
                curveTo(8f, 19.5f, 5f, 16f, 5f, 11f)
                verticalLineTo(6f)
                close()
            }
        }.build()
    }

    val Trash: ImageVector by lazy {
        ImageVector.Builder(
            name = "Trash",
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
                moveTo(4f, 7f)
                horizontalLineTo(20f)
                moveTo(10f, 11f)
                verticalLineTo(17f)
                moveTo(14f, 11f)
                verticalLineTo(17f)
                moveTo(6f, 7f)
                lineTo(7f, 20f)
                horizontalLineTo(17f)
                lineTo(18f, 7f)
                moveTo(9f, 7f)
                verticalLineTo(4f)
                horizontalLineTo(15f)
                verticalLineTo(7f)
            }
        }.build()
    }

    val Upload: ImageVector by lazy {
        ImageVector.Builder(
            name = "Upload",
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
                moveTo(12f, 16f)
                verticalLineTo(4f)
                moveTo(7f, 9f)
                lineTo(12f, 4f)
                lineTo(17f, 9f)
                moveTo(5f, 20f)
                horizontalLineTo(19f)
            }
        }.build()
    }

    val Camera: ImageVector by lazy {
        ImageVector.Builder(
            name = "Camera",
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
                moveTo(4f, 8f)
                horizontalLineTo(7f)
                lineTo(9f, 5f)
                horizontalLineTo(15f)
                lineTo(17f, 8f)
                horizontalLineTo(20f)
                arcTo(1f, 1f, 0f, false, true, 21f, 9f)
                verticalLineTo(19f)
                arcTo(1f, 1f, 0f, false, true, 20f, 20f)
                horizontalLineTo(4f)
                arcTo(1f, 1f, 0f, false, true, 3f, 19f)
                verticalLineTo(9f)
                arcTo(1f, 1f, 0f, false, true, 4f, 8f)
                close()
                moveTo(15.5f, 13.5f)
                arcTo(3.5f, 3.5f, 0f, true, true, 8.5f, 13.5f)
                arcTo(3.5f, 3.5f, 0f, true, true, 15.5f, 13.5f)
            }
        }.build()
    }

    val Gallery: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gallery",
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
                moveTo(5f, 4f)
                horizontalLineTo(19f)
                arcTo(2f, 2f, 0f, false, true, 21f, 6f)
                verticalLineTo(18f)
                arcTo(2f, 2f, 0f, false, true, 19f, 20f)
                horizontalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 3f, 18f)
                verticalLineTo(6f)
                arcTo(2f, 2f, 0f, false, true, 5f, 4f)
                close()
                moveTo(11f, 10f)
                arcTo(2f, 2f, 0f, true, true, 7f, 10f)
                arcTo(2f, 2f, 0f, true, true, 11f, 10f)
                moveTo(21f, 16f)
                lineTo(16f, 11f)
                lineTo(7f, 20f)
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
                strokeLineWidth = 1.75f,
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

    val Warning: ImageVector by lazy {
        ImageVector.Builder(
            name = "Warning",
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
                moveTo(12f, 3f)
                lineTo(2f, 20f)
                horizontalLineTo(22f)
                close()
                moveTo(12f, 10f)
                verticalLineTo(14f)
                moveTo(12f, 17f)
                horizontalLineTo(12.01f)
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
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, true, true, 3f, 12f)
                arcTo(9f, 9f, 0f, true, true, 21f, 12f)
                moveTo(12f, 7f)
                verticalLineTo(12f)
                lineTo(15f, 14f)
            }
        }.build()
    }

    val Lock: ImageVector by lazy {
        ImageVector.Builder(
            name = "Lock",
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
                moveTo(7f, 11f)
                horizontalLineTo(17f)
                arcTo(2f, 2f, 0f, false, true, 19f, 13f)
                verticalLineTo(19f)
                arcTo(2f, 2f, 0f, false, true, 17f, 21f)
                horizontalLineTo(7f)
                arcTo(2f, 2f, 0f, false, true, 5f, 19f)
                verticalLineTo(13f)
                arcTo(2f, 2f, 0f, false, true, 7f, 11f)
                close()
                moveTo(8f, 11f)
                verticalLineTo(8f)
                arcTo(4f, 4f, 0f, false, true, 16f, 8f)
                verticalLineTo(11f)
            }
        }.build()
    }

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
