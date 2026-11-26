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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import vn.unicom.thi360.R
import vn.unicom.thi360.core.brand.BrandMark
import vn.unicom.thi360.core.biometric.BiometricAuthFailureReason
import vn.unicom.thi360.core.biometric.BiometricAuthenticator
import vn.unicom.thi360.core.biometric.BiometricController
import vn.unicom.thi360.core.biometric.BiometricUnlockOutcome
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.account.AccountIcons
import vn.unicom.thi360.feature.account.formatAvatarInitials
import vn.unicom.thi360.feature.login.LoginIcons
import vn.unicom.thi360.feature.login.LoginScreen
import vn.unicom.thi360.feature.recovery.ForgotPasswordScreen
import vn.unicom.thi360.feature.recovery.RecoveryGateway

enum class LockFeedbackKind {
    FAILED,
    CANCELLED,
    LOCKOUT,
    KEY_INVALIDATED,
}

private enum class LockHostStep {
    LOCKED,
    PASSWORD_FALLBACK,
    FORGOT_PASSWORD,
}

@Composable
fun LockHost(
    username: String?,
    displayName: String?,
    sessionManager: SessionManager,
    biometricController: BiometricController?,
    biometricAuthenticator: BiometricAuthenticator?,
    recoveryGateway: RecoveryGateway,
    onSelectLocale: (AppLocale) -> Unit,
    onPasswordFallbackAuthenticated: (mustChangePassword: Boolean) -> Unit,
    clockMillis: () -> Long,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableStateOf(LockHostStep.LOCKED) }
    var feedback by remember { mutableStateOf<LockFeedbackKind?>(null) }
    var isUnlocking by remember { mutableStateOf(false) }
    var isSwitchingAccount by remember { mutableStateOf(false) }
    val unlockInFlight = remember { AtomicBoolean(false) }
    val switchInFlight = remember { AtomicBoolean(false) }
    val scope = rememberCoroutineScope()

    when (step) {
        LockHostStep.LOCKED -> {
            BackHandler(enabled = true) {}

            LockScreenContent(
                displayName = displayName,
                username = username,
                feedback = feedback,
                isUnlocking = isUnlocking,
                isSwitchingAccount = isSwitchingAccount,
                onUnlockClick = {
                    val controller = biometricController
                    val authenticator = biometricAuthenticator
                    if (controller == null || authenticator == null) {
                        feedback = LockFeedbackKind.FAILED
                        return@LockScreenContent
                    }
                    if (!unlockInFlight.compareAndSet(false, true)) return@LockScreenContent
                    isUnlocking = true
                    feedback = null
                    scope.launch {
                        try {
                            when (val outcome = controller.unlock(authenticator)) {
                                is BiometricUnlockOutcome.Unlocked -> {
                                    sessionManager.unlockAndRefresh()
                                }

                                BiometricUnlockOutcome.KeyInvalidated -> {
                                    feedback = LockFeedbackKind.KEY_INVALIDATED
                                }

                                is BiometricUnlockOutcome.Failed -> {
                                    feedback = when (outcome.reason) {
                                        BiometricAuthFailureReason.CANCELLED -> LockFeedbackKind.CANCELLED
                                        BiometricAuthFailureReason.LOCKOUT,
                                        BiometricAuthFailureReason.LOCKOUT_PERMANENT,
                                        -> LockFeedbackKind.LOCKOUT
                                        BiometricAuthFailureReason.FAILED,
                                        BiometricAuthFailureReason.ERROR,
                                        -> LockFeedbackKind.FAILED
                                    }
                                }
                            }
                        } finally {
                            isUnlocking = false
                            unlockInFlight.set(false)
                        }
                    }
                },
                onEnterPasswordClick = {
                    if (!isUnlocking && !isSwitchingAccount) {
                        step = LockHostStep.PASSWORD_FALLBACK
                    }
                },
                onOtherAccountClick = {
                    if (!switchInFlight.compareAndSet(false, true)) return@LockScreenContent
                    isSwitchingAccount = true
                    scope.launch {
                        try {
                            sessionManager.logout()
                        } finally {
                            isSwitchingAccount = false
                            switchInFlight.set(false)
                        }
                    }
                },
                modifier = modifier,
            )
        }

        LockHostStep.PASSWORD_FALLBACK -> {
            LoginScreen(
                sessionManager = sessionManager,
                signOutReason = SignOutReason.None,
                onSelectLocale = onSelectLocale,
                onForgotPasswordClick = { step = LockHostStep.FORGOT_PASSWORD },
                onRegisterClick = {},
                initialIdentifier = username.orEmpty(),
                onBack = { step = LockHostStep.LOCKED },
                showRegisterLink = false,
                onSubmitCredentials = sessionManager::replaceSessionFromLock,
                onLoginAuthenticated = { mustChangePassword ->
                    onPasswordFallbackAuthenticated(mustChangePassword)
                },
                modifier = modifier,
            )
        }

        LockHostStep.FORGOT_PASSWORD -> {
            ForgotPasswordScreen(
                recoveryGateway = recoveryGateway,
                onBackToLogin = { step = LockHostStep.PASSWORD_FALLBACK },
                clockMillis = clockMillis,
                modifier = modifier,
            )
        }
    }
}

@Composable
fun LockScreenContent(
    displayName: String?,
    username: String?,
    feedback: LockFeedbackKind?,
    isUnlocking: Boolean,
    isSwitchingAccount: Boolean,
    onUnlockClick: () -> Unit,
    onEnterPasswordClick: () -> Unit,
    onOtherAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val busy = isUnlocking || isSwitchingAccount
    val keyInvalidated = feedback == LockFeedbackKind.KEY_INVALIDATED
    val primaryHeading = displayName?.takeIf { it.isNotBlank() } ?: username.orEmpty()
    val secondaryUsername = username?.takeIf { it.isNotBlank() && it != primaryHeading }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .testTag(LockTestTags.LOCK_SCREEN),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(
                markSize = 44.dp,
                modifier = Modifier.padding(top = 24.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainerStrong),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = formatAvatarInitials(displayName, username),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.primaryText,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (primaryHeading.isNotBlank()) {
                        Text(
                            text = primaryHeading,
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.text,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .semantics { heading() }
                                .testTag(LockTestTags.LOCK_USER_NAME),
                        )
                    }
                    if (!secondaryUsername.isNullOrBlank()) {
                        Text(
                            text = secondaryUsername,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.lock_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(LockTestTags.LOCK_BODY),
                )

                if (feedback != null) {
                    val feedbackMessageRes = when (feedback) {
                        LockFeedbackKind.FAILED -> R.string.lock_error_failed
                        LockFeedbackKind.CANCELLED -> R.string.lock_error_cancelled
                        LockFeedbackKind.LOCKOUT -> R.string.lock_error_lockout
                        LockFeedbackKind.KEY_INVALIDATED -> R.string.lock_error_key_invalidated
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.dangerBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                            .testTag(LockTestTags.LOCK_ERROR_BANNER),
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
                                text = stringResource(feedbackMessageRes),
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
                    onClick = onUnlockClick,
                    enabled = !busy && !keyInvalidated,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryFill,
                        contentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(LockTestTags.UNLOCK_BUTTON),
                ) {
                    if (isUnlocking) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = colors.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.lock_action_unlocking))
                    } else {
                        Icon(
                            imageVector = AccountIcons.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.lock_action_unlock))
                    }
                }

                FilledTonalButton(
                    onClick = onEnterPasswordClick,
                    enabled = !busy,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.primaryContainerStrong,
                        contentColor = colors.primaryText,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(LockTestTags.ENTER_PASSWORD_BUTTON),
                ) {
                    Text(text = stringResource(R.string.lock_action_password))
                }

                TextButton(
                    onClick = onOtherAccountClick,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(LockTestTags.OTHER_ACCOUNT_BUTTON),
                ) {
                    Text(
                        text = stringResource(R.string.lock_action_other_account),
                        color = colors.primaryText,
                    )
                }
            }
        }
    }
}
