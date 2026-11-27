package vn.unicom.thi360.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import vn.unicom.thi360.R
import vn.unicom.thi360.core.theme.Thi360Theme

internal const val GENERATION_BUSY_NOTICE_DELAY_MS = 15_000L

@Composable
internal fun GenerationPanel(
    panel: GenerationPanelUi,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    busyNoticeDelayMs: Long = GENERATION_BUSY_NOTICE_DELAY_MS,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors

    var showBusyNotice by remember(panel.partId, panel.isFailed) { mutableStateOf(false) }

    LaunchedEffect(panel.partId, panel.isFailed, busyNoticeDelayMs) {
        if (!panel.isFailed) {
            showBusyNotice = false
            delay(busyNoticeDelayMs)
            showBusyNotice = true
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(
                if (panel.isFailed) PlayerTestTags.GENERATION_FAILED_PANEL else PlayerTestTags.GENERATION_PANEL,
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "generation_card") {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.bgSurface,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (panel.isFailed) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(colors.dangerBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = PlayerIcons.Warning,
                                    contentDescription = null,
                                    tint = colors.dangerText,
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            Text(
                                text = stringResource(R.string.attempt_partChoice_failedTitle),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.text,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.semantics {
                                    heading()
                                    liveRegion = LiveRegionMode.Assertive
                                },
                            )

                            if (!panel.chosenOptionName.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = colors.bgSubtle,
                                    border = BorderStroke(1.dp, colors.border),
                                ) {
                                    Text(
                                        text = panel.chosenOptionName,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                        color = colors.text,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.attempt_partChoice_failedDescription),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )

                            if (panel.errorCode != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.dangerBg,
                                    border = BorderStroke(1.dp, colors.dangerText),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics(mergeDescendants = true) {}
                                        .testTag(PlayerTestTags.GENERATION_ERROR),
                                ) {
                                    Text(
                                        text = partChoiceErrorMessage(panel.errorCode),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.dangerText,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }

                            Button(
                                onClick = onRetry,
                                enabled = !panel.isRetrying,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primaryFill,
                                    contentColor = colors.onPrimary,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag(PlayerTestTags.GENERATION_RETRY),
                            ) {
                                if (panel.isRetrying) {
                                    CircularProgressIndicator(
                                        color = colors.onPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = stringResource(R.string.attempt_partChoice_failedRetry),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(colors.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = colors.primaryFill,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(32.dp),
                                )
                            }

                            Text(
                                text = stringResource(R.string.attempt_partChoice_generatingTitle),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.text,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.semantics {
                                    heading()
                                    liveRegion = LiveRegionMode.Polite
                                },
                            )

                            if (!panel.chosenOptionName.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = colors.primaryContainer,
                                    border = BorderStroke(1.dp, colors.primaryFill.copy(alpha = 0.35f)),
                                ) {
                                    Text(
                                        text = panel.chosenOptionName,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.primaryText,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.attempt_partChoice_generatingDescription),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )

                            if (showBusyNotice) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = statusColors.warningBg,
                                    border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                                        .testTag(PlayerTestTags.GENERATION_BUSY_NOTICE),
                                ) {
                                    Text(
                                        text = stringResource(R.string.attempt_partChoice_busyNotice),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = statusColors.warning,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
