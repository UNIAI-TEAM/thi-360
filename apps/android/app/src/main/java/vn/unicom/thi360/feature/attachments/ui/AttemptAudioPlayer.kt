package vn.unicom.thi360.feature.attachments.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.unicom.thi360.R
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.attachments.controller.AttemptAudioController
import vn.unicom.thi360.feature.attachments.controller.AudioLifecycleBridge
import vn.unicom.thi360.feature.attachments.model.AudioLockReason
import vn.unicom.thi360.feature.attachments.model.AudioPlayerPhase
import vn.unicom.thi360.feature.attachments.model.AudioPlayerUiState
import vn.unicom.thi360.feature.attachments.model.PauseReason
import vn.unicom.thi360.feature.attachments.model.formatAudioTime
import vn.unicom.thi360.feature.question.logic.OptionOrdering

private enum class BannerTone {
    INFO,
    WARNING,
    DANGER,
    NEUTRAL,
}

private data class BannerSpec(
    val tone: BannerTone,
    val messageRes: Int,
)

@Composable
fun AttemptAudioPlayer(
    controller: AttemptAudioController,
    modifier: Modifier = Modifier,
    attachLifecycle: Boolean = true,
) {
    val state by controller.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    if (attachLifecycle) {
        DisposableEffect(controller, context, lifecycleOwner) {
            val activity = context.findActivity()
            val bridge = AudioLifecycleBridge.forContext(
                context = context,
                screenLifecycle = lifecycleOwner.lifecycle,
                isScreenChangingConfigurations = { activity?.isChangingConfigurations == true },
                onAppBackgrounded = { controller.onAppBackgrounded() },
                onScreenStopped = { controller.onLeaveScreen() },
            ).attach()
            onDispose {
                bridge.close()
                controller.onLeaveScreen()
            }
        }
    }

    AttemptAudioPlayer(
        state = state,
        onPrimaryClick = { controller.onPrimaryAction() },
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttemptAudioPlayer(
    state: AudioPlayerUiState,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val locale = LocalAppLocale.current
    val regionLabel = stringResource(R.string.question_audioPlayer_regionLabel)
    val progressLabel = stringResource(R.string.question_audioPlayer_progressLabel)

    val phase = state.phase
    val isPlaying = phase is AudioPlayerPhase.Playing
    val isOpening = phase is AudioPlayerPhase.Opening
    val isPaused = phase is AudioPlayerPhase.Paused
    val isWaitingNetwork = phase is AudioPlayerPhase.WaitingNetwork
    val isLocked = phase is AudioPlayerPhase.Locked
    val isExhausted = state.isExhausted

    val buttonEnabled = !isLocked && !isExhausted && !isPlaying && !isOpening && !isWaitingNetwork && state.isOnline
    val buttonLabel = when {
        isPlaying -> stringResource(R.string.question_audioPlayer_playing)
        isPaused || isWaitingNetwork || (phase is AudioPlayerPhase.Opening && phase.isResuming) ->
            stringResource(R.string.question_audioPlayer_resume)
        else -> stringResource(R.string.question_audioPlayer_play)
    }
    val buttonIcon: ImageVector = when {
        isPlaying -> AttachmentIcons.Waveform
        isPaused || isWaitingNetwork -> AttachmentIcons.Resume
        else -> AttachmentIcons.Play
    }

    val buttonColors = if (isPlaying) {
        ButtonDefaults.buttonColors(
            containerColor = brand.primaryContainer,
            contentColor = brand.primaryText,
            disabledContainerColor = brand.primaryContainer,
            disabledContentColor = brand.primaryText,
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = brand.primaryFill,
            contentColor = brand.onPrimary,
            disabledContainerColor = brand.border,
            disabledContentColor = brand.textMuted,
        )
    }

    val playsLeftFmt = OptionOrdering.formatInteger(state.playsLeft, locale)
    val maxPlaysFmt = OptionOrdering.formatInteger(state.effectiveMaxPlays, locale)
    val badgeText = if (isExhausted) {
        stringResource(R.string.question_audioPlayer_playsExhaustedBadge, maxPlaysFmt)
    } else {
        stringResource(R.string.question_audioPlayer_playsRemaining, playsLeftFmt, maxPlaysFmt)
    }
    val badgeBg = if (isExhausted) brand.bgSubtle else status.infoBg
    val badgeFg = if (isExhausted) brand.textSecondary else status.info

    val timeText = "${formatAudioTime(state.clampedPositionMs)} / ${formatAudioTime(state.durationMs)}"
    val bannerSpec = resolveBannerSpec(state)

    val cardShape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(brand.bgSurface)
            .border(1.dp, brand.border, cardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = regionLabel }
            .testTag(AttachmentTestTags.audioPlayer(state.assetId)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.fileName.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = AttachmentIcons.MusicNote,
                    contentDescription = null,
                    tint = brand.primaryText,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = state.fileName,
                    color = brand.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(AttachmentTestTags.audioFileName(state.assetId)),
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onPrimaryClick,
                enabled = buttonEnabled,
                colors = buttonColors,
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(AttachmentTestTags.audioPrimaryButton(state.assetId)),
            ) {
                if (isOpening) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = brand.textMuted,
                    )
                } else {
                    Icon(
                        imageVector = buttonIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buttonLabel,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(badgeBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = badgeText,
                        color = badgeFg,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag(AttachmentTestTags.audioPlaysBadge(state.assetId)),
                    )
                }
                Text(
                    text = timeText,
                    color = brand.textSecondary,
                    style = TextStyle(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                    ),
                    modifier = Modifier.testTag(AttachmentTestTags.audioTimeLabel(state.assetId)),
                )
            }
        }

        // Read-only progress bar: intentionally no click/drag modifiers so audio cannot be scrubbed (R-134).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(brand.bgSubtle)
                .semantics {
                    contentDescription = progressLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = state.progressFraction,
                        range = 0f..1f,
                    )
                }
                .testTag(AttachmentTestTags.audioProgressBar(state.assetId)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(state.progressFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(brand.primaryFill),
            )
        }

        if (bannerSpec != null) {
            val bannerBg: Color
            val bannerFg: Color
            val bannerIcon: ImageVector
            when (bannerSpec.tone) {
                BannerTone.INFO -> {
                    bannerBg = status.infoBg
                    bannerFg = status.info
                    bannerIcon = AttachmentIcons.InfoCircle
                }
                BannerTone.WARNING -> {
                    bannerBg = status.warningBg
                    bannerFg = status.warning
                    bannerIcon = AttachmentIcons.WarningTriangle
                }
                BannerTone.DANGER -> {
                    bannerBg = brand.dangerBg
                    bannerFg = brand.dangerText
                    bannerIcon = AttachmentIcons.WarningTriangle
                }
                BannerTone.NEUTRAL -> {
                    bannerBg = brand.bgSubtle
                    bannerFg = brand.textSecondary
                    bannerIcon = AttachmentIcons.InfoCircle
                }
            }
            val bannerShape = RoundedCornerShape(8.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(bannerShape)
                    .background(bannerBg)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(AttachmentTestTags.audioBanner(state.assetId)),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = bannerIcon,
                    contentDescription = null,
                    tint = bannerFg,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(16.dp),
                )
                Text(
                    text = stringResource(bannerSpec.messageRes),
                    color = bannerFg,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun resolveBannerSpec(state: AudioPlayerUiState): BannerSpec? {
    val phase = state.phase
    if (phase is AudioPlayerPhase.Locked) {
        return when (phase.reason) {
            AudioLockReason.Acquiring -> null
            AudioLockReason.PartClosed -> BannerSpec(BannerTone.WARNING, R.string.question_audioPlayer_partClosedReason)
            AudioLockReason.TimeUp -> BannerSpec(BannerTone.DANGER, R.string.question_audioPlayer_timeUpReason)
            AudioLockReason.Submitted -> BannerSpec(BannerTone.WARNING, R.string.question_audioPlayer_submittedReason)
            AudioLockReason.Evicted -> BannerSpec(BannerTone.DANGER, R.string.question_audioPlayer_evictedReason)
            AudioLockReason.Disabled -> BannerSpec(BannerTone.NEUTRAL, R.string.question_audioPlayer_disabledReason)
        }
    }
    if (state.isExhausted) {
        return BannerSpec(BannerTone.NEUTRAL, R.string.question_audioPlayer_exhaustedReason)
    }
    if (phase is AudioPlayerPhase.WaitingNetwork || !state.isOnline) {
        return BannerSpec(BannerTone.WARNING, R.string.question_audioPlayer_offlineWaiting)
    }
    if (phase is AudioPlayerPhase.Paused) {
        return when (phase.reason) {
            PauseReason.Error -> BannerSpec(BannerTone.DANGER, R.string.question_audioPlayer_playErrorRetry)
            PauseReason.Background,
            PauseReason.Interrupted,
            PauseReason.Noisy,
            PauseReason.LeftScreen,
            PauseReason.Headset,
            -> BannerSpec(BannerTone.INFO, R.string.question_audioPlayer_pausedResumable)
        }
    }
    if (state.hasPlayError) {
        return BannerSpec(BannerTone.DANGER, R.string.question_audioPlayer_playErrorRetry)
    }
    return null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
