package vn.unicom.thi360.feature.password

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.recovery.PasswordTestTags
import vn.unicom.thi360.feature.recovery.RecoveryGateway
import vn.unicom.thi360.feature.recovery.RecoveryIcons

enum class OldPasswordError {
    REQUIRED,
    WRONG,
}

enum class ConfirmPasswordError {
    REQUIRED,
    MISMATCH,
}

enum class PasswordBannerError {
    NETWORK,
    RESET_GENERIC,
    CHANGE_GENERIC,
}

data class NewPasswordUiState(
    val oldPasswordVisible: Boolean = false,
    val newPasswordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    val oldPasswordError: OldPasswordError? = null,
    val newPasswordError: PasswordValidationError? = null,
    val confirmPasswordError: ConfirmPasswordError? = null,
    val bannerError: PasswordBannerError? = null,
    val showVoluntarySuccess: Boolean = false,
    val isSubmitting: Boolean = false,
    val isLoggingOut: Boolean = false,
)

@Stable
class NewPasswordController(
    val mode: NewPasswordMode,
    private val recoveryGateway: RecoveryGateway,
    private val sessionManager: SessionManager?,
    private val scope: CoroutineScope,
    val oldPasswordState: TextFieldState,
    val newPasswordState: TextFieldState,
    val confirmPasswordState: TextFieldState,
    private val onResetCompleted: () -> Unit,
    private val onResetTokenInvalid: () -> Unit,
    private val onVoluntaryChanged: () -> Unit,
    private val onClearDraft: () -> Unit = {},
) {
    private val submitInFlight = AtomicBoolean(false)
    private val logoutInFlight = AtomicBoolean(false)

    // Keep reset token strictly in ephemeral controller memory and clear immediately upon exit or completion.
    private var ephemeralResetToken: String? = (mode as? NewPasswordMode.FromLink)?.token

    var uiState by mutableStateOf(NewPasswordUiState())
        private set

    fun invalidateEphemeralToken() {
        ephemeralResetToken = null
        if (mode is NewPasswordMode.FromLink) {
            newPasswordState.clearText()
            confirmPasswordState.clearText()
        }
    }

    fun hasActiveResetToken(): Boolean = ephemeralResetToken != null

    fun toggleOldPasswordVisibility() {
        uiState = uiState.copy(oldPasswordVisible = !uiState.oldPasswordVisible)
    }

    fun toggleNewPasswordVisibility() {
        uiState = uiState.copy(newPasswordVisible = !uiState.newPasswordVisible)
    }

    fun toggleConfirmPasswordVisibility() {
        uiState = uiState.copy(confirmPasswordVisible = !uiState.confirmPasswordVisible)
    }

    fun onOldPasswordEdited() {
        if (uiState.oldPasswordError != null || uiState.showVoluntarySuccess || uiState.bannerError != null) {
            uiState = uiState.copy(
                oldPasswordError = null,
                bannerError = null,
                showVoluntarySuccess = false,
            )
        }
    }

    fun onNewPasswordEdited() {
        if (uiState.newPasswordError != null || uiState.showVoluntarySuccess || uiState.bannerError != null) {
            uiState = uiState.copy(
                newPasswordError = null,
                bannerError = null,
                showVoluntarySuccess = false,
            )
        }
    }

    fun onConfirmPasswordEdited() {
        if (uiState.confirmPasswordError != null || uiState.showVoluntarySuccess || uiState.bannerError != null) {
            uiState = uiState.copy(
                confirmPasswordError = null,
                bannerError = null,
                showVoluntarySuccess = false,
            )
        }
    }

    fun submit() {
        val requiresOldPassword = mode !is NewPasswordMode.FromLink
        val username = when (mode) {
            is NewPasswordMode.FromLink -> null
            is NewPasswordMode.Forced -> mode.username
            is NewPasswordMode.Voluntary -> mode.username
        }

        val oldPassword = oldPasswordState.text.toString()
        val newPassword = newPasswordState.text.toString()
        val confirmPassword = confirmPasswordState.text.toString()

        val oldError = if (requiresOldPassword && oldPassword.isEmpty()) OldPasswordError.REQUIRED else null
        val newError = PasswordRules.validate(newPassword, username)
        val confirmError = when {
            confirmPassword.isEmpty() -> ConfirmPasswordError.REQUIRED
            confirmPassword != newPassword -> ConfirmPasswordError.MISMATCH
            else -> null
        }

        if (oldError != null || newError != null || confirmError != null) {
            uiState = uiState.copy(
                oldPasswordError = oldError,
                newPasswordError = newError,
                confirmPasswordError = confirmError,
                bannerError = null,
                showVoluntarySuccess = false,
            )
            return
        }

        val tokenForLink = if (mode is NewPasswordMode.FromLink) {
            ephemeralResetToken ?: run {
                onResetTokenInvalid()
                return
            }
        } else {
            null
        }

        if (!submitInFlight.compareAndSet(false, true)) {
            return
        }

        uiState = uiState.copy(
            oldPasswordError = null,
            newPasswordError = null,
            confirmPasswordError = null,
            bannerError = null,
            showVoluntarySuccess = false,
            isSubmitting = true,
        )

        scope.launch {
            try {
                when (mode) {
                    is NewPasswordMode.FromLink -> {
                        val token = checkNotNull(tokenForLink)
                        when (val result = recoveryGateway.resetPassword(token = token, newPassword = newPassword)) {
                            is ApiResult.Success -> {
                                invalidateEphemeralToken()
                                sessionManager?.clearSession(SignOutReason.PasswordReset)
                                uiState = uiState.copy(
                                    newPasswordVisible = false,
                                    confirmPasswordVisible = false,
                                    isSubmitting = false,
                                )
                                onResetCompleted()
                            }

                            is ApiResult.HttpFailure -> handleResetFailure(result)

                            is ApiResult.NetworkFailure -> {
                                uiState = uiState.copy(
                                    bannerError = PasswordBannerError.NETWORK,
                                    isSubmitting = false,
                                )
                            }
                        }
                    }

                    is NewPasswordMode.Forced, is NewPasswordMode.Voluntary -> {
                        when (val result = recoveryGateway.changePassword(oldPassword = oldPassword, newPassword = newPassword)) {
                            is ApiResult.Success -> {
                                onClearDraft()
                                oldPasswordState.clearText()
                                newPasswordState.clearText()
                                confirmPasswordState.clearText()
                                if (mode is NewPasswordMode.Forced) {
                                    sessionManager?.markPasswordChanged()
                                    uiState = uiState.copy(
                                        oldPasswordVisible = false,
                                        newPasswordVisible = false,
                                        confirmPasswordVisible = false,
                                        isSubmitting = false,
                                    )
                                } else {
                                    uiState = uiState.copy(
                                        oldPasswordVisible = false,
                                        newPasswordVisible = false,
                                        confirmPasswordVisible = false,
                                        showVoluntarySuccess = true,
                                        isSubmitting = false,
                                    )
                                    onVoluntaryChanged()
                                }
                            }

                            is ApiResult.HttpFailure -> handleChangeFailure(result)

                            is ApiResult.NetworkFailure -> {
                                uiState = uiState.copy(
                                    bannerError = PasswordBannerError.NETWORK,
                                    isSubmitting = false,
                                )
                            }
                        }
                    }
                }
            } finally {
                submitInFlight.set(false)
            }
        }
    }

    fun logout() {
        val manager = sessionManager ?: return
        if (!logoutInFlight.compareAndSet(false, true)) return
        uiState = uiState.copy(isLoggingOut = true, bannerError = null)
        scope.launch {
            try {
                onClearDraft()
                oldPasswordState.clearText()
                newPasswordState.clearText()
                confirmPasswordState.clearText()
                manager.logout()
            } finally {
                logoutInFlight.set(false)
                uiState = uiState.copy(isLoggingOut = false)
            }
        }
    }

    private fun handleResetFailure(failure: ApiResult.HttpFailure) {
        val error = failure.error
        when (error?.code?.uppercase()) {
            CODE_RESET_TOKEN_INVALID -> {
                invalidateEphemeralToken()
                uiState = uiState.copy(isSubmitting = false)
                onResetTokenInvalid()
            }

            CODE_PASSWORD_WEAK -> {
                val isSameAsUsername = error.fieldErrors.any {
                    it.code.equals(CODE_PASSWORD_SAME_AS_USERNAME, ignoreCase = true)
                }
                uiState = uiState.copy(
                    newPasswordError = if (isSameAsUsername) {
                        PasswordValidationError.SAME_AS_USERNAME
                    } else {
                        PasswordValidationError.WEAK
                    },
                    isSubmitting = false,
                )
            }

            else -> {
                uiState = uiState.copy(
                    bannerError = PasswordBannerError.RESET_GENERIC,
                    isSubmitting = false,
                )
            }
        }
    }

    private fun handleChangeFailure(failure: ApiResult.HttpFailure) {
        val error = failure.error
        when (error?.code?.uppercase()) {
            CODE_WRONG_OLD_PASSWORD -> {
                uiState = uiState.copy(
                    oldPasswordError = OldPasswordError.WRONG,
                    isSubmitting = false,
                )
            }

            CODE_PASSWORD_WEAK -> {
                val isSameAsUsername = error.fieldErrors.any {
                    it.code.equals(CODE_PASSWORD_SAME_AS_USERNAME, ignoreCase = true)
                }
                uiState = uiState.copy(
                    newPasswordError = if (isSameAsUsername) {
                        PasswordValidationError.SAME_AS_USERNAME
                    } else {
                        PasswordValidationError.WEAK
                    },
                    isSubmitting = false,
                )
            }

            else -> {
                uiState = uiState.copy(
                    bannerError = PasswordBannerError.CHANGE_GENERIC,
                    isSubmitting = false,
                )
            }
        }
    }

    companion object {
        private const val CODE_RESET_TOKEN_INVALID = "RESET_TOKEN_INVALID"
        private const val CODE_WRONG_OLD_PASSWORD = "WRONG_OLD_PASSWORD"
        private const val CODE_PASSWORD_WEAK = "PASSWORD_WEAK"
        private const val CODE_PASSWORD_SAME_AS_USERNAME = "PASSWORD_SAME_AS_USERNAME"
    }
}

@Composable
fun ChangePasswordScreen(
    username: String?,
    recoveryGateway: RecoveryGateway,
    modifier: Modifier = Modifier,
    embeddedInPane: Boolean = false,
    onBack: (() -> Unit)? = null,
    onPasswordChanged: () -> Unit = {},
) {
    NewPasswordScreen(
        mode = NewPasswordMode.Voluntary(username = username),
        recoveryGateway = recoveryGateway,
        sessionManager = null,
        modifier = modifier,
        embeddedInPane = embeddedInPane,
        onBack = onBack,
        onVoluntaryChanged = onPasswordChanged,
    )
}

@Composable
fun NewPasswordScreen(
    mode: NewPasswordMode,
    recoveryGateway: RecoveryGateway,
    sessionManager: SessionManager?,
    modifier: Modifier = Modifier,
    embeddedInPane: Boolean = false,
    onBack: (() -> Unit)? = null,
    onResetCompleted: () -> Unit = {},
    onResetTokenInvalid: () -> Unit = {},
    onVoluntaryChanged: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val latestOnResetCompleted by rememberUpdatedState(onResetCompleted)
    val latestOnResetTokenInvalid by rememberUpdatedState(onResetTokenInvalid)
    val latestOnVoluntaryChanged by rememberUpdatedState(onVoluntaryChanged)

    val viewModelStoreOwner = LocalViewModelStoreOwner.current
    val draftViewModel = remember(mode, viewModelStoreOwner) {
        if (mode is NewPasswordMode.FromLink || viewModelStoreOwner == null) {
            null
        } else {
            val key = when (mode) {
                is NewPasswordMode.Forced -> "forced_${mode.username.orEmpty()}"
                is NewPasswordMode.Voluntary -> "voluntary_${mode.username.orEmpty()}"
                is NewPasswordMode.FromLink -> error("unreachable")
            }
            ViewModelProvider(viewModelStoreOwner)[key, ChangePasswordDraftViewModel::class.java]
        }
    }
    val draftFields = remember(mode, draftViewModel) {
        if (draftViewModel != null) {
            Triple(
                draftViewModel.oldPasswordState,
                draftViewModel.newPasswordState,
                draftViewModel.confirmPasswordState,
            )
        } else {
            Triple(TextFieldState(), TextFieldState(), TextFieldState())
        }
    }

    val controller = remember(mode, recoveryGateway, sessionManager, draftFields, draftViewModel) {
        NewPasswordController(
            mode = mode,
            recoveryGateway = recoveryGateway,
            sessionManager = sessionManager,
            scope = scope,
            oldPasswordState = draftFields.first,
            newPasswordState = draftFields.second,
            confirmPasswordState = draftFields.third,
            onResetCompleted = { latestOnResetCompleted() },
            onResetTokenInvalid = { latestOnResetTokenInvalid() },
            onVoluntaryChanged = { latestOnVoluntaryChanged() },
            onClearDraft = { draftViewModel?.clearAll() },
        )
    }

    val handleExit: (() -> Unit)? = onBack?.let { back ->
        {
            if (!controller.uiState.isSubmitting && !controller.uiState.isLoggingOut) {
                controller.invalidateEphemeralToken()
                if (mode is NewPasswordMode.Voluntary) {
                    draftViewModel?.clearAll()
                }
                back()
            }
        }
    }

    when {
        mode is NewPasswordMode.Forced -> BackHandler(enabled = true) {}
        mode is NewPasswordMode.FromLink && handleExit != null -> BackHandler(enabled = true) { handleExit() }
        mode is NewPasswordMode.Voluntary && !embeddedInPane && handleExit != null ->
            BackHandler(enabled = true) { handleExit() }
    }

    DisposableEffect(controller) {
        onDispose {
            if (mode is NewPasswordMode.FromLink) {
                controller.invalidateEphemeralToken()
            }
        }
    }

    val oldText = controller.oldPasswordState.text.toString()
    val newText = controller.newPasswordState.text.toString()
    val confirmText = controller.confirmPasswordState.text.toString()

    LaunchedEffect(oldText) {
        if (oldText.isNotEmpty()) controller.onOldPasswordEdited()
    }
    LaunchedEffect(newText) {
        if (newText.isNotEmpty()) controller.onNewPasswordEdited()
    }
    LaunchedEffect(confirmText) {
        if (confirmText.isNotEmpty()) controller.onConfirmPasswordEdited()
    }

    val evaluation = remember(newText) { PasswordRules.evaluate(newText) }

    NewPasswordScreenContent(
        mode = mode,
        uiState = controller.uiState,
        oldPasswordState = controller.oldPasswordState,
        newPasswordState = controller.newPasswordState,
        confirmPasswordState = controller.confirmPasswordState,
        evaluation = evaluation,
        embeddedInPane = embeddedInPane,
        onBack = handleExit,
        onToggleOldVisibility = controller::toggleOldPasswordVisibility,
        onToggleNewVisibility = controller::toggleNewPasswordVisibility,
        onToggleConfirmVisibility = controller::toggleConfirmPasswordVisibility,
        onSubmit = controller::submit,
        onLogout = controller::logout,
        modifier = modifier,
    )
}

@Composable
fun NewPasswordScreenContent(
    mode: NewPasswordMode,
    uiState: NewPasswordUiState,
    oldPasswordState: TextFieldState,
    newPasswordState: TextFieldState,
    confirmPasswordState: TextFieldState,
    evaluation: PasswordRuleEvaluation,
    modifier: Modifier = Modifier,
    embeddedInPane: Boolean = false,
    onBack: (() -> Unit)? = null,
    onToggleOldVisibility: () -> Unit = {},
    onToggleNewVisibility: () -> Unit = {},
    onToggleConfirmVisibility: () -> Unit = {},
    onSubmit: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val colors = Thi360Theme.colors
    val busy = uiState.isSubmitting || uiState.isLoggingOut

    val screenTitle = when (mode) {
        is NewPasswordMode.FromLink, is NewPasswordMode.Forced -> stringResource(R.string.new_password_title)
        is NewPasswordMode.Voluntary -> stringResource(R.string.change_password_title)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (embeddedInPane) colors.bgSurface else colors.bgPage)
            .imePadding()
            .testTag(PasswordTestTags.SCREEN),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!embeddedInPane) {
            RecoveryTopBar(
                title = screenTitle,
                onNavigationClick = if (mode is NewPasswordMode.Forced) null else onBack,
                navigationContentDescription = when (mode) {
                    is NewPasswordMode.FromLink -> stringResource(R.string.recovery_close)
                    is NewPasswordMode.Voluntary -> stringResource(R.string.recovery_back)
                    is NewPasswordMode.Forced -> null
                },
                navigationTestTag = PasswordTestTags.CLOSE_BUTTON,
                useCloseIcon = mode is NewPasswordMode.FromLink,
                navigationEnabled = !busy,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = if (embeddedInPane) 640.dp else 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = if (embeddedInPane) 24.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (embeddedInPane) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = screenTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    val username = (mode as? NewPasswordMode.Voluntary)?.username
                    Text(
                        text = if (!username.isNullOrBlank()) {
                            stringResource(R.string.voluntary_password_description_with_username, username)
                        } else {
                            stringResource(R.string.voluntary_password_description_generic)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
            } else {
                when (mode) {
                    is NewPasswordMode.FromLink -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            RecoveryBanner(
                                message = stringResource(R.string.reset_link_banner),
                                isError = false,
                                isLinkInfo = true,
                                tag = PasswordTestTags.LINK_BANNER,
                            )
                            Text(
                                text = stringResource(R.string.reset_link_description),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    is NewPasswordMode.Forced -> {
                        val username = mode.username
                        val descriptionText = if (!username.isNullOrBlank()) {
                            val formatted = stringResource(R.string.forced_password_description_with_username, username)
                            highlightToken(formatted, username, colors.text)
                        } else {
                            buildAnnotatedString {
                                append(stringResource(R.string.forced_password_description_generic))
                            }
                        }
                        Text(
                            text = descriptionText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textSecondary,
                            modifier = Modifier.testTag(PasswordTestTags.FORCED_DESCRIPTION),
                        )
                    }

                    is NewPasswordMode.Voluntary -> {
                        val username = mode.username
                        Text(
                            text = if (!username.isNullOrBlank()) {
                                stringResource(R.string.voluntary_password_description_with_username, username)
                            } else {
                                stringResource(R.string.voluntary_password_description_generic)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            if (uiState.bannerError != null) {
                val messageRes = when (uiState.bannerError) {
                    PasswordBannerError.NETWORK -> R.string.recovery_network_error
                    PasswordBannerError.RESET_GENERIC -> R.string.reset_error_generic
                    PasswordBannerError.CHANGE_GENERIC -> R.string.change_password_error_generic
                }
                RecoveryBanner(
                    message = stringResource(messageRes),
                    isError = true,
                    tag = PasswordTestTags.ERROR_BANNER,
                )
            } else if (uiState.showVoluntarySuccess) {
                RecoveryBanner(
                    message = stringResource(R.string.change_password_success),
                    isError = false,
                    tag = PasswordTestTags.SUCCESS_BANNER,
                )
            }

            if (mode !is NewPasswordMode.FromLink) {
                val oldErrorText = when (uiState.oldPasswordError) {
                    OldPasswordError.REQUIRED -> stringResource(R.string.change_password_old_required)
                    OldPasswordError.WRONG -> stringResource(R.string.change_password_old_wrong)
                    null -> null
                }
                SecurePasswordField(
                    state = oldPasswordState,
                    label = stringResource(R.string.change_password_old_label),
                    visible = uiState.oldPasswordVisible,
                    enabled = !busy,
                    errorText = oldErrorText,
                    showContentDescription = stringResource(R.string.change_password_old_show),
                    hideContentDescription = stringResource(R.string.change_password_old_hide),
                    fieldTag = PasswordTestTags.OLD_PASSWORD_FIELD,
                    errorTag = PasswordTestTags.OLD_PASSWORD_ERROR,
                    toggleTag = PasswordTestTags.OLD_PASSWORD_TOGGLE,
                    imeAction = ImeAction.Next,
                    onToggleVisibility = onToggleOldVisibility,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val newErrorText = when (uiState.newPasswordError) {
                    PasswordValidationError.REQUIRED -> stringResource(R.string.change_password_new_required)
                    PasswordValidationError.WEAK -> stringResource(R.string.change_password_new_weak)
                    PasswordValidationError.SAME_AS_USERNAME -> stringResource(R.string.change_password_new_same_as_username)
                    null -> null
                }
                SecurePasswordField(
                    state = newPasswordState,
                    label = stringResource(R.string.change_password_new_label),
                    visible = uiState.newPasswordVisible,
                    enabled = !busy,
                    errorText = newErrorText,
                    showContentDescription = stringResource(R.string.change_password_new_show),
                    hideContentDescription = stringResource(R.string.change_password_new_hide),
                    fieldTag = PasswordTestTags.NEW_PASSWORD_FIELD,
                    errorTag = PasswordTestTags.NEW_PASSWORD_ERROR,
                    toggleTag = PasswordTestTags.NEW_PASSWORD_TOGGLE,
                    imeAction = ImeAction.Next,
                    onToggleVisibility = onToggleNewVisibility,
                )
                PasswordRulesChecklist(evaluation = evaluation)
            }

            val confirmErrorText = when (uiState.confirmPasswordError) {
                ConfirmPasswordError.REQUIRED -> stringResource(R.string.change_password_confirm_required)
                ConfirmPasswordError.MISMATCH -> stringResource(R.string.change_password_confirm_mismatch)
                null -> null
            }
            SecurePasswordField(
                state = confirmPasswordState,
                label = stringResource(R.string.change_password_confirm_label),
                visible = uiState.confirmPasswordVisible,
                enabled = !busy,
                errorText = confirmErrorText,
                showContentDescription = stringResource(R.string.change_password_confirm_show),
                hideContentDescription = stringResource(R.string.change_password_confirm_hide),
                fieldTag = PasswordTestTags.CONFIRM_PASSWORD_FIELD,
                errorTag = PasswordTestTags.CONFIRM_PASSWORD_ERROR,
                toggleTag = PasswordTestTags.CONFIRM_PASSWORD_TOGGLE,
                imeAction = ImeAction.Done,
                onToggleVisibility = onToggleConfirmVisibility,
                onKeyboardAction = onSubmit,
            )

            Button(
                onClick = onSubmit,
                enabled = !busy,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryFill,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(PasswordTestTags.SUBMIT_BUTTON),
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = colors.onPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .testTag(PasswordTestTags.SUBMIT_PROGRESS),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.recovery_submitting))
                } else {
                    Text(text = stringResource(R.string.change_password_submit))
                }
            }

            if (mode is NewPasswordMode.FromLink) {
                Text(
                    text = stringResource(R.string.reset_link_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }

            if (mode is NewPasswordMode.Forced) {
                Spacer(modifier = Modifier.weight(1f, fill = true))
                OutlinedButton(
                    onClick = onLogout,
                    enabled = !busy,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, colors.borderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.dangerText,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(PasswordTestTags.LOGOUT_BUTTON),
                ) {
                    Text(text = stringResource(R.string.forced_password_logout))
                }
            }
        }
    }
}

@Composable
fun InvalidResetLinkScreen(
    onRequestNewLink: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    BackHandler(enabled = true, onBack = onBackToLogin)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .testTag(PasswordTestTags.SCREEN_INVALID_LINK),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RecoveryTopBar(
            title = stringResource(R.string.new_password_title),
            onNavigationClick = onBackToLogin,
            navigationContentDescription = stringResource(R.string.recovery_close),
            navigationTestTag = PasswordTestTags.CLOSE_BUTTON,
            useCloseIcon = true,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(colors.dangerBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = RecoveryIcons.LinkOff,
                    contentDescription = null,
                    tint = colors.dangerText,
                    modifier = Modifier.size(44.dp),
                )
            }

            Text(
                text = stringResource(R.string.reset_invalid_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .semantics { heading() }
                    .testTag(PasswordTestTags.INVALID_LINK_TITLE),
            )

            Text(
                text = stringResource(R.string.reset_invalid_message),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = onRequestNewLink,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryFill,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(PasswordTestTags.INVALID_LINK_REQUEST_NEW),
            ) {
                Text(text = stringResource(R.string.reset_request_new))
            }

            TextButton(
                onClick = onBackToLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(PasswordTestTags.INVALID_LINK_BACK_TO_LOGIN),
            ) {
                Text(
                    text = stringResource(R.string.forgot_back_to_login),
                    color = colors.primaryText,
                )
            }
        }
    }
}

private fun highlightToken(
    formatted: String,
    token: String,
    emphasisColor: androidx.compose.ui.graphics.Color,
) = buildAnnotatedString {
    val index = formatted.indexOf(token)
    if (index >= 0) {
        append(formatted.substring(0, index))
        withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = emphasisColor)) {
            append(token)
        }
        append(formatted.substring(index + token.length))
    } else {
        append(formatted)
    }
}
