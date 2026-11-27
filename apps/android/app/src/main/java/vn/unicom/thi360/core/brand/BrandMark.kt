package vn.unicom.thi360.core.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vn.unicom.thi360.R
import vn.unicom.thi360.core.theme.Thi360Theme

object BrandMarkTestTags {
    const val CONTAINER = "brand_mark"
    const val ICON = "brand_mark_icon"
    const val NAME = "brand_mark_name"
}

const val MAX_BRAND_MARK_SIZE_DP = 96

@Composable
fun BrandMark(
    markSize: Dp = 36.dp,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    nameStyle: TextStyle? = null,
) {
    val clampedSize = markSize.coerceIn(16.dp, MAX_BRAND_MARK_SIZE_DP.dp)
    val resolvedNameStyle = nameStyle ?: if (clampedSize >= 40.dp) {
        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    } else {
        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    }
    val spacing = if (clampedSize >= 40.dp) 12.dp else 10.dp

    Row(
        modifier = modifier.testTag(BrandMarkTestTags.CONTAINER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(clampedSize)
                .testTag(BrandMarkTestTags.ICON),
        )
        if (showName) {
            Text(
                text = stringResource(R.string.app_name),
                style = resolvedNameStyle,
                color = Thi360Theme.colors.text,
                modifier = Modifier.testTag(BrandMarkTestTags.NAME),
            )
        }
    }
}
