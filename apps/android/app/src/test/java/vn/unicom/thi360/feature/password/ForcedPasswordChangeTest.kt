package vn.unicom.thi360.feature.password

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway
import vn.unicom.thi360.feature.recovery.PasswordTestTags
import vn.unicom.thi360.feature.recovery.RecoveryGateway
import vn.unicom.thi360.feature.recovery.RegistrationStartData
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class ForcedPasswordChangeTest {

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

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun loginMustChangePasswordBlocksTabsAndCompletingChangeEntersShellWithoutRelogin() {
        val tokenStore = InMemoryTokenStore()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)

        server.respondAuthFixture(
            "POST",
            "/api/v1/auth/mobile/login",
            200,
            "login-200-must-change-password.json",
        )
        server.respondEmpty("POST", "/api/v1/auth/change-password", 200)

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                recoveryGateway = gateway,
            )
        }

        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).performTextInput("student.forced")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("TempPass1!")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.MustChangePassword
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.LOGOUT_BUTTON).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()
        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertDoesNotExist()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).assertDoesNotExist()

        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).performScrollTo().performTextInput("TempPass1!")
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performScrollTo().performTextInput("BrandNew1!")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performScrollTo().performTextInput("BrandNew1!")
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.Active
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home)).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertIsDisplayed()
        assertEquals(false, tokenStore.read()?.mustChangePassword)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun refreshReturningMustChangePasswordBlocksTabsAndLogoutExitsToLogin() {
        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "active-access",
                refreshToken = "active-refresh",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.refreshed",
            ),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)

        server.respondAuthFixture(
            "POST",
            "/api/v1/auth/mobile/refresh",
            200,
            "login-200-must-change-password.json",
        )
        server.respondEmpty("POST", "/api/v1/auth/mobile/logout", 204)

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                recoveryGateway = gateway,
            )
        }

        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertIsDisplayed()

        runBlocking { stack.sessionManager.refreshSession() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()
        composeRule.onNodeWithTag(PasswordTestTags.FORCED_DESCRIPTION)
            .assertTextContains("student.refreshed", substring = true)

        composeRule.onNodeWithTag(PasswordTestTags.LOGOUT_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.SignedOut
        }
        composeRule.waitForIdle()
        assertNull(tokenStore.read())
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun forcedSubmitDisablesButtonWhileInFlight() {
        val release = CompletableDeferred<ApiResult<Unit>>()
        var calls = 0
        val gatedGateway = object : RecoveryGateway {
            override suspend fun startRegistration(
                fullName: String,
                username: String,
                email: String,
                password: String,
                termsVersion: String,
                registrationId: UUID?,
            ): ApiResult<RegistrationStartData> = error("unused")

            override suspend fun resendRegistrationCode(registrationId: UUID): ApiResult<RegistrationStartData> = error("unused")
            override suspend fun verifyRegistration(registrationId: UUID, code: String, username: String?): ApiResult<Tokens> = error("unused")
            override suspend fun forgotPassword(email: String): ApiResult<Unit> = error("unused")
            override suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> = error("unused")

            override suspend fun changePassword(oldPassword: String, newPassword: String): ApiResult<Unit> {
                calls++
                return release.await()
            }
        }
        val tokenStore = InMemoryTokenStore(
            Tokens("a", "r", 300, 604800, mustChangePassword = true, username = "student.forced"),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                recoveryGateway = gatedGateway,
            )
        }

        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).performScrollTo().performTextInput("TempPass1!")
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performScrollTo().performTextInput("BrandNew1!")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performScrollTo().performTextInput("BrandNew1!")
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 3_000) { calls == 1 }
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performClick()
        assertEquals(1, calls)

        release.complete(ApiResult.Success(Unit))
        composeRule.waitUntil(timeoutMillis = 3_000) {
            stack.sessionManager.state.value is SessionState.Active
        }
        assertTrue(stack.sessionManager.state.value is SessionState.Active)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun externalSignOutClearsForcedPasswordDraftAcrossUsersWithNullUsername() {
        val tokenStore = InMemoryTokenStore(
            Tokens("a1", "r1", 300, 604800, mustChangePassword = true, username = null),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)
        val retainedViewModelStore = androidx.lifecycle.ViewModelStore()
        val retainedOwner = object : androidx.lifecycle.ViewModelStoreOwner {
            override val viewModelStore: androidx.lifecycle.ViewModelStore = retainedViewModelStore
        }

        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides retainedOwner,
            ) {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = stack.sessionManager,
                    validateOnLaunch = false,
                    recoveryGateway = gateway,
                )
            }
        }

        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).performScrollTo().performTextInput("UserAOld1!")
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performScrollTo().performTextInput("UserANew2@")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performScrollTo().performTextInput("UserANew2@")

        runBlocking {
            stack.sessionManager.clearSession(vn.unicom.thi360.core.session.SignOutReason.Expired)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        runBlocking {
            stack.sessionManager.adoptSession(
                Tokens("a2", "r2", 300, 604800, mustChangePassword = true, username = null),
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_TOGGLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_TOGGLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_TOGGLE).performScrollTo().performClick()

        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).performScrollTo().assertTextContains("", substring = false)
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performScrollTo().assertTextContains("", substring = false)
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performScrollTo().assertTextContains("", substring = false)

        retainedViewModelStore.clear()
    }
}
