package vn.unicom.thi360.feature.account

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.unicom.thi360.R
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LoadStateView
import vn.unicom.thi360.feature.login.LoginIcons
import vn.unicom.thi360.feature.password.RecoveryTopBar

private enum class AvatarErrorKind {
    TOO_LARGE,
    DIMENSIONS_EXCEEDED,
    INVALID_IMAGE,
    GENERIC,
}

private enum class ChangeEmailAlertKind {
    RATE_LIMITED,
    GENERIC,
}

private enum class ChangeEmailFieldError {
    NEW_EMAIL_REQUIRED,
    NEW_EMAIL_INVALID,
    NEW_EMAIL_TAKEN,
    PASSWORD_REQUIRED,
    WRONG_PASSWORD,
}

private enum class ConfirmEmailAlertKind {
    EXPIRED,
    EMAIL_TAKEN,
    SYNC_FAILED,
    GENERIC,
}

private sealed interface ConfirmCodeError {
    data object Required : ConfirmCodeError
    data class Invalid(val remainingAttempts: Int) : ConfirmCodeError
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    embeddedInPane: Boolean = false,
    onBack: () -> Unit = {},
    onOpenVerifyEmail: () -> Unit = {},
    dependencies: ProfileDependencies? = null,
) {
    val context = LocalContext.current
    val deps = dependencies ?: rememberFallbackProfileDependencies(context)
    val cameraAccess = deps.cameraAccess ?: rememberAndroidCameraAccess(context)
    val avatarPreparer = deps.avatarPreparer ?: remember(context) {
        AndroidAvatarImagePreparer(context.contentResolver)
    }

    val profileState by deps.profileStore.state.collectAsStateWithLifecycle()
    val refreshError by deps.profileStore.refreshError.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(deps.profileStore) {
        deps.profileStore.refresh()
    }

    val loadedProfile = (profileState as? LoadState.Loaded)?.value
    LaunchedEffect(loadedProfile) {
        if (loadedProfile != null) {
            deps.draftStore.syncWithProfile(loadedProfile)
        }
    }

    BackHandler(enabled = !embeddedInPane) {
        deps.draftStore.clear()
        onBack()
    }

    var avatarBusy by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<AvatarErrorKind?>(null) }
    var showAvatarSheet by rememberSaveable { mutableStateOf(false) }
    var showCameraDeniedBanner by rememberSaveable { mutableStateOf(false) }
    val avatarOpInFlight = remember { AtomicBoolean(false) }
    var pendingCaptureTarget by remember { mutableStateOf<CameraTempTarget?>(null) }

    var isSavingInfo by remember { mutableStateOf(false) }
    var fullNameError by remember { mutableStateOf<FullNameValidationError?>(null) }
    var phoneError by remember { mutableStateOf<PhoneValidationError?>(null) }
    var infoGenericError by remember { mutableStateOf(false) }
    val infoSaveInFlight = remember { AtomicBoolean(false) }

    var showChangePasswordText by rememberSaveable { mutableStateOf(false) }
    var isSendingChange by remember { mutableStateOf(false) }
    var changeEmailAlert by remember { mutableStateOf<ChangeEmailAlertKind?>(null) }
    var newEmailError by remember { mutableStateOf<ChangeEmailFieldError?>(null) }
    var changePasswordError by remember { mutableStateOf<ChangeEmailFieldError?>(null) }
    val sendChangeInFlight = remember { AtomicBoolean(false) }

    var isConfirmingEmail by remember { mutableStateOf(false) }
    var confirmAlert by remember { mutableStateOf<ConfirmEmailAlertKind?>(null) }
    var confirmCodeError by remember { mutableStateOf<ConfirmCodeError?>(null) }
    val confirmInFlight = remember { AtomicBoolean(false) }

    var cooldown by remember { mutableStateOf<SendCooldown>(SendCooldown.Ready) }
    var cooldownTick by remember { mutableIntStateOf(0) }
    var cooldownLiveMessage by remember { mutableStateOf("") }
    var hadActiveCooldown by remember { mutableStateOf(false) }

    val cooldownStartTemplate = stringResource(R.string.account_profile_email_cooldownStart)
    val cooldownEndMessage = stringResource(R.string.account_profile_email_cooldownEnd)

    LaunchedEffect(cooldown, deps.clock) {
        if (cooldown == SendCooldown.Ready) return@LaunchedEffect
        while (cooldown.remainingSeconds(deps.clock) > 0L) {
            withContext(Dispatchers.Default) {
                delay(COOLDOWN_POLL_INTERVAL_MS)
            }
            cooldownTick++
        }
        cooldown = SendCooldown.Ready
        if (hadActiveCooldown) {
            hadActiveCooldown = false
            cooldownLiveMessage = cooldownEndMessage
            deps.onAnnounceCooldown?.invoke(cooldownEndMessage)
        }
    }

    fun startCooldown(retryAfterSeconds: Long?) {
        val next = SendCooldown.start(retryAfterSeconds, deps.clock)
        cooldown = next
        cooldownTick++
        hadActiveCooldown = true
        if (next is SendCooldown.Counting) {
            val remaining = next.remainingSeconds(deps.clock)
            val msg = cooldownStartTemplate.format(SendCooldown.formatClock(remaining))
            cooldownLiveMessage = msg
            deps.onAnnounceCooldown?.invoke(msg)
        }
    }

    val snackbarSavedText = stringResource(R.string.account_profile_info_saved)
    val snackbarAvatarUploadedText = stringResource(R.string.account_profile_avatar_uploaded)
    val snackbarAvatarRemovedText = stringResource(R.string.account_profile_avatar_removed)
    val snackbarEmailChangedText = stringResource(R.string.account_profile_email_changed)

    fun processSelectedAvatarUri(uri: Uri, tempTarget: CameraTempTarget?) {
        if (!avatarOpInFlight.compareAndSet(false, true)) {
            tempTarget?.deleteQuietly()
            return
        }
        avatarBusy = true
        avatarError = null
        scope.launch {
            try {
                when (val prepared = avatarPreparer.prepare(uri)) {
                    AvatarPrepareResult.Unreadable -> {
                        avatarError = AvatarErrorKind.INVALID_IMAGE
                    }

                    is AvatarPrepareResult.Prepared -> {
                        when (val upload = deps.profileGateway.uploadAvatar(prepared.avatar)) {
                            is ProfileOperationResult.Success -> {
                                deps.profileStore.replace(upload.value)
                                scope.launch { snackbarHostState.showSnackbar(snackbarAvatarUploadedText) }
                            }

                            is ProfileOperationResult.Failure -> {
                                avatarError = when (upload.error) {
                                    ProfileError.AvatarTooLarge -> AvatarErrorKind.TOO_LARGE
                                    ProfileError.AvatarDimensions -> AvatarErrorKind.DIMENSIONS_EXCEEDED
                                    ProfileError.AvatarInvalid -> AvatarErrorKind.INVALID_IMAGE
                                    else -> AvatarErrorKind.GENERIC
                                }
                            }
                        }
                    }
                }
            } finally {
                tempTarget?.deleteQuietly()
                avatarBusy = false
                avatarOpInFlight.set(false)
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            processSelectedAvatarUri(uri, tempTarget = null)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val target = pendingCaptureTarget
        pendingCaptureTarget = null
        if (success && target != null) {
            processSelectedAvatarUri(target.uri, tempTarget = target)
        } else {
            target?.deleteQuietly()
        }
    }

    fun launchCameraCapture() {
        val customUri = cameraAccess.createCaptureUri()
        if (customUri != null) {
            val target = CameraTempTarget(
                file = java.io.File(context.cacheDir, "avatar_custom_capture_placeholder"),
                uri = customUri,
            )
            pendingCaptureTarget = target
            runCatching {
                takePictureLauncher.launch(customUri)
            }.onFailure {
                pendingCaptureTarget = null
                avatarError = AvatarErrorKind.GENERIC
            }
            return
        }
        val target = runCatching { CameraCaptureFiles.createTempCapture(context) }.getOrNull()
        if (target == null) {
            avatarError = AvatarErrorKind.GENERIC
            return
        }
        pendingCaptureTarget = target
        runCatching {
            takePictureLauncher.launch(target.uri)
        }.onFailure {
            pendingCaptureTarget = null
            target.deleteQuietly()
            avatarError = AvatarErrorKind.GENERIC
        }
    }

    val colors = Thi360Theme.colors
    val errorTitle = stringResource(R.string.account_profile_errorTitle)
    val errorDescription = stringResource(R.string.account_profile_errorDescription)
    val offlineTitle = stringResource(R.string.common_offline_title)
    val offlineDescription = stringResource(R.string.common_offline_description)
    val effectiveCooldown = if (cooldownTick >= 0) cooldown.resolve(deps.clock) else cooldown
    val cooldownRemaining = effectiveCooldown.remainingSeconds(deps.clock)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(ProfileTestTags.SCREEN),
        containerColor = if (embeddedInPane) colors.bgSurface else colors.bgPage,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    modifier = Modifier
                        .semantics(mergeDescendants = true) {}
                        .testTag(ProfileTestTags.SNACKBAR),
                )
            }
        },
        topBar = {
            if (!embeddedInPane) {
                RecoveryTopBar(
                    title = stringResource(R.string.account_profile_title),
                    onNavigationClick = {
                        deps.draftStore.clear()
                        onBack()
                    },
                    navigationContentDescription = stringResource(R.string.recovery_back),
                    navigationTestTag = ProfileTestTags.BACK_BUTTON,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LoadStateView(
                state = profileState,
                onRetry = {
                    scope.launch { deps.profileStore.refresh() }
                },
                emptyContent = {},
                customErrorTitle = { failed ->
                    if (failed.kind == LoadFailureKind.Offline) offlineTitle else errorTitle
                },
                customErrorDescription = { failed ->
                    if (failed.kind == LoadFailureKind.Offline) offlineDescription else errorDescription
                },
            ) { profile ->
                ProfileLoadedContent(
                    profile = profile,
                    embeddedInPane = embeddedInPane,
                    refreshError = refreshError,
                    draftStore = deps.draftStore,
                    avatarBusy = avatarBusy,
                    avatarError = avatarError,
                    showCameraDeniedBanner = showCameraDeniedBanner,
                    isSavingInfo = isSavingInfo,
                    fullNameError = fullNameError,
                    phoneError = phoneError,
                    infoGenericError = infoGenericError,
                    showChangePasswordText = showChangePasswordText,
                    isSendingChange = isSendingChange,
                    changeEmailAlert = changeEmailAlert,
                    newEmailError = newEmailError,
                    changePasswordError = changePasswordError,
                    isConfirmingEmail = isConfirmingEmail,
                    confirmAlert = confirmAlert,
                    confirmCodeError = confirmCodeError,
                    cooldown = effectiveCooldown,
                    cooldownRemainingSeconds = cooldownRemaining,
                    cooldownLiveMessage = cooldownLiveMessage,
                    onRetryRefresh = { scope.launch { deps.profileStore.refresh() } },
                    onOpenVerifyEmail = onOpenVerifyEmail,
                    onOpenAvatarSourceSheet = {
                        avatarError = null
                        showAvatarSheet = true
                    },
                    onOpenCameraSettings = { cameraAccess.openAppSettings() },
                    onRemoveAvatar = {
                        if (!avatarOpInFlight.compareAndSet(false, true)) return@ProfileLoadedContent
                        avatarBusy = true
                        avatarError = null
                        scope.launch {
                            try {
                                when (deps.profileGateway.removeAvatar()) {
                                    is ProfileOperationResult.Success -> {
                                        deps.profileStore.replace(
                                            profile.copy(avatarAssetId = null, avatarUrl = null),
                                        )
                                        scope.launch { snackbarHostState.showSnackbar(snackbarAvatarRemovedText) }
                                    }

                                    is ProfileOperationResult.Failure -> {
                                        avatarError = AvatarErrorKind.GENERIC
                                    }
                                }
                            } finally {
                                avatarBusy = false
                                avatarOpInFlight.set(false)
                            }
                        }
                    },
                    onFullNameChanged = { raw ->
                        deps.draftStore.fullName = PersonalInfoValidator.clampFullNameInput(raw)
                        fullNameError = null
                        infoGenericError = false
                    },
                    onPhoneChanged = { raw ->
                        deps.draftStore.phone = raw
                        phoneError = null
                        infoGenericError = false
                    },
                    onDiscardInfo = {
                        deps.draftStore.discardInfoChanges()
                        fullNameError = null
                        phoneError = null
                        infoGenericError = false
                    },
                    onSaveInfo = {
                        val validation = PersonalInfoValidator.validate(
                            fullName = deps.draftStore.fullName,
                            phone = deps.draftStore.phone,
                            savedPhone = profile.phone,
                        )
                        fullNameError = validation.fullNameError
                        phoneError = validation.phoneError
                        infoGenericError = false
                        if (!validation.isValid) return@ProfileLoadedContent
                        if (!infoSaveInFlight.compareAndSet(false, true)) return@ProfileLoadedContent
                        isSavingInfo = true
                        val patch = PersonalInfoValidator.buildPatch(
                            fullName = deps.draftStore.fullName,
                            phone = deps.draftStore.phone,
                            savedPhone = profile.phone,
                        )
                        scope.launch {
                            try {
                                when (val result = deps.profileGateway.update(patch.fullName, patch.phone)) {
                                    is ProfileOperationResult.Success -> {
                                        deps.profileStore.replace(result.value)
                                        deps.draftStore.syncWithProfile(result.value, forceReset = true)
                                        fullNameError = null
                                        phoneError = null
                                        infoGenericError = false
                                        scope.launch { snackbarHostState.showSnackbar(snackbarSavedText) }
                                    }

                                    is ProfileOperationResult.Failure -> {
                                        when (val err = result.error) {
                                            is ProfileError.Field -> when (err.field) {
                                                "fullName" -> fullNameError = if (err.code == "SIZE") {
                                                    FullNameValidationError.TOO_LONG
                                                } else {
                                                    FullNameValidationError.REQUIRED
                                                }

                                                "phone" -> phoneError = PhoneValidationError.INVALID
                                                else -> infoGenericError = true
                                            }

                                            else -> infoGenericError = true
                                        }
                                    }
                                }
                            } finally {
                                isSavingInfo = false
                                infoSaveInFlight.set(false)
                            }
                        }
                    },
                    onToggleChangeEmail = {
                        val nextOpen = !deps.draftStore.isChangeEmailOpen
                        deps.draftStore.isChangeEmailOpen = nextOpen
                        if (nextOpen) {
                            deps.draftStore.newEmail = ""
                            deps.draftStore.changeEmailPassword = ""
                            changeEmailAlert = null
                            newEmailError = null
                            changePasswordError = null
                        }
                    },
                    onCloseChangeEmail = {
                        deps.draftStore.isChangeEmailOpen = false
                        changeEmailAlert = null
                        newEmailError = null
                        changePasswordError = null
                    },
                    onNewEmailChanged = {
                        deps.draftStore.newEmail = it
                        newEmailError = null
                        changeEmailAlert = null
                    },
                    onChangePasswordChanged = {
                        deps.draftStore.changeEmailPassword = it
                        changePasswordError = null
                        changeEmailAlert = null
                    },
                    onToggleChangePasswordVisibility = {
                        showChangePasswordText = !showChangePasswordText
                    },
                    onSubmitChangeEmail = {
                        val trimmedEmail = deps.draftStore.newEmail.trim()
                        val password = deps.draftStore.changeEmailPassword
                        val emailErr = when {
                            trimmedEmail.isEmpty() -> ChangeEmailFieldError.NEW_EMAIL_REQUIRED
                            !PersonalInfoValidator.isValidEmail(trimmedEmail) -> ChangeEmailFieldError.NEW_EMAIL_INVALID
                            else -> null
                        }
                        val passErr = if (password.isEmpty()) ChangeEmailFieldError.PASSWORD_REQUIRED else null
                        newEmailError = emailErr
                        changePasswordError = passErr
                        changeEmailAlert = null
                        if (emailErr != null || passErr != null) return@ProfileLoadedContent
                        if (effectiveCooldown != SendCooldown.Ready) return@ProfileLoadedContent
                        if (!sendChangeInFlight.compareAndSet(false, true)) return@ProfileLoadedContent
                        isSendingChange = true
                        scope.launch {
                            try {
                                when (val result = deps.profileGateway.requestEmailChange(trimmedEmail, password)) {
                                    is ProfileOperationResult.Success -> {
                                        deps.profileStore.replace(profile.copy(pendingEmail = trimmedEmail))
                                        deps.draftStore.isChangeEmailOpen = false
                                        deps.draftStore.newEmail = ""
                                        deps.draftStore.changeEmailPassword = ""
                                        deps.draftStore.pendingCode = ""
                                        confirmAlert = null
                                        confirmCodeError = null
                                        startCooldown(SendCooldown.DEFAULT_SECONDS)
                                    }

                                    is ProfileOperationResult.Failure -> {
                                        when (val err = result.error) {
                                            ProfileError.EmailTaken -> newEmailError = ChangeEmailFieldError.NEW_EMAIL_TAKEN
                                            is ProfileError.Field -> when (err.field) {
                                                "currentPassword" -> changePasswordError = ChangeEmailFieldError.WRONG_PASSWORD
                                                "newEmail" -> newEmailError = ChangeEmailFieldError.NEW_EMAIL_INVALID
                                                else -> changeEmailAlert = ChangeEmailAlertKind.GENERIC
                                            }
                                            is ProfileError.RateLimited -> {
                                                changeEmailAlert = ChangeEmailAlertKind.RATE_LIMITED
                                                startCooldown(err.retryAfterSeconds ?: SendCooldown.DEFAULT_SECONDS)
                                            }
                                            ProfileError.Throttled -> {
                                                changeEmailAlert = ChangeEmailAlertKind.RATE_LIMITED
                                                startCooldown(SendCooldown.DEFAULT_SECONDS)
                                            }
                                            else -> changeEmailAlert = ChangeEmailAlertKind.GENERIC
                                        }
                                    }
                                }
                            } finally {
                                isSendingChange = false
                                sendChangeInFlight.set(false)
                            }
                        }
                    },
                    onPendingCodeChanged = {
                        deps.draftStore.pendingCode = it
                        confirmCodeError = null
                        confirmAlert = null
                    },
                    onConfirmPendingEmail = {
                        val code = deps.draftStore.pendingCode
                        confirmAlert = null
                        if (!PersonalInfoValidator.isValidVerificationCode(code)) {
                            confirmCodeError = ConfirmCodeError.Required
                            return@ProfileLoadedContent
                        }
                        confirmCodeError = null
                        if (!confirmInFlight.compareAndSet(false, true)) return@ProfileLoadedContent
                        isConfirmingEmail = true
                        scope.launch {
                            try {
                                when (val result = deps.profileGateway.confirmEmailChange(code)) {
                                    is ProfileOperationResult.Success -> {
                                        val nextEmail = profile.pendingEmail ?: profile.email
                                        deps.profileStore.replace(
                                            profile.copy(
                                                email = nextEmail,
                                                emailVerified = true,
                                                pendingEmail = null,
                                            ),
                                        )
                                        deps.draftStore.pendingCode = ""
                                        confirmAlert = null
                                        confirmCodeError = null
                                        scope.launch { snackbarHostState.showSnackbar(snackbarEmailChangedText) }
                                    }

                                    is ProfileOperationResult.Failure -> {
                                        when (val err = result.error) {
                                            is ProfileError.CodeInvalid -> {
                                                confirmCodeError = ConfirmCodeError.Invalid(err.remainingAttempts ?: 0)
                                            }
                                            ProfileError.CodeExpired -> confirmAlert = ConfirmEmailAlertKind.EXPIRED
                                            ProfileError.EmailTaken -> confirmAlert = ConfirmEmailAlertKind.EMAIL_TAKEN
                                            ProfileError.SyncFailed -> confirmAlert = ConfirmEmailAlertKind.SYNC_FAILED
                                            else -> confirmAlert = ConfirmEmailAlertKind.GENERIC
                                        }
                                    }
                                }
                            } finally {
                                isConfirmingEmail = false
                                confirmInFlight.set(false)
                            }
                        }
                    },
                    onResendPendingEmail = {
                        if (effectiveCooldown != SendCooldown.Ready) return@ProfileLoadedContent
                        deps.draftStore.newEmail = profile.pendingEmail.orEmpty()
                        deps.draftStore.changeEmailPassword = ""
                        changeEmailAlert = null
                        newEmailError = null
                        changePasswordError = null
                        deps.draftStore.isChangeEmailOpen = true
                    },
                )
            }
        }
    }

    if (showAvatarSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAvatarSheet = false },
            containerColor = colors.bgSurface,
            modifier = Modifier.testTag(ProfileTestTags.AVATAR_SOURCE_SHEET),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = stringResource(R.string.account_profile_avatar_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            showAvatarSheet = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .testTag(ProfileTestTags.AVATAR_SOURCE_GALLERY),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        imageVector = AccountIcons.Gallery,
                        contentDescription = null,
                        tint = colors.text,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(R.string.account_profile_avatar_sourceGallery),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.text,
                    )
                }
                if (cameraAccess.hasCamera) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                showAvatarSheet = false
                                when (cameraAccess.permission) {
                                    CameraPermissionStatus.Granted -> {
                                        showCameraDeniedBanner = false
                                        launchCameraCapture()
                                    }

                                    CameraPermissionStatus.Denied -> {
                                        showCameraDeniedBanner = true
                                    }

                                    CameraPermissionStatus.NotAsked -> {
                                        cameraAccess.request { status ->
                                            if (status == CameraPermissionStatus.Granted) {
                                                showCameraDeniedBanner = false
                                                launchCameraCapture()
                                            } else {
                                                showCameraDeniedBanner = true
                                            }
                                        }
                                    }
                                }
                            }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .testTag(ProfileTestTags.AVATAR_SOURCE_CAMERA),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = AccountIcons.Camera,
                            contentDescription = null,
                            tint = colors.text,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = stringResource(R.string.account_profile_avatar_sourceCamera),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.text,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileLoadedContent(
    profile: UserProfile,
    embeddedInPane: Boolean,
    refreshError: LoadState.Failed?,
    draftStore: ProfileDraftStore,
    avatarBusy: Boolean,
    avatarError: AvatarErrorKind?,
    showCameraDeniedBanner: Boolean,
    isSavingInfo: Boolean,
    fullNameError: FullNameValidationError?,
    phoneError: PhoneValidationError?,
    infoGenericError: Boolean,
    showChangePasswordText: Boolean,
    isSendingChange: Boolean,
    changeEmailAlert: ChangeEmailAlertKind?,
    newEmailError: ChangeEmailFieldError?,
    changePasswordError: ChangeEmailFieldError?,
    isConfirmingEmail: Boolean,
    confirmAlert: ConfirmEmailAlertKind?,
    confirmCodeError: ConfirmCodeError?,
    cooldown: SendCooldown,
    cooldownRemainingSeconds: Long,
    cooldownLiveMessage: String,
    onRetryRefresh: () -> Unit,
    onOpenVerifyEmail: () -> Unit,
    onOpenAvatarSourceSheet: () -> Unit,
    onOpenCameraSettings: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onFullNameChanged: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onDiscardInfo: () -> Unit,
    onSaveInfo: () -> Unit,
    onToggleChangeEmail: () -> Unit,
    onCloseChangeEmail: () -> Unit,
    onNewEmailChanged: (String) -> Unit,
    onChangePasswordChanged: (String) -> Unit,
    onToggleChangePasswordVisibility: () -> Unit,
    onSubmitChangeEmail: () -> Unit,
    onPendingCodeChanged: (String) -> Unit,
    onConfirmPendingEmail: () -> Unit,
    onResendPendingEmail: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val isInfoDirty = PersonalInfoValidator.isDirty(draftStore.fullName, draftStore.phone, profile)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = if (embeddedInPane) 24.dp else 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (embeddedInPane) {
                Text(
                    text = stringResource(R.string.account_profile_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag(ProfileTestTags.TITLE),
                )
            }

            Text(
                text = stringResource(R.string.account_profile_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                modifier = Modifier.testTag(ProfileTestTags.INTRO),
            )

            if (refreshError != null) {
                val bannerTitle = if (refreshError.kind == LoadFailureKind.Offline) {
                    stringResource(R.string.common_offline_title)
                } else {
                    stringResource(R.string.account_profile_errorTitle)
                }
                val bannerDesc = if (refreshError.kind == LoadFailureKind.Offline) {
                    stringResource(R.string.common_offline_description)
                } else {
                    stringResource(R.string.account_profile_errorDescription)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.dangerBg,
                    border = BorderStroke(1.dp, colors.dangerText.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ProfileTestTags.REFRESH_ERROR_BANNER),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = bannerTitle,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.dangerText,
                        )
                        Text(
                            text = bannerDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.dangerText,
                        )
                        OutlinedButton(
                            onClick = onRetryRefresh,
                            border = BorderStroke(1.dp, colors.dangerText.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(ProfileTestTags.REFRESH_RETRY_BUTTON),
                        ) {
                            Text(
                                text = stringResource(R.string.common_retry),
                                color = colors.dangerText,
                            )
                        }
                    }
                }
            }

            if (!profile.email.isNullOrBlank() && !profile.emailVerified) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColors.warningBg,
                    border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ProfileTestTags.UNVERIFIED_BANNER),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val titlePart = stringResource(R.string.account_profile_unverifiedTitle)
                            val bodyPart = stringResource(R.string.account_profile_unverifiedBody)
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(titlePart)
                                    }
                                    append(' ')
                                    append(bodyPart)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = statusColors.warning,
                            )
                            OutlinedButton(
                                onClick = onOpenVerifyEmail,
                                border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag(ProfileTestTags.VERIFY_EMAIL_BUTTON),
                            ) {
                                Text(
                                    text = stringResource(R.string.account_profile_verifyEmail),
                                    color = statusColors.warning,
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfileTestTags.AVATAR_SECTION),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.account_profile_avatar_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AuthenticatedAvatar(
                            fullName = profile.fullName,
                            username = profile.username,
                            avatarAssetId = profile.avatarAssetId,
                            avatarUrl = profile.avatarUrl,
                            size = 72.dp,
                            initialsStyle = MaterialTheme.typography.headlineSmall,
                            imageTestTag = ProfileTestTags.AVATAR_IMAGE,
                            initialsTestTag = ProfileTestTags.AVATAR_INITIALS,
                            includeSemantics = true,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = profile.fullName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                color = colors.text,
                            )
                            Text(
                                text = profile.username,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onOpenAvatarSourceSheet,
                            enabled = !avatarBusy,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = colors.primaryContainer,
                                contentColor = colors.primaryText,
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(ProfileTestTags.AVATAR_UPLOAD_BUTTON),
                        ) {
                            if (avatarBusy) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = colors.primaryText,
                                    modifier = Modifier.size(16.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = AccountIcons.Upload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.account_profile_avatar_upload))
                        }

                        if (!profile.avatarUrl.isNullOrBlank()) {
                            TextButton(
                                onClick = onRemoveAvatar,
                                enabled = !avatarBusy,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag(ProfileTestTags.AVATAR_REMOVE_BUTTON),
                            ) {
                                Icon(
                                    imageVector = AccountIcons.Trash,
                                    contentDescription = null,
                                    tint = colors.dangerText,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.account_profile_avatar_remove),
                                    color = colors.dangerText,
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.account_profile_avatar_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.testTag(ProfileTestTags.AVATAR_HINT),
                    )

                    if (showCameraDeniedBanner) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = statusColors.warningBg,
                            border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(ProfileTestTags.CAMERA_DENIED_BANNER),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.account_profile_avatar_cameraDenied),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColors.warning,
                                )
                                OutlinedButton(
                                    onClick = onOpenCameraSettings,
                                    border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag(ProfileTestTags.CAMERA_OPEN_SETTINGS_BUTTON),
                                ) {
                                    Text(
                                        text = stringResource(R.string.account_profile_avatar_openSettings),
                                        color = statusColors.warning,
                                    )
                                }
                            }
                        }
                    }

                    if (avatarError != null) {
                        val errorText = when (avatarError) {
                            AvatarErrorKind.TOO_LARGE -> stringResource(R.string.account_profile_avatar_errors_TOO_LARGE)
                            AvatarErrorKind.DIMENSIONS_EXCEEDED -> stringResource(R.string.account_profile_avatar_errors_DIMENSIONS_EXCEEDED)
                            AvatarErrorKind.INVALID_IMAGE -> stringResource(R.string.account_profile_avatar_errors_INVALID_IMAGE)
                            AvatarErrorKind.GENERIC -> stringResource(R.string.account_profile_avatar_errors_generic)
                        }
                        ProfileErrorAlertBanner(
                            message = errorText,
                            testTag = ProfileTestTags.AVATAR_ERROR,
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfileTestTags.INFO_SECTION),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.account_profile_info_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                        modifier = Modifier.semantics { heading() },
                    )

                    if (infoGenericError) {
                        ProfileErrorAlertBanner(
                            message = stringResource(R.string.account_profile_info_genericError),
                            testTag = ProfileTestTags.INFO_ERROR_BANNER,
                        )
                    }

                    OutlinedTextField(
                        value = profile.username,
                        onValueChange = {},
                        enabled = false,
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.account_profile_info_usernameLabel)) },
                        supportingText = {
                            Text(
                                text = stringResource(R.string.account_profile_info_usernameHint),
                                color = colors.textMuted,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = AccountIcons.Lock,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = colors.border,
                            disabledTextColor = colors.textSecondary,
                            disabledLabelColor = colors.textMuted,
                            disabledSupportingTextColor = colors.textMuted,
                            disabledLeadingIconColor = colors.textMuted,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(ProfileTestTags.USERNAME_FIELD),
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = draftStore.fullName,
                            onValueChange = onFullNameChanged,
                            singleLine = true,
                            enabled = !isSavingInfo,
                            isError = fullNameError != null,
                            label = { Text(stringResource(R.string.account_profile_info_fullNameLabel)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(ProfileTestTags.FULL_NAME_FIELD),
                        )
                        if (fullNameError != null) {
                            Text(
                                text = when (fullNameError) {
                                    FullNameValidationError.REQUIRED ->
                                        stringResource(R.string.account_profile_info_fullNameRequired)
                                    FullNameValidationError.TOO_LONG ->
                                        stringResource(R.string.account_profile_info_fullNameTooLong)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.dangerText,
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                                    .testTag(ProfileTestTags.FULL_NAME_ERROR),
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = draftStore.phone,
                            onValueChange = onPhoneChanged,
                            singleLine = true,
                            enabled = !isSavingInfo,
                            isError = phoneError != null,
                            label = { Text(stringResource(R.string.account_profile_info_phoneLabel)) },
                            placeholder = { Text(stringResource(R.string.account_profile_info_phonePlaceholder)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(ProfileTestTags.PHONE_FIELD),
                        )
                        when {
                            phoneError != null -> {
                                Text(
                                    text = stringResource(R.string.account_profile_info_phoneInvalid),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.dangerText,
                                    modifier = Modifier
                                        .padding(start = 16.dp)
                                        .semantics { liveRegion = LiveRegionMode.Polite }
                                        .testTag(ProfileTestTags.PHONE_ERROR),
                                )
                            }

                            profile.phone.isNullOrBlank() -> {
                                Text(
                                    text = stringResource(R.string.account_profile_info_phoneEmpty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                    modifier = Modifier
                                        .padding(start = 16.dp)
                                        .testTag(ProfileTestTags.PHONE_EMPTY_HINT),
                                )
                            }
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDiscardInfo,
                            enabled = isInfoDirty && !isSavingInfo,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(ProfileTestTags.INFO_CANCEL_BUTTON),
                        ) {
                            Text(text = stringResource(R.string.account_profile_info_cancel))
                        }

                        Button(
                            onClick = onSaveInfo,
                            enabled = isInfoDirty && !isSavingInfo,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryFill,
                                contentColor = colors.onPrimary,
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(ProfileTestTags.INFO_SAVE_BUTTON),
                        ) {
                            if (isSavingInfo) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = colors.onPrimary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(text = stringResource(R.string.account_profile_info_save))
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfileTestTags.EMAIL_SECTION),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.account_profile_email_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                        modifier = Modifier.semantics { heading() },
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.bgPage,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (!profile.email.isNullOrBlank()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = AccountIcons.Mail,
                                        contentDescription = null,
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = profile.email,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            color = colors.text,
                                            modifier = Modifier.testTag(ProfileTestTags.EMAIL_VALUE),
                                        )
                                        val chipBg = if (profile.emailVerified) statusColors.successBg else statusColors.warningBg
                                        val chipFg = if (profile.emailVerified) statusColors.success else statusColors.warning
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = chipBg,
                                            modifier = Modifier
                                                .semantics(mergeDescendants = true) {}
                                                .testTag(ProfileTestTags.EMAIL_BADGE),
                                        ) {
                                            Text(
                                                text = stringResource(
                                                    if (profile.emailVerified) {
                                                        R.string.account_profile_email_verified
                                                    } else {
                                                        R.string.account_profile_email_unverified
                                                    },
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = chipFg,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.account_profile_email_none),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textMuted,
                                    modifier = Modifier.testTag(ProfileTestTags.EMAIL_NONE),
                                )
                            }

                            OutlinedButton(
                                onClick = onToggleChangeEmail,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag(ProfileTestTags.CHANGE_EMAIL_TOGGLE),
                            ) {
                                Text(text = stringResource(R.string.account_profile_email_change))
                            }
                        }
                    }

                    if (draftStore.isChangeEmailOpen) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.bgPage,
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(ProfileTestTags.CHANGE_EMAIL_FORM),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.account_profile_email_changeTitle),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.text,
                                )

                                if (changeEmailAlert != null) {
                                    ProfileErrorAlertBanner(
                                        message = when (changeEmailAlert) {
                                            ChangeEmailAlertKind.RATE_LIMITED ->
                                                stringResource(R.string.account_profile_email_changeErrors_RATE_LIMITED)
                                            ChangeEmailAlertKind.GENERIC ->
                                                stringResource(R.string.account_profile_email_changeErrors_generic)
                                        },
                                        testTag = ProfileTestTags.CHANGE_EMAIL_ALERT,
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedTextField(
                                        value = draftStore.newEmail,
                                        onValueChange = onNewEmailChanged,
                                        singleLine = true,
                                        enabled = !isSendingChange,
                                        isError = newEmailError != null,
                                        label = { Text(stringResource(R.string.account_profile_email_newEmailLabel)) },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Email,
                                            imeAction = ImeAction.Next,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag(ProfileTestTags.NEW_EMAIL_FIELD),
                                    )
                                    if (newEmailError != null) {
                                        Text(
                                            text = when (newEmailError) {
                                                ChangeEmailFieldError.NEW_EMAIL_REQUIRED ->
                                                    stringResource(R.string.account_profile_email_newEmailRequired)
                                                ChangeEmailFieldError.NEW_EMAIL_INVALID ->
                                                    stringResource(R.string.account_profile_email_newEmailInvalid)
                                                ChangeEmailFieldError.NEW_EMAIL_TAKEN ->
                                                    stringResource(R.string.account_profile_email_newEmailTaken)
                                                else -> ""
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.dangerText,
                                            modifier = Modifier
                                                .padding(start = 16.dp)
                                                .semantics { liveRegion = LiveRegionMode.Polite }
                                                .testTag(ProfileTestTags.NEW_EMAIL_ERROR),
                                        )
                                    }
                                }

                                val passwordToggleDesc = stringResource(
                                    if (showChangePasswordText) {
                                        R.string.account_profile_email_passwordHide
                                    } else {
                                        R.string.account_profile_email_passwordShow
                                    },
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedTextField(
                                        value = draftStore.changeEmailPassword,
                                        onValueChange = onChangePasswordChanged,
                                        singleLine = true,
                                        enabled = !isSendingChange,
                                        isError = changePasswordError != null,
                                        label = { Text(stringResource(R.string.account_profile_email_currentPasswordLabel)) },
                                        visualTransformation = if (showChangePasswordText) {
                                            VisualTransformation.None
                                        } else {
                                            PasswordVisualTransformation()
                                        },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = onToggleChangePasswordVisibility,
                                                modifier = Modifier
                                                    .semantics { contentDescription = passwordToggleDesc }
                                                    .testTag(ProfileTestTags.CURRENT_PASSWORD_TOGGLE),
                                            ) {
                                                Icon(
                                                    imageVector = if (showChangePasswordText) LoginIcons.VisibilityOff else LoginIcons.Visibility,
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
                                            .testTag(ProfileTestTags.CURRENT_PASSWORD_FIELD),
                                    )
                                    if (changePasswordError != null) {
                                        Text(
                                            text = when (changePasswordError) {
                                                ChangeEmailFieldError.PASSWORD_REQUIRED ->
                                                    stringResource(R.string.account_profile_email_currentPasswordRequired)
                                                ChangeEmailFieldError.WRONG_PASSWORD ->
                                                    stringResource(R.string.account_profile_email_wrongPassword)
                                                else -> ""
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.dangerText,
                                            modifier = Modifier
                                                .padding(start = 16.dp)
                                                .semantics { liveRegion = LiveRegionMode.Polite }
                                                .testTag(ProfileTestTags.CURRENT_PASSWORD_ERROR),
                                        )
                                    }
                                }

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = onCloseChangeEmail,
                                        enabled = !isSendingChange,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag(ProfileTestTags.CHANGE_EMAIL_CANCEL),
                                    ) {
                                        Text(text = stringResource(R.string.account_profile_email_cancel))
                                    }
                                    Button(
                                        onClick = onSubmitChangeEmail,
                                        enabled = !isSendingChange && cooldown == SendCooldown.Ready,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.primaryFill,
                                            contentColor = colors.onPrimary,
                                        ),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag(ProfileTestTags.CHANGE_EMAIL_SEND),
                                    ) {
                                        if (isSendingChange) {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                color = colors.onPrimary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(text = stringResource(R.string.account_profile_email_sendCode))
                                    }
                                }

                                if (cooldown is SendCooldown.Counting && cooldownRemainingSeconds > 0L) {
                                    Text(
                                        text = stringResource(
                                            R.string.account_profile_email_sendCodeIn,
                                            SendCooldown.formatClock(cooldownRemainingSeconds),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted,
                                        modifier = Modifier.testTag(ProfileTestTags.CHANGE_EMAIL_COOLDOWN),
                                    )
                                }
                            }
                        }
                    }

                    if (!profile.pendingEmail.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = statusColors.warningBg,
                            border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(ProfileTestTags.PENDING_EMAIL_CARD),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Icon(
                                        imageVector = AccountIcons.Clock,
                                        contentDescription = null,
                                        tint = statusColors.warning,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(20.dp),
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = stringResource(
                                                R.string.account_profile_email_pendingTitle,
                                                profile.pendingEmail,
                                            ),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = statusColors.warning,
                                            modifier = Modifier.testTag(ProfileTestTags.PENDING_EMAIL_TITLE),
                                        )
                                        Text(
                                            text = stringResource(R.string.account_profile_email_pendingBody),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary,
                                        )
                                    }
                                }

                                if (confirmAlert != null) {
                                    ProfileErrorAlertBanner(
                                        message = when (confirmAlert) {
                                            ConfirmEmailAlertKind.EXPIRED ->
                                                stringResource(R.string.account_profile_email_confirmErrors_EXPIRED)
                                            ConfirmEmailAlertKind.EMAIL_TAKEN ->
                                                stringResource(R.string.account_profile_email_confirmErrors_EMAIL_TAKEN)
                                            ConfirmEmailAlertKind.SYNC_FAILED ->
                                                stringResource(R.string.account_profile_email_confirmErrors_SYNC_FAILED)
                                            ConfirmEmailAlertKind.GENERIC ->
                                                stringResource(R.string.account_profile_email_confirmErrors_generic)
                                        },
                                        testTag = ProfileTestTags.PENDING_EMAIL_ALERT,
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedTextField(
                                        value = draftStore.pendingCode,
                                        onValueChange = onPendingCodeChanged,
                                        singleLine = true,
                                        enabled = !isConfirmingEmail,
                                        isError = confirmCodeError != null,
                                        label = { Text(stringResource(R.string.account_profile_email_codeLabel)) },
                                        placeholder = { Text(stringResource(R.string.account_profile_email_codePlaceholder)) },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag(ProfileTestTags.PENDING_CODE_FIELD),
                                    )
                                    if (confirmCodeError != null) {
                                        Text(
                                            text = when (confirmCodeError) {
                                                ConfirmCodeError.Required ->
                                                    stringResource(R.string.account_profile_email_codeRequired)
                                                is ConfirmCodeError.Invalid ->
                                                    stringResource(
                                                        R.string.account_profile_email_codeInvalid,
                                                        confirmCodeError.remainingAttempts,
                                                    )
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.dangerText,
                                            modifier = Modifier
                                                .padding(start = 16.dp)
                                                .semantics { liveRegion = LiveRegionMode.Polite }
                                                .testTag(ProfileTestTags.PENDING_CODE_ERROR),
                                        )
                                    }
                                }

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = onConfirmPendingEmail,
                                        enabled = !isConfirmingEmail,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.primaryFill,
                                            contentColor = colors.onPrimary,
                                        ),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag(ProfileTestTags.PENDING_CONFIRM_BUTTON),
                                    ) {
                                        if (isConfirmingEmail) {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                color = colors.onPrimary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(text = stringResource(R.string.account_profile_email_confirm))
                                    }

                                    OutlinedButton(
                                        onClick = onResendPendingEmail,
                                        enabled = cooldown == SendCooldown.Ready && !isConfirmingEmail,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag(ProfileTestTags.PENDING_RESEND_BUTTON),
                                    ) {
                                        Text(text = stringResource(R.string.account_profile_email_resend))
                                    }
                                }

                                if (cooldown is SendCooldown.Counting && cooldownRemainingSeconds > 0L) {
                                    Text(
                                        text = stringResource(
                                            R.string.account_profile_email_resendIn,
                                            SendCooldown.formatClock(cooldownRemainingSeconds),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted,
                                        modifier = Modifier.testTag(ProfileTestTags.PENDING_COOLDOWN),
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = cooldownLiveMessage
                            }
                            .testTag(ProfileTestTags.COOLDOWN_LIVE_REGION),
                    )
                }
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
internal fun ProfileErrorAlertBanner(
    message: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.dangerBg,
        border = BorderStroke(1.dp, colors.dangerText.copy(alpha = 0.3f)),
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = LoginIcons.AlertCircle,
                contentDescription = null,
                tint = colors.dangerText,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.dangerText,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private const val COOLDOWN_POLL_INTERVAL_MS = 250L
