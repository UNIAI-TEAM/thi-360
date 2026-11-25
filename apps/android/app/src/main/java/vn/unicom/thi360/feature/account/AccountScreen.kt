package vn.unicom.thi360.feature.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import vn.unicom.thi360.R
import vn.unicom.thi360.core.biometric.BiometricAuthenticator
import vn.unicom.thi360.core.biometric.BiometricController
import vn.unicom.thi360.core.biometric.BiometricEnableOutcome
import vn.unicom.thi360.core.biometric.BiometricState
import vn.unicom.thi360.core.biometric.BiometricUnavailableReason
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.login.LoginIcons
import vn.unicom.thi360.feature.password.ChangePasswordScreen
import vn.unicom.thi360.feature.recovery.RecoveryGateway
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags

@Composable
fun AccountScreen(
    isTwoPaneWidth: Boolean,
    usernameFallback: String?,
    preferences: AppPreferences,
    onSelectLocale: (AppLocale) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    sessionManager: SessionManager,
    profileGateway: AccountProfileGateway,
    recoveryGateway: RecoveryGateway,
    biometricController: BiometricController?,
    biometricAuthenticator: BiometricAuthenticator?,
    profileState: ProfileLoadState,
    onProfileStateChanged: (ProfileLoadState) -> Unit,
    selectedPane: AccountDetailPane,
    onSelectPane: (AccountDetailPane) -> Unit,
    showSinglePaneChangePassword: Boolean,
    onShowSinglePaneChangePassword: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val biometricState by (biometricController?.state
        ?: remember { kotlinx.coroutines.flow.MutableStateFlow<BiometricState>(BiometricState.Unavailable(BiometricUnavailableReason.NO_HARDWARE)) })
        .collectAsStateWithLifecycle()

    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var isUpdatingBiometric by remember { mutableStateOf(false) }
    var showBiometricError by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }
    val biometricInFlight = remember { AtomicBoolean(false) }
    val logoutInFlight = remember { AtomicBoolean(false) }
    val profileFetchInFlight = remember { AtomicBoolean(false) }

    fun loadProfile() {
        if (!profileFetchInFlight.compareAndSet(false, true)) return
        onProfileStateChanged(ProfileLoadState.Loading)
        scope.launch {
            try {
                val next = when (val result = profileGateway.fetchProfile()) {
                    is ApiResult.Success -> ProfileLoadState.Loaded(result.data)
                    is ApiResult.NetworkFailure -> ProfileLoadState.Offline
                    is ApiResult.HttpFailure -> ProfileLoadState.Error
                }
                onProfileStateChanged(next)
            } finally {
                profileFetchInFlight.set(false)
            }
        }
    }

    LaunchedEffect(profileGateway, profileState is ProfileLoadState.Loading) {
        if (profileState is ProfileLoadState.Loading) {
            loadProfile()
        }
    }

    LaunchedEffect(biometricController) {
        biometricController?.refreshAvailability()
    }

    val resolvedUsername = (profileState as? ProfileLoadState.Loaded)?.profile?.username
        ?: usernameFallback

    if (!isTwoPaneWidth && showSinglePaneChangePassword) {
        ChangePasswordScreen(
            username = resolvedUsername,
            recoveryGateway = recoveryGateway,
            embeddedInPane = false,
            onBack = { onShowSinglePaneChangePassword(false) },
            modifier = modifier,
        )
        return
    }

    AccountScreenContent(
        isTwoPaneWidth = isTwoPaneWidth,
        profileState = profileState,
        preferences = preferences,
        biometricState = biometricState,
        selectedPane = selectedPane,
        isUpdatingBiometric = isUpdatingBiometric,
        showBiometricError = showBiometricError,
        isLoggingOut = isLoggingOut,
        onRetryProfile = ::loadProfile,
        onChangePasswordClick = {
            onSelectPane(AccountDetailPane.CHANGE_PASSWORD)
            if (!isTwoPaneWidth) {
                onShowSinglePaneChangePassword(true)
            }
        },
        onToggleBiometric = { targetChecked ->
            val controller = biometricController ?: return@AccountScreenContent
            if (!targetChecked) {
                showBiometricError = false
                controller.disable()
                return@AccountScreenContent
            }
            val authenticator = biometricAuthenticator
            if (authenticator == null) {
                showBiometricError = true
                return@AccountScreenContent
            }
            if (!biometricInFlight.compareAndSet(false, true)) return@AccountScreenContent
            isUpdatingBiometric = true
            showBiometricError = false
            scope.launch {
                try {
                    when (
                        controller.enable(
                            refreshToken = sessionManager.currentRefreshToken(),
                            authenticator = authenticator,
                        )
                    ) {
                        BiometricEnableOutcome.Enabled -> Unit
                        is BiometricEnableOutcome.Failed,
                        is BiometricEnableOutcome.Unavailable,
                        BiometricEnableOutcome.MissingSession,
                        -> {
                            showBiometricError = true
                        }
                    }
                } finally {
                    isUpdatingBiometric = false
                    biometricInFlight.set(false)
                }
            }
        },
        onLanguageRowClick = {
            if (isTwoPaneWidth) {
                onSelectPane(AccountDetailPane.LANGUAGE)
            } else {
                showLanguageDialog = true
            }
        },
        onThemeRowClick = {
            if (isTwoPaneWidth) {
                onSelectPane(AccountDetailPane.THEME)
            }
        },
        onSelectThemeMode = onSelectThemeMode,
        onLogoutClick = {
            if (!isLoggingOut) {
                showLogoutDialog = true
            }
        },
        detailContent = { pane ->
            when (pane) {
                AccountDetailPane.CHANGE_PASSWORD -> {
                    ChangePasswordScreen(
                        username = resolvedUsername,
                        recoveryGateway = recoveryGateway,
                        embeddedInPane = true,
                    )
                }

                AccountDetailPane.LANGUAGE -> {
                    AccountLanguageDetailPane(
                        selectedLocale = preferences.effectiveLocale,
                        onSelectLocale = onSelectLocale,
                    )
                }

                AccountDetailPane.THEME -> {
                    AccountThemeDetailPane(
                        selectedMode = Thi360Theme.themeMode,
                        onSelectThemeMode = onSelectThemeMode,
                    )
                }
            }
        },
        modifier = modifier,
    )

    if (showLanguageDialog && !isTwoPaneWidth) {
        LanguagePickerDialog(
            selectedLocale = preferences.effectiveLocale,
            onSelectLocale = { locale ->
                showLanguageDialog = false
                onSelectLocale(locale)
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            isLoggingOut = isLoggingOut,
            onConfirm = {
                if (!logoutInFlight.compareAndSet(false, true)) return@LogoutConfirmationDialog
                isLoggingOut = true
                scope.launch {
                    try {
                        sessionManager.logout()
                        showLogoutDialog = false
                    } finally {
                        isLoggingOut = false
                        logoutInFlight.set(false)
                    }
                }
            },
            onDismiss = {
                if (!isLoggingOut) {
                    showLogoutDialog = false
                }
            },
        )
    }
}

@Composable
fun AccountScreenContent(
    isTwoPaneWidth: Boolean,
    profileState: ProfileLoadState,
    preferences: AppPreferences,
    biometricState: BiometricState,
    selectedPane: AccountDetailPane,
    isUpdatingBiometric: Boolean,
    showBiometricError: Boolean,
    isLoggingOut: Boolean,
    onRetryProfile: () -> Unit,
    onChangePasswordClick: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onLanguageRowClick: () -> Unit,
    onThemeRowClick: () -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onLogoutClick: () -> Unit,
    detailContent: @Composable (AccountDetailPane) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors

    if (isTwoPaneWidth) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(colors.bgPage)
                .testTag(ShellTestTags.ACCOUNT_TWO_PANE)
                .padding(vertical = 16.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccountListColumn(
                compactTwoPane = true,
                profileState = profileState,
                preferences = preferences,
                biometricState = biometricState,
                selectedPane = selectedPane,
                isUpdatingBiometric = isUpdatingBiometric,
                showBiometricError = showBiometricError,
                isLoggingOut = isLoggingOut,
                onRetryProfile = onRetryProfile,
                onChangePasswordClick = onChangePasswordClick,
                onToggleBiometric = onToggleBiometric,
                onLanguageRowClick = onLanguageRowClick,
                onThemeRowClick = onThemeRowClick,
                onSelectThemeMode = onSelectThemeMode,
                onLogoutClick = onLogoutClick,
                modifier = Modifier
                    .width(312.dp)
                    .fillMaxHeight(),
            )

            VerticalDivider(color = colors.border)

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(AccountTestTags.DETAIL_PANE),
                shape = RoundedCornerShape(28.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.border),
            ) {
                detailContent(selectedPane)
            }
        }
    } else {
        AccountListColumn(
            compactTwoPane = false,
            profileState = profileState,
            preferences = preferences,
            biometricState = biometricState,
            selectedPane = selectedPane,
            isUpdatingBiometric = isUpdatingBiometric,
            showBiometricError = showBiometricError,
            isLoggingOut = isLoggingOut,
            onRetryProfile = onRetryProfile,
            onChangePasswordClick = onChangePasswordClick,
            onToggleBiometric = onToggleBiometric,
            onLanguageRowClick = onLanguageRowClick,
            onThemeRowClick = onThemeRowClick,
            onSelectThemeMode = onSelectThemeMode,
            onLogoutClick = onLogoutClick,
            modifier = modifier
                .fillMaxSize()
                .background(colors.bgPage)
                .testTag(ShellTestTags.ACCOUNT_SINGLE_PANE),
        )
    }
}

@Composable
private fun AccountListColumn(
    compactTwoPane: Boolean,
    profileState: ProfileLoadState,
    preferences: AppPreferences,
    biometricState: BiometricState,
    selectedPane: AccountDetailPane,
    isUpdatingBiometric: Boolean,
    showBiometricError: Boolean,
    isLoggingOut: Boolean,
    onRetryProfile: () -> Unit,
    onChangePasswordClick: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onLanguageRowClick: () -> Unit,
    onThemeRowClick: () -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val currentLocale = preferences.effectiveLocale
    val currentTheme = Thi360Theme.themeMode
    val horizontalPad = if (compactTwoPane) 8.dp else 24.dp

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_account),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.text,
            modifier = Modifier
                .padding(horizontal = horizontalPad, vertical = 4.dp)
                .semantics { heading() }
                .testTag(ShellTestTags.screenTitle(Destination.Account)),
        )

        Box(modifier = Modifier.padding(horizontal = horizontalPad, vertical = 4.dp)) {
            AccountProfileCard(
                profileState = profileState,
                onRetry = onRetryProfile,
            )
        }

        AccountSectionHeader(
            title = stringResource(R.string.account_section_security),
            horizontalPad = horizontalPad,
        )

        AccountNavigationRow(
            icon = AccountIcons.Key,
            title = stringResource(R.string.account_change_password),
            trailingValue = null,
            selected = compactTwoPane && selectedPane == AccountDetailPane.CHANGE_PASSWORD,
            showChevron = !compactTwoPane,
            horizontalPad = horizontalPad,
            onClick = onChangePasswordClick,
            testTag = AccountTestTags.CHANGE_PASSWORD_ROW,
        )

        BiometricSettingRow(
            biometricState = biometricState,
            isUpdating = isUpdatingBiometric,
            horizontalPad = horizontalPad,
            onCheckedChange = onToggleBiometric,
        )

        if (showBiometricError) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.dangerBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPad)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                    .testTag(AccountTestTags.BIOMETRIC_ERROR_BANNER),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = LoginIcons.AlertCircle,
                        contentDescription = null,
                        tint = colors.dangerText,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.account_biometric_enable_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dangerText,
                    )
                }
            }
        }

        AccountSectionHeader(
            title = stringResource(R.string.account_section_display),
            horizontalPad = horizontalPad,
        )

        val currentLocaleLabel = if (currentLocale == AppLocale.VI) {
            stringResource(R.string.account_language_vi)
        } else {
            stringResource(R.string.account_language_en)
        }
        AccountNavigationRow(
            icon = LoginIcons.Globe,
            title = stringResource(R.string.account_language_label),
            trailingValue = currentLocaleLabel,
            selected = compactTwoPane && selectedPane == AccountDetailPane.LANGUAGE,
            showChevron = !compactTwoPane,
            horizontalPad = horizontalPad,
            onClick = onLanguageRowClick,
            testTag = AccountTestTags.LANGUAGE_ROW,
        )

        val currentThemeLabel = if (currentTheme == ThemeMode.LIGHT) {
            stringResource(R.string.account_theme_light)
        } else {
            stringResource(R.string.account_theme_dark)
        }
        if (compactTwoPane) {
            AccountNavigationRow(
                icon = AccountIcons.Sun,
                title = stringResource(R.string.account_theme_label),
                trailingValue = currentThemeLabel,
                selected = selectedPane == AccountDetailPane.THEME,
                showChevron = false,
                horizontalPad = horizontalPad,
                onClick = onThemeRowClick,
                testTag = AccountTestTags.THEME_ROW,
            )
        } else {
            InlineThemeSettingRow(
                selectedMode = currentTheme,
                horizontalPad = horizontalPad,
                onSelectThemeMode = onSelectThemeMode,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.padding(horizontal = horizontalPad)) {
            OutlinedButton(
                onClick = onLogoutClick,
                enabled = !isLoggingOut,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, colors.borderStrong),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.dangerText,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(AccountTestTags.LOGOUT_BUTTON),
            ) {
                Icon(
                    imageVector = AccountIcons.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.account_logout))
            }
        }
    }
}

@Composable
private fun AccountProfileCard(
    profileState: ProfileLoadState,
    onRetry: () -> Unit,
) {
    val colors = Thi360Theme.colors

    when (profileState) {
        ProfileLoadState.Loading -> {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.bgSubtle,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AccountTestTags.PROFILE_LOADING),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = colors.primaryFill,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = stringResource(R.string.account_profile_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
            }
        }

        ProfileLoadState.Error -> {
            ProfileFailureCard(
                message = stringResource(R.string.account_profile_error),
                isError = true,
                cardTag = AccountTestTags.PROFILE_ERROR,
                onRetry = onRetry,
            )
        }

        ProfileLoadState.Offline -> {
            ProfileFailureCard(
                message = stringResource(R.string.account_profile_offline),
                isError = false,
                cardTag = AccountTestTags.PROFILE_OFFLINE,
                onRetry = onRetry,
            )
        }

        is ProfileLoadState.Loaded -> {
            val profile = profileState.profile
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.bgSubtle,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AccountTestTags.PROFILE_CARD),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainerStrong),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = formatAvatarInitials(profile.fullName, profile.username),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primaryText,
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = profile.fullName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                            color = colors.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag(AccountTestTags.PROFILE_FULL_NAME),
                        )
                        Text(
                            text = profile.username,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag(AccountTestTags.PROFILE_USERNAME),
                        )
                        if (!profile.email.isNullOrBlank()) {
                            Text(
                                text = profile.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag(AccountTestTags.PROFILE_EMAIL),
                            )
                            val badgeBg = if (profile.emailVerified) {
                                colors.primaryContainer
                            } else {
                                colors.bgSurface
                            }
                            val badgeFg = if (profile.emailVerified) {
                                colors.primaryText
                            } else {
                                colors.textMuted
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = badgeBg,
                                border = if (profile.emailVerified) null else BorderStroke(1.dp, colors.border),
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .semantics(mergeDescendants = true) {}
                                    .testTag(AccountTestTags.PROFILE_EMAIL_BADGE),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (profile.emailVerified) {
                                        Icon(
                                            imageVector = LoginIcons.Check,
                                            contentDescription = null,
                                            tint = badgeFg,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                    Text(
                                        text = stringResource(
                                            if (profile.emailVerified) {
                                                R.string.account_email_verified
                                            } else {
                                                R.string.account_email_unverified
                                            },
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeFg,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileFailureCard(
    message: String,
    isError: Boolean,
    cardTag: String,
    onRetry: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val containerColor = if (isError) colors.dangerBg else colors.bgSubtle
    val contentColor = if (isError) colors.dangerText else colors.textSecondary

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = if (isError) null else BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(cardTag),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.borderStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primaryText),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(AccountTestTags.PROFILE_RETRY_BUTTON),
            ) {
                Text(text = stringResource(R.string.account_profile_retry))
            }
        }
    }
}

@Composable
private fun AccountSectionHeader(
    title: String,
    horizontalPad: androidx.compose.ui.unit.Dp,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
        color = Thi360Theme.colors.primaryText,
        modifier = Modifier.padding(
            start = horizontalPad,
            end = horizontalPad,
            top = 12.dp,
            bottom = 4.dp,
        ),
    )
}

@Composable
private fun AccountNavigationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    trailingValue: String?,
    selected: Boolean,
    showChevron: Boolean,
    horizontalPad: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = Thi360Theme.colors
    val rowBg = if (selected) colors.primaryContainer else colors.bgPage.copy(alpha = 0f)
    val contentColor = if (selected) colors.primaryText else colors.text
    val iconColor = if (selected) colors.primaryText else colors.textMuted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (selected) 4.dp else 0.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(rowBg)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = horizontalPad, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        if (!trailingValue.isNullOrBlank()) {
            Text(
                text = trailingValue,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) colors.primaryText else colors.textMuted,
            )
        }
        if (showChevron) {
            Icon(
                imageVector = AccountIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BiometricSettingRow(
    biometricState: BiometricState,
    isUpdating: Boolean,
    horizontalPad: androidx.compose.ui.unit.Dp,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = Thi360Theme.colors
    val isAvailable = biometricState !is BiometricState.Unavailable
    val checked = biometricState is BiometricState.On
    val subtitleText = when (biometricState) {
        is BiometricState.Unavailable -> when (biometricState.reason) {
            BiometricUnavailableReason.NO_HARDWARE ->
                stringResource(R.string.account_biometric_unavailable_no_hardware)
            BiometricUnavailableReason.NOT_ENROLLED ->
                stringResource(R.string.account_biometric_unavailable_not_enrolled)
            BiometricUnavailableReason.HARDWARE_UNAVAILABLE,
            BiometricUnavailableReason.SECURITY_UPDATE_REQUIRED,
            BiometricUnavailableReason.UNSUPPORTED,
            -> stringResource(R.string.account_biometric_unavailable_generic)
        }
        BiometricState.Off, BiometricState.On ->
            stringResource(R.string.account_biometric_subtitle)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = horizontalPad, vertical = 10.dp)
            .testTag(AccountTestTags.BIOMETRIC_ROW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = AccountIcons.Fingerprint,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.account_biometric_title),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isAvailable) colors.text else colors.textMuted,
            )
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.testTag(AccountTestTags.BIOMETRIC_EXPLANATION),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = isAvailable && !isUpdating,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primaryFill,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.bgSubtle,
                uncheckedBorderColor = colors.borderStrong,
            ),
            modifier = Modifier.testTag(AccountTestTags.BIOMETRIC_SWITCH),
        )
    }
}

@Composable
private fun InlineThemeSettingRow(
    selectedMode: ThemeMode,
    horizontalPad: androidx.compose.ui.unit.Dp,
    onSelectThemeMode: (ThemeMode) -> Unit,
) {
    val colors = Thi360Theme.colors
    val buttonColors = SegmentedButtonDefaults.colors(
        activeContainerColor = colors.primaryContainer,
        activeContentColor = colors.primaryText,
        activeBorderColor = colors.borderStrong,
        inactiveContainerColor = colors.bgSurface,
        inactiveContentColor = colors.textSecondary,
        inactiveBorderColor = colors.border,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = horizontalPad, vertical = 8.dp)
            .testTag(AccountTestTags.THEME_ROW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = AccountIcons.Sun,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = stringResource(R.string.account_theme_label),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = selectedMode == ThemeMode.LIGHT,
                onClick = { onSelectThemeMode(ThemeMode.LIGHT) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                colors = buttonColors,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(AccountTestTags.THEME_OPTION_LIGHT),
                label = { Text(stringResource(R.string.account_theme_light)) },
            )
            SegmentedButton(
                selected = selectedMode == ThemeMode.DARK,
                onClick = { onSelectThemeMode(ThemeMode.DARK) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                colors = buttonColors,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(AccountTestTags.THEME_OPTION_DARK),
                label = { Text(stringResource(R.string.account_theme_dark)) },
            )
        }
    }
}

@Composable
private fun AccountLanguageDetailPane(
    selectedLocale: AppLocale,
    onSelectLocale: (AppLocale) -> Unit,
) {
    val colors = Thi360Theme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.account_language_label),
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.account_language_detail_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        }
        HorizontalDivider(color = colors.border)
        LanguageOptionRow(
            label = stringResource(R.string.account_language_vi),
            selected = selectedLocale == AppLocale.VI,
            onClick = { onSelectLocale(AppLocale.VI) },
            testTag = AccountTestTags.LANGUAGE_OPTION_VI,
        )
        LanguageOptionRow(
            label = stringResource(R.string.account_language_en),
            selected = selectedLocale == AppLocale.EN,
            onClick = { onSelectLocale(AppLocale.EN) },
            testTag = AccountTestTags.LANGUAGE_OPTION_EN,
        )
    }
}

@Composable
private fun AccountThemeDetailPane(
    selectedMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
) {
    val colors = Thi360Theme.colors
    val buttonColors = SegmentedButtonDefaults.colors(
        activeContainerColor = colors.primaryContainer,
        activeContentColor = colors.primaryText,
        activeBorderColor = colors.borderStrong,
        inactiveContainerColor = colors.bgSurface,
        inactiveContentColor = colors.textSecondary,
        inactiveBorderColor = colors.border,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.account_theme_label),
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.account_theme_detail_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        }
        HorizontalDivider(color = colors.border)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedMode == ThemeMode.LIGHT,
                onClick = { onSelectThemeMode(ThemeMode.LIGHT) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                colors = buttonColors,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(AccountTestTags.THEME_OPTION_LIGHT),
                label = { Text(stringResource(R.string.account_theme_light)) },
            )
            SegmentedButton(
                selected = selectedMode == ThemeMode.DARK,
                onClick = { onSelectThemeMode(ThemeMode.DARK) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                colors = buttonColors,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(AccountTestTags.THEME_OPTION_DARK),
                label = { Text(stringResource(R.string.account_theme_dark)) },
            )
        }
    }
}

@Composable
private fun LanguageOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = Thi360Theme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.primaryFill,
                unselectedColor = colors.textMuted,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.text,
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    selectedLocale: AppLocale,
    onSelectLocale: (AppLocale) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Thi360Theme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgSurface,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        modifier = Modifier.testTag(AccountTestTags.LANGUAGE_DIALOG),
        title = { Text(text = stringResource(R.string.account_language_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LanguageOptionRow(
                    label = stringResource(R.string.account_language_vi),
                    selected = selectedLocale == AppLocale.VI,
                    onClick = { onSelectLocale(AppLocale.VI) },
                    testTag = AccountTestTags.LANGUAGE_OPTION_VI,
                )
                LanguageOptionRow(
                    label = stringResource(R.string.account_language_en),
                    selected = selectedLocale == AppLocale.EN,
                    onClick = { onSelectLocale(AppLocale.EN) },
                    testTag = AccountTestTags.LANGUAGE_OPTION_EN,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.account_logout_cancel),
                    color = colors.primaryText,
                )
            }
        },
    )
}

@Composable
private fun LogoutConfirmationDialog(
    isLoggingOut: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Thi360Theme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgSurface,
        titleContentColor = colors.text,
        textContentColor = colors.textSecondary,
        modifier = Modifier.testTag(AccountTestTags.LOGOUT_DIALOG),
        title = { Text(text = stringResource(R.string.account_logout_confirm_title)) },
        text = { Text(text = stringResource(R.string.account_logout_confirm_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isLoggingOut,
                modifier = Modifier.testTag(AccountTestTags.LOGOUT_CONFIRM_BUTTON),
            ) {
                Text(
                    text = stringResource(R.string.account_logout_confirm_action),
                    color = colors.dangerText,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoggingOut,
                modifier = Modifier.testTag(AccountTestTags.LOGOUT_CANCEL_BUTTON),
            ) {
                Text(
                    text = stringResource(R.string.account_logout_cancel),
                    color = colors.primaryText,
                )
            }
        },
    )
}
