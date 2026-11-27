package vn.unicom.thi360.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import vn.unicom.thi360.R
import vn.unicom.thi360.core.theme.Thi360Theme

object StaleBannerTestTags {
    const val BANNER = "stale_banner"
    const val RETRY = "stale_banner_retry"
}

@Composable
fun StaleBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryEnabled: Boolean = true,
) {
    val statusColors = Thi360Theme.statusColors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = statusColors.warningBg,
        border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag(StaleBannerTestTags.BANNER),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColors.warning,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    enabled = retryEnabled,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(StaleBannerTestTags.RETRY),
                ) {
                    Text(
                        text = stringResource(R.string.common_retry),
                        color = statusColors.warning,
                    )
                }
            }
        }
    }
}
