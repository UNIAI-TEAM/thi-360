package vn.unicom.thi360.feature.attachments.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.unicom.thi360.R
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.network.generated.model.AttemptAttachmentView
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.attachments.model.AttachmentMerge
import vn.unicom.thi360.feature.attachments.model.QuestionAttachment
import vn.unicom.thi360.feature.attachments.viewer.AttachmentOpenState
import vn.unicom.thi360.feature.attachments.viewer.AttachmentOpener
import vn.unicom.thi360.feature.attachments.viewer.ViewerIntentLauncher
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource

@Composable
fun rememberAttachmentOpener(
    assetSource: AttemptAssetSource,
    sessionCleanup: SessionCleanup? = null,
    customLauncher: ViewerIntentLauncher? = null,
): AttachmentOpener {
    val context = LocalContext.current
    val effectiveCleanup = sessionCleanup ?: (context.applicationContext as? Thi360Application)?.sessionCleanup
    var pendingCloseCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    val activityResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        val cb = pendingCloseCallback
        pendingCloseCallback = null
        cb?.invoke()
    }

    val intentLauncher = remember(customLauncher, activityResultLauncher) {
        customLauncher ?: ViewerIntentLauncher { intent: Intent, onClosed: () -> Unit ->
            pendingCloseCallback?.invoke()
            pendingCloseCallback = onClosed
            try {
                activityResultLauncher.launch(intent)
            } catch (t: Throwable) {
                pendingCloseCallback = null
                throw t
            }
        }
    }

    val opener = remember(assetSource, context, intentLauncher, effectiveCleanup) {
        AttachmentOpener(
            assetSource = assetSource,
            uriResolver = AttachmentOpener.defaultUriResolver(context),
            launcher = intentLauncher,
            sessionCleanup = effectiveCleanup,
        )
    }

    DisposableEffect(opener) {
        onDispose {
            pendingCloseCallback = null
            opener.close()
        }
    }

    return opener
}

@Composable
fun AttachmentsView(
    parentAttachments: List<AttemptAttachmentView>?,
    audioPlayer: @Composable (QuestionAttachment) -> Unit,
    opener: AttachmentOpener,
    modifier: Modifier = Modifier,
    childItems: List<AttemptItemView>? = null,
) {
    val merged = remember(parentAttachments, childItems) {
        AttachmentMerge.mergeQuestionAttachments(parentAttachments, childItems)
    }
    val openState by opener.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    AttachmentsView(
        attachments = merged,
        audioPlayer = audioPlayer,
        onOpenAttachment = { attachment ->
            scope.launch {
                opener.open(attachment)
            }
        },
        modifier = modifier,
        openState = openState,
    )
}

@Composable
fun AttachmentsView(
    attachments: List<QuestionAttachment>,
    audioPlayer: @Composable (QuestionAttachment) -> Unit,
    onOpenAttachment: (QuestionAttachment) -> Unit,
    modifier: Modifier = Modifier,
    openState: AttachmentOpenState = AttachmentOpenState.Idle,
) {
    if (attachments.isEmpty()) return

    val brand = Thi360Theme.colors
    val label = stringResource(R.string.attempt_player_attachmentsLabel)
    val containerShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(containerShape)
            .background(brand.bgSubtle)
            .border(1.dp, brand.border, containerShape)
            .padding(12.dp)
            .semantics { contentDescription = label }
            .testTag(AttachmentTestTags.ATTACHMENTS_CONTAINER),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = brand.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag(AttachmentTestTags.ATTACHMENTS_LABEL),
        )

        for (attachment in attachments) {
            key(attachment.assetId) {
                if (attachment.isAudio) {
                    audioPlayer(attachment)
                } else {
                    val isLoading = openState is AttachmentOpenState.Loading && openState.assetId == attachment.assetId
                    AttachmentFileRow(
                        attachment = attachment,
                        isLoading = isLoading,
                        onClick = { onOpenAttachment(attachment) },
                    )
                }
            }
        }

        if (openState is AttachmentOpenState.OpenFailed) {
            val errorShape = RoundedCornerShape(8.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(errorShape)
                    .background(brand.dangerBg)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(AttachmentTestTags.ATTACHMENTS_OPEN_ERROR),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = AttachmentIcons.WarningTriangle,
                    contentDescription = null,
                    tint = brand.dangerText,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(16.dp),
                )
                Text(
                    text = stringResource(R.string.attempt_attachments_openError),
                    color = brand.dangerText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun AttachmentFileRow(
    attachment: QuestionAttachment,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val brand = Thi360Theme.colors
    val rowShape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(rowShape)
            .background(brand.bgSurface)
            .border(1.dp, brand.border, rowShape)
            .clickable(enabled = !isLoading, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(AttachmentTestTags.fileRow(attachment.assetId)),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = brand.primaryFill,
            )
        } else {
            Icon(
                imageVector = AttachmentIcons.FileDocument,
                contentDescription = null,
                tint = brand.primaryText,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = attachment.fileName,
            color = brand.primaryText,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
