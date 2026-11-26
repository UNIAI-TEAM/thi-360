package vn.unicom.thi360.feature.password

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway
import vn.unicom.thi360.feature.recovery.PasswordTestTags
import vn.unicom.thi360.feature.recovery.RecoveryGateway
import vn.unicom.thi360.feature.recovery.RecoveryTestTags
import vn.unicom.thi360.feature.recovery.RegistrationStartData
import vn.unicom.thi360.feature.recovery.ResetLink
import vn.unicom.thi360.shell.AppRoot

@RunWith(RobolectricTestRunner::class)
class NewPasswordFromLinkTest {

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

    private fun resetIntent(token: String): Intent {
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://$host/reset-password?token=$token"))
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun successfulResetClearsExistingLocalSessionAndShowsLoginResetBanner() {
        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "existing-user-access",
                refreshToken = "existing-user-refresh",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "other.student",
            ),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)
        server.enqueue(MockResponse().setResponseCode(204))

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                recoveryGateway = gateway,
                initialIntent = resetIntent("valid-reset-token-123"),
            )
        }

        composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.LINK_BANNER).assertIsDisplayed()

        // Client-side validation: weak password and mismatch block API call.
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performTextInput("weak")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("different")
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_ERROR, useUnmergedTree = true)
            .assertTextContains("Mật khẩu mới chưa đáp ứng đủ các điều kiện.")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_ERROR, useUnmergedTree = true)
            .assertTextContains("Mật khẩu nhập lại chưa khớp.")
        assertEquals("Invalid input must not call reset API", 0, server.requestCount)

        // Valid matching password resets and wipes existing local session.
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performTextClearance()
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performTextInput("NewStrongPass1!")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performTextClearance()
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("NewStrongPass1!")
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value == SessionState.SignedOut(SignOutReason.PasswordReset)
        }
        composeRule.waitForIdle()

        assertNull("Successful password reset must clear any existing local session", tokenStore.read())
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.NOTICE_BANNER)
            .assertIsDisplayed()
            .assertTextContains("Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới.")
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun resetTokenInvalidShowsInvalidLinkScreenWithLinkToForgotPassword() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                422,
                ContractFixtureLoader.readApiFixture("password-reset-422-reset-token-invalid.json"),
            ),
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                recoveryGateway = gateway,
                initialIntent = resetIntent("expired-token-999"),
            )
        }

        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performTextInput("NewStrongPass1!")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("NewStrongPass1!")
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(PasswordTestTags.SCREEN_INVALID_LINK).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(PasswordTestTags.INVALID_LINK_TITLE)
            .assertTextContains("Liên kết không còn dùng được")

        composeRule.onNodeWithTag(PasswordTestTags.INVALID_LINK_REQUEST_NEW).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SCREEN_FORM).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun submitsOnceWhileInFlight() {
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

            override suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> {
                calls++
                return release.await()
            }

            override suspend fun changePassword(oldPassword: String, newPassword: String): ApiResult<Unit> = error("unused")
        }
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
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
                initialIntent = resetIntent("single-submit-token"),
            )
        }

        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performTextInput("NewStrongPass1!")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("NewStrongPass1!")
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 3_000) { calls == 1 }
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performClick()
        assertEquals(1, calls)

        release.complete(ApiResult.Success(Unit))
        composeRule.waitUntil(timeoutMillis = 3_000) {
            stack.sessionManager.state.value == SessionState.SignedOut(SignOutReason.PasswordReset)
        }
        assertEquals(1, calls)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun signedInInvalidResetLinkOpensForgotOverlayAndSystemBackReturnsToActiveShell() {
        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "active-user-access",
                refreshToken = "active-user-refresh",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "active.student",
            ),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                422,
                ContractFixtureLoader.readApiFixture("password-reset-422-reset-token-invalid.json"),
            ),
        )

        var dispatcher: androidx.activity.OnBackPressedDispatcher? = null

        composeRule.setContent {
            dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                recoveryGateway = gateway,
                initialIntent = resetIntent("expired-while-signed-in"),
            )
        }

        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performTextInput("NewStrongPass1!")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("NewStrongPass1!")
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(PasswordTestTags.SCREEN_INVALID_LINK).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(PasswordTestTags.INVALID_LINK_REQUEST_NEW).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SCREEN_FORM).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        // System Back must dismiss the Forgot Password overlay back to the Active shell, not finish Activity.
        composeRule.runOnIdle { checkNotNull(dispatcher).onBackPressed() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(vn.unicom.thi360.shell.ShellTestTags.BOTTOM_BAR).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SCREEN_FORM).assertDoesNotExist()
    }
}
