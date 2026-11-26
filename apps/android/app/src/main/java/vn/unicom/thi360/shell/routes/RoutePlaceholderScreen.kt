package vn.unicom.thi360.shell.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import vn.unicom.thi360.R
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.recovery.RecoveryIcons

object RoutePlaceholderTestTags {
    const val SCREEN = "route_placeholder_screen"
    const val ROUTE_NAME = "route_placeholder_name"
    const val ROUTE_PARAMS = "route_placeholder_params"
    const val BACK_BUTTON = "route_placeholder_back"
}

@Composable
internal fun RoutePlaceholderScreen(
    routeName: String,
    params: Map<String, String> = emptyMap(),
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val paramsSummary = params.entries.joinToString(separator = "\n") { (k, v) -> "$k=$v" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .testTag(RoutePlaceholderTestTags.SCREEN),
    ) {
        if (onBack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(RoutePlaceholderTestTags.BACK_BUTTON),
                ) {
                    Icon(
                        imageVector = RecoveryIcons.ArrowBack,
                        contentDescription = stringResource(R.string.recovery_back),
                        tint = colors.text,
                    )
                }
                Text(
                    text = stringResource(R.string.placeholder_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = colors.bgSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = routeName,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                        modifier = Modifier
                            .semantics { heading() }
                            .testTag(RoutePlaceholderTestTags.ROUTE_NAME),
                    )
                    if (paramsSummary.isNotEmpty()) {
                        Text(
                            text = paramsSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.testTag(RoutePlaceholderTestTags.ROUTE_PARAMS),
                        )
                    }
                    Text(
                        text = stringResource(R.string.placeholder_empty_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}
