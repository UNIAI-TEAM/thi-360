package vn.unicom.thi360.feature.question.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object QuestionIcons {
    val FlagOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "QuestionFlagOutline",
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
                moveTo(4f, 15f)
                reflectiveCurveTo(5f, 14f, 8f, 14f)
                reflectiveCurveTo(13f, 16f, 16f, 16f)
                reflectiveCurveTo(20f, 15f, 20f, 15f)
                verticalLineTo(3f)
                reflectiveCurveTo(19f, 4f, 16f, 4f)
                reflectiveCurveTo(11f, 2f, 8f, 2f)
                reflectiveCurveTo(4f, 3f, 4f, 3f)
                close()
                moveTo(4f, 22f)
                verticalLineTo(15f)
            }
        }.build()
    }

    val FlagFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "QuestionFlagFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 15f)
                reflectiveCurveTo(5f, 14f, 8f, 14f)
                reflectiveCurveTo(13f, 16f, 16f, 16f)
                reflectiveCurveTo(20f, 15f, 20f, 15f)
                verticalLineTo(3f)
                reflectiveCurveTo(19f, 4f, 16f, 4f)
                reflectiveCurveTo(11f, 2f, 8f, 2f)
                reflectiveCurveTo(4f, 3f, 4f, 3f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(4f, 22f)
                verticalLineTo(15f)
            }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "QuestionCheck",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 3f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(20f, 6f)
                lineTo(9f, 17f)
                lineTo(4f, 12f)
            }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "QuestionClose",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.6f,
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

    val Partial: ImageVector by lazy {
        ImageVector.Builder(
            name = "QuestionPartial",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.4f,
            ) {
                moveTo(12f, 3f)
                arcToRelative(9f, 9f, 0f, true, false, 0f, 18f)
                arcToRelative(9f, 9f, 0f, true, false, 0f, -18f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                arcToRelative(9f, 9f, 0f, false, true, 0f, 18f)
                close()
            }
        }.build()
    }

    val Clock: ImageVector by lazy {
        ImageVector.Builder(
            name = "QuestionClock",
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

    val Dash: ImageVector by lazy {
        ImageVector.Builder(
            name = "QuestionDash",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 3f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(6f, 12f)
                horizontalLineTo(18f)
            }
        }.build()
    }

    val ChevronDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "QuestionChevronDown",
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
                moveTo(6f, 9f)
                lineTo(12f, 15f)
                lineTo(18f, 9f)
            }
        }.build()
    }

    val BulletList: ImageVector by lazy {
        ImageVector.Builder(
            name = "QuestionBulletList",
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
                moveTo(9f, 6f)
                horizontalLineTo(20f)
                moveTo(9f, 12f)
                horizontalLineTo(20f)
                moveTo(9f, 18f)
                horizontalLineTo(20f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 6f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, 3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, -3f, 0f)
                moveTo(4f, 12f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, 3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, -3f, 0f)
                moveTo(4f, 18f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, 3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, -3f, 0f)
            }
        }.build()
    }

    val OrderedList: ImageVector by lazy {
        ImageVector.Builder(
            name = "QuestionOrderedList",
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
                moveTo(10f, 6f)
                horizontalLineTo(20f)
                moveTo(10f, 12f)
                horizontalLineTo(20f)
                moveTo(10f, 18f)
                horizontalLineTo(20f)
                moveTo(4f, 5f)
                lineTo(5.5f, 4f)
                verticalLineTo(8f)
                moveTo(4f, 11f)
                horizontalLineTo(6.5f)
                lineTo(4f, 14f)
                horizontalLineTo(6.5f)
                moveTo(4f, 17f)
                horizontalLineTo(6.5f)
                lineTo(5f, 18.5f)
                lineTo(6.5f, 20f)
                horizontalLineTo(4f)
            }
        }.build()
    }

    val Undo: ImageVector by lazy {
        ImageVector.Builder(
            name = "QuestionUndo",
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
                moveTo(9f, 14f)
                lineTo(4f, 9f)
                lineTo(9f, 4f)
                moveTo(4f, 9f)
                horizontalLineTo(14.5f)
                arcToRelative(5.5f, 5.5f, 0f, false, true, 0f, 11f)
                horizontalLineTo(11f)
            }
        }.build()
    }

    val Redo: ImageVector by lazy {
        ImageVector.Builder(
            name = "QuestionRedo",
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
                moveTo(15f, 14f)
                lineTo(20f, 9f)
                lineTo(15f, 4f)
                moveTo(20f, 9f)
                horizontalLineTo(9.5f)
                arcToRelative(5.5f, 5.5f, 0f, false, false, 0f, 11f)
                horizontalLineTo(13f)
            }
        }.build()
    }
}
