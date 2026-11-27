package vn.unicom.thi360.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.generated.model.ChoiceSubjectView
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.attemptengine.ItemId

@Composable
internal fun PlayerNarrowBottomBar(
    currentIndex: Int,
    answeredCount: Int,
    totalCount: Int,
    onOpenGrid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val openGridA11y = stringResource(R.string.attempt_nav_openGrid, currentIndex, totalCount)
    val progressFraction = if (totalCount > 0) {
        (answeredCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        color = colors.bgSurface,
        modifier = modifier
            .fillMaxWidth()
            .testTag(PlayerTestTags.NARROW_BOTTOM_BAR),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = colors.border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenGrid,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.borderStrong),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = openGridA11y }
                        .testTag(PlayerTestTags.NARROW_GRID_BUTTON),
                ) {
                    Icon(
                        imageVector = PlayerIcons.Grid,
                        contentDescription = null,
                        tint = colors.primaryText,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.attempt_nav_openGridCompact, currentIndex, totalCount),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = colors.text,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.attempt_nav_answeredCount, answeredCount, totalCount),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(PlayerTestTags.NARROW_ANSWERED_TEXT),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.border)
                            .testTag(PlayerTestTags.NARROW_PROGRESS_BAR),
                    ) {
                        if (progressFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.primaryFill),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuestionGridSheet(
    ui: PlayerUi,
    onDismiss: () -> Unit,
    onSelectFilter: (GridFilter) -> Unit,
    onSelectItem: (ItemId) -> Unit,
) {
    val colors = Thi360Theme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bgSurface,
        modifier = Modifier.testTag(PlayerTestTags.GRID_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.attempt_nav_gridLabel),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.text,
                    )
                    Text(
                        text = stringResource(
                            R.string.attempt_nav_answeredCount,
                            ui.answeredInActivePart,
                            ui.totalInActivePart,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(PlayerTestTags.GRID_CLOSE),
                ) {
                    Icon(
                        imageVector = PlayerIcons.Close,
                        contentDescription = stringResource(R.string.attempt_nav_closeGrid),
                        tint = colors.textSecondary,
                    )
                }
            }
            HorizontalDivider(color = colors.border)
            QuestionGridBody(
                gridGroups = ui.gridGroups,
                filter = ui.gridFilter,
                subjectsByCode = ui.subjectsByCode,
                onSelectFilter = onSelectFilter,
                onSelectItem = { itemId ->
                    onSelectItem(itemId)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
            )
        }
    }
}

@Composable
internal fun QuestionGridSidebar(
    ui: PlayerUi,
    sidebarWidth: Dp,
    onSelectFilter: (GridFilter) -> Unit,
    onSelectItem: (ItemId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors

    Row(
        modifier = modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .background(colors.bgSurface)
            .testTag(PlayerTestTags.GRID_SIDEBAR),
    ) {
        VerticalDivider(color = colors.border)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.attempt_nav_gridLabel),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                )
                Text(
                    text = stringResource(
                        R.string.attempt_nav_answeredCount,
                        ui.answeredInActivePart,
                        ui.totalInActivePart,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            HorizontalDivider(color = colors.border)
            QuestionGridBody(
                gridGroups = ui.gridGroups,
                filter = ui.gridFilter,
                subjectsByCode = ui.subjectsByCode,
                onSelectFilter = onSelectFilter,
                onSelectItem = onSelectItem,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuestionGridBody(
    gridGroups: List<GridGroup>,
    filter: GridFilter,
    subjectsByCode: Map<String, ChoiceSubjectView>,
    onSelectFilter: (GridFilter) -> Unit,
    onSelectItem: (ItemId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors

    Column(modifier = modifier) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GridFilterChip(
                label = stringResource(R.string.attempt_nav_filterAll),
                selected = filter == GridFilter.All,
                tag = PlayerTestTags.GRID_FILTER_ALL,
                onClick = { onSelectFilter(GridFilter.All) },
            )
            GridFilterChip(
                label = stringResource(R.string.attempt_nav_filterUnanswered),
                selected = filter == GridFilter.Unanswered,
                tag = PlayerTestTags.GRID_FILTER_UNANSWERED,
                onClick = { onSelectFilter(GridFilter.Unanswered) },
            )
            GridFilterChip(
                label = stringResource(R.string.attempt_nav_filterFlagged),
                selected = filter == GridFilter.Flagged,
                tag = PlayerTestTags.GRID_FILTER_FLAGGED,
                onClick = { onSelectFilter(GridFilter.Flagged) },
            )
        }

        HorizontalDivider(color = colors.border)

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val horizontalPad = 12.dp
            val gap = 6.dp
            val available = (maxWidth - horizontalPad * 2).coerceAtLeast(160.dp)
            val columns = ((available + gap) / (48.dp + gap)).toInt().coerceIn(3, 6)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PlayerTestTags.GRID_LIST),
                contentPadding = PaddingValues(horizontal = horizontalPad, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (gridGroups.isEmpty()) {
                    item(key = "empty_filtered") {
                        Text(
                            text = stringResource(R.string.attempt_nav_emptyFiltered),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp)
                                .testTag(PlayerTestTags.GRID_EMPTY_FILTERED),
                        )
                    }
                } else {
                    gridGroups.forEach { group ->
                        if (group.header != UnitGroupHeader.None) {
                            item(key = "hdr::${group.key}") {
                                val headerText = when (val h = group.header) {
                                    is UnitGroupHeader.Segment -> {
                                        if (h.isUngrouped) {
                                            stringResource(R.string.attempt_segments_ungrouped)
                                        } else {
                                            h.name?.takeIf { it.isNotBlank() }
                                                ?: stringResource(
                                                    R.string.attempt_segments_range,
                                                    h.fromDisplayNo,
                                                    h.toDisplayNo,
                                                )
                                        }
                                    }
                                    is UnitGroupHeader.Subject -> {
                                        SubjectNames.resolveLocalized(h.subjectCode, subjectsByCode)
                                    }
                                    UnitGroupHeader.None -> ""
                                }
                                if (headerText.isNotBlank()) {
                                    Text(
                                        text = headerText,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.textSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .testTag(PlayerTestTags.gridGroupHeader(group.key)),
                                    )
                                }
                            }
                        }

                        val rows = group.cells.chunked(columns)
                        items(
                            items = rows.withIndex().toList(),
                            key = { (rowIdx, _) -> "row::${group.key}::$rowIdx" },
                        ) { (_, rowCells) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(gap),
                            ) {
                                rowCells.forEach { cell ->
                                    QuestionGridCell(
                                        cell = cell,
                                        onClick = { onSelectItem(cell.itemId) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                val missingSlots = columns - rowCells.size
                                repeat(missingSlots) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                item(key = "legend") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HorizontalDivider(color = colors.border)
                        QuestionGridLegend()
                    }
                }
            }
        }
    }
}

@Composable
private fun GridFilterChip(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = if (selected) colors.primaryContainer else colors.bgSubtle,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) colors.primaryFill else colors.border,
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .testTag(tag),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                ),
                color = if (selected) colors.primaryText else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun QuestionGridCell(
    cell: GridCellUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors

    val bgColor: Color
    val fgColor: Color
    val borderColor: Color
    val isDashed: Boolean

    if (cell.flagged) {
        when (cell.completion) {
            ItemCompletion.Answered -> {
                bgColor = statusColors.dangerSolid
                fgColor = statusColors.onDanger
                borderColor = statusColors.dangerSolid
                isDashed = false
            }

            ItemCompletion.Partial -> {
                bgColor = colors.dangerBg
                fgColor = colors.dangerText
                borderColor = colors.dangerText
                isDashed = true
            }

            ItemCompletion.Blank -> {
                bgColor = colors.dangerBg
                fgColor = colors.dangerText
                borderColor = colors.dangerText
                isDashed = false
            }
        }
    } else {
        when (cell.completion) {
            ItemCompletion.Answered -> {
                bgColor = colors.primaryFill
                fgColor = colors.onPrimary
                borderColor = colors.primaryFill
                isDashed = false
            }

            ItemCompletion.Partial -> {
                bgColor = colors.primaryContainer
                fgColor = colors.primaryText
                borderColor = colors.primaryFill
                isDashed = true
            }

            ItemCompletion.Blank -> {
                bgColor = colors.bgSurface
                fgColor = colors.text
                borderColor = colors.borderStrong
                isDashed = false
            }
        }
    }

    val ringColor = if (cell.flagged) statusColors.dangerSolid else colors.primaryFill
    val completionWord = when (cell.completion) {
        ItemCompletion.Answered -> stringResource(R.string.attempt_nav_stateAnswered)
        ItemCompletion.Partial -> stringResource(R.string.attempt_nav_statePartial)
        ItemCompletion.Blank -> stringResource(R.string.attempt_nav_stateBlank)
    }
    val flaggedWord = stringResource(R.string.attempt_nav_stateFlagged)
    val currentWord = stringResource(R.string.attempt_nav_stateCurrent)
    val unsavedWord = stringResource(R.string.attempt_nav_stateUnsaved)
    val qNumber = stringResource(R.string.attempt_nav_questionNumber, cell.displayNo)
    val cellA11y = buildList {
        add(qNumber)
        add(completionWord)
        if (cell.flagged) add(flaggedWord)
        if (cell.isCurrent) add(currentWord)
        if (cell.isUnsaved) add(unsavedWord)
    }.joinToString(", ")

    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(bgColor)
            .drawBehind {
                val radiusPx = 10.dp.toPx()
                val strokeWidth = if (cell.isCurrent) 2.5.dp.toPx() else 1.5.dp.toPx()
                val inset = strokeWidth / 2f
                val stroke = if (isDashed) {
                    Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                    )
                } else {
                    Stroke(width = strokeWidth)
                }
                drawRoundRect(
                    color = if (cell.isCurrent) ringColor else borderColor,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = stroke,
                )
                if (cell.isCurrent) {
                    val innerOffset = strokeWidth + 2.dp.toPx()
                    drawRoundRect(
                        color = if (cell.completion == ItemCompletion.Answered) {
                            statusColors.onDanger.copy(alpha = 0.7f)
                        } else {
                            ringColor.copy(alpha = 0.35f)
                        },
                        topLeft = Offset(innerOffset, innerOffset),
                        size = Size(
                            (size.width - innerOffset * 2).coerceAtLeast(0f),
                            (size.height - innerOffset * 2).coerceAtLeast(0f),
                        ),
                        cornerRadius = CornerRadius((radiusPx - 2.dp.toPx()).coerceAtLeast(2f)),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = cellA11y
                selected = cell.isCurrent
            }
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(PlayerTestTags.gridCell(cell.itemId.toString())),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cell.displayNo.toString(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (cell.isCurrent || cell.completion == ItemCompletion.Answered) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                },
                fontFeatureSettings = "tnum",
            ),
            color = fgColor,
            maxLines = 1,
        )

        if (cell.isUnsaved) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.bgSurface)
                    .padding(1.dp)
                    .clip(CircleShape)
                    .background(statusColors.warning)
                    .testTag(PlayerTestTags.gridCellUnsaved(cell.itemId.toString())),
            )
        }

        if (cell.flagged) {
            Icon(
                imageVector = PlayerIcons.Flag,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 3.dp, end = 3.dp)
                    .size(10.dp)
                    .testTag(PlayerTestTags.gridCellFlag(cell.itemId.toString())),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuestionGridLegend(
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PlayerTestTags.GRID_LEGEND),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LegendItem(
            label = stringResource(R.string.attempt_nav_legendAnswered),
            swatch = {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.primaryFill),
                )
            },
        )
        LegendItem(
            label = stringResource(R.string.attempt_nav_legendPartial),
            swatch = {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.primaryContainer)
                        .drawBehind {
                            drawRoundRect(
                                color = colors.primaryFill,
                                cornerRadius = CornerRadius(4.dp.toPx()),
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f),
                                ),
                            )
                        },
                )
            },
        )
        LegendItem(
            label = stringResource(R.string.attempt_nav_legendBlank),
            swatch = {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = colors.bgSurface,
                    border = BorderStroke(1.dp, colors.borderStrong),
                    modifier = Modifier.size(14.dp),
                ) {}
            },
        )
        LegendItem(
            label = stringResource(R.string.attempt_nav_legendCurrent),
            swatch = {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = colors.bgSurface,
                    border = BorderStroke(2.dp, colors.primaryFill),
                    modifier = Modifier.size(14.dp),
                ) {}
            },
        )
        LegendItem(
            label = stringResource(R.string.attempt_nav_legendFlagged),
            tag = PlayerTestTags.GRID_LEGEND_FLAGGED,
            swatch = {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = colors.dangerBg,
                    border = BorderStroke(1.2.dp, colors.dangerText),
                    modifier = Modifier.size(14.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = PlayerIcons.Flag,
                            contentDescription = null,
                            tint = colors.dangerText,
                            modifier = Modifier.size(9.dp),
                        )
                    }
                }
            },
        )
        LegendItem(
            label = stringResource(R.string.attempt_nav_legendUnsaved),
            swatch = {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColors.warning),
                )
            },
        )
    }
}

@Composable
private fun LegendItem(
    label: String,
    swatch: @Composable () -> Unit,
    tag: String? = null,
) {
    val colors = Thi360Theme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
    ) {
        swatch()
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
