package vn.unicom.thi360.feature.account

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Instant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.biometric.BiometricState
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val verifiedProfile = UserProfile(
        username = "minhanh.tran",
        fullName = "Trần Minh Anh",
        email = "minhanh@example.com",
        emailVerified = true,
        phone = "0912345678",
    )

    private val unverifiedPendingProfile = UserProfile(
        username = "minhanh.tran",
        fullName = "Trần Minh Anh",
        email = "minhanh@example.com",
        emailVerified = false,
        phone = "0912345678",
        pendingEmail = "minhanh.new@example.com",
    )

    private val noEmailProfile = UserProfile(
        username = "minhanh.tran",
        fullName = "Trần Minh Anh",
        email = null,
        emailVerified = false,
    )

    private val readyWarningsCheck = DeletionCheck(
        canDelete = true,
        blockers = emptyList(),
        unusedAttempts = 3,
        pendingGrading = true,
    )

    private val blockedAllCheck = DeletionCheck(
        canDelete = false,
        blockers = listOf(
            DeletionBlocker.ATTEMPT_IN_PROGRESS,
            DeletionBlocker.ORDER_BEING_CHECKED,
            DeletionBlocker.ORDER_PENDING_PAYMENT,
        ),
        unusedAttempts = 0,
        pendingGrading = false,
    )

    private fun buildDeps(
        profile: UserProfile,
        deletionCheck: DeletionCheck = readyWarningsCheck,
    ): ProfileDependencies {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val gateway = object : ProfileGateway {
            override suspend fun fetch(): ApiResult<UserProfile> = ApiResult.Success(profile)
            override suspend fun update(fullName: String, phone: String?) = ProfileOperationResult.Success(profile)
            override suspend fun uploadAvatar(avatar: PreparedAvatar) = ProfileOperationResult.Success(profile)
            override suspend fun removeAvatar() = ProfileOperationResult.Success(Unit)
            override suspend fun sendVerification() = ProfileOperationResult.Success(Unit)
            override suspend fun verify(code: String) = ProfileOperationResult.Success(Unit)
            override suspend fun requestEmailChange(newEmail: String, currentPassword: String) =
                ProfileOperationResult.Success(Unit)
            override suspend fun confirmEmailChange(code: String) = ProfileOperationResult.Success(Unit)
        }
        val deletionGateway = object : AccountDeletionGateway {
            override suspend fun check(): ApiResult<DeletionCheck> = ApiResult.Success(deletionCheck)
            override suspend fun delete(currentPassword: String): AccountDeletionOutcome = AccountDeletionOutcome.Deleted
        }
        val store = AccountProfileStore(gateway).apply { replace(profile) }
        return ProfileDependencies(
            profileStore = store,
            profileGateway = gateway,
            accountDeletionGateway = deletionGateway,
            sessionManager = stack.sessionManager,
            imageLoader = SingletonImageLoader.get(ApplicationProvider.getApplicationContext()),
            apiBaseUrl = server.url("/"),
            clock = MutableTestClock(Instant.parse("2026-04-20T10:00:00Z")),
        )
    }

    private fun captureProfile(
        name: String,
        profile: UserProfile,
        themeMode: ThemeMode,
        fontScale: Float = 1.0f,
    ) {
        val deps = buildDeps(profile)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode)) {
                    ProfileScreen(dependencies = deps)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun captureVerifyEmail(
        name: String,
        themeMode: ThemeMode,
    ) {
        val deps = buildDeps(unverifiedPendingProfile.copy(pendingEmail = null))
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode)) {
                VerifyEmailScreen(
                    onBackToProfile = {},
                    dependencies = deps,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun captureAccountTwoPaneWithProfile(name: String, themeMode: ThemeMode) {
        val deps = buildDeps(verifiedProfile)
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode)) {
                AccountScreenContent(
                    isTwoPaneWidth = true,
                    profileState = LoadState.Loaded(verifiedProfile),
                    preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode),
                    biometricState = BiometricState.On,
                    selectedPane = AccountDetailPane.PROFILE,
                    isUpdatingBiometric = false,
                    showBiometricError = false,
                    isLoggingOut = false,
                    onRetryProfile = {},
                    onChangePasswordClick = {},
                    onToggleBiometric = {},
                    onLanguageRowClick = {},
                    onThemeRowClick = {},
                    onSelectThemeMode = {},
                    onLogoutClick = {},
                    detailContent = {
                        ProfileScreen(embeddedInPane = true, dependencies = deps)
                    },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun captureAccountTwoPaneWithDeleteAccount(
        name: String,
        check: DeletionCheck,
        themeMode: ThemeMode,
    ) {
        val deps = buildDeps(verifiedProfile, deletionCheck = check)
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode)) {
                AccountScreenContent(
                    isTwoPaneWidth = true,
                    profileState = LoadState.Loaded(verifiedProfile),
                    preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode),
                    biometricState = BiometricState.On,
                    selectedPane = AccountDetailPane.DELETE_ACCOUNT,
                    isUpdatingBiometric = false,
                    showBiometricError = false,
                    isLoggingOut = false,
                    onRetryProfile = {},
                    onChangePasswordClick = {},
                    onToggleBiometric = {},
                    onLanguageRowClick = {},
                    onThemeRowClick = {},
                    onSelectThemeMode = {},
                    onLogoutClick = {},
                    detailContent = {
                        DeleteAccountScreen(embeddedInPane = true, dependencies = deps)
                    },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun captureDeleteAccount(
        name: String,
        check: DeletionCheck,
        themeMode: ThemeMode,
    ) {
        val deps = buildDeps(verifiedProfile, deletionCheck = check)
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode)) {
                DeleteAccountScreen(dependencies = deps)
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun account_twopane_profile_840x720_light() =
        captureAccountTwoPaneWithProfile("account_twopane_profile_840x720_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun account_twopane_profile_840x720_dark() =
        captureAccountTwoPaneWithProfile("account_twopane_profile_840x720_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun account_twopane_profile_1280x800_light() =
        captureAccountTwoPaneWithProfile("account_twopane_profile_1280x800_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun account_twopane_profile_1280x800_dark() =
        captureAccountTwoPaneWithProfile("account_twopane_profile_1280x800_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun profile_verified_360x640_light() =
        captureProfile("profile_verified_360x640_light", verifiedProfile, ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun profile_verified_360x640_dark() =
        captureProfile("profile_verified_360x640_dark", verifiedProfile, ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun profile_verified_412x915_light() =
        captureProfile("profile_verified_412x915_light", verifiedProfile, ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun profile_verified_412x915_dark() =
        captureProfile("profile_verified_412x915_dark", verifiedProfile, ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun profile_unverified_pending_412x915_light() =
        captureProfile("profile_unverified_pending_412x915_light", unverifiedPendingProfile, ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun profile_unverified_pending_412x915_dark() =
        captureProfile("profile_unverified_pending_412x915_dark", unverifiedPendingProfile, ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun profile_no_email_412x915_light() =
        captureProfile("profile_no_email_412x915_light", noEmailProfile, ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun profile_no_email_412x915_dark() =
        captureProfile("profile_no_email_412x915_dark", noEmailProfile, ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun profile_verified_360x640_font130_light() =
        captureProfile("profile_verified_360x640_font130_light", verifiedProfile, ThemeMode.LIGHT, fontScale = 1.3f)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun verify_email_360x640_light() =
        captureVerifyEmail("verify_email_360x640_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun verify_email_412x915_light() =
        captureVerifyEmail("verify_email_412x915_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun verify_email_412x915_dark() =
        captureVerifyEmail("verify_email_412x915_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun delete_account_ready_warnings_412x915_light() =
        captureDeleteAccount("delete_account_ready_warnings_412x915_light", readyWarningsCheck, ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun delete_account_ready_warnings_412x915_dark() =
        captureDeleteAccount("delete_account_ready_warnings_412x915_dark", readyWarningsCheck, ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun delete_account_blocked_412x915_light() =
        captureDeleteAccount("delete_account_blocked_412x915_light", blockedAllCheck, ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun delete_account_blocked_412x915_dark() =
        captureDeleteAccount("delete_account_blocked_412x915_dark", blockedAllCheck, ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun delete_account_ready_warnings_840x720_light() =
        captureAccountTwoPaneWithDeleteAccount("delete_account_ready_warnings_840x720_light", readyWarningsCheck, ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun delete_account_blocked_840x720_dark() =
        captureAccountTwoPaneWithDeleteAccount("delete_account_blocked_840x720_dark", blockedAllCheck, ThemeMode.DARK)
}
