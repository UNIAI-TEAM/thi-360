package vn.unicom.thi360.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.unicom.thi360.R
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.attemptengine.PartId
import vn.unicom.thi360.feature.attemptengine.SaveStatus
import vn.unicom.thi360.feature.attemptengine.UnsavedReason

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerTopBar(
    ui: PlayerUi,
    onBack: () -> Unit,
    onToggleCollapse: () -> Unit,
    onActionClick: () -> Unit,
    onSelectPart: (PartId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PlayerTestTags.TOP_BAR),
    ) {
        Surface(
            color = colors.bgSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (ui.headerCollapsed) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag(PlayerTestTags.TOP_BAR_COLLAPSED),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = ui.activePartDisplayName.ifBlank {
                                ui.examTitle.ifBlank { stringResource(R.string.attempt_player_pageTitle) }
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { heading() }
                                .testTag(PlayerTestTags.TOP_BAR_PART_NAME),
                        )
                        if (ui.timer.visible) {
                            TimerPill(timer = ui.timer, compact = true)
                        }
                        IconButton(
                            onClick = onToggleCollapse,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag(PlayerTestTags.TOP_BAR_COLLAPSE_TOGGLE),
                        ) {
                            Icon(
                                imageVector = PlayerIcons.ChevronDown,
                                contentDescription = stringResource(R.string.attempt_header_expandHeader),
                                tint = colors.textSecondary,
                            )
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SaveStatusChip(saveStatus = ui.saveStatus)
                        if (ui.topAction != PlayerTopAction.None) {
                            PrimaryActionButton(
                                action = ui.topAction,
                                enabled = ui.topActionEnabled,
                                onClick = onActionClick,
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = if (ui.showPartTabs) 8.dp else 10.dp)
                        .testTag(PlayerTestTags.TOP_BAR_EXPANDED),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag(PlayerTestTags.TOP_BAR_BACK),
                        ) {
                            Icon(
                                imageVector = PlayerIcons.ArrowBack,
                                contentDescription = stringResource(R.string.attempt_header_backLabel),
                                tint = colors.text,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = ui.examTitle.ifBlank { stringResource(R.string.attempt_player_pageTitle) },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .semantics { heading() }
                                    .testTag(PlayerTestTags.TOP_BAR_TITLE),
                            )
                            if (ui.activePartDisplayName.isNotBlank()) {
                                Text(
                                    text = ui.activePartDisplayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag(PlayerTestTags.TOP_BAR_PART_NAME),
                                )
                            }
                        }
                        IconButton(
                            onClick = onToggleCollapse,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag(PlayerTestTags.TOP_BAR_COLLAPSE_TOGGLE),
                        ) {
                            Icon(
                                imageVector = PlayerIcons.ChevronUp,
                                contentDescription = stringResource(R.string.attempt_header_collapseHeader),
                                tint = colors.textSecondary,
                            )
                        }
                    }

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (ui.timer.visible) {
                            TimerPill(timer = ui.timer, compact = false)
                        }
                        SaveStatusChip(saveStatus = ui.saveStatus)
                        if (ui.topAction != PlayerTopAction.None) {
                            PrimaryActionButton(
                                action = ui.topAction,
                                enabled = ui.topActionEnabled,
                                onClick = onActionClick,
                            )
                        }
                    }

                    if (ui.showPartTabs) {
                        PartTabs(
                            tabs = ui.partTabs,
                            onSelectPart = onSelectPart,
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = colors.border)

        if (ui.isOffline) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(statusColors.warningBg)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(PlayerTestTags.OFFLINE_BANNER),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = PlayerIcons.WifiOff,
                    contentDescription = null,
                    tint = statusColors.warning,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.attempt_player_offlineBanner),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = statusColors.warning,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = statusColors.warning.copy(alpha = 0.25f))
        }

        if (ui.readOnlyBanner != ReadOnlyBannerKind.None) {
            val bannerText = when (ui.readOnlyBanner) {
                ReadOnlyBannerKind.SessionUnavailable -> stringResource(R.string.attempt_readOnly_unavailable)
                ReadOnlyBannerKind.PartClosed -> stringResource(R.string.attempt_readOnly_partClosed)
                ReadOnlyBannerKind.None -> ""
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(statusColors.infoBg)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(PlayerTestTags.READ_ONLY_BANNER),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = PlayerIcons.Info,
                    contentDescription = null,
                    tint = statusColors.info,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = bannerText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColors.info,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = colors.border)
        }
    }
}

@Composable
private fun TimerPill(
    timer: TimerUi,
    compact: Boolean,
) {
    val colors = Thi360Theme.colors
    val label = if (timer.isShared) {
        stringResource(R.string.attempt_timer_sharedRemaining)
    } else {
        stringResource(R.string.attempt_timer_partRemaining, timer.partOrd ?: 1)
    }
    val accessibilityText = if (timer.parts.hours > 0) {
        stringResource(
            R.string.attempt_timer_ariaLabel,
            label,
            timer.parts.hours,
            timer.parts.minutes,
            timer.parts.seconds,
        )
    } else {
        stringResource(
            R.string.attempt_timer_ariaLabelShort,
            label,
            timer.parts.minutes,
            timer.parts.seconds,
        )
    }

    val bgColor = if (timer.isUrgent) colors.dangerBg else colors.bgSubtle
    val borderColor = if (timer.isUrgent) colors.dangerText else colors.borderStrong
    val textColor = if (timer.isUrgent) colors.dangerText else colors.text

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityText
            }
            .testTag(PlayerTestTags.TOP_BAR_TIMER),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 6.dp else 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = PlayerIcons.Clock,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp),
            )
            if (!compact) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (timer.isUrgent) colors.dangerText else colors.textSecondary,
                )
            }
            Text(
                text = timer.formatted,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                ),
                color = textColor,
                modifier = Modifier.testTag(PlayerTestTags.TOP_BAR_TIMER_VALUE),
            )
        }
    }
}

@Composable
internal fun SaveStatusChip(
    saveStatus: SaveStatus,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors

    val (label, bg, fg, border) = when (saveStatus) {
        SaveStatus.Saved -> Quad(
            stringResource(R.string.attempt_saveStatus_saved),
            statusColors.successBg,
            statusColors.success,
            statusColors.success.copy(alpha = 0.35f),
        )

        SaveStatus.Saving -> Quad(
            stringResource(R.string.attempt_saveStatus_saving),
            colors.bgSubtle,
            colors.textSecondary,
            colors.borderStrong,
        )

        SaveStatus.Offline -> Quad(
            "${stringResource(R.string.attempt_saveStatus_unsaved)} · ${stringResource(R.string.attempt_saveStatus_reasons_offline)}",
            statusColors.warningBg,
            statusColors.warning,
            statusColors.warning.copy(alpha = 0.45f),
        )

        is SaveStatus.Unsaved -> {
            val reasonText = when (val r = saveStatus.reason) {
                UnsavedReason.NetworkError -> stringResource(R.string.attempt_saveStatus_reasons_network_error)
                UnsavedReason.ServerError -> stringResource(R.string.attempt_saveStatus_reasons_server_error)
                is UnsavedReason.RateLimited -> stringResource(
                    R.string.attempt_saveStatus_reasons_rate_limited,
                    r.retryAfterSeconds.coerceAtLeast(1L),
                )
                UnsavedReason.Rejected -> stringResource(R.string.attempt_saveStatus_reasons_rejected)
                UnsavedReason.SessionConflict -> stringResource(R.string.attempt_saveStatus_reasons_session_conflict)
            }
            val isRateLimit = saveStatus.reason is UnsavedReason.RateLimited
            Quad(
                "${stringResource(R.string.attempt_saveStatus_unsaved)} · $reasonText",
                if (isRateLimit) statusColors.warningBg else colors.dangerBg,
                if (isRateLimit) statusColors.warning else colors.dangerText,
                if (isRateLimit) statusColors.warning.copy(alpha = 0.45f) else colors.dangerText.copy(alpha = 0.45f),
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
            }
            .testTag(PlayerTestTags.TOP_BAR_SAVE_STATUS),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PrimaryActionButton(
    action: PlayerTopAction,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val label = when (action) {
        is PlayerTopAction.FinishPart -> stringResource(R.string.attempt_header_finishPart)
        PlayerTopAction.Submit -> stringResource(R.string.attempt_header_submit)
        PlayerTopAction.None -> return
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryFill,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.bgSubtle,
            disabledContentColor = colors.textMuted,
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(PlayerTestTags.TOP_BAR_ACTION_BUTTON),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)
