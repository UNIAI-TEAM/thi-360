package vn.unicom.thi360.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object TwoColumnTestTags {
    const val SIDE_BY_SIDE = "two_column_side_by_side"
    const val STACKED = "two_column_stacked"
    const val START_PANE = "two_column_start"
    const val END_PANE = "two_column_end"
}

@Composable
fun TwoColumnLayout(
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    startWeight: Float = 1f,
    endWeight: Float = 1f,
) {
    val shellWidth = LocalShellWidth.current
    if (shellWidth == ShellWidth.TwoColumn) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .testTag(TwoColumnTestTags.SIDE_BY_SIDE),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Box(
                modifier = Modifier
                    .weight(startWeight)
                    .testTag(TwoColumnTestTags.START_PANE),
            ) {
                start()
            }
            Box(
                modifier = Modifier
                    .weight(endWeight)
                    .testTag(TwoColumnTestTags.END_PANE),
            ) {
                end()
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag(TwoColumnTestTags.STACKED),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TwoColumnTestTags.START_PANE),
            ) {
                start()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TwoColumnTestTags.END_PANE),
            ) {
                end()
            }
        }
    }
}
