package vn.unicom.thi360.feature.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LoadStateView
import vn.unicom.thi360.core.ui.toLoadFailed
import vn.unicom.thi360.feature.login.LoginIcons
import vn.unicom.thi360.feature.password.RecoveryTopBar

internal enum class VerifyEmailAlertKind {
    CODE_EXPIRED,
    RESEND_FAILED,
    GENERIC,
}

internal sealed interface VerifyEmailCodeError {
    data object Required : VerifyEmailCodeError
    data class Invalid(val remainingAttempts: Int) : VerifyEmailCodeError
}

class VerifyEmailViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    var autoSendAttempted: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_AUTO_SEND_ATTEMPTED) ?: false
        set(value) {
            savedStateHandle[KEY_AUTO_SEND_ATTEMPTED] = value
        }

    var code by mutableStateOf(savedStateHandle.get<String>(KEY_CODE).orEmpty())
        private set

    var loadState by mutableStateOf<LoadState<UserProfile>>(LoadState.Loading)
    internal var alert by mutableStateOf<VerifyEmailAlertKind?>(null)
    internal var codeError by mutableStateOf<VerifyEmailCodeError?>(null)
    var codeResentNote by mutableStateOf(false)
    var cooldown by mutableStateOf(restoreCooldown())
        private set
    var hadActiveCooldown by mutableStateOf(savedStateHandle.get<Boolean>(KEY_HAD_ACTIVE_COOLDOWN) ?: false)
    var cooldownLiveMessage by mutableStateOf("")

    fun updateCode(value: String) {
        code = value
        savedStateHandle[KEY_CODE] = value
        codeError = null
        alert = null
    }

    fun updateCooldown(next: SendCooldown) {
        cooldown = next
        when (next) {
            SendCooldown.Ready -> {
                savedStateHandle.remove<String>(KEY_COOLDOWN_KIND)
                savedStateHandle.remove<Long>(KEY_COOLDOWN_UNTIL_MS)
            }
            is SendCooldown.Counting -> {
                savedStateHandle[KEY_COOLDOWN_KIND] = KIND_COUNTING
                savedStateHandle[KEY_COOLDOWN_UNTIL_MS] = next.until.toEpochMilli()
            }
            is SendCooldown.DailyLimit -> {
                savedStateHandle[KEY_COOLDOWN_KIND] = KIND_DAILY
                savedStateHandle[KEY_COOLDOWN_UNTIL_MS] = next.until.toEpochMilli()
            }
        }
    }

    fun markCooldownStarted() {
        hadActiveCooldown = true
        savedStateHandle[KEY_HAD_ACTIVE_COOLDOWN] = true
    }

    fun markCooldownEnded() {
        hadActiveCooldown = false
        savedStateHandle[KEY_HAD_ACTIVE_COOLDOWN] = false
    }

    private fun restoreCooldown(): SendCooldown {
        val kind = savedStateHandle.get<String>(KEY_COOLDOWN_KIND) ?: return SendCooldown.Ready
        val untilMs = savedStateHandle.get<Long>(KEY_COOLDOWN_UNTIL_MS) ?: return SendCooldown.Ready
        val until = Instant.ofEpochMilli(untilMs)
        return when (kind) {
            KIND_COUNTING -> SendCooldown.Counting(until)
            KIND_DAILY -> SendCooldown.DailyLimit(until)
            else -> SendCooldown.Ready
        }
    }

    companion object {
        const val KEY_AUTO_SEND_ATTEMPTED = "verify_email_auto_send_attempted"
        private const val KEY_CODE = "verify_email_code"
        private const val KEY_COOLDOWN_KIND = "verify_email_cooldown_kind"
        private const val KEY_COOLDOWN_UNTIL_MS = "verify_email_cooldown_until_ms"
        private const val KEY_HAD_ACTIVE_COOLDOWN = "verify_email_had_active_cooldown"
        private const val KIND_COUNTING = "counting"
        private const val KIND_DAILY = "daily"
    }
}

@Composable
fun VerifyEmailScreen(
    onBackToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    embeddedInPane: Boolean = false,
    dependencies: ProfileDependencies? = null,
    viewModel: VerifyEmailViewModel = viewModel(),
) {
    val context = LocalContext.current
    val deps = dependencies ?: rememberFallbackProfileDependencies(context)
    val scope = rememberCoroutineScope()

    var isSubmitting by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var cooldownTick by remember { mutableIntStateOf(0) }
    val loadInFlight = remember { AtomicBoolean(false) }
    val verifyInFlight = remember { AtomicBoolean(false) }
    val resendInFlight = remember { AtomicBoolean(false) }

    val cooldownStartTemplate = stringResource(R.string.account_verifyEmail_cooldownStart)
    val cooldownEndMessage = stringResource(R.string.account_verifyEmail_cooldownEnd)

    fun startCooldown(retryAfterSeconds: Long?) {
        val next = SendCooldown.start(retryAfterSeconds, deps.clock)
        viewModel.updateCooldown(next)
        viewModel.markCooldownStarted()
        cooldownTick++
        if (next is SendCooldown.Counting) {
            val remaining = next.remainingSeconds(deps.clock)
            val msg = cooldownStartTemplate.format(SendCooldown.formatClock(remaining))
            viewModel.cooldownLiveMessage = msg
            deps.onAnnounceCooldown?.invoke(msg)
        }
    }

    LaunchedEffect(viewModel.cooldown, deps.clock) {
        val current = viewModel.cooldown
        if (current == SendCooldown.Ready) return@LaunchedEffect
        while (current.remainingSeconds(deps.clock) > 0L) {
            withContext(Dispatchers.Default) {
                delay(VERIFY_COOLDOWN_POLL_MS)
            }
            cooldownTick++
        }
        viewModel.updateCooldown(SendCooldown.Ready)
        if (viewModel.hadActiveCooldown) {
            viewModel.markCooldownEnded()
            viewModel.cooldownLiveMessage = cooldownEndMessage
            deps.onAnnounceCooldown?.invoke(cooldownEndMessage)
        }
    }

    fun loadProfileAndAutoSend() {
        if (!loadInFlight.compareAndSet(false, true)) return
        viewModel.loadState = LoadState.Loading
        scope.launch {
            try {
                when (val result = deps.profileGateway.fetch()) {
                    is ApiResult.Success -> {
                        val profile = result.data
                        deps.profileStore.replace(profile)
                        viewModel.loadState = LoadState.Loaded(profile)
                        if (!profile.emailVerified &&
                            !profile.email.isNullOrBlank() &&
                            !viewModel.autoSendAttempted
                        ) {
                            viewModel.autoSendAttempted = true
                            when (val sendResult = deps.profileGateway.sendVerification()) {
                                is ProfileOperationResult.Success -> {
                                    startCooldown(SendCooldown.DEFAULT_SECONDS)
                                }

                                is ProfileOperationResult.Failure -> {
                                    when (val err = sendResult.error) {
                                        is ProfileError.RateLimited -> {
                                            startCooldown(err.retryAfterSeconds ?: SendCooldown.DEFAULT_SECONDS)
                                        }

                                        else -> {
                                            viewModel.alert = VerifyEmailAlertKind.RESEND_FAILED
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is ApiResult.HttpFailure -> {
                        viewModel.loadState = result.toLoadFailed().copy(canRetry = true)
                    }

                    is ApiResult.NetworkFailure -> {
                        viewModel.loadState = result.toLoadFailed(isOnline = false).copy(canRetry = true)
                    }
                }
            } finally {
                loadInFlight.set(false)
            }
        }
    }

    LaunchedEffect(deps.profileGateway) {
        if (viewModel.loadState !is LoadState.Loaded) {
            loadProfileAndAutoSend()
        }
    }

    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val errorLoadTitle = stringResource(R.string.account_verifyEmail_errorLoadTitle)
    val errorLoadDesc = stringResource(R.string.account_verifyEmail_errorLoad)
    val effectiveCooldown = if (cooldownTick >= 0) viewModel.cooldown.resolve(deps.clock) else viewModel.cooldown
    val cooldownRemaining = effectiveCooldown.remainingSeconds(deps.clock)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (embeddedInPane) colors.bgSurface else colors.bgPage)
            .testTag(VerifyEmailTestTags.SCREEN),
    ) {
        RecoveryTopBar(
            title = "",
            onNavigationClick = onBackToProfile,
            navigationContentDescription = stringResource(R.string.recovery_back),
            navigationTestTag = VerifyEmailTestTags.BACK_BUTTON,
        )

        LoadStateView(
            state = viewModel.loadState,
            onRetry = ::loadProfileAndAutoSend,
            emptyContent = {},
            customErrorTitle = { errorLoadTitle },
            customErrorDescription = { errorLoadDesc },
            modifier = Modifier.weight(1f),
        ) { profile ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (profile.emailVerified) {
                        Text(
                            text = stringResource(R.string.account_verifyEmail_title),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.text,
                            modifier = Modifier
                                .semantics { heading() }
                                .testTag(VerifyEmailTestTags.TITLE),
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = statusColors.successBg,
                            border = BorderStroke(1.dp, statusColors.success.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(VerifyEmailTestTags.ALREADY_VERIFIED_BANNER),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = LoginIcons.Check,
                                    contentDescription = null,
                                    tint = statusColors.success,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(R.string.account_verifyEmail_alreadyVerified),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = statusColors.success,
                                )
                            }
                        }
                        Button(
                            onClick = onBackToProfile,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryFill,
                                contentColor = colors.onPrimary,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag(VerifyEmailTestTags.CONTINUE_BUTTON),
                        ) {
                            Text(text = stringResource(R.string.account_verifyEmail_continue))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(colors.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = AccountIcons.Mail,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.account_verifyEmail_title),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.text,
                                modifier = Modifier
                                    .semantics { heading() }
                                    .testTag(VerifyEmailTestTags.TITLE),
                            )
                            val emailAddress = profile.email.orEmpty()
                            val sentToFull = stringResource(R.string.account_verifyEmail_sentTo, emailAddress)
                            val emailStart = sentToFull.indexOf(emailAddress)
                            Text(
                                text = buildAnnotatedString {
                                    if (emailAddress.isNotEmpty() && emailStart >= 0) {
                                        append(sentToFull.substring(0, emailStart))
                                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.text)) {
                                            append(emailAddress)
                                        }
                                        append(sentToFull.substring(emailStart + emailAddress.length))
                                    } else {
                                        append(sentToFull)
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                                modifier = Modifier.testTag(VerifyEmailTestTags.SENT_TO),
                            )
                        }

                        if (viewModel.alert != null) {
                            ProfileErrorAlertBanner(
                                message = when (viewModel.alert!!) {
                                    VerifyEmailAlertKind.CODE_EXPIRED ->
                                        stringResource(R.string.account_verifyEmail_alerts_codeExpired)
                                    VerifyEmailAlertKind.RESEND_FAILED ->
                                        stringResource(R.string.account_verifyEmail_alerts_resendFailed)
                                    VerifyEmailAlertKind.GENERIC ->
                                        stringResource(R.string.account_verifyEmail_alerts_generic)
                                },
                                testTag = VerifyEmailTestTags.ALERT,
                            )
                        }

                        if (effectiveCooldown is SendCooldown.DailyLimit) {
                            val formattedTime = SendCooldown.formatDailyLimit(effectiveCooldown.until)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = statusColors.warningBg,
                                border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                                    .testTag(VerifyEmailTestTags.DAILY_LIMIT_BANNER),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Icon(
                                        imageVector = AccountIcons.Clock,
                                        contentDescription = null,
                                        tint = statusColors.warning,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(18.dp),
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.account_verifyEmail_dailyLimit,
                                            formattedTime,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColors.warning,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = viewModel.code,
                                onValueChange = { viewModel.updateCode(it) },
                                singleLine = true,
                                enabled = !isSubmitting,
                                isError = viewModel.codeError != null,
                                label = { Text(stringResource(R.string.account_verifyEmail_codeLabel)) },
                                placeholder = { Text(stringResource(R.string.account_verifyEmail_codePlaceholder)) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(VerifyEmailTestTags.CODE_FIELD),
                            )
                            val activeCodeError = viewModel.codeError
                            if (activeCodeError != null) {
                                Text(
                                    text = when (activeCodeError) {
                                        VerifyEmailCodeError.Required ->
                                            stringResource(R.string.account_verifyEmail_codeRequired)
                                        is VerifyEmailCodeError.Invalid ->
                                            stringResource(
                                                R.string.account_verifyEmail_codeInvalid,
                                                activeCodeError.remainingAttempts,
                                            )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.dangerText,
                                    modifier = Modifier
                                        .padding(start = 16.dp)
                                        .semantics { liveRegion = LiveRegionMode.Polite }
                                        .testTag(VerifyEmailTestTags.CODE_ERROR),
                                )
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.alert = null
                                val enteredCode = viewModel.code
                                if (!PersonalInfoValidator.isValidVerificationCode(enteredCode)) {
                                    viewModel.codeError = VerifyEmailCodeError.Required
                                    return@Button
                                }
                                viewModel.codeError = null
                                if (!verifyInFlight.compareAndSet(false, true)) return@Button
                                isSubmitting = true
                                scope.launch {
                                    try {
                                        when (val result = deps.profileGateway.verify(enteredCode)) {
                                            is ProfileOperationResult.Success -> {
                                                val refreshed = deps.profileStore.refresh()
                                                if (refreshed !is ApiResult.Success) {
                                                    deps.profileStore.replace(profile.copy(emailVerified = true))
                                                }
                                                onBackToProfile()
                                            }

                                            is ProfileOperationResult.Failure -> {
                                                when (val err = result.error) {
                                                    is ProfileError.CodeInvalid -> {
                                                        viewModel.codeError = VerifyEmailCodeError.Invalid(
                                                            err.remainingAttempts ?: 0,
                                                        )
                                                    }
                                                    ProfileError.CodeExpired -> {
                                                        viewModel.alert = VerifyEmailAlertKind.CODE_EXPIRED
                                                    }
                                                    else -> {
                                                        viewModel.alert = VerifyEmailAlertKind.GENERIC
                                                    }
                                                }
                                            }
                                        }
                                    } finally {
                                        isSubmitting = false
                                        verifyInFlight.set(false)
                                    }
                                }
                            },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryFill,
                                contentColor = colors.onPrimary,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag(VerifyEmailTestTags.SUBMIT_BUTTON),
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = colors.onPrimary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(text = stringResource(R.string.account_verifyEmail_submit))
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.account_verifyEmail_notReceived),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                            TextButton(
                                onClick = {
                                    if (effectiveCooldown != SendCooldown.Ready) return@TextButton
                                    if (!resendInFlight.compareAndSet(false, true)) return@TextButton
                                    isResending = true
                                    viewModel.alert = null
                                    viewModel.codeResentNote = false
                                    scope.launch {
                                        try {
                                            when (val result = deps.profileGateway.sendVerification()) {
                                                is ProfileOperationResult.Success -> {
                                                    viewModel.codeResentNote = true
                                                    viewModel.codeError = null
                                                    startCooldown(SendCooldown.DEFAULT_SECONDS)
                                                }

                                                is ProfileOperationResult.Failure -> {
                                                    when (val err = result.error) {
                                                        is ProfileError.RateLimited -> {
                                                            startCooldown(
                                                                err.retryAfterSeconds ?: SendCooldown.DEFAULT_SECONDS,
                                                            )
                                                        }
                                                        else -> {
                                                            viewModel.alert = VerifyEmailAlertKind.RESEND_FAILED
                                                        }
                                                    }
                                                }
                                            }
                                        } finally {
                                            isResending = false
                                            resendInFlight.set(false)
                                        }
                                    }
                                },
                                enabled = effectiveCooldown == SendCooldown.Ready && !isResending,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag(VerifyEmailTestTags.RESEND_BUTTON),
                            ) {
                                Text(text = stringResource(R.string.account_verifyEmail_resend))
                            }

                            if (effectiveCooldown is SendCooldown.Counting && cooldownRemaining > 0L) {
                                Text(
                                    text = stringResource(
                                        R.string.account_verifyEmail_resendIn,
                                        SendCooldown.formatClock(cooldownRemaining),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                    modifier = Modifier.testTag(VerifyEmailTestTags.RESEND_COOLDOWN),
                                )
                            }

                            if (viewModel.codeResentNote) {
                                Text(
                                    text = stringResource(R.string.account_verifyEmail_codeResent),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColors.success,
                                    modifier = Modifier.testTag(VerifyEmailTestTags.CODE_RESENT_NOTE),
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .semantics {
                                    liveRegion = LiveRegionMode.Polite
                                    contentDescription = viewModel.cooldownLiveMessage
                                }
                                .testTag(VerifyEmailTestTags.COOLDOWN_LIVE_REGION),
                        )

                        OutlinedButton(
                            onClick = onBackToProfile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag(VerifyEmailTestTags.LATER_BUTTON),
                        ) {
                            Text(text = stringResource(R.string.account_verifyEmail_later))
                        }

                        VerifyEmailFootnote(
                            raw = stringResource(R.string.account_verifyEmail_footnote),
                            onProfileClick = onBackToProfile,
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VerifyEmailFootnote(
    raw: String,
    onProfileClick: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val openTag = "<profile>"
    val closeTag = "</profile>"
    val startIdx = raw.indexOf(openTag)
    val endIdx = raw.indexOf(closeTag)

    val annotated = buildAnnotatedString {
        if (startIdx >= 0 && endIdx > startIdx) {
            append(raw.substring(0, startIdx))
            val linkText = raw.substring(startIdx + openTag.length, endIdx)
            withLink(
                LinkAnnotation.Clickable(
                    tag = "profile",
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = colors.primaryText,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                    linkInteractionListener = { onProfileClick() },
                ),
            ) {
                append(linkText)
            }
            append(raw.substring(endIdx + closeTag.length))
        } else {
            append(raw)
        }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall,
        color = colors.textMuted,
        modifier = Modifier.testTag(VerifyEmailTestTags.FOOTNOTE),
    )
}

private const val VERIFY_COOLDOWN_POLL_MS = 250L
