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
import okhttp3.mockwebserver.MockWebServer
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
import vn.unicom.thi360.core.network.ContractFixtureLoader
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

@RunWith(RobolectricTestRunner::class)
class AccountScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
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
            baseUrl = server.url("/").toString(),
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

        // 1. Loading state
        composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_SINGLE_PANE).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_LOADING).assertIsDisplayed()

        // Unavailable biometric switch is disabled with explanation and TalkBack contentDescription.
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

        // 2. Error state + Retry + local settings still usable
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

        // 3. Offline state on retry
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_RETRY_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_OFFLINE).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        // 4. Loaded state on retry
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
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                200,
                ContractFixtureLoader.readApiFixture("me-profile-200-no-email.json"),
            ),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
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
                profileGateway = OpenApiAccountProfileGateway(stack.profileApi),
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
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                200,
                ContractFixtureLoader.readApiFixture("me-profile-200.json"),
            ),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
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
                profileGateway = OpenApiAccountProfileGateway(stack.profileApi),
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
        composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.CHANGE_PASSWORD_ROW).assertIsSelected()
        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_ROW).assertIsNotSelected()
        composeRule.onNodeWithTag(AccountTestTags.THEME_ROW).assertIsNotSelected()

        // Selecting Language switches right detail pane and updates selection semantics & app copy immediately.
        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_ROW).assertIsSelected()
        composeRule.onNodeWithTag(AccountTestTags.CHANGE_PASSWORD_ROW).assertIsNotSelected()
        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_OPTION_EN).performClick()
        assertEquals(AppLocale.EN, preferences.locale)
        composeRule.onNodeWithText("Security").assertIsDisplayed()

        // Selecting Theme switches right detail pane and updates theme preference.
        composeRule.onNodeWithTag(AccountTestTags.THEME_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.THEME_ROW).assertIsSelected()
        composeRule.onNodeWithTag(AccountTestTags.THEME_OPTION_DARK).performClick()
        assertEquals(ThemeMode.DARK, preferences.themeMode)

        // Selecting Change Password remounts AND-3 ChangePasswordScreen in right pane.
        composeRule.onNodeWithTag(AccountTestTags.CHANGE_PASSWORD_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.CHANGE_PASSWORD_ROW).assertIsSelected()
        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun foldFromTwoPaneToSinglePaneMidPasswordDraftPreservesChangePasswordScreenAndDraft() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(signedInTokens("student.fold")),
            localeProvider = { AppLocale.VI },
        )
        val profile = UserProfile("student.fold", "Học viên Gập", "fold@thi360.vn", true)
        var isTwoPaneWidth by mutableStateOf(true)
        var selectedPane by mutableStateOf(AccountDetailPane.CHANGE_PASSWORD)
        var showSinglePaneChangePassword by mutableStateOf(false)
        var profileState by mutableStateOf<ProfileLoadState>(ProfileLoadState.Loaded(profile))

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                AccountScreen(
                    isTwoPaneWidth = isTwoPaneWidth,
                    usernameFallback = "student.fold",
                    preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = stack.sessionManager,
                    profileGateway = object : AccountProfileGateway {
                        override suspend fun fetchProfile(): ApiResult<UserProfile> = ApiResult.Success(profile)
                    },
                    recoveryGateway = OpenApiRecoveryGateway(
                        publicAuthApi = stack.publicAuthApi,
                        authenticatedAuthApi = stack.authenticatedAuthApi,
                    ),
                    biometricController = null,
                    biometricAuthenticator = null,
                    profileState = profileState,
                    onProfileStateChanged = { profileState = it },
                    selectedPane = selectedPane,
                    onSelectPane = { selectedPane = it },
                    showSinglePaneChangePassword = showSinglePaneChangePassword,
                    onShowSinglePaneChangePassword = { showSinglePaneChangePassword = it },
                )
            }
        }

        // User starts typing into the mounted two-pane ChangePasswordScreen without clicking the left row.
        composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).performTextInput("OldSecret1!")
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performTextInput("NewSecret1!")

        // Device folds below 840dp: ChangePasswordScreen must stay mounted with draft preserved.
        composeRule.runOnIdle { isTwoPaneWidth = false }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).assertTextContains("OldSecret1!")
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).assertTextContains("NewSecret1!")

        // Navigating back from single-pane ChangePasswordScreen returns to the Account list.
        composeRule.onNodeWithTag(PasswordTestTags.CLOSE_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_SINGLE_PANE).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 2.0f)
    fun narrowViewportAt200PercentFontScaleKeepsThemeRowReadableAndActionable() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
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
