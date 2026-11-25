package vn.unicom.thi360.feature.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import vn.unicom.thi360.R
import vn.unicom.thi360.core.biometric.BiometricAuthenticator
import vn.unicom.thi360.core.biometric.BiometricController
import vn.unicom.thi360.core.biometric.BiometricEnableOutcome
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.account.AccountIcons
import vn.unicom.thi360.feature.login.LoginIcons

@Composable
fun BiometricEnablePromptScreen(
    biometricController: BiometricController,
    biometricAuthenticator: BiometricAuthenticator?,
    sessionManager: SessionManager,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val inFlight = remember { AtomicBoolean(false) }
    var isEnabling by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    BackHandler(enabled = !isEnabling) {
        biometricController.dismissEnablePromptWithoutRecording()
    }

    BiometricEnablePromptContent(
        isEnabling = isEnabling,
        showError = showError,
        onEnableClick = {
            if (biometricAuthenticator == null) {
                showError = true
                return@BiometricEnablePromptContent
            }
            if (!inFlight.compareAndSet(false, true)) return@BiometricEnablePromptContent
            isEnabling = true
            showError = false
            scope.launch {
                try {
                    when (
                        biometricController.enable(
                            refreshToken = sessionManager.currentRefreshToken(),
                            authenticator = biometricAuthenticator,
                        )
                    ) {
                        BiometricEnableOutcome.Enabled -> Unit
                        is BiometricEnableOutcome.Failed,
                        is BiometricEnableOutcome.Unavailable,
                        BiometricEnableOutcome.MissingSession,
                        -> {
                            showError = true
                        }
                    }
                } finally {
                    isEnabling = false
                    inFlight.set(false)
                }
            }
        },
        onLaterClick = {
            if (!inFlight.compareAndSet(false, true)) return@BiometricEnablePromptContent
            scope.launch {
                try {
                    biometricController.declineEnablePrompt()
                } finally {
                    inFlight.set(false)
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun BiometricEnablePromptContent(
    isEnabling: Boolean,
    showError: Boolean,
    onEnableClick: () -> Unit,
    onLaterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .testTag(LockTestTags.ENABLE_PROMPT_SCREEN),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainerStrong),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AccountIcons.Fingerprint,
                        contentDescription = null,
                        tint = colors.primaryText,
                        modifier = Modifier.size(44.dp),
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.biometric_enable_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .semantics { heading() }
                            .testTag(LockTestTags.ENABLE_PROMPT_TITLE),
                    )
                    Text(
                        text = stringResource(R.string.biometric_enable_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag(LockTestTags.ENABLE_PROMPT_BODY),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.bgSubtle,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.biometric_enable_note_1),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = stringResource(R.string.biometric_enable_note_2),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = stringResource(R.string.biometric_enable_note_3),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }

                if (showError) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.dangerBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                            .testTag(LockTestTags.ENABLE_PROMPT_ERROR),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = LoginIcons.AlertCircle,
                                contentDescription = null,
                                tint = colors.dangerText,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.biometric_enable_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.dangerText,
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onEnableClick,
                    enabled = !isEnabling,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryFill,
                        contentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(LockTestTags.ENABLE_PROMPT_CONFIRM),
                ) {
                    if (isEnabling) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = colors.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = stringResource(R.string.biometric_enable_confirm))
                }

                TextButton(
                    onClick = onLaterClick,
                    enabled = !isEnabling,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(LockTestTags.ENABLE_PROMPT_LATER),
                ) {
                    Text(
                        text = stringResource(R.string.biometric_enable_later),
                        color = colors.primaryText,
                    )
                }
            }
        }
    }
}
