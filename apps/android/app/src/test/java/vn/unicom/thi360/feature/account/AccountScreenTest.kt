package vn.unicom.thi360.feature.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.biometric.BiometricAvailability
import vn.unicom.thi360.core.biometric.BiometricController
import vn.unicom.thi360.core.biometric.BiometricUnavailableReason
import vn.unicom.thi360.core.biometric.FakeBiometricAuthenticator
import vn.unicom.thi360.core.biometric.FakeBiometricAvailabilityChecker
import vn.unicom.thi360.core.biometric.FakeBiometricVault
import vn.unicom.thi360.core.biometric.InMemoryBiometricPromptMemoryStore
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiError
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway
import vn.unicom.thi360.feature.recovery.PasswordTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class AccountScreenTest {

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

    private fun signedInTokens(username: String = "student.demo") = Tokens(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        accessExpiresIn = 300,
        refreshExpiresIn = 604800,
        mustChangePassword = false,
        username = username,
    )

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun profileFourStatesAndLocalSettingsStayUsableOnErrorAndOffline() {
        val gate = CompletableDeferred<ApiResult<UserProfile>>()
        var attempt = 0
        val scriptedGateway = object : AccountProfileGateway {
            override suspend fun fetchProfile(): ApiResult<UserProfile> {
                attempt++
                return when (attempt) {
                    1 -> gate.await()
                    2 -> ApiResult.NetworkFailure(IOException("Offline"))
                    3 -> ApiResult.Success(
                        UserProfile(
                            username = "student.demo",
                            fullName = "Nguyễn Văn An",
                            email = "student.demo@thi360.vn",
                            emailVerified = true,
                        ),
                    )
                    else -> error("Unexpected attempt $attempt")
                }
            }
        }
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(signedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val biometricController = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(
                BiometricAvailability.Unavailable(BiometricUnavailableReason.NOT_ENROLLED),
            ),
            vault = FakeBiometricVault(),
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        var preferences by mutableStateOf(AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT))

        composeRule.setContent {
            AppRoot(
                preferences = preferences,
                onSelectLocale = { preferences = preferences.copy(locale = it) },
                onSelectThemeMode = { preferences = preferences.copy(themeMode = it) },
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                startDestination = Destination.Account.route,
                profileGateway = scriptedGateway,
                biometricController = biometricController,
                biometricAuthenticator = FakeBiometricAuthenticator(),
            )
        }

        composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_SINGLE_PANE).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_LOADING).assertIsDisplayed()

        val switchNode = composeRule.onNodeWithTag(AccountTestTags.BIOMETRIC_SWITCH)
            .assertIsNotEnabled()
            .fetchSemanticsNode()
        val contentDescriptions = switchNode.config
            .getOrElseNullable(SemanticsProperties.ContentDescription) { null }
            .orEmpty()
        assertTrue(
            "Biometric switch must expose a non-empty ContentDescription for TalkBack",
            contentDescriptions.any { it.isNotBlank() },
        )
        composeRule.onNodeWithTag(AccountTestTags.BIOMETRIC_EXPLANATION).assertIsDisplayed()

        gate.complete(ApiResult.HttpFailure(500, ApiError(code = "INTERNAL_ERROR")))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_ERROR).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(AccountTestTags.THEME_OPTION_DARK).performScrollTo().performClick()
        assertEquals(ThemeMode.DARK, preferences.themeMode)

        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_OPTION_EN).performClick()
        assertEquals(AppLocale.EN, preferences.locale)
        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_OPTION_VI).performClick()
        assertEquals(AppLocale.VI, preferences.locale)

        composeRule.onNodeWithTag(AccountTestTags.PROFILE_RETRY_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_OFFLINE).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(AccountTestTags.PROFILE_RETRY_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_CARD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_FULL_NAME).assertTextContains("Nguyễn Văn An")
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_USERNAME).assertTextContains("student.demo")
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_EMAIL).assertTextContains("student.demo@thi360.vn")
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_EMAIL_BADGE).assertTextContains("Email đã xác minh")
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun accountWithoutEmailHidesEmailRowAndVerificationBadge() {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200-no-email.json")
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(signedInTokens("student.noemail")),
            localeProvider = { AppLocale.VI },
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                startDestination = Destination.Account.route,
                profileGateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/")),
                apiBaseUrl = server.url("/"),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_CARD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_FULL_NAME).assertTextContains("Nguyễn Văn Bình")
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_USERNAME).assertTextContains("student.noemail")
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_EMAIL).assertDoesNotExist()
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_EMAIL_BADGE).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun twoPane() {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(signedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        var preferences by mutableStateOf(AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT))

        composeRule.setContent {
            AppRoot(
                preferences = preferences,
                onSelectLocale = { preferences = preferences.copy(locale = it) },
                onSelectThemeMode = { preferences = preferences.copy(themeMode = it) },
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                startDestination = Destination.Account.route,
                profileGateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/")),
                apiBaseUrl = server.url("/"),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_CARD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_TWO_PANE).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_SINGLE_PANE).assertDoesNotExist()
        composeRule.onNodeWithTag(AccountTestTags.DETAIL_PANE).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).assertIsSelected()
        composeRule.onNodeWithTag(AccountTestTags.CHANGE_PASSWORD_ROW).assertIsNotSelected()
        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_ROW).assertIsNotSelected()
        composeRule.onNodeWithTag(AccountTestTags.THEME_ROW).assertIsNotSelected()

        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_ROW).assertIsSelected()
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).assertIsNotSelected()
        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_OPTION_EN).performClick()
        assertEquals(AppLocale.EN, preferences.locale)
        composeRule.onNodeWithText("Security").assertIsDisplayed()

        composeRule.onNodeWithTag(AccountTestTags.THEME_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.THEME_ROW).assertIsSelected()
        composeRule.onNodeWithTag(AccountTestTags.THEME_OPTION_DARK).performClick()
        assertEquals(ThemeMode.DARK, preferences.themeMode)

        composeRule.onNodeWithTag(AccountTestTags.CHANGE_PASSWORD_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.CHANGE_PASSWORD_ROW).assertIsSelected()
        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun foldFromTwoPaneToSinglePaneMidPasswordDraftPreservesChangePasswordScreenAndDraft() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(signedInTokens("student.fold")),
            localeProvider = { AppLocale.VI },
        )
        val profile = UserProfile("student.fold", "Học viên Gập", "fold@thi360.vn", true)
        var isTwoPaneWidth by mutableStateOf(true)
        var selectedPane by mutableStateOf(AccountDetailPane.CHANGE_PASSWORD)
        var showSinglePaneChangePassword by mutableStateOf(false)
        val profileStore = AccountProfileStore(
            gateway = object : ProfileGateway {
                override suspend fun fetch(): ApiResult<UserProfile> = ApiResult.Success(profile)
                override suspend fun update(fullName: String, phone: String?) = error("unused")
                override suspend fun uploadAvatar(avatar: PreparedAvatar) = error("unused")
                override suspend fun removeAvatar() = error("unused")
                override suspend fun sendVerification() = error("unused")
                override suspend fun verify(code: String) = error("unused")
                override suspend fun requestEmailChange(newEmail: String, currentPassword: String) = error("unused")
                override suspend fun confirmEmailChange(code: String) = error("unused")
            },
        ).apply { replace(profile) }

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                AccountScreen(
                    isTwoPaneWidth = isTwoPaneWidth,
                    usernameFallback = "student.fold",
                    preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = stack.sessionManager,
                    profileStore = profileStore,
                    recoveryGateway = OpenApiRecoveryGateway(
                        publicAuthApi = stack.publicAuthApi,
                        authenticatedAuthApi = stack.authenticatedAuthApi,
                    ),
                    biometricController = null,
                    biometricAuthenticator = null,
                    selectedPane = selectedPane,
                    onSelectPane = { selectedPane = it },
                    showSinglePaneChangePassword = showSinglePaneChangePassword,
                    onShowSinglePaneChangePassword = { showSinglePaneChangePassword = it },
                )
            }
        }

        composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).performTextInput("OldSecret1!")
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performTextInput("NewSecret1!")

        composeRule.runOnIdle { isTwoPaneWidth = false }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).assertTextContains("OldSecret1!")
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).assertTextContains("NewSecret1!")

        composeRule.onNodeWithTag(PasswordTestTags.CLOSE_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_SINGLE_PANE).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 2.0f)
    fun narrowViewportAt200PercentFontScaleKeepsThemeRowReadableAndActionable() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(signedInTokens()),
            localeProvider = { AppLocale.EN },
        )
        var preferences by mutableStateOf(AppPreferences(locale = AppLocale.EN, themeMode = ThemeMode.LIGHT))
        val profile = UserProfile("student.demo", "Demo Student", "demo@thi360.vn", true)

        composeRule.setContent {
            AppRoot(
                preferences = preferences,
                onSelectLocale = { preferences = preferences.copy(locale = it) },
                onSelectThemeMode = { preferences = preferences.copy(themeMode = it) },
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                startDestination = Destination.Account.route,
                profileGateway = object : AccountProfileGateway {
                    override suspend fun fetchProfile(): ApiResult<UserProfile> = ApiResult.Success(profile)
                },
            )
        }

        composeRule.onNodeWithTag(AccountTestTags.THEME_ROW).performScrollTo().assertIsDisplayed()
        val labelBounds = composeRule.onNodeWithText("Appearance").getUnclippedBoundsInRoot()
        assertTrue(
            "Appearance label must not be crushed at 360dp @ 200% font scale (width=${labelBounds.right - labelBounds.left})",
            (labelBounds.right - labelBounds.left) >= 80.dp,
        )
        composeRule.onNodeWithTag(AccountTestTags.THEME_OPTION_DARK).performScrollTo().performClick()
        assertEquals(ThemeMode.DARK, preferences.themeMode)
    }
}
