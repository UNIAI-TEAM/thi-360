package vn.unicom.thi360.feature.catalog

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object CatalogIcons {
    val Search: ImageVector by lazy {
        vectorIcon("CatalogSearch") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(11f, 4f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = false, 11f, 18f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = false, 11f, 4f)
                moveTo(20f, 20f)
                lineTo(16.2f, 16.2f)
            }
        }
    }

    val Close: ImageVector by lazy {
        vectorIcon("CatalogClose") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }
    }

    val Filter: ImageVector by lazy {
        vectorIcon("CatalogFilter") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(4f, 6f)
                lineTo(20f, 6f)
                moveTo(7f, 12f)
                lineTo(17f, 12f)
                moveTo(10f, 18f)
                lineTo(14f, 18f)
            }
        }
    }

    val ArrowBack: ImageVector by lazy {
        vectorIcon("CatalogArrowBack") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(19f, 12f)
                lineTo(5f, 12f)
                moveTo(11f, 6f)
                lineTo(5f, 12f)
                lineTo(11f, 18f)
            }
        }
    }

    val Refresh: ImageVector by lazy {
        vectorIcon("CatalogRefresh") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(20f, 12f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.6f, 6.3f)
                moveTo(20f, 4f)
                lineTo(20f, 9f)
                lineTo(15f, 9f)
            }
        }
    }

    val Info: ImageVector by lazy {
        vectorIcon("CatalogInfo") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(12f, 3f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 21f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 3f)
                moveTo(12f, 11f)
                lineTo(12f, 16f)
                moveTo(12f, 8f)
                lineTo(12f, 8.2f)
            }
        }
    }

    val Warning: ImageVector by lazy {
        vectorIcon("CatalogWarning") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3f)
                lineTo(21f, 19f)
                lineTo(3f, 19f)
                close()
                moveTo(12f, 10f)
                lineTo(12f, 14f)
                moveTo(12f, 16.5f)
                lineTo(12f, 16.7f)
            }
        }
    }

    val Bundle: ImageVector by lazy {
        vectorIcon("CatalogBundle") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 7f)
                lineTo(12f, 3f)
                lineTo(20f, 7f)
                lineTo(20f, 17f)
                lineTo(12f, 21f)
                lineTo(4f, 17f)
                close()
                moveTo(4f, 7f)
                lineTo(12f, 11f)
                lineTo(20f, 7f)
                moveTo(12f, 11f)
                lineTo(12f, 21f)
            }
        }
    }

    val Book: ImageVector by lazy {
        vectorIcon("CatalogBook") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 5f)
                lineTo(11f, 5f)
                lineTo(12f, 6.5f)
                lineTo(13f, 5f)
                lineTo(20f, 5f)
                lineTo(20f, 19f)
                lineTo(13f, 19f)
                lineTo(12f, 20f)
                lineTo(11f, 19f)
                lineTo(4f, 19f)
                close()
                moveTo(12f, 6.5f)
                lineTo(12f, 20f)
            }
        }
    }

    val Grid: ImageVector by lazy {
        vectorIcon("CatalogGrid") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 4f)
                lineTo(10f, 4f)
                lineTo(10f, 10f)
                lineTo(4f, 10f)
                close()
                moveTo(14f, 4f)
                lineTo(20f, 4f)
                lineTo(20f, 10f)
                lineTo(14f, 10f)
                close()
                moveTo(4f, 14f)
                lineTo(10f, 14f)
                lineTo(10f, 20f)
                lineTo(4f, 20f)
                close()
                moveTo(14f, 14f)
                lineTo(20f, 14f)
                lineTo(20f, 20f)
                lineTo(14f, 20f)
                close()
            }
        }
    }

    val Document: ImageVector by lazy {
        vectorIcon("CatalogDocument") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 3f)
                lineTo(14f, 3f)
                lineTo(19f, 8f)
                lineTo(19f, 21f)
                lineTo(6f, 21f)
                close()
                moveTo(9f, 13f)
                lineTo(16f, 13f)
                moveTo(9f, 17f)
                lineTo(14f, 17f)
            }
        }
    }

    private fun vectorIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()
}
