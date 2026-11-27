package vn.unicom.thi360.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import vn.unicom.thi360.R
import vn.unicom.thi360.core.theme.Thi360Theme

object LoadStateTestTags {
    const val LOADING = "load_state_loading"
    const val ERROR_CONTAINER = "load_state_error"
    const val ERROR_TITLE = "load_state_error_title"
    const val ERROR_DESCRIPTION = "load_state_error_description"
    const val RETRY_BUTTON = "load_state_retry_button"
}

@Composable
fun <T> LoadStateView(
    state: LoadState<T>,
    onRetry: () -> Unit,
    emptyContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    staleBannerMessage: String? = null,
    onStaleRetry: (() -> Unit)? = null,
    customErrorTitle: ((LoadState.Failed) -> String?)? = null,
    customErrorDescription: ((LoadState.Failed) -> String?)? = null,
    failedContent: (@Composable (LoadState.Failed) -> Unit)? = null,
    clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    content: @Composable (T) -> Unit,
) {
    val colors = Thi360Theme.colors

    when (state) {
        LoadState.Loading -> {
            val loadingDescription = stringResource(R.string.common_loading)
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .semantics { contentDescription = loadingDescription }
                    .testTag(LoadStateTestTags.LOADING),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.primaryFill)
            }
        }

        LoadState.Empty -> {
            Box(modifier = modifier.fillMaxSize()) {
                emptyContent()
            }
        }

        is LoadState.Loaded -> {
            Column(modifier = modifier.fillMaxSize()) {
                if (state.stale && !staleBannerMessage.isNullOrBlank()) {
                    StaleBanner(
                        message = staleBannerMessage,
                        onRetry = onStaleRetry,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    content(state.value)
                }
            }
        }

        is LoadState.Failed -> {
            if (failedContent != null) {
                Box(modifier = modifier.fillMaxSize()) {
                    failedContent(state)
                }
            } else {
                DefaultFailedContent(
                    failed = state,
                    onRetry = onRetry,
                    customErrorTitle = customErrorTitle,
                    customErrorDescription = customErrorDescription,
                    clockMillis = clockMillis,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun DefaultFailedContent(
    failed: LoadState.Failed,
    onRetry: () -> Unit,
    customErrorTitle: ((LoadState.Failed) -> String?)?,
    customErrorDescription: ((LoadState.Failed) -> String?)?,
    clockMillis: () -> Long,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val blockedUntilMillis = remember(failed) {
        val wait = failed.retryAfter
        if (wait != null && wait > 0L) clockMillis() + wait * 1_000L else 0L
    }
    var tick by remember(failed) { mutableIntStateOf(0) }
    LaunchedEffect(failed, blockedUntilMillis) {
        while (blockedUntilMillis > 0L && clockMillis() < blockedUntilMillis) {
            withContext(Dispatchers.Default) {
                delay(RETRY_POLL_INTERVAL_MS)
            }
            tick++
        }
    }
    val retryAllowedNow = tick >= 0 && (blockedUntilMillis <= 0L || clockMillis() >= blockedUntilMillis)

    val title = customErrorTitle?.invoke(failed) ?: when (failed.kind) {
        LoadFailureKind.Offline -> stringResource(R.string.common_offline_title)
        else -> stringResource(R.string.errorPage_title)
    }
    val description = customErrorDescription?.invoke(failed) ?: when (failed.kind) {
        LoadFailureKind.Offline -> stringResource(R.string.common_offline_description)
        else -> stringResource(R.string.errorPage_description)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(LoadStateTestTags.ERROR_CONTAINER),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .semantics { heading() }
                    .testTag(LoadStateTestTags.ERROR_TITLE),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(LoadStateTestTags.ERROR_DESCRIPTION),
            )
            if (failed.canRetry) {
                Button(
                    onClick = {
                        if (blockedUntilMillis <= 0L || clockMillis() >= blockedUntilMillis) {
                            onRetry()
                        }
                    },
                    enabled = retryAllowedNow,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryFill,
                        contentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(LoadStateTestTags.RETRY_BUTTON),
                ) {
                    Text(text = stringResource(R.string.common_retry))
                }
            }
        }
    }
}

private const val RETRY_POLL_INTERVAL_MS = 250L
