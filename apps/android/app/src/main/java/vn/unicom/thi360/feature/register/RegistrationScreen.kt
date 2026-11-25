package vn.unicom.thi360.feature.register

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.ApiError
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.password.PasswordRuleEvaluation
import vn.unicom.thi360.feature.password.PasswordRules
import vn.unicom.thi360.feature.password.PasswordRulesChecklist
import vn.unicom.thi360.feature.password.PasswordValidationError
import vn.unicom.thi360.feature.password.RecoveryBanner
import vn.unicom.thi360.feature.password.RecoveryTopBar
import vn.unicom.thi360.feature.password.SecurePasswordField
import vn.unicom.thi360.feature.password.thi360FieldColors
import vn.unicom.thi360.feature.recovery.RecoveryGateway
import vn.unicom.thi360.feature.recovery.RecoveryIcons
import vn.unicom.thi360.feature.recovery.RegisterTestTags

enum class RegisterAlertError {
    RATE_LIMITED,
    NETWORK,
    GENERIC,
}

sealed interface OtpCodeFieldError {
    data object Required : OtpCodeFieldError
    data class Invalid(val remainingAttempts: Int?) : OtpCodeFieldError
}

enum class OtpAlertError {
    EXPIRED,
    RATE_LIMITED,
    RESEND_FAILED,
    NETWORK,
    GENERIC,
}

data class RegisterUiState(
    val step: RegistrationState = RegistrationState.Form(),
    val fieldErrors: RegistrationFieldErrors = RegistrationFieldErrors(),
    val passwordVisible: Boolean = false,
    val registerAlert: RegisterAlertError? = null,
    val isSubmittingForm: Boolean = false,
    val otpCodeError: OtpCodeFieldError? = null,
    val otpAlert: OtpAlertError? = null,
    val otpResentNotice: Boolean = false,
    val isVerifyingOtp: Boolean = false,
    val isResendingOtp: Boolean = false,
    val cooldownGeneration: Int = 0,
)

@Stable
class RegistrationController(
    private val recoveryGateway: RecoveryGateway,
    private val sessionManager: SessionManager,
    private val scope: CoroutineScope,
    private val clockMillis: () -> Long,
) {
    private val formSubmitInFlight = AtomicBoolean(false)
    private val otpSubmitInFlight = AtomicBoolean(false)
    private val resendInFlight = AtomicBoolean(false)

    val passwordState = TextFieldState()
    val otpCodeState = TextFieldState()

    var uiState by mutableStateOf(RegisterUiState())
        private set

    fun onFullNameChanged(value: String) {
        updateFormData { copy(fullName = value) }
        if (uiState.fieldErrors.fullName != null) {
            uiState = uiState.copy(fieldErrors = uiState.fieldErrors.copy(fullName = null))
        }
    }

    fun onUsernameChanged(value: String) {
        updateFormData { copy(username = value) }
        val currentErrors = uiState.fieldErrors
        if (currentErrors.username != null || currentErrors.password == PasswordValidationError.SAME_AS_USERNAME) {
            uiState = uiState.copy(
                fieldErrors = currentErrors.copy(
                    username = null,
                    password = if (currentErrors.password == PasswordValidationError.SAME_AS_USERNAME) {
                        null
                    } else {
                        currentErrors.password
                    },
                ),
            )
        }
    }

    fun onEmailChanged(value: String) {
        updateFormData { copy(email = value) }
        if (uiState.fieldErrors.email != null) {
            uiState = uiState.copy(fieldErrors = uiState.fieldErrors.copy(email = null))
        }
    }

    fun onAcceptTermsChanged(checked: Boolean) {
        updateFormData { copy(acceptTerms = checked) }
        if (checked && uiState.fieldErrors.acceptTermsRequired) {
            uiState = uiState.copy(fieldErrors = uiState.fieldErrors.copy(acceptTermsRequired = false))
        }
    }

    fun onPasswordEdited() {
        if (uiState.fieldErrors.password != null) {
            uiState = uiState.copy(fieldErrors = uiState.fieldErrors.copy(password = null))
        }
    }

    fun togglePasswordVisibility() {
        uiState = uiState.copy(passwordVisible = !uiState.passwordVisible)
    }

    fun onOtpEdited() {
        if (uiState.otpCodeError != null || uiState.otpAlert != null) {
            uiState = uiState.copy(
                otpCodeError = null,
                otpAlert = null,
            )
        }
    }

    fun backToEditForm() {
        val otp = uiState.step as? RegistrationState.Otp ?: return
        passwordState.clearText()
        otpCodeState.clearText()
        uiState = uiState.copy(
            step = RegistrationState.Form(
                data = otp.formData.copy(pendingRegistrationId = otp.registrationId),
            ),
            fieldErrors = RegistrationFieldErrors(),
            passwordVisible = false,
            registerAlert = null,
            otpCodeError = null,
            otpAlert = null,
            otpResentNotice = false,
        )
    }

    fun submitForm() {
        val formStep = uiState.step as? RegistrationState.Form ?: return
        val password = passwordState.text.toString()
        val validationErrors = RegistrationValidation.validateForm(formStep.data, password)
        if (validationErrors.hasErrors) {
            uiState = uiState.copy(
                fieldErrors = validationErrors,
                registerAlert = null,
            )
            return
        }

        if (!formSubmitInFlight.compareAndSet(false, true)) {
            return
        }

        val formData = formStep.data
        uiState = uiState.copy(
            fieldErrors = RegistrationFieldErrors(),
            registerAlert = null,
            isSubmittingForm = true,
        )

        scope.launch {
            try {
                val result = recoveryGateway.startRegistration(
                    fullName = formData.fullName.trim(),
                    username = formData.username.trim(),
                    email = formData.email.trim(),
                    password = password,
                    termsVersion = TERMS_VERSION,
                    registrationId = formData.pendingRegistrationId,
                )
                when (result) {
                    is ApiResult.Success -> {
                        val started = result.data
                        passwordState.clearText()
                        otpCodeState.clearText()
                        val resendAt = clockMillis() + started.resendAfterSeconds.coerceAtLeast(0L) * 1_000L
                        uiState = uiState.copy(
                            step = RegistrationState.Otp(
                                registrationId = started.registrationId,
                                email = started.email,
                                resendAt = resendAt,
                                formData = formData.copy(pendingRegistrationId = started.registrationId),
                            ),
                            passwordVisible = false,
                            registerAlert = null,
                            isSubmittingForm = false,
                            otpCodeError = null,
                            otpAlert = null,
                            otpResentNotice = false,
                            cooldownGeneration = uiState.cooldownGeneration + 1,
                        )
                    }

                    is ApiResult.HttpFailure -> handleFormHttpFailure(result)

                    is ApiResult.NetworkFailure -> {
                        uiState = uiState.copy(
                            registerAlert = RegisterAlertError.NETWORK,
                            isSubmittingForm = false,
                        )
                    }
                }
            } finally {
                formSubmitInFlight.set(false)
            }
        }
    }

    fun submitOtp() {
        val otp = uiState.step as? RegistrationState.Otp ?: return
        val code = otpCodeState.text.toString().filter(Char::isDigit).take(OTP_LENGTH)
        if (!RegistrationValidation.validateOtpCode(code)) {
            uiState = uiState.copy(
                otpCodeError = OtpCodeFieldError.Required,
                otpAlert = null,
            )
            return
        }

        if (!otpSubmitInFlight.compareAndSet(false, true)) {
            return
        }

        uiState = uiState.copy(
            otpCodeError = null,
            otpAlert = null,
            isVerifyingOtp = true,
        )

        scope.launch {
            try {
                val result = recoveryGateway.verifyRegistration(
                    registrationId = otp.registrationId,
                    code = code,
                    username = otp.formData.username.trim(),
                )
                when (result) {
                    is ApiResult.Success -> {
                        otpCodeState.clearText()
                        uiState = uiState.copy(
                            step = RegistrationState.Done,
                            isVerifyingOtp = false,
                        )
                        sessionManager.adoptSession(result.data)
                    }

                    is ApiResult.HttpFailure -> handleOtpVerifyFailure(otp, result)

                    is ApiResult.NetworkFailure -> {
                        uiState = uiState.copy(
                            otpAlert = OtpAlertError.NETWORK,
                            isVerifyingOtp = false,
                        )
                    }
                }
            } finally {
                otpSubmitInFlight.set(false)
            }
        }
    }

    fun resendOtp() {
        val otp = uiState.step as? RegistrationState.Otp ?: return
        if (computeRemainingSeconds(otp.resendAt, clockMillis()) > 0L) return
        if (!resendInFlight.compareAndSet(false, true)) return

        uiState = uiState.copy(
            otpAlert = null,
            otpResentNotice = false,
            isResendingOtp = true,
        )

        scope.launch {
            try {
                when (val result = recoveryGateway.resendRegistrationCode(otp.registrationId)) {
                    is ApiResult.Success -> {
                        val started = result.data
                        val resendAt = clockMillis() + started.resendAfterSeconds.coerceAtLeast(0L) * 1_000L
                        uiState = uiState.copy(
                            step = otp.copy(
                                registrationId = started.registrationId,
                                email = started.email,
                                resendAt = resendAt,
                            ),
                            otpCodeError = null,
                            otpAlert = null,
                            otpResentNotice = true,
                            isResendingOtp = false,
                            cooldownGeneration = uiState.cooldownGeneration + 1,
                        )
                    }

                    is ApiResult.HttpFailure -> {
                        when {
                            result.statusCode == HTTP_TOO_MANY_REQUESTS -> {
                                val retrySeconds = (result.error?.retryAfter ?: DEFAULT_RESEND_SECONDS)
                                    .coerceAtLeast(1L)
                                val nextResendAt = clockMillis() + retrySeconds * 1_000L
                                uiState = uiState.copy(
                                    step = otp.copy(resendAt = nextResendAt),
                                    otpAlert = OtpAlertError.RATE_LIMITED,
                                    otpResentNotice = false,
                                    isResendingOtp = false,
                                    cooldownGeneration = uiState.cooldownGeneration + 1,
                                )
                            }

                            result.error?.code.equals(CODE_EMAIL_CODE_EXPIRED, ignoreCase = true) -> {
                                uiState = uiState.copy(
                                    otpAlert = OtpAlertError.EXPIRED,
                                    isResendingOtp = false,
                                )
                            }

                            else -> {
                                uiState = uiState.copy(
                                    otpAlert = OtpAlertError.RESEND_FAILED,
                                    isResendingOtp = false,
                                )
                            }
                        }
                    }

                    is ApiResult.NetworkFailure -> {
                        uiState = uiState.copy(
                            otpAlert = OtpAlertError.NETWORK,
                            isResendingOtp = false,
                        )
                    }
                }
            } finally {
                resendInFlight.set(false)
            }
        }
    }

    private fun handleFormHttpFailure(failure: ApiResult.HttpFailure) {
        if (failure.statusCode == HTTP_TOO_MANY_REQUESTS) {
            uiState = uiState.copy(
                registerAlert = RegisterAlertError.RATE_LIMITED,
                isSubmittingForm = false,
            )
            return
        }
        val mapped = mapServerFieldErrors(failure.statusCode, failure.error)
        if (mapped != null) {
            uiState = uiState.copy(
                fieldErrors = mapped,
                registerAlert = null,
                isSubmittingForm = false,
            )
        } else {
            uiState = uiState.copy(
                registerAlert = RegisterAlertError.GENERIC,
                isSubmittingForm = false,
            )
        }
    }

    private fun handleOtpVerifyFailure(otp: RegistrationState.Otp, failure: ApiResult.HttpFailure) {
        val code = failure.error?.code?.uppercase()
        when {
            code == CODE_EMAIL_CODE_INVALID -> {
                uiState = uiState.copy(
                    otpCodeError = OtpCodeFieldError.Invalid(failure.error.remainingAttempts),
                    isVerifyingOtp = false,
                )
            }

            code == CODE_EMAIL_CODE_EXPIRED -> {
                uiState = uiState.copy(
                    otpAlert = OtpAlertError.EXPIRED,
                    isVerifyingOtp = false,
                )
            }

            failure.statusCode == HTTP_TOO_MANY_REQUESTS -> {
                uiState = uiState.copy(
                    otpAlert = OtpAlertError.RATE_LIMITED,
                    isVerifyingOtp = false,
                )
            }

            failure.statusCode == HTTP_CONFLICT || failure.statusCode == HTTP_BAD_REQUEST -> {
                val mapped = mapServerFieldErrors(failure.statusCode, failure.error)
                if (mapped != null) {
                    passwordState.clearText()
                    otpCodeState.clearText()
                    uiState = uiState.copy(
                        step = RegistrationState.Form(
                            data = otp.formData.copy(pendingRegistrationId = otp.registrationId),
                        ),
                        fieldErrors = mapped,
                        passwordVisible = false,
                        isVerifyingOtp = false,
                    )
                } else {
                    uiState = uiState.copy(
                        otpAlert = OtpAlertError.GENERIC,
                        isVerifyingOtp = false,
                    )
                }
            }

            else -> {
                uiState = uiState.copy(
                    otpAlert = OtpAlertError.GENERIC,
                    isVerifyingOtp = false,
                )
            }
        }
    }

    private fun mapServerFieldErrors(statusCode: Int, error: ApiError?): RegistrationFieldErrors? {
        if (error == null) return null
        if (statusCode == HTTP_CONFLICT && error.code.equals(CODE_USERNAME_TAKEN, ignoreCase = true)) {
            return RegistrationFieldErrors(username = UsernameError.TAKEN)
        }
        if (statusCode == HTTP_CONFLICT && error.code.equals(CODE_EMAIL_TAKEN, ignoreCase = true)) {
            return RegistrationFieldErrors(email = EmailError.TAKEN)
        }

        var fullNameError: FullNameError? = null
        var usernameError: UsernameError? = null
        var emailError: EmailError? = null
        var passwordError: PasswordValidationError? = null
        var termsError = false

        for (field in error.fieldErrors) {
            when (field.field) {
                "fullName" -> fullNameError = FullNameError.REQUIRED
                "username" -> usernameError = if (field.code.equals(CODE_USERNAME_TAKEN, ignoreCase = true)) {
                    UsernameError.TAKEN
                } else {
                    UsernameError.PATTERN
                }
                "email" -> emailError = when {
                    field.code.equals(CODE_EMAIL_TAKEN, ignoreCase = true) -> EmailError.TAKEN
                    field.code.equals("EMAIL_INVALID", ignoreCase = true) -> EmailError.INVALID
                    else -> EmailError.REQUIRED
                }
                "password" -> passwordError = when {
                    field.code.equals(CODE_PASSWORD_SAME_AS_USERNAME, ignoreCase = true) ->
                        PasswordValidationError.SAME_AS_USERNAME
                    else -> PasswordValidationError.WEAK
                }
                "acceptTerms" -> termsError = true
            }
        }

        val combined = RegistrationFieldErrors(
            fullName = fullNameError,
            username = usernameError,
            email = emailError,
            password = passwordError,
            acceptTermsRequired = termsError,
        )
        return combined.takeIf { it.hasErrors }
    }

    private inline fun updateFormData(transform: RegistrationFormData.() -> RegistrationFormData) {
        val current = uiState.step as? RegistrationState.Form ?: return
        uiState = uiState.copy(step = current.copy(data = current.data.transform()))
    }

    companion object {
        const val OTP_LENGTH = 6
        private const val DEFAULT_RESEND_SECONDS = 60L
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_CONFLICT = 409
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val CODE_USERNAME_TAKEN = "USERNAME_TAKEN"
        private const val CODE_EMAIL_TAKEN = "EMAIL_TAKEN"
        private const val CODE_EMAIL_CODE_INVALID = "EMAIL_CODE_INVALID"
        private const val CODE_EMAIL_CODE_EXPIRED = "EMAIL_CODE_EXPIRED"
        private const val CODE_PASSWORD_SAME_AS_USERNAME = "PASSWORD_SAME_AS_USERNAME"
    }
}

@Composable
fun RegistrationFlowScreen(
    recoveryGateway: RecoveryGateway,
    sessionManager: SessionManager,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    clockMillis: () -> Long = System::currentTimeMillis,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(recoveryGateway, sessionManager, clockMillis) {
        RegistrationController(
            recoveryGateway = recoveryGateway,
            sessionManager = sessionManager,
            scope = scope,
            clockMillis = clockMillis,
        )
    }

    val passwordText = controller.passwordState.text.toString()
    LaunchedEffect(passwordText) {
        if (passwordText.isNotEmpty()) {
            controller.onPasswordEdited()
        }
    }
    val otpText = controller.otpCodeState.text.toString()
    LaunchedEffect(otpText) {
        if (otpText.isNotEmpty()) {
            controller.onOtpEdited()
        }
    }
    val evaluation = remember(passwordText) { PasswordRules.evaluate(passwordText) }

    when (val step = controller.uiState.step) {
        is RegistrationState.Form -> {
            RegisterFormScreenContent(
                formData = step.data,
                uiState = controller.uiState,
                passwordState = controller.passwordState,
                evaluation = evaluation,
                onFullNameChanged = controller::onFullNameChanged,
                onUsernameChanged = controller::onUsernameChanged,
                onEmailChanged = controller::onEmailChanged,
                onTogglePasswordVisibility = controller::togglePasswordVisibility,
                onAcceptTermsChanged = controller::onAcceptTermsChanged,
                onSubmit = controller::submitForm,
                onBackToLogin = onBackToLogin,
                modifier = modifier,
            )
        }

        is RegistrationState.Otp -> {
            BackHandler(enabled = true) {
                controller.backToEditForm()
            }
            var cooldownTick by remember(step.resendAt, controller.uiState.cooldownGeneration) {
                mutableIntStateOf(0)
            }
            LaunchedEffect(step.resendAt, controller.uiState.cooldownGeneration) {
                while (computeRemainingSeconds(step.resendAt, clockMillis()) > 0L) {
                    // Run wall-clock poll off Compose's test frame dispatcher so fake-clock tests never spin in waitForIdle().
                    withContext(Dispatchers.Default) {
                        delay(COOLDOWN_POLL_INTERVAL_MS)
                    }
                    cooldownTick++
                }
            }
            val remainingSeconds = if (cooldownTick >= 0) {
                computeRemainingSeconds(step.resendAt, clockMillis())
            } else {
                0L
            }
            OtpScreenContent(
                email = step.email,
                otpState = controller.otpCodeState,
                otpCodeError = controller.uiState.otpCodeError,
                otpAlert = controller.uiState.otpAlert,
                showResentNotice = controller.uiState.otpResentNotice,
                isVerifying = controller.uiState.isVerifyingOtp,
                isResending = controller.uiState.isResendingOtp,
                remainingResendSeconds = remainingSeconds,
                onVerify = controller::submitOtp,
                onResend = controller::resendOtp,
                onBackToEdit = controller::backToEditForm,
                modifier = modifier,
            )
        }

        RegistrationState.Done -> Unit
    }
}

@Composable
fun RegisterFormScreenContent(
    formData: RegistrationFormData,
    uiState: RegisterUiState,
    passwordState: TextFieldState,
    evaluation: PasswordRuleEvaluation,
    onFullNameChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onAcceptTermsChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val fieldColors = thi360FieldColors()
    var showTermsDialog by remember { mutableStateOf(false) }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            containerColor = colors.bgSurface,
            titleContentColor = colors.text,
            textContentColor = colors.textSecondary,
            title = { Text(stringResource(R.string.register_terms_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.register_terms_draft),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                    Text(
                        text = stringResource(R.string.register_terms_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showTermsDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.register_terms_close),
                        color = colors.primaryText,
                    )
                }
            },
            modifier = Modifier.testTag(RegisterTestTags.TERMS_DIALOG),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .imePadding()
            .testTag(RegisterTestTags.SCREEN_FORM),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RecoveryTopBar(
            title = stringResource(R.string.register_title),
            onNavigationClick = onBackToLogin,
            navigationContentDescription = stringResource(R.string.recovery_back),
            navigationTestTag = RegisterTestTags.BACK_BUTTON,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.registerAlert != null) {
                val alertRes = when (uiState.registerAlert) {
                    RegisterAlertError.RATE_LIMITED -> R.string.register_error_rate_limited
                    RegisterAlertError.NETWORK -> R.string.recovery_network_error
                    RegisterAlertError.GENERIC -> R.string.register_error_generic
                }
                RecoveryBanner(
                    message = stringResource(alertRes),
                    isError = true,
                    tag = RegisterTestTags.ERROR_BANNER,
                )
            }

            val fullNameErrorText = when (uiState.fieldErrors.fullName) {
                FullNameError.REQUIRED -> stringResource(R.string.register_full_name_required)
                FullNameError.TOO_LONG -> stringResource(R.string.register_full_name_too_long)
                null -> null
            }
            OutlinedTextField(
                value = formData.fullName,
                onValueChange = onFullNameChanged,
                label = { Text(stringResource(R.string.register_full_name_label)) },
                singleLine = true,
                enabled = !uiState.isSubmittingForm,
                isError = fullNameErrorText != null,
                supportingText = fullNameErrorText?.let { msg ->
                    {
                        Text(
                            text = msg,
                            color = colors.dangerText,
                            modifier = Modifier.testTag(RegisterTestTags.FULL_NAME_ERROR),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RegisterTestTags.FULL_NAME_FIELD),
            )

            val usernameErrorText = when (uiState.fieldErrors.username) {
                UsernameError.REQUIRED -> stringResource(R.string.register_username_required)
                UsernameError.PATTERN -> stringResource(R.string.register_username_pattern)
                UsernameError.TAKEN -> stringResource(R.string.register_username_taken)
                null -> null
            }
            OutlinedTextField(
                value = formData.username,
                onValueChange = onUsernameChanged,
                label = { Text(stringResource(R.string.register_username_label)) },
                singleLine = true,
                enabled = !uiState.isSubmittingForm,
                isError = usernameErrorText != null,
                supportingText = {
                    if (usernameErrorText != null) {
                        Text(
                            text = usernameErrorText,
                            color = colors.dangerText,
                            modifier = Modifier.testTag(RegisterTestTags.USERNAME_ERROR),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.register_username_hint),
                            color = colors.textMuted,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RegisterTestTags.USERNAME_FIELD),
            )

            val emailErrorText = when (uiState.fieldErrors.email) {
                EmailError.REQUIRED -> stringResource(R.string.register_email_required)
                EmailError.INVALID -> stringResource(R.string.register_email_invalid)
                EmailError.TAKEN -> stringResource(R.string.register_email_taken)
                null -> null
            }
            OutlinedTextField(
                value = formData.email,
                onValueChange = onEmailChanged,
                label = { Text(stringResource(R.string.register_email_label)) },
                singleLine = true,
                enabled = !uiState.isSubmittingForm,
                isError = emailErrorText != null,
                supportingText = {
                    if (emailErrorText != null) {
                        Text(
                            text = emailErrorText,
                            color = colors.dangerText,
                            modifier = Modifier.testTag(RegisterTestTags.EMAIL_ERROR),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.register_email_hint),
                            color = colors.textMuted,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RegisterTestTags.EMAIL_FIELD),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val passwordErrorText = when (uiState.fieldErrors.password) {
                    PasswordValidationError.REQUIRED -> stringResource(R.string.register_password_required)
                    PasswordValidationError.WEAK -> stringResource(R.string.register_password_weak)
                    PasswordValidationError.SAME_AS_USERNAME -> stringResource(R.string.register_password_same_as_username)
                    null -> null
                }
                SecurePasswordField(
                    state = passwordState,
                    label = stringResource(R.string.register_password_label),
                    visible = uiState.passwordVisible,
                    enabled = !uiState.isSubmittingForm,
                    errorText = passwordErrorText,
                    showContentDescription = stringResource(R.string.register_password_show),
                    hideContentDescription = stringResource(R.string.register_password_hide),
                    fieldTag = RegisterTestTags.PASSWORD_FIELD,
                    errorTag = RegisterTestTags.PASSWORD_ERROR,
                    toggleTag = RegisterTestTags.PASSWORD_VISIBILITY_TOGGLE,
                    imeAction = ImeAction.Done,
                    onToggleVisibility = onTogglePasswordVisibility,
                    onKeyboardAction = onSubmit,
                )
                PasswordRulesChecklist(evaluation = evaluation)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = formData.acceptTerms,
                            enabled = !uiState.isSubmittingForm,
                            role = Role.Checkbox,
                            onValueChange = onAcceptTermsChanged,
                        )
                        .testTag(RegisterTestTags.TERMS_CHECKBOX),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(
                        checked = formData.acceptTerms,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.primaryFill,
                            checkmarkColor = colors.onPrimary,
                            uncheckedColor = if (uiState.fieldErrors.acceptTermsRequired) {
                                colors.dangerText
                            } else {
                                colors.borderStrong
                            },
                        ),
                    )
                    Text(
                        text = stringResource(R.string.register_terms_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }

                TextButton(
                    onClick = { showTermsDialog = true },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(RegisterTestTags.TERMS_VIEW_BUTTON),
                ) {
                    Text(
                        text = stringResource(R.string.register_terms_view),
                        color = colors.primaryText,
                    )
                }

                if (uiState.fieldErrors.acceptTermsRequired) {
                    Text(
                        text = stringResource(R.string.register_terms_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dangerText,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag(RegisterTestTags.TERMS_ERROR),
                    )
                }
            }

            Button(
                onClick = onSubmit,
                enabled = !uiState.isSubmittingForm,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryFill,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(RegisterTestTags.SUBMIT_BUTTON),
            ) {
                if (uiState.isSubmittingForm) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = colors.onPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .testTag(RegisterTestTags.SUBMIT_PROGRESS),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.recovery_submitting))
                } else {
                    Text(text = stringResource(R.string.register_submit))
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = true))

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.Center,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.register_have_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
                TextButton(
                    onClick = onBackToLogin,
                    enabled = !uiState.isSubmittingForm,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(RegisterTestTags.LOGIN_LINK),
                ) {
                    Text(
                        text = stringResource(R.string.register_login_link),
                        color = colors.primaryText,
                    )
                }
            }
        }
    }
}

@Composable
fun OtpScreenContent(
    email: String,
    otpState: TextFieldState,
    otpCodeError: OtpCodeFieldError?,
    otpAlert: OtpAlertError?,
    showResentNotice: Boolean,
    isVerifying: Boolean,
    isResending: Boolean,
    remainingResendSeconds: Long,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onBackToEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val busy = isVerifying || isResending
    val otpCode = otpState.text.toString()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .imePadding()
            .testTag(RegisterTestTags.SCREEN_OTP),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RecoveryTopBar(
            title = stringResource(R.string.register_otp_title),
            onNavigationClick = onBackToEdit,
            navigationContentDescription = stringResource(R.string.register_otp_back_to_edit),
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

            val sentText = stringResource(R.string.register_otp_sent_to, email)
            val sentAnnotated = buildAnnotatedString {
                val idx = sentText.indexOf(email)
                if (idx >= 0) {
                    append(sentText.substring(0, idx))
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = colors.text)) {
                        append(email)
                    }
                    append(sentText.substring(idx + email.length))
                } else {
                    append(sentText)
                }
            }
            Text(
                text = sentAnnotated,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                modifier = Modifier.testTag(RegisterTestTags.OTP_EMAIL_TEXT),
            )

            if (otpAlert != null) {
                val alertRes = when (otpAlert) {
                    OtpAlertError.EXPIRED -> R.string.register_otp_code_expired
                    OtpAlertError.RATE_LIMITED -> R.string.register_otp_error_rate_limited
                    OtpAlertError.RESEND_FAILED -> R.string.register_otp_error_resend_failed
                    OtpAlertError.NETWORK -> R.string.recovery_network_error
                    OtpAlertError.GENERIC -> R.string.register_otp_error_generic
                }
                RecoveryBanner(
                    message = stringResource(alertRes),
                    isError = true,
                    tag = RegisterTestTags.OTP_ERROR_BANNER,
                )
            }

            if (showResentNotice) {
                RecoveryBanner(
                    message = stringResource(R.string.register_otp_code_resent),
                    isError = false,
                    tag = RegisterTestTags.OTP_RESENT_BANNER,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val codeLabel = stringResource(R.string.register_otp_code_label)
                Text(
                    text = codeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )

                BasicTextField(
                    state = otpState,
                    enabled = !busy,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    inputTransformation = OtpDigitsInputTransformation,
                    cursorBrush = SolidColor(colors.primaryFill),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    onKeyboardAction = { onVerify() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = codeLabel }
                        .testTag(RegisterTestTags.OTP_CODE_FIELD),
                    decorator = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.matchParentSize().alpha(0f)) {
                                innerTextField()
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                repeat(RegistrationController.OTP_LENGTH) { index ->
                                    val char = otpCode.getOrNull(index)?.toString().orEmpty()
                                    val isActive = index == otpCode.length.coerceAtMost(RegistrationController.OTP_LENGTH - 1)
                                    val borderColor = when {
                                        otpCodeError != null -> colors.dangerText
                                        isActive -> colors.primaryFill
                                        else -> colors.borderStrong
                                    }
                                    val borderWidth = if (isActive || otpCodeError != null) 2.dp else 1.dp
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = colors.bgSurface,
                                        border = BorderStroke(borderWidth, borderColor),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = char,
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = colors.text,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                )

                val codeErrorMessage = when (otpCodeError) {
                    OtpCodeFieldError.Required -> stringResource(R.string.register_otp_code_required)
                    is OtpCodeFieldError.Invalid -> if (otpCodeError.remainingAttempts != null) {
                        pluralStringResource(
                            R.plurals.register_otp_code_invalid,
                            otpCodeError.remainingAttempts,
                            otpCodeError.remainingAttempts,
                        )
                    } else {
                        stringResource(R.string.register_otp_code_invalid_generic)
                    }
                    null -> null
                }
                if (codeErrorMessage != null) {
                    Text(
                        text = codeErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dangerText,
                        modifier = Modifier.testTag(RegisterTestTags.OTP_CODE_ERROR),
                    )
                }

                Text(
                    text = stringResource(R.string.register_otp_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }

            Button(
                onClick = onVerify,
                enabled = !busy,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryFill,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(RegisterTestTags.OTP_SUBMIT_BUTTON),
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = colors.onPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .testTag(RegisterTestTags.OTP_SUBMIT_PROGRESS),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.recovery_submitting))
                } else {
                    Text(text = stringResource(R.string.register_otp_submit))
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.register_otp_not_received),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )

                FilledTonalButton(
                    onClick = onResend,
                    enabled = remainingResendSeconds <= 0L && !busy,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.primaryText,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(RegisterTestTags.OTP_RESEND_BUTTON),
                ) {
                    Text(text = stringResource(R.string.register_otp_resend))
                }

                if (remainingResendSeconds > 0L) {
                    Text(
                        text = stringResource(
                            R.string.register_otp_resend_in,
                            RegistrationValidation.formatCountdown(remainingResendSeconds),
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.textSecondary,
                        modifier = Modifier.testTag(RegisterTestTags.OTP_RESEND_COUNTDOWN),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = true))

            TextButton(
                onClick = onBackToEdit,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(RegisterTestTags.OTP_BACK_TO_EDIT_BUTTON),
            ) {
                Text(
                    text = stringResource(R.string.register_otp_back_to_edit),
                    color = colors.primaryText,
                )
            }
        }
    }
}

private val OtpDigitsInputTransformation = InputTransformation {
    val current = asCharSequence()
    val filtered = buildString {
        for (i in 0 until current.length) {
            val ch = current[i]
            if (ch.isDigit() && length < RegistrationController.OTP_LENGTH) {
                append(ch)
            }
        }
    }
    if (filtered != current.toString()) {
        replace(0, length, filtered)
    }
}

private const val COOLDOWN_POLL_INTERVAL_MS = 500L

internal fun computeRemainingSeconds(targetMillis: Long, nowMillis: Long): Long {
    val diff = targetMillis - nowMillis
    return if (diff <= 0L) 0L else (diff + 999L) / 1_000L
}
