package vn.unicom.thi360.feature.recovery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.password.RecoveryBanner
import vn.unicom.thi360.feature.password.RecoveryTopBar
import vn.unicom.thi360.feature.password.thi360FieldColors
import vn.unicom.thi360.feature.register.EmailError
import vn.unicom.thi360.feature.register.RegistrationValidation
import vn.unicom.thi360.feature.register.computeRemainingSeconds

enum class ForgotPasswordAlert {
    RATE_LIMITED,
    NETWORK,
    GENERIC,
}

sealed interface ForgotPasswordStep {
    data object Form : ForgotPasswordStep
    data class Sent(val email: String, val resendAtMillis: Long) : ForgotPasswordStep
}

data class ForgotPasswordUiState(
    val step: ForgotPasswordStep = ForgotPasswordStep.Form,
    val email: String = "",
    val emailError: EmailError? = null,
    val alert: ForgotPasswordAlert? = null,
    val isSubmitting: Boolean = false,
    val cooldownGeneration: Int = 0,
)

@Stable
class ForgotPasswordController(
    private val recoveryGateway: RecoveryGateway,
    private val scope: CoroutineScope,
    private val clockMillis: () -> Long,
) {
    private val requestInFlight = AtomicBoolean(false)

    var uiState by mutableStateOf(ForgotPasswordUiState())
        private set

    fun onEmailChanged(value: String) {
        uiState = uiState.copy(
            email = value,
            emailError = null,
            alert = null,
        )
    }

    fun submit() {
        val error = RegistrationValidation.validateEmail(uiState.email)
        if (error != null) {
            uiState = uiState.copy(
                emailError = error,
                alert = null,
            )
            return
        }
        sendRequest(uiState.email.trim())
    }

    fun resend() {
        val sent = uiState.step as? ForgotPasswordStep.Sent ?: return
        if (computeRemainingSeconds(sent.resendAtMillis, clockMillis()) > 0L) return
        sendRequest(sent.email)
    }

    private fun sendRequest(email: String) {
        if (!requestInFlight.compareAndSet(false, true)) return

        uiState = uiState.copy(
            emailError = null,
            alert = null,
            isSubmitting = true,
        )

        scope.launch {
            try {
                when (val result = recoveryGateway.forgotPassword(email)) {
                    is ApiResult.Success -> {
                        val nextResendAt = clockMillis() + RESEND_COOLDOWN_SECONDS * 1_000L
                        uiState = uiState.copy(
                            step = ForgotPasswordStep.Sent(email = email, resendAtMillis = nextResendAt),
                            alert = null,
                            isSubmitting = false,
                            cooldownGeneration = uiState.cooldownGeneration + 1,
                        )
                    }

                    is ApiResult.HttpFailure -> {
                        val alert = if (result.statusCode == HTTP_TOO_MANY_REQUESTS) {
                            ForgotPasswordAlert.RATE_LIMITED
                        } else {
                            ForgotPasswordAlert.GENERIC
                        }
                        uiState = uiState.copy(
                            alert = alert,
                            isSubmitting = false,
                        )
                    }

                    is ApiResult.NetworkFailure -> {
                        uiState = uiState.copy(
                            alert = ForgotPasswordAlert.NETWORK,
                            isSubmitting = false,
                        )
                    }
                }
            } finally {
                requestInFlight.set(false)
            }
        }
    }

    companion object {
        const val RESEND_COOLDOWN_SECONDS = 60L
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

@Composable
fun ForgotPasswordScreen(
    recoveryGateway: RecoveryGateway,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    clockMillis: () -> Long = System::currentTimeMillis,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(recoveryGateway, clockMillis) {
        ForgotPasswordController(
            recoveryGateway = recoveryGateway,
            scope = scope,
            clockMillis = clockMillis,
        )
    }

    val state = controller.uiState
    BackHandler(enabled = true) {
        if (!state.isSubmitting) {
            onBackToLogin()
        }
    }
    val sentStep = state.step as? ForgotPasswordStep.Sent
    var cooldownTick by remember(sentStep?.resendAtMillis, state.cooldownGeneration) {
        mutableIntStateOf(0)
    }
    LaunchedEffect(sentStep?.resendAtMillis, state.cooldownGeneration) {
        val target = sentStep?.resendAtMillis ?: return@LaunchedEffect
        while (computeRemainingSeconds(target, clockMillis()) > 0L) {
            // Run the wall-clock poll delay off Compose's test frame dispatcher so tests using
            // a fake clock do not spin infinitely during waitForIdle().
            withContext(Dispatchers.Default) {
                delay(500L)
            }
            cooldownTick++
        }
    }
    val remainingSeconds = if (sentStep != null && cooldownTick >= 0) {
        computeRemainingSeconds(sentStep.resendAtMillis, clockMillis())
    } else {
        0L
    }

    ForgotPasswordScreenContent(
        uiState = state,
        remainingResendSeconds = remainingSeconds,
        onEmailChanged = controller::onEmailChanged,
        onSubmit = controller::submit,
        onResend = controller::resend,
        onBackToLogin = onBackToLogin,
        modifier = modifier,
    )
}

@Composable
fun ForgotPasswordScreenContent(
    uiState: ForgotPasswordUiState,
    remainingResendSeconds: Long,
    onEmailChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val isSent = uiState.step is ForgotPasswordStep.Sent

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .imePadding()
            .testTag(if (isSent) RecoveryTestTags.FORGOT_SCREEN_SENT else RecoveryTestTags.FORGOT_SCREEN_FORM),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RecoveryTopBar(
            title = stringResource(R.string.forgot_title),
            onNavigationClick = onBackToLogin,
            navigationContentDescription = stringResource(R.string.forgot_back_to_login),
            navigationTestTag = RecoveryTestTags.FORGOT_BACK_BUTTON,
            navigationEnabled = !uiState.isSubmitting,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (uiState.alert != null) {
                val alertRes = when (uiState.alert) {
                    ForgotPasswordAlert.RATE_LIMITED -> R.string.forgot_error_rate_limited
                    ForgotPasswordAlert.NETWORK -> R.string.recovery_network_error
                    ForgotPasswordAlert.GENERIC -> R.string.forgot_error_generic
                }
                RecoveryBanner(
                    message = stringResource(alertRes),
                    isError = true,
                    tag = RecoveryTestTags.FORGOT_ERROR_BANNER,
                )
            }

            if (!isSent) {
                Text(
                    text = stringResource(R.string.forgot_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                )

                val emailErrorText = when (uiState.emailError) {
                    EmailError.REQUIRED -> stringResource(R.string.forgot_email_required)
                    EmailError.INVALID -> stringResource(R.string.forgot_email_invalid)
                    EmailError.TAKEN, null -> null
                }
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = onEmailChanged,
                    label = { Text(stringResource(R.string.forgot_email_label)) },
                    singleLine = true,
                    enabled = !uiState.isSubmitting,
                    isError = emailErrorText != null,
                    supportingText = emailErrorText?.let { msg ->
                        {
                            Text(
                                text = msg,
                                color = colors.dangerText,
                                modifier = Modifier.testTag(RecoveryTestTags.FORGOT_EMAIL_ERROR),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    colors = thi360FieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(RecoveryTestTags.FORGOT_EMAIL_FIELD),
                )

                Button(
                    onClick = onSubmit,
                    enabled = !uiState.isSubmitting,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryFill,
                        contentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(RecoveryTestTags.FORGOT_SUBMIT_BUTTON),
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = colors.onPrimary,
                            modifier = Modifier
                                .size(18.dp)
                                .testTag(RecoveryTestTags.FORGOT_SUBMIT_PROGRESS),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.recovery_submitting))
                    } else {
                        Text(text = stringResource(R.string.forgot_submit))
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = RecoveryIcons.Mail,
                        contentDescription = null,
                        tint = colors.primaryText,
                        modifier = Modifier.size(44.dp),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.forgot_sent_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.text,
                        modifier = Modifier
                            .semantics { heading() }
                            .testTag(RecoveryTestTags.FORGOT_SENT_TITLE),
                    )
                    Text(
                        text = stringResource(R.string.forgot_sent_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(RecoveryTestTags.FORGOT_SENT_MESSAGE),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.bgSubtle,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.forgot_sent_note_1),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = stringResource(R.string.forgot_sent_note_2),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = stringResource(R.string.forgot_sent_note_3),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = onResend,
                        enabled = remainingResendSeconds <= 0L && !uiState.isSubmitting,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colors.primaryContainer,
                            contentColor = colors.primaryText,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag(RecoveryTestTags.FORGOT_RESEND_BUTTON),
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = colors.primaryText,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = stringResource(R.string.forgot_resend))
                    }

                    if (remainingResendSeconds > 0L) {
                        Text(
                            text = stringResource(
                                R.string.forgot_resend_in,
                                RegistrationValidation.formatCountdown(remainingResendSeconds),
                            ),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = colors.textSecondary,
                            modifier = Modifier.testTag(RecoveryTestTags.FORGOT_RESEND_COUNTDOWN),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = true))

            TextButton(
                onClick = onBackToLogin,
                enabled = !uiState.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(RecoveryTestTags.FORGOT_BACK_TO_LOGIN_BUTTON),
            ) {
                Text(
                    text = stringResource(R.string.forgot_back_to_login),
                    color = colors.primaryText,
                )
            }
        }
    }
}
