package vn.unicom.thi360.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vn.unicom.thi360.core.theme.Thi360Theme

object ListDetailTestTags {
    const val TWO_COLUMN = "list_detail_two_column"
    const val SINGLE_COLUMN = "list_detail_single_column"
    const val LIST_PANE = "list_detail_list_pane"
    const val DETAIL_PANE = "list_detail_detail_pane"
    const val EMPTY_DETAIL = "list_detail_empty_detail"
}

@Composable
fun <T> ListDetailLayout(
    selected: T?,
    list: @Composable () -> Unit,
    detail: @Composable (T) -> Unit,
    emptyDetail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    listPaneWidth: Dp = 340.dp,
) {
    val shellWidth = LocalShellWidth.current
    val stateHolder = rememberSaveableStateHolder()
    val currentList by rememberUpdatedState(list)
    val listContent = remember(stateHolder) {
        movableContentOf {
            stateHolder.SaveableStateProvider(LIST_PANE_KEY) {
                currentList()
            }
        }
    }

    if (shellWidth == ShellWidth.TwoColumn) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .testTag(ListDetailTestTags.TWO_COLUMN),
        ) {
            Box(
                modifier = Modifier
                    .width(listPaneWidth)
                    .fillMaxHeight()
                    .testTag(ListDetailTestTags.LIST_PANE),
            ) {
                listContent()
            }
            VerticalDivider(color = Thi360Theme.colors.border)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(
                        if (selected != null) {
                            ListDetailTestTags.DETAIL_PANE
                        } else {
                            ListDetailTestTags.EMPTY_DETAIL
                        },
                    ),
            ) {
                if (selected != null) {
                    detail(selected)
                } else {
                    emptyDetail()
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag(ListDetailTestTags.SINGLE_COLUMN),
        ) {
            if (selected != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(ListDetailTestTags.DETAIL_PANE),
                ) {
                    detail(selected)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(ListDetailTestTags.LIST_PANE),
                ) {
                    listContent()
                }
            }
        }
    }
}

private const val LIST_PANE_KEY = "thi360_list_detail_list_pane"
