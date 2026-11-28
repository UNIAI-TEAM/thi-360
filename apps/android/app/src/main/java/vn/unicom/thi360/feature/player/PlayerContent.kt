package vn.unicom.thi360.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.attemptengine.ItemId
import vn.unicom.thi360.feature.attemptengine.LocalAnswer
import vn.unicom.thi360.feature.attemptengine.PartId
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.ui.QuestionSetView
import vn.unicom.thi360.feature.question.ui.QuestionView
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine

private sealed interface PlayerContentRow {
    val rowKey: String

    data class Header(
        override val rowKey: String,
        val header: UnitGroupHeader,
    ) : PlayerContentRow

    data class Unit(
        override val rowKey: String,
        val unit: QuestionDisplayUnit,
    ) : PlayerContentRow
}

data class ScrollToItemRequest(
    val itemId: ItemId,
    val nonce: Long,
)

@Composable
internal fun PlayerContent(
    ui: PlayerUi,
    answers: Map<ItemId, LocalAnswer>,
    scrollRequest: ScrollToItemRequest?,
    assets: AttemptAssetSource?,
    formulaEngine: FormulaEngine?,
    onAnswer: (ItemId, AnswerValue) -> Unit,
    onFlag: (ItemId, Boolean) -> Unit,
    onVisibleItemChanged: (PartId, ItemId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val activePartId = ui.activePart?.partId
    val attachmentHost = LocalPlayerAttachmentHost.current
    val attempt = ui.attempt

    val rows = remember(ui.unitGroups) {
        buildList {
            for (group in ui.unitGroups) {
                if (group.header != UnitGroupHeader.None) {
                    add(PlayerContentRow.Header(rowKey = "hdr::${group.key}", header = group.header))
                }
                for (unit in group.units) {
                    add(PlayerContentRow.Unit(rowKey = "unit::${unit.key}", unit = unit))
                }
            }
        }
    }

    if (rows.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag(PlayerTestTags.CONTENT_EMPTY),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.attempt_player_noActiveQuestion),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        }
        return
    }

    val listState = rememberLazyListState()
    var programmaticScrollDepth by remember(activePartId) { mutableIntStateOf(0) }

    LaunchedEffect(activePartId) {
        val targetId = ui.currentItemId ?: return@LaunchedEffect
        val idx = rows.indexOfFirst { row ->
            row is PlayerContentRow.Unit && targetId in row.unit.leafIds
        }
        if (idx >= 0) {
            val scrollIdx = if (idx > 0 && rows[idx - 1] is PlayerContentRow.Header) idx - 1 else idx
            programmaticScrollDepth += 1
            try {
                listState.scrollToItem(scrollIdx)
            } finally {
                programmaticScrollDepth -= 1
            }
        }
    }

    LaunchedEffect(scrollRequest?.nonce) {
        val req = scrollRequest ?: return@LaunchedEffect
        val idx = rows.indexOfFirst { row ->
            row is PlayerContentRow.Unit && req.itemId in row.unit.leafIds
        }
        if (idx >= 0) {
            val scrollIdx = if (idx > 0 && rows[idx - 1] is PlayerContentRow.Header) idx - 1 else idx
            programmaticScrollDepth += 1
            try {
                listState.animateScrollToItem(scrollIdx)
            } finally {
                programmaticScrollDepth -= 1
            }
        }
    }

    LaunchedEffect(listState, activePartId, rows) {
        val partId = activePartId ?: return@LaunchedEffect
        snapshotFlow {
            Triple(
                listState.isScrollInProgress && programmaticScrollDepth == 0,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
            .filter { (userScrolling, _, _) -> userScrolling }
            .map { (_, firstIndex, _) -> firstIndex }
            .distinctUntilChanged()
            .collect { firstIndex ->
                val visibleUnit = (firstIndex until rows.size)
                    .asSequence()
                    .mapNotNull { rows.getOrNull(it) as? PlayerContentRow.Unit }
                    .firstOrNull()
                val leafId = visibleUnit?.unit?.leafItems?.firstOrNull()?.itemId
                if (leafId != null) {
                    onVisibleItemChanged(partId, leafId)
                }
            }
    }

    val answerValueById = remember(answers) {
        buildMap<String, AnswerValue>(answers.size) {
            answers.forEach { (id, local) ->
                local.value?.let { put(id.toString(), it) }
            }
        }
    }
    val flaggedIds = remember(answers) {
        answers.entries
            .filter { it.value.flagged }
            .mapTo(HashSet()) { it.key.toString() }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag(PlayerTestTags.CONTENT_LIST),
        contentPadding = PaddingValues(
            horizontal = if (ui.layout == PlayerLayout.Wide) 16.dp else 12.dp,
            vertical = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items = rows, key = { it.rowKey }) { row ->
            when (row) {
                is PlayerContentRow.Header -> {
                    GroupHeaderCard(
                        header = row.header,
                        subjectsByCode = ui.subjectsByCode,
                    )
                }

                is PlayerContentRow.Unit -> {
                    val unit = row.unit
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(PlayerTestTags.contentUnit(unit.key)),
                    ) {
                        when (val qItem = unit.questionItem) {
                            is QuestionItem.QuestionSet -> {
                                val childById = remember(unit.childItems) {
                                    unit.childItems.associateBy { it.itemId?.toString().orEmpty() }
                                }
                                QuestionSetView(
                                    item = qItem,
                                    answers = answerValueById,
                                    mode = ui.questionMode,
                                    flaggedIds = flaggedIds,
                                    onAnswer = { childIdStr, value ->
                                        runCatching { UUID.fromString(childIdStr) }.getOrNull()?.let { id ->
                                            onAnswer(id, value)
                                        }
                                    },
                                    onFlag = if (ui.canEdit) {
                                        { childIdStr ->
                                            runCatching { UUID.fromString(childIdStr) }.getOrNull()?.let { id ->
                                                val next = !(answers[id]?.flagged == true)
                                                onFlag(id, next)
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                    assets = assets,
                                    formulaEngine = formulaEngine,
                                    attachments = buildAttachmentSlot(
                                        host = attachmentHost,
                                        attempt = attempt,
                                        item = unit.parentItem,
                                        isReadOnly = !ui.canUseAttachments,
                                    ),
                                    childAttachments = { childIdStr ->
                                        val childItem = childById[childIdStr]
                                        if (childItem != null) {
                                            buildAttachmentSlot(
                                                host = attachmentHost,
                                                attempt = attempt,
                                                item = childItem,
                                                isReadOnly = !ui.canUseAttachments,
                                            )
                                        } else {
                                            null
                                        }
                                    },
                                    forceTwoColumn = ui.layout == PlayerLayout.Wide,
                                )
                            }

                            else -> {
                                val leafId = unit.parentItem.itemId
                                val localAns = leafId?.let { answers[it] }
                                QuestionView(
                                    item = qItem,
                                    answer = localAns?.value,
                                    mode = ui.questionMode,
                                    flagged = localAns?.flagged == true,
                                    onAnswer = { value ->
                                        if (leafId != null) {
                                            onAnswer(leafId, value)
                                        }
                                    },
                                    onFlag = if (ui.canEdit && leafId != null) {
                                        { onFlag(leafId, !(localAns?.flagged == true)) }
                                    } else {
                                        null
                                    },
                                    assets = assets,
                                    formulaEngine = formulaEngine,
                                    attachments = buildAttachmentSlot(
                                        host = attachmentHost,
                                        attempt = attempt,
                                        item = unit.parentItem,
                                        isReadOnly = !ui.canUseAttachments,
                                    ),
                                    modifier = if (leafId != null) {
                                        Modifier.testTag(PlayerTestTags.contentItem(leafId.toString()))
                                    } else {
                                        Modifier
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildAttachmentSlot(
    host: PlayerAttachmentHost?,
    attempt: AttemptStateResponse?,
    item: AttemptItemView,
    isReadOnly: Boolean,
): (@Composable () -> Unit)? {
    if (host == null || attempt == null || item.attachments.isNullOrEmpty()) return null
    val context = PlayerAttachmentContext(
        attempt = attempt,
        item = item,
        isReadOnly = isReadOnly,
    )
    return { host.RenderAttachments(context) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupHeaderCard(
    header: UnitGroupHeader,
    subjectsByCode: Map<String, vn.unicom.thi360.core.network.generated.model.ChoiceSubjectView>,
) {
    val colors = Thi360Theme.colors

    when (header) {
        is UnitGroupHeader.Segment -> {
            val title = if (header.isUngrouped) {
                stringResource(R.string.attempt_segments_ungrouped)
            } else {
                header.name?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.attempt_segments_range, header.fromDisplayNo, header.toDisplayNo)
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.bgSubtle,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .testTag(PlayerTestTags.segmentHeader(header.key)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.attempt_segments_range,
                                header.fromDisplayNo,
                                header.toDisplayNo,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        val cmdCount = header.commandCount
                        if (cmdCount != null && cmdCount > 0) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.attempt_segments_commands,
                                    cmdCount,
                                    cmdCount,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary,
                            )
                        }
                        val points = header.maxPoints
                        if (points != null && points > BigDecimal.ZERO) {
                            Text(
                                text = stringResource(
                                    R.string.attempt_segments_maxPoints,
                                    points.stripTrailingZeros().toPlainString(),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.primaryText,
                            )
                        }
                    }
                }
            }
        }

        is UnitGroupHeader.Subject -> {
            val subjectName = SubjectNames.resolveLocalized(header.subjectCode, subjectsByCode)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.primaryContainer,
                border = BorderStroke(1.dp, colors.primaryFill.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .testTag(PlayerTestTags.subjectHeader(header.key)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.attempt_subjects_heading, subjectName),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.primaryText,
                        modifier = Modifier.semantics { heading() },
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.attempt_subjects_range,
                                header.fromDisplayNo,
                                header.toDisplayNo,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.attempt_subjects_questionCount,
                                header.questionCount,
                                header.questionCount,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }

        UnitGroupHeader.None -> Unit
    }
}
