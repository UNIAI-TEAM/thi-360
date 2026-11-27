package vn.unicom.thi360.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.attemptengine.PartId

@Composable
internal fun PartTabs(
    tabs: List<PartTabUi>,
    onSelectPart: (PartId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val navLabel = stringResource(R.string.attempt_parts_navLabel)
    val selectedIndex = tabs.indexOfFirst { it.isSelected }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = navLabel }
            .testTag(PlayerTestTags.PART_TABS),
    ) {
        items(items = tabs, key = { it.partId.toString() }) { tab ->
            PartTabCard(
                tab = tab,
                onClick = {
                    if (tab.isClickable) {
                        onSelectPart(tab.partId)
                    }
                },
            )
        }
    }
}

@Composable
private fun PartTabCard(
    tab: PartTabUi,
    onClick: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val stateLabel = partStateLabel(tab.state)

    val a11yDescription = if (tab.state == AttemptPartStateView.State.OPEN) {
        val progress = stringResource(R.string.attempt_nav_progressLabel, tab.answeredCount, tab.totalCount)
        stringResource(R.string.attempt_parts_tabOpenAccessibility, tab.name, stateLabel, progress)
    } else {
        stringResource(R.string.attempt_parts_tabAccessibility, tab.name, stateLabel)
    }

    val shape = RoundedCornerShape(12.dp)
    val containerColor = when {
        tab.isSelected -> colors.primaryContainer
        tab.state == AttemptPartStateView.State.CLOSED -> statusColors.successBg
        tab.state == AttemptPartStateView.State.PENDING ||
            tab.state == AttemptPartStateView.State.SKIPPED -> colors.bgSubtle
        else -> colors.bgSurface
    }
    val borderColor = when {
        tab.isSelected -> colors.primaryFill
        tab.state == AttemptPartStateView.State.CLOSED -> statusColors.success.copy(alpha = 0.45f)
        tab.state == AttemptPartStateView.State.GENERATION_FAILED -> colors.dangerText.copy(alpha = 0.55f)
        else -> colors.border
    }
    val borderWidth = if (tab.isSelected) 1.5.dp else 1.dp

    Surface(
        shape = shape,
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .widthIn(min = 128.dp, max = 236.dp)
            .heightIn(min = 48.dp)
            .clip(shape)
            .semantics(mergeDescendants = true) {
                selected = tab.isSelected
                contentDescription = a11yDescription
            }
            .clickable(
                enabled = tab.isClickable,
                role = Role.Tab,
                onClick = onClick,
            )
            .testTag(PlayerTestTags.partTab(tab.partId.toString())),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (tab.state) {
                    AttemptPartStateView.State.CLOSED -> {
                        Icon(
                            imageVector = PlayerIcons.CheckCircle,
                            contentDescription = null,
                            tint = statusColors.success,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    AttemptPartStateView.State.PENDING,
                    AttemptPartStateView.State.SKIPPED,
                    -> {
                        Icon(
                            imageVector = PlayerIcons.Lock,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(15.dp),
                        )
                    }

                    AttemptPartStateView.State.GENERATION_FAILED -> {
                        Icon(
                            imageVector = PlayerIcons.Warning,
                            contentDescription = null,
                            tint = colors.dangerText,
                            modifier = Modifier.size(15.dp),
                        )
                    }

                    AttemptPartStateView.State.AWAITING_CHOICE,
                    AttemptPartStateView.State.GENERATING,
                    -> {
                        Icon(
                            imageVector = PlayerIcons.Clock,
                            contentDescription = null,
                            tint = statusColors.warning,
                            modifier = Modifier.size(15.dp),
                        )
                    }

                    AttemptPartStateView.State.OPEN -> Unit
                }

                Text(
                    text = tab.name,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (tab.isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    color = when {
                        tab.isSelected -> colors.primaryText
                        tab.state == AttemptPartStateView.State.CLOSED -> statusColors.success
                        tab.state == AttemptPartStateView.State.PENDING ||
                            tab.state == AttemptPartStateView.State.SKIPPED -> colors.textMuted
                        else -> colors.text
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when (tab.state) {
                AttemptPartStateView.State.CLOSED -> Unit

                AttemptPartStateView.State.OPEN -> {
                    val progressFraction = if (tab.totalCount > 0) {
                        (tab.answeredCount.toFloat() / tab.totalCount.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Text(
                        text = stringResource(
                            R.string.attempt_parts_openProgress,
                            tab.answeredCount,
                            tab.totalCount,
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(PlayerTestTags.partTabProgress(tab.partId.toString())),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.border),
                    ) {
                        if (progressFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.primaryFill),
                            )
                        }
                    }
                }

                else -> {
                    Text(
                        text = stateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (tab.state) {
                            AttemptPartStateView.State.GENERATION_FAILED -> colors.dangerText
                            AttemptPartStateView.State.AWAITING_CHOICE,
                            AttemptPartStateView.State.GENERATING,
                            -> statusColors.warning
                            else -> colors.textMuted
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(PlayerTestTags.partTabState(tab.partId.toString())),
                    )
                }
            }
        }
    }
}

@Composable
internal fun partStateLabel(state: AttemptPartStateView.State): String = when (state) {
    AttemptPartStateView.State.PENDING -> stringResource(R.string.attempt_parts_states_PENDING)
    AttemptPartStateView.State.AWAITING_CHOICE -> stringResource(R.string.attempt_parts_states_AWAITING_CHOICE)
    AttemptPartStateView.State.GENERATING -> stringResource(R.string.attempt_parts_states_GENERATING)
    AttemptPartStateView.State.GENERATION_FAILED -> stringResource(R.string.attempt_parts_states_GENERATION_FAILED)
    AttemptPartStateView.State.OPEN -> stringResource(R.string.attempt_parts_states_OPEN)
    AttemptPartStateView.State.CLOSED -> stringResource(R.string.attempt_parts_states_CLOSED)
    AttemptPartStateView.State.SKIPPED -> stringResource(R.string.attempt_parts_states_SKIPPED)
}
