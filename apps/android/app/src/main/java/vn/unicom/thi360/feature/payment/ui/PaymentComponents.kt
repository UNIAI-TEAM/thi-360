package vn.unicom.thi360.feature.payment.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import vn.unicom.thi360.R
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.payment.domain.OrderActionErrorKey
import vn.unicom.thi360.feature.payment.domain.OrderActionErrorMapper
import vn.unicom.thi360.feature.payment.domain.PaymentStatusTone
import vn.unicom.thi360.feature.payment.viewmodel.CancelDialogState
import vn.unicom.thi360.feature.payment.viewmodel.OrderActionErrorBanner

@Composable
internal fun PaymentCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Thi360Theme.colors.border,
    containerColor: Color = Thi360Theme.colors.bgSurface,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
internal fun PaymentStatusChip(
    label: String,
    tone: PaymentStatusTone,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val (bg, fg) = when (tone) {
        PaymentStatusTone.Success -> status.successBg to status.success
        PaymentStatusTone.Warning -> status.warningBg to status.warning
        PaymentStatusTone.Danger -> colors.dangerBg to colors.dangerText
        PaymentStatusTone.Info -> status.infoBg to status.info
        PaymentStatusTone.Neutral -> colors.bgSubtle to colors.textMuted
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun PaymentBanner(
    tone: PaymentStatusTone,
    title: String?,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val (bg, border, titleColor) = when (tone) {
        PaymentStatusTone.Success -> Triple(status.successBg, status.success.copy(alpha = 0.35f), status.success)
        PaymentStatusTone.Warning -> Triple(status.warningBg, status.warning.copy(alpha = 0.45f), status.warning)
        PaymentStatusTone.Danger -> Triple(colors.dangerBg, colors.dangerText.copy(alpha = 0.35f), colors.dangerText)
        PaymentStatusTone.Info -> Triple(status.infoBg, status.info.copy(alpha = 0.35f), status.info)
        PaymentStatusTone.Neutral -> Triple(colors.bgSubtle, colors.border, colors.text)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
            )
            if (action != null) {
                action()
            }
        }
    }
}

@Composable
internal fun PaymentTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backButtonTestTag: String? = null,
    backLabel: String = stringResource(R.string.payment_back),
) {
    val colors = Thi360Theme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .then(if (backButtonTestTag != null) Modifier.testTag(backButtonTestTag) else Modifier)
                    .semantics { contentDescription = backLabel },
            ) {
                BackArrowGlyph(color = colors.text)
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = if (onBack == null) 8.dp else 0.dp)
                .semantics { heading() },
        )
    }
}

@Composable
private fun BackArrowGlyph(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        val cy = size.height / 2f
        val leftX = size.width * 0.22f
        val rightX = size.width * 0.82f
        val wingX = size.width * 0.48f
        val wingTop = size.height * 0.22f
        val wingBottom = size.height * 0.78f
        drawLine(color = color, start = Offset(leftX, cy), end = Offset(rightX, cy), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(leftX, cy), end = Offset(wingX, wingTop), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(leftX, cy), end = Offset(wingX, wingBottom), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
internal fun PaymentSkeletonBlock(
    height: Dp,
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(Thi360Theme.colors.bgSubtle),
    )
}

@Composable
internal fun PaymentPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = Thi360Theme.colors
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryFill,
            contentColor = colors.onPrimary,
        ),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.onPrimary,
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
        Text(
            text = text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PaymentSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = Thi360Theme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.borderStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.text,
        ),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.primaryFill,
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
        Text(
            text = text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrderActionErrorView(
    banner: OrderActionErrorBanner,
    showCreateNew: Boolean,
    onCreateNew: (String) -> Unit,
    onToCatalog: () -> Unit,
    modifier: Modifier = Modifier,
    createNewTestTag: String = PaymentTestTags.ORDERS_CREATE_NEW_BUTTON,
    toCatalogTestTag: String = PaymentTestTags.ORDERS_TO_CATALOG_BUTTON,
) {
    val message = stringResource(OrderActionErrorMapper.stringRes(banner.errorKey))
    val hasCreateNew = showCreateNew &&
        (banner.errorKey == OrderActionErrorKey.ORDER_EXPIRED ||
            banner.errorKey == OrderActionErrorKey.PAYMENT_ATTEMPTS_EXCEEDED)
    val hasToCatalog = banner.errorKey == OrderActionErrorKey.PRODUCT_DISCONTINUED ||
        banner.errorKey == OrderActionErrorKey.PRODUCT_NOT_FOUND

    PaymentBanner(
        tone = PaymentStatusTone.Danger,
        title = null,
        body = message,
        modifier = modifier,
        action = if (hasCreateNew || hasToCatalog) {
            {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (hasCreateNew && banner.productCode.isNotBlank()) {
                        PaymentSecondaryButton(
                            text = stringResource(R.string.orders_actions_createNew),
                            onClick = { onCreateNew(banner.productCode) },
                            modifier = Modifier.testTag(createNewTestTag),
                        )
                    }
                    if (hasToCatalog) {
                        PaymentSecondaryButton(
                            text = stringResource(R.string.orders_actions_toCatalog),
                            onClick = onToCatalog,
                            modifier = Modifier.testTag(toCatalogTestTag),
                        )
                    }
                }
            }
        } else {
            null
        },
    )
}

@Composable
internal fun CancelOrderConfirmDialog(
    state: CancelDialogState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    AlertDialog(
        onDismissRequest = {
            if (!state.submitting) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !state.submitting,
            dismissOnClickOutside = !state.submitting,
        ),
        modifier = Modifier.testTag(PaymentTestTags.CANCEL_DIALOG),
        containerColor = colors.bgSurface,
        title = {
            Text(
                text = stringResource(R.string.orders_cancelDialog_title, state.orderCode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.text,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.orders_cancelDialog_description, state.orderCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
                if (state.errorKey != null) {
                    Text(
                        text = stringResource(OrderActionErrorMapper.stringRes(state.errorKey)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.dangerText,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(PaymentTestTags.CANCEL_DIALOG_ERROR),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !state.submitting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = statusColors.dangerSolid,
                    contentColor = statusColors.onDanger,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(PaymentTestTags.CANCEL_DIALOG_CONFIRM),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = statusColors.onDanger,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(text = stringResource(R.string.orders_cancelDialog_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !state.submitting,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.borderStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(PaymentTestTags.CANCEL_DIALOG_KEEP),
            ) {
                Text(text = stringResource(R.string.orders_cancelDialog_keep))
            }
        },
    )
}
