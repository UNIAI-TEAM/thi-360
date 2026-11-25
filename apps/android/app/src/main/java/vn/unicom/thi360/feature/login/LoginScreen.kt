package vn.unicom.thi360.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.unicom.thi360.R
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.session.LoginErrorKind
import vn.unicom.thi360.core.session.LoginOutcome
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme

private const val TABLET_LOGIN_BREAKPOINT_DP = 840
private const val WIDE_BRAND_PANEL_BREAKPOINT_DP = 1100

data class LoginUiState(
    val identifier: String = "",
    val passwordVisible: Boolean = false,
    val identifierRequiredError: Boolean = false,
    val passwordRequiredError: Boolean = false,
    val errorKind: LoginErrorKind? = null,
    val isSubmitting: Boolean = false,
)

@Stable
class LoginController(
    private val sessionManager: SessionManager,
    private val scope: CoroutineScope,
    initialIdentifier: String = "",
) {
    private val submitInFlight = AtomicBoolean(false)

    val passwordState = TextFieldState()

    var uiState by mutableStateOf(LoginUiState(identifier = initialIdentifier))
        private set

    fun onIdentifierChanged(value: String) {
        uiState = uiState.copy(
            identifier = value,
            identifierRequiredError = false,
        )
    }

    fun clearPasswordRequiredError() {
        if (uiState.passwordRequiredError) {
            uiState = uiState.copy(passwordRequiredError = false)
        }
    }

    fun onTogglePasswordVisibility() {
        uiState = uiState.copy(passwordVisible = !uiState.passwordVisible)
    }

    fun submit() {
        val current = uiState
        val submittedPassword = passwordState.text.toString()
        val missingIdentifier = current.identifier.trim().isEmpty()
        val missingPassword = submittedPassword.isEmpty()
        if (missingIdentifier || missingPassword) {
            uiState = current.copy(
                identifierRequiredError = missingIdentifier,
                passwordRequiredError = missingPassword,
                errorKind = null,
            )
            return
        }
        if (!submitInFlight.compareAndSet(false, true)) {
            return
        }

        val submittedIdentifier = current.identifier
        uiState = current.copy(
            identifierRequiredError = false,
            passwordRequiredError = false,
            errorKind = null,
            isSubmitting = true,
        )

        scope.launch {
            try {
                when (val outcome = sessionManager.login(submittedIdentifier, submittedPassword)) {
                    LoginOutcome.Authenticated -> {
                        passwordState.clearText()
                        uiState = uiState.copy(
                            passwordVisible = false,
                            isSubmitting = false,
                        )
                    }

                    is LoginOutcome.StudentOnly -> {
                        passwordState.clearText()
                        uiState = uiState.copy(
                            passwordVisible = false,
                            isSubmitting = false,
                        )
                    }

                    is LoginOutcome.Error -> {
                        passwordState.clearText()
                        uiState = uiState.copy(
                            identifier = submittedIdentifier,
                            passwordVisible = false,
                            errorKind = outcome.kind,
                            isSubmitting = false,
                        )
                    }
                }
            } finally {
                submitInFlight.set(false)
            }
        }
    }
}

@Composable
fun LoginScreen(
    sessionManager: SessionManager,
    signOutReason: SignOutReason,
    onSelectLocale: (AppLocale) -> Unit,
    onForgotPasswordClick: () -> Unit,
    onRegisterClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialIdentifier: String = "",
) {
    var savedIdentifier by rememberSaveable(initialIdentifier) {
        mutableStateOf(initialIdentifier)
    }
    val scope = rememberCoroutineScope()
    val controller = remember(sessionManager, initialIdentifier) {
        LoginController(
            sessionManager = sessionManager,
            scope = scope,
            initialIdentifier = savedIdentifier,
        )
    }
    LaunchedEffect(controller.passwordState.text) {
        if (controller.passwordState.text.isNotEmpty()) {
            controller.clearPasswordRequiredError()
        }
    }
    LoginScreenContent(
        uiState = controller.uiState,
        passwordState = controller.passwordState,
        signOutReason = signOutReason,
        onIdentifierChanged = { value ->
            savedIdentifier = value
            controller.onIdentifierChanged(value)
        },
        onTogglePasswordVisibility = controller::onTogglePasswordVisibility,
        onSubmit = controller::submit,
        onSelectLocale = onSelectLocale,
        onForgotPasswordClick = onForgotPasswordClick,
        onRegisterClick = onRegisterClick,
        modifier = modifier,
    )
}

@Composable
fun LoginScreenContent(
    uiState: LoginUiState,
    signOutReason: SignOutReason,
    onIdentifierChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onSelectLocale: (AppLocale) -> Unit,
    onForgotPasswordClick: () -> Unit,
    onRegisterClick: () -> Unit,
    modifier: Modifier = Modifier,
    passwordState: TextFieldState = rememberTextFieldState(),
) {
    val currentLocale = LocalAppLocale.current
    val nextLocale = if (currentLocale == AppLocale.VI) AppLocale.EN else AppLocale.VI

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Thi360Theme.colors.bgPage)
            .imePadding(),
    ) {
        val widthDp = maxWidth.value.toInt()
        if (widthDp >= TABLET_LOGIN_BREAKPOINT_DP) {
            TabletLoginLayout(
                availableWidthDp = widthDp,
                uiState = uiState,
                passwordState = passwordState,
                signOutReason = signOutReason,
                onIdentifierChanged = onIdentifierChanged,
                onTogglePasswordVisibility = onTogglePasswordVisibility,
                onSubmit = onSubmit,
                onToggleLocale = { onSelectLocale(nextLocale) },
                onForgotPasswordClick = onForgotPasswordClick,
                onRegisterClick = onRegisterClick,
            )
        } else {
            PhoneLoginLayout(
                uiState = uiState,
                passwordState = passwordState,
                signOutReason = signOutReason,
                onIdentifierChanged = onIdentifierChanged,
                onTogglePasswordVisibility = onTogglePasswordVisibility,
                onSubmit = onSubmit,
                onToggleLocale = { onSelectLocale(nextLocale) },
                onForgotPasswordClick = onForgotPasswordClick,
                onRegisterClick = onRegisterClick,
            )
        }
    }
}

@Composable
private fun PhoneLoginLayout(
    uiState: LoginUiState,
    passwordState: TextFieldState,
    signOutReason: SignOutReason,
    onIdentifierChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onToggleLocale: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onRegisterClick: () -> Unit,
) {
    val colors = Thi360Theme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(LoginTestTags.SCREEN_PHONE),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LanguageToggleIconButton(onClick = onToggleLocale)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LoginBrandHeader()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.login_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.login_headline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
            }

            LoginFormSection(
                uiState = uiState,
                passwordState = passwordState,
                signOutReason = signOutReason,
                onIdentifierChanged = onIdentifierChanged,
                onTogglePasswordVisibility = onTogglePasswordVisibility,
                onSubmit = onSubmit,
                onForgotPasswordClick = onForgotPasswordClick,
            )

            Spacer(modifier = Modifier.weight(1f, fill = true))

            RegisterPromptRow(onRegisterClick = onRegisterClick)
        }
    }
}

@Composable
private fun TabletLoginLayout(
    availableWidthDp: Int,
    uiState: LoginUiState,
    passwordState: TextFieldState,
    signOutReason: SignOutReason,
    onIdentifierChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onToggleLocale: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onRegisterClick: () -> Unit,
) {
    val colors = Thi360Theme.colors

    Row(
        modifier = Modifier
            .fillMaxSize()
            .testTag(LoginTestTags.SCREEN_TABLET),
    ) {
        val brandPanelModifier = if (availableWidthDp >= WIDE_BRAND_PANEL_BREAKPOINT_DP) {
            Modifier
                .width(560.dp)
                .fillMaxHeight()
        } else {
            Modifier
                .weight(0.44f)
                .fillMaxHeight()
        }

        Column(
            modifier = brandPanelModifier
                .background(colors.primaryContainerStrong)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 56.dp, vertical = 64.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
        ) {
            LoginBrandHeader()
            Text(
                text = stringResource(R.string.login_headline),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.primaryText,
            )
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TabletFeatureRow(stringResource(R.string.login_feature_1))
                TabletFeatureRow(stringResource(R.string.login_feature_2))
                TabletFeatureRow(stringResource(R.string.login_feature_3))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                LanguageToggleIconButton(onClick = onToggleLocale)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    Text(
                        text = stringResource(R.string.login_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    LoginFormSection(
                        uiState = uiState,
                        passwordState = passwordState,
                        signOutReason = signOutReason,
                        onIdentifierChanged = onIdentifierChanged,
                        onTogglePasswordVisibility = onTogglePasswordVisibility,
                        onSubmit = onSubmit,
                        onForgotPasswordClick = onForgotPasswordClick,
                    )
                    RegisterPromptRow(onRegisterClick = onRegisterClick)
                }
            }
        }
    }
}

@Composable
private fun LoginFormSection(
    uiState: LoginUiState,
    passwordState: TextFieldState,
    signOutReason: SignOutReason,
    onIdentifierChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onForgotPasswordClick: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.primaryFill,
        unfocusedBorderColor = colors.borderStrong,
        errorBorderColor = colors.dangerText,
        focusedLabelColor = colors.primaryText,
        unfocusedLabelColor = colors.textMuted,
        errorLabelColor = colors.dangerText,
        focusedTextColor = colors.text,
        unfocusedTextColor = colors.text,
        cursorColor = colors.primaryFill,
        focusedContainerColor = colors.bgSurface,
        unfocusedContainerColor = colors.bgSurface,
        disabledContainerColor = colors.bgSubtle,
    )

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (uiState.errorKind != null) {
            StatusBanner(
                message = stringResource(uiState.errorKind.messageRes),
                isError = true,
                tag = LoginTestTags.ERROR_BANNER,
            )
        } else {
            val noticeRes = when (signOutReason) {
                SignOutReason.Expired -> R.string.login_notice_session_expired
                SignOutReason.PasswordReset -> R.string.login_notice_password_reset
                SignOutReason.None, is SignOutReason.StudentOnly -> null
            }
            if (noticeRes != null) {
                StatusBanner(
                    message = stringResource(noticeRes),
                    isError = false,
                    tag = LoginTestTags.NOTICE_BANNER,
                )
            }
        }

        OutlinedTextField(
            value = uiState.identifier,
            onValueChange = onIdentifierChanged,
            label = { Text(stringResource(R.string.login_identifier_label)) },
            singleLine = true,
            enabled = !uiState.isSubmitting,
            isError = uiState.identifierRequiredError,
            supportingText = if (uiState.identifierRequiredError) {
                {
                    Text(
                        text = stringResource(R.string.login_identifier_required),
                        color = colors.dangerText,
                        modifier = Modifier.testTag(LoginTestTags.IDENTIFIER_ERROR),
                    )
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            colors = fieldColors,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LoginTestTags.IDENTIFIER_FIELD),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedSecureTextField(
                state = passwordState,
                label = { Text(stringResource(R.string.login_password_label)) },
                enabled = !uiState.isSubmitting,
                isError = uiState.passwordRequiredError,
                textObfuscationMode = if (uiState.passwordVisible) {
                    TextObfuscationMode.Visible
                } else {
                    TextObfuscationMode.Hidden
                },
                trailingIcon = {
                    IconButton(
                        onClick = onTogglePasswordVisibility,
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(LoginTestTags.PASSWORD_VISIBILITY_TOGGLE),
                    ) {
                        Icon(
                            imageVector = if (uiState.passwordVisible) {
                                LoginIcons.VisibilityOff
                            } else {
                                LoginIcons.Visibility
                            },
                            contentDescription = stringResource(
                                if (uiState.passwordVisible) {
                                    R.string.login_hide_password
                                } else {
                                    R.string.login_show_password
                                },
                            ),
                            tint = colors.textMuted,
                        )
                    }
                },
                supportingText = if (uiState.passwordRequiredError) {
                    {
                        Text(
                            text = stringResource(R.string.login_password_required),
                            color = colors.dangerText,
                            modifier = Modifier.testTag(LoginTestTags.PASSWORD_ERROR),
                        )
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                onKeyboardAction = KeyboardActionHandler { onSubmit() },
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LoginTestTags.PASSWORD_FIELD),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onForgotPasswordClick,
                    enabled = !uiState.isSubmitting,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(LoginTestTags.FORGOT_PASSWORD_LINK),
                ) {
                    Text(
                        text = stringResource(R.string.login_forgot_link),
                        color = colors.primaryText,
                    )
                }
            }
        }

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
                .testTag(LoginTestTags.SUBMIT_BUTTON),
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = colors.onPrimary,
                    modifier = Modifier
                        .size(18.dp)
                        .testTag(LoginTestTags.SUBMIT_PROGRESS),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.login_submitting))
            } else {
                Text(text = stringResource(R.string.login_submit))
            }
        }
    }
}

@Composable
private fun StatusBanner(
    message: String,
    isError: Boolean,
    tag: String,
) {
    val colors = Thi360Theme.colors
    val containerColor = if (isError) colors.dangerBg else colors.primaryContainer
    val contentColor = if (isError) colors.dangerText else colors.primaryText
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag(tag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = LoginIcons.AlertCircle,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun LoginBrandHeader() {
    val colors = Thi360Theme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.primaryFill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_thi360_logo),
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.text,
        )
    }
}

@Composable
private fun TabletFeatureRow(text: String) {
    val colors = Thi360Theme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = LoginIcons.Check,
            contentDescription = null,
            tint = colors.primaryText,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.primaryText,
        )
    }
}

@Composable
internal fun LanguageToggleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .testTag(LoginTestTags.LANGUAGE_TOGGLE),
    ) {
        Icon(
            imageVector = LoginIcons.Globe,
            contentDescription = stringResource(R.string.login_switch_language),
            tint = Thi360Theme.colors.textMuted,
        )
    }
}

@Composable
private fun RegisterPromptRow(onRegisterClick: () -> Unit) {
    val colors = Thi360Theme.colors
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.login_no_account),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
        TextButton(
            onClick = onRegisterClick,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(LoginTestTags.REGISTER_LINK),
        ) {
            Text(
                text = stringResource(R.string.login_register_link),
                color = colors.primaryText,
            )
        }
    }
}
