package vn.unicom.thi360.feature.attachments.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object AttachmentIcons {
    val MusicNote: ImageVector by lazy {
        ImageVector.Builder(
            name = "AttachmentMusicNote",
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
                moveTo(9f, 18f)
                verticalLineTo(5f)
                lineTo(21f, 3f)
                verticalLineTo(16f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
            ) {
                moveTo(6f, 15f)
                arcToRelative(3f, 3f, 0f, true, false, 0f, 6f)
                arcToRelative(3f, 3f, 0f, true, false, 0f, -6f)
                close()
                moveTo(18f, 13f)
                arcToRelative(3f, 3f, 0f, true, false, 0f, 6f)
                arcToRelative(3f, 3f, 0f, true, false, 0f, -6f)
                close()
            }
        }.build()
    }

    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "AttachmentPlay",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 4f)
                verticalLineTo(20f)
                lineTo(20f, 12f)
                close()
            }
        }.build()
    }

    val Waveform: ImageVector by lazy {
        ImageVector.Builder(
            name = "AttachmentWaveform",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(4f, 10f)
                verticalLineTo(14f)
                moveTo(8f, 6f)
                verticalLineTo(18f)
                moveTo(12f, 9f)
                verticalLineTo(15f)
                moveTo(16f, 4f)
                verticalLineTo(20f)
                moveTo(20f, 10f)
                verticalLineTo(14f)
            }
        }.build()
    }

    val Resume: ImageVector by lazy {
        ImageVector.Builder(
            name = "AttachmentResume",
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
                moveTo(3f, 12f)
                arcToRelative(9f, 9f, 0f, true, false, 3f, -6.7f)
                lineTo(3f, 8f)
                moveTo(3f, 3f)
                verticalLineTo(8f)
                horizontalLineTo(8f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 9f)
                verticalLineTo(15f)
                lineTo(15f, 12f)
                close()
            }
        }.build()
    }

    val FileDocument: ImageVector by lazy {
        ImageVector.Builder(
            name = "AttachmentFileDocument",
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
                moveTo(14f, 2f)
                horizontalLineTo(6f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
                verticalLineTo(20f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                horizontalLineTo(18f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
                verticalLineTo(8f)
                close()
                moveTo(14f, 2f)
                verticalLineTo(8f)
                horizontalLineTo(20f)
                moveTo(8f, 13f)
                horizontalLineTo(16f)
                moveTo(8f, 17f)
                horizontalLineTo(13f)
            }
        }.build()
    }

    val InfoCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "AttachmentInfoCircle",
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
                moveTo(12f, 3f)
                arcToRelative(9f, 9f, 0f, true, false, 0f, 18f)
                arcToRelative(9f, 9f, 0f, true, false, 0f, -18f)
                close()
                moveTo(12f, 11f)
                verticalLineTo(16f)
                moveTo(12f, 8f)
                horizontalLineTo(12.01f)
            }
        }.build()
    }

    val WarningTriangle: ImageVector by lazy {
        ImageVector.Builder(
            name = "AttachmentWarningTriangle",
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
}
