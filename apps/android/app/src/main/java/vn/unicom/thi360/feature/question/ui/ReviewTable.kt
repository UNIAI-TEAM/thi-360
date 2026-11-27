package vn.unicom.thi360.feature.question.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.unicom.thi360.core.theme.Thi360Theme

private val REVIEW_TABLE_WIDE_BREAKPOINT_DP = 600.dp

internal data class ReviewTableColumn(
    val header: String,
    val weight: Float = 1f,
)

internal data class ReviewTableRowData(
    val key: String,
    val primaryCell: @Composable () -> Unit,
    val detailCells: List<Pair<String, @Composable () -> Unit>>,
)

@Composable
internal fun ResponsiveReviewTable(
    columns: List<ReviewTableColumn>,
    rows: List<ReviewTableRowData>,
    modifier: Modifier = Modifier,
) {
    val brand = Thi360Theme.colors
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isWide = maxWidth >= REVIEW_TABLE_WIDE_BREAKPOINT_DP
        if (isWide) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(QuestionTestTags.REVIEW_TABLE_WIDE),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    columns.forEach { col ->
                        Text(
                            text = col.header,
                            color = brand.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(col.weight),
                        )
                    }
                }
                HorizontalDivider(color = brand.border, thickness = 1.dp)
                rows.forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(modifier = Modifier.weight(columns[0].weight)) {
                            row.primaryCell()
                        }
                        row.detailCells.forEachIndexed { cellIndex, (_, cellContent) ->
                            val colWeight = columns.getOrElse(cellIndex + 1) { columns.last() }.weight
                            Box(modifier = Modifier.weight(colWeight)) {
                                cellContent()
                            }
                        }
                    }
                    if (rowIndex < rows.lastIndex) {
                        HorizontalDivider(color = brand.border.copy(alpha = 0.6f), thickness = 1.dp)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(QuestionTestTags.REVIEW_TABLE_STACKED),
            ) {
                rows.forEachIndexed { rowIndex, row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.primaryCell()
                        row.detailCells.forEach { (label, cellContent) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = "$label:",
                                    color = brand.textSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    cellContent()
                                }
                            }
                        }
                    }
                    if (rowIndex < rows.lastIndex) {
                        HorizontalDivider(color = brand.border.copy(alpha = 0.7f), thickness = 1.dp)
                    }
                }
            }
        }
    }
}
