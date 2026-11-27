package vn.unicom.thi360.feature.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LoadStateView
import vn.unicom.thi360.core.ui.toLoadFailed
import vn.unicom.thi360.feature.login.LoginIcons
import vn.unicom.thi360.feature.password.RecoveryTopBar

@Composable
fun DeleteAccountScreen(
    modifier: Modifier = Modifier,
    embeddedInPane: Boolean = false,
    onBack: () -> Unit = {},
    onOpenMyExams: (() -> Unit)? = null,
    onOpenOrders: (() -> Unit)? = null,
    dependencies: ProfileDependencies? = null,
) {
    val context = LocalContext.current
    val deps = dependencies ?: rememberFallbackProfileDependencies(context)
    val scope = rememberCoroutineScope()

    var deletionState by remember {
        mutableStateOf<AccountDeletionState>(AccountDeletionState.Checking(LoadState.Loading))
    }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    val checkInFlight = remember { AtomicBoolean(false) }
    val deleteInFlight = remember { AtomicBoolean(false) }

    fun runCheck() {
        if (!checkInFlight.compareAndSet(false, true)) return
        deletionState = AccountDeletionState.Checking(LoadState.Loading)
        scope.launch {
            try {
                when (val result = deps.accountDeletionGateway.check()) {
                    is ApiResult.Success -> {
                        val check = result.data
                        deletionState = if (!check.canDelete || check.blockers.isNotEmpty()) {
                            AccountDeletionState.Blocked(check.blockers)
                        } else {
                            AccountDeletionState.Ready(
                                unusedAttempts = check.unusedAttempts,
                                pendingGrading = check.pendingGrading,
                            )
                        }
                    }

                    is ApiResult.HttpFailure -> {
                        deletionState = AccountDeletionState.Checking(result.toLoadFailed().copy(canRetry = true))
                    }

                    is ApiResult.NetworkFailure -> {
                        deletionState = AccountDeletionState.Checking(
                            result.toLoadFailed(isOnline = false).copy(canRetry = true),
                        )
                    }
                }
            } finally {
                checkInFlight.set(false)
            }
        }
    }

    LaunchedEffect(deps.accountDeletionGateway) {
        runCheck()
    }

    val resolvedOpenMyExams = onOpenMyExams ?: deps.onOpenMyExams ?: {}
    val resolvedOpenOrders = onOpenOrders ?: deps.onOpenOrders ?: {}

    DeleteAccountScreenContent(
        state = deletionState,
        embeddedInPane = embeddedInPane,
        password = password,
        passwordVisible = passwordVisible,
        acknowledged = acknowledged,
        onBack = onBack,
        onRetryCheck = ::runCheck,
        onOpenMyExams = resolvedOpenMyExams,
        onOpenOrders = resolvedOpenOrders,
        onPasswordChanged = { next ->
            password = next
            val current = deletionState
            if (current is AccountDeletionState.Failed) {
                deletionState = AccountDeletionState.Ready(
                    unusedAttempts = current.unusedAttempts,
                    pendingGrading = current.pendingGrading,
                )
            }
        },
        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
        onAcknowledgeChanged = { acknowledged = it },
        onRequestConfirm = {
            val current = deletionState
            when (current) {
                is AccountDeletionState.Ready -> {
                    if (password.isNotEmpty() && acknowledged) {
                        deletionState = AccountDeletionState.Confirming(
                            unusedAttempts = current.unusedAttempts,
                            pendingGrading = current.pendingGrading,
                        )
                    }
                }

                is AccountDeletionState.Failed -> {
                    if (password.isNotEmpty() && acknowledged) {
                        deletionState = AccountDeletionState.Confirming(
                            unusedAttempts = current.unusedAttempts,
                            pendingGrading = current.pendingGrading,
                        )
                    }
                }

                else -> Unit
            }
        },
        onDismissConfirm = {
            val current = deletionState
            if (current is AccountDeletionState.Confirming) {
                deletionState = AccountDeletionState.Ready(
                    unusedAttempts = current.unusedAttempts,
                    pendingGrading = current.pendingGrading,
                )
            }
        },
        onConfirmDelete = {
            val current = deletionState
            val unusedAttempts: Int
            val pendingGrading: Boolean
            when (current) {
                is AccountDeletionState.Confirming -> {
                    unusedAttempts = current.unusedAttempts
                    pendingGrading = current.pendingGrading
                }
                else -> return@DeleteAccountScreenContent
            }
            if (!deleteInFlight.compareAndSet(false, true)) return@DeleteAccountScreenContent
            deletionState = AccountDeletionState.Deleting(
                unusedAttempts = unusedAttempts,
                pendingGrading = pendingGrading,
            )
            val submittedPassword = password
            scope.launch {
                try {
                    when (val outcome = deps.accountDeletionGateway.delete(submittedPassword)) {
                        AccountDeletionOutcome.Deleted -> {
                            deletionState = AccountDeletionState.Deleted
                            password = ""
                            deps.sessionManager.logout(SignOutReason.AccountDeleted)
                        }

                        is AccountDeletionOutcome.Blocked -> {
                            deletionState = AccountDeletionState.Blocked(outcome.blockers)
                        }

                        is AccountDeletionOutcome.Failed -> {
                            if (outcome.kind == AccountDeletionFailureKind.WrongPassword) {
                                password = ""
                            }
                            deletionState = AccountDeletionState.Failed(
                                kind = outcome.kind,
                                unusedAttempts = unusedAttempts,
                                pendingGrading = pendingGrading,
                            )
                        }
                    }
                } finally {
                    deleteInFlight.set(false)
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
internal fun DeleteAccountScreenContent(
    state: AccountDeletionState,
    embeddedInPane: Boolean,
    password: String,
    passwordVisible: Boolean,
    acknowledged: Boolean,
    onBack: () -> Unit,
    onRetryCheck: () -> Unit,
    onOpenMyExams: () -> Unit,
    onOpenOrders: () -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onAcknowledgeChanged: (Boolean) -> Unit,
    onRequestConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (embeddedInPane) colors.bgSurface else colors.bgPage)
            .testTag(DeleteAccountTestTags.SCREEN),
    ) {
        if (!embeddedInPane) {
            RecoveryTopBar(
                title = stringResource(R.string.account_deletion_title),
                onNavigationClick = onBack,
                navigationContentDescription = stringResource(R.string.recovery_back),
                navigationTestTag = DeleteAccountTestTags.BACK_BUTTON,
            )
        }

        when (state) {
            is AccountDeletionState.Checking -> {
                LoadStateView(
                    state = state.loadState,
                    onRetry = onRetryCheck,
                    emptyContent = {},
                    modifier = Modifier.weight(1f),
                ) {}
            }

            AccountDeletionState.Deleted -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.primaryFill)
                }
            }

            is AccountDeletionState.Blocked -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = if (embeddedInPane) 24.dp else 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (embeddedInPane) {
                            Text(
                                text = stringResource(R.string.account_deletion_title),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.text,
                                modifier = Modifier
                                    .semantics { heading() }
                                    .testTag(DeleteAccountTestTags.TITLE),
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.dangerBg,
                            border = BorderStroke(1.dp, colors.dangerText.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(DeleteAccountTestTags.BLOCKED_CARD),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (DeletionBlocker.ATTEMPT_IN_PROGRESS in state.blockers) {
                                    Text(
                                        text = stringResource(R.string.account_deletion_blockedAttempt),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.dangerText,
                                        modifier = Modifier.testTag(DeleteAccountTestTags.BLOCKED_ATTEMPT_MESSAGE),
                                    )
                                    Button(
                                        onClick = onOpenMyExams,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.primaryFill,
                                            contentColor = colors.onPrimary,
                                        ),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag(DeleteAccountTestTags.OPEN_MY_EXAMS_BUTTON),
                                    ) {
                                        Text(text = stringResource(R.string.account_deletion_openMyExams))
                                    }
                                }

                                if (DeletionBlocker.ORDER_BEING_CHECKED in state.blockers) {
                                    Text(
                                        text = stringResource(R.string.account_deletion_blockedOrder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.dangerText,
                                        modifier = Modifier.testTag(DeleteAccountTestTags.BLOCKED_ORDER_MESSAGE),
                                    )
                                }

                                if (DeletionBlocker.ORDER_PENDING_PAYMENT in state.blockers) {
                                    Text(
                                        text = stringResource(R.string.account_deletion_blockedPendingPayment),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.dangerText,
                                        modifier = Modifier.testTag(DeleteAccountTestTags.BLOCKED_PENDING_PAYMENT_MESSAGE),
                                    )
                                }

                                if (DeletionBlocker.ORDER_BEING_CHECKED in state.blockers ||
                                    DeletionBlocker.ORDER_PENDING_PAYMENT in state.blockers
                                ) {
                                    OutlinedButton(
                                        onClick = onOpenOrders,
                                        border = BorderStroke(1.dp, colors.borderStrong),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag(DeleteAccountTestTags.OPEN_MY_ORDERS_BUTTON),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.account_deletion_openMyOrders),
                                            color = colors.primaryText,
                                        )
                                    }
                                }
                            }
                        }

                        DeletionExplanationCard()
                    }
                }
            }

            is AccountDeletionState.Ready,
            is AccountDeletionState.Confirming,
            is AccountDeletionState.Deleting,
            is AccountDeletionState.Failed,
            -> {
                val unusedAttempts = when (state) {
                    is AccountDeletionState.Ready -> state.unusedAttempts
                    is AccountDeletionState.Confirming -> state.unusedAttempts
                    is AccountDeletionState.Deleting -> state.unusedAttempts
                    is AccountDeletionState.Failed -> state.unusedAttempts
                }
                val pendingGrading = when (state) {
                    is AccountDeletionState.Ready -> state.pendingGrading
                    is AccountDeletionState.Confirming -> state.pendingGrading
                    is AccountDeletionState.Deleting -> state.pendingGrading
                    is AccountDeletionState.Failed -> state.pendingGrading
                }
                val isDeleting = state is AccountDeletionState.Deleting
                val failureKind = (state as? AccountDeletionState.Failed)?.kind
                val isWrongPassword = failureKind == AccountDeletionFailureKind.WrongPassword
                val formErrorText = when (failureKind) {
                    AccountDeletionFailureKind.Throttled -> stringResource(R.string.account_deletion_throttled)
                    AccountDeletionFailureKind.Network,
                    AccountDeletionFailureKind.Server,
                    -> stringResource(R.string.account_deletion_failed)
                    AccountDeletionFailureKind.WrongPassword,
                    null,
                    -> null
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = if (embeddedInPane) 24.dp else 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (embeddedInPane) {
                            Text(
                                text = stringResource(R.string.account_deletion_title),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.text,
                                modifier = Modifier
                                    .semantics { heading() }
                                    .testTag(DeleteAccountTestTags.TITLE),
                            )
                        }

                        DeletionExplanationCard()

                        if (unusedAttempts > 0) {
                            val warningTemplate = stringResource(R.string.account_deletion_unusedAttemptsWarning)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = statusColors.warningBg,
                                border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics(mergeDescendants = true) {}
                                    .testTag(DeleteAccountTestTags.UNUSED_ATTEMPTS_WARNING),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Icon(
                                        imageVector = AccountIcons.Warning,
                                        contentDescription = null,
                                        tint = statusColors.warning,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(20.dp),
                                    )
                                    Text(
                                        text = warningTemplate.replace("{n}", unusedAttempts.toString()),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = statusColors.warning,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        if (pendingGrading) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = statusColors.warningBg,
                                border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics(mergeDescendants = true) {}
                                    .testTag(DeleteAccountTestTags.PENDING_GRADING_WARNING),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Icon(
                                        imageVector = AccountIcons.Warning,
                                        contentDescription = null,
                                        tint = statusColors.warning,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(20.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.account_deletion_pendingGradingWarning),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = statusColors.warning,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        if (formErrorText != null) {
                            ProfileErrorAlertBanner(
                                message = formErrorText,
                                testTag = DeleteAccountTestTags.FORM_ERROR_BANNER,
                            )
                        }

                        val toggleDesc = stringResource(
                            if (passwordVisible) {
                                R.string.account_profile_email_passwordHide
                            } else {
                                R.string.account_profile_email_passwordShow
                            },
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = onPasswordChanged,
                                singleLine = true,
                                enabled = !isDeleting,
                                isError = isWrongPassword,
                                label = { Text(stringResource(R.string.account_deletion_passwordLabel)) },
                                visualTransformation = if (passwordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = onTogglePasswordVisibility,
                                        modifier = Modifier
                                            .semantics { contentDescription = toggleDesc }
                                            .testTag(DeleteAccountTestTags.PASSWORD_TOGGLE),
                                    ) {
                                        Icon(
                                            imageVector = if (passwordVisible) LoginIcons.VisibilityOff else LoginIcons.Visibility,
                                            contentDescription = null,
                                            tint = colors.textMuted,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(DeleteAccountTestTags.PASSWORD_FIELD),
                            )
                            if (isWrongPassword) {
                                Text(
                                    text = stringResource(R.string.account_deletion_wrongPassword),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.dangerText,
                                    modifier = Modifier
                                        .padding(start = 16.dp)
                                        .testTag(DeleteAccountTestTags.PASSWORD_ERROR),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(
                                    enabled = !isDeleting,
                                    role = Role.Checkbox,
                                    onClick = { onAcknowledgeChanged(!acknowledged) },
                                )
                                .padding(vertical = 4.dp)
                                .testTag(DeleteAccountTestTags.ACKNOWLEDGE_ROW),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = acknowledged,
                                onCheckedChange = null,
                                enabled = !isDeleting,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = statusColors.dangerSolid,
                                    checkmarkColor = statusColors.onDanger,
                                ),
                                modifier = Modifier.testTag(DeleteAccountTestTags.ACKNOWLEDGE_CHECKBOX),
                            )
                            Text(
                                text = stringResource(R.string.account_deletion_acknowledge),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        val canSubmit = password.isNotEmpty() && acknowledged && !isDeleting
                        Button(
                            onClick = onRequestConfirm,
                            enabled = canSubmit,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = statusColors.dangerSolid,
                                contentColor = statusColors.onDanger,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag(DeleteAccountTestTags.SUBMIT_BUTTON),
                        ) {
                            if (isDeleting) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = statusColors.onDanger,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = AccountIcons.Trash,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.account_deletion_submit))
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                if (state is AccountDeletionState.Confirming) {
                    AlertDialog(
                        onDismissRequest = onDismissConfirm,
                        containerColor = colors.bgSurface,
                        titleContentColor = colors.text,
                        textContentColor = colors.textSecondary,
                        modifier = Modifier.testTag(DeleteAccountTestTags.CONFIRM_DIALOG),
                        title = {
                            Text(text = stringResource(R.string.account_deletion_confirmTitle))
                        },
                        confirmButton = {
                            TextButton(
                                onClick = onConfirmDelete,
                                modifier = Modifier.testTag(DeleteAccountTestTags.CONFIRM_ACTION_BUTTON),
                            ) {
                                Text(
                                    text = stringResource(R.string.account_deletion_confirmAction),
                                    color = colors.dangerText,
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = onDismissConfirm,
                                modifier = Modifier.testTag(DeleteAccountTestTags.CONFIRM_CANCEL_BUTTON),
                            ) {
                                Text(
                                    text = stringResource(R.string.account_deletion_confirmCancel),
                                    color = colors.primaryText,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeletionExplanationCard() {
    val colors = Thi360Theme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.account_deletion_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier
                .padding(16.dp)
                .testTag(DeleteAccountTestTags.EXPLANATION),
        )
    }
}
