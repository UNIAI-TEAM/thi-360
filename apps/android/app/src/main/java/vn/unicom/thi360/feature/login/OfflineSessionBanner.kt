package vn.unicom.thi360.feature.login

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.unicom.thi360.R
import vn.unicom.thi360.core.theme.Thi360Theme

@Composable
fun OfflineSessionBanner(
    isRetrying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.bgSubtle,
        border = BorderStroke(1.dp, colors.borderStrong),
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(LoginTestTags.OFFLINE_BANNER),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = LoginIcons.WifiOff,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.offline_banner_title),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = colors.text,
                )
                Text(
                    text = stringResource(R.string.offline_banner_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            val retryLabel = stringResource(R.string.offline_retry)
            TextButton(
                onClick = onRetry,
                enabled = !isRetrying,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics {
                        if (isRetrying) {
                            contentDescription = retryLabel
                        }
                    }
                    .testTag(LoginTestTags.OFFLINE_RETRY_BUTTON),
            ) {
                if (isRetrying) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = colors.primaryText,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        text = retryLabel,
                        color = colors.primaryText,
                    )
                }
            }
        }
    }
}
