package vn.unicom.thi360.feature.password

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.runtime.CompositionLocalProvider
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway
import vn.unicom.thi360.feature.recovery.PasswordTestTags
import vn.unicom.thi360.feature.recovery.RecoveryGateway
import vn.unicom.thi360.feature.recovery.RegistrationStartData
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.Thi360App

@RunWith(RobolectricTestRunner::class)
class ChangePasswordTest {

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

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun voluntaryChangeValidatesFieldErrorsAndPreservesSessionOnSuccess() {
        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "active-access-token",
                refreshToken = "active-refresh-token",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "UserPass1!",
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
                400,
                ContractFixtureLoader.readApiFixture("change-password-400-wrong-old-password.json"),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200))

        var callbackCount = 0

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ChangePasswordScreen(
                    username = "UserPass1!",
                    recoveryGateway = gateway,
                    onPasswordChanged = { callbackCount++ },
                )
            }
        }

        // Weak password + same as username client validation blocks network call.
        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).performTextInput("OldPass1!")
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performTextInput("short")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("short")
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_ERROR, useUnmergedTree = true)
            .assertTextContains("Mật khẩu mới chưa đáp ứng đủ các điều kiện.")
        assertEquals(0, server.requestCount)

        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performTextClearance()
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performTextInput("userpass1!")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performTextClearance()
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("userpass1!")
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_ERROR, useUnmergedTree = true)
            .assertTextContains("Mật khẩu mới không được trùng tên đăng nhập.")
        assertEquals(0, server.requestCount)

        // Wrong old password from backend -> shows error under old password field.
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performTextClearance()
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performTextInput("StrongNew2!")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performTextClearance()
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("StrongNew2!")
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_ERROR, useUnmergedTree = true)
                    .assertTextContains("Mật khẩu hiện tại không đúng.")
                true
            }.getOrDefault(false)
        }
        val wrongOldReq = server.takeRequest()
        assertEquals("/api/v1/auth/change-password", wrongOldReq.path)
        assertEquals("Bearer active-access-token", wrongOldReq.getHeader("Authorization"))

        // Correct old password -> success banner shown and session remains Active with tokens intact.
        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).performTextClearance()
        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).performTextInput("RealOldPass1!")
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(PasswordTestTags.SUCCESS_BANNER)
                    .assertIsDisplayed()
                    .assertTextContains("Đã đổi mật khẩu thành công.")
                true
            }.getOrDefault(false)
        }
        assertEquals(1, callbackCount)
        assertEquals(SessionState.Active(username = "UserPass1!", isOffline = false), stack.sessionManager.state.value)
        assertNotNull("Voluntary password change must preserve session tokens", tokenStore.read())
    }

    @Test
    @Config(qualifiers = "w1280dp-h915dp")
    fun survivesConfigurationChange() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)

        val retainedViewModelStore = ViewModelStore()
        val retainedOwner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = retainedViewModelStore
        }

        var widthDp by mutableStateOf(412.dp)
        var heightDp by mutableStateOf(915.dp)
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides retainedOwner) {
                Box(modifier = Modifier.size(width = widthDp, height = heightDp)) {
                    Thi360App(
                        preferences = AppPreferences(locale = AppLocale.VI),
                        startDestination = Destination.Account.route,
                        accountContent = { isTwoPane ->
                            ChangePasswordScreen(
                                username = "minhanh.tran",
                                recoveryGateway = gateway,
                                embeddedInPane = isTwoPane,
                            )
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).performScrollTo().performTextInput("OldDraft1!")
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performScrollTo().performTextInput("NewDraft2@")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performScrollTo().performTextInput("NewDraft2@")

        // Unfold to 840x720 + emulate Activity config-change recreation -> all 3 fields survive in retained ViewModelStore.
        composeRule.runOnIdle {
            widthDp = 840.dp
            heightDp = 720.dp
        }
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_TOGGLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_TOGGLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_TOGGLE).performScrollTo().performClick()

        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).performScrollTo().assertTextContains("OldDraft1!")
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performScrollTo().assertTextContains("NewDraft2@")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performScrollTo().assertTextContains("NewDraft2@")

        // Fold back to compact 360x640 + emulate config-change recreation -> all 3 fields still preserved.
        composeRule.runOnIdle {
            widthDp = 360.dp
            heightDp = 640.dp
        }
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_TOGGLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_TOGGLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_TOGGLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).performScrollTo().assertTextContains("OldDraft1!")
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performScrollTo().assertTextContains("NewDraft2@")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performScrollTo().assertTextContains("NewDraft2@")

        retainedViewModelStore.clear()
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
            override suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> = error("unused")

            override suspend fun changePassword(oldPassword: String, newPassword: String): ApiResult<Unit> {
                calls++
                return release.await()
            }
        }

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ChangePasswordScreen(
                    username = "student.demo",
                    recoveryGateway = gatedGateway,
                )
            }
        }

        composeRule.onNodeWithTag(PasswordTestTags.OLD_PASSWORD_FIELD).performTextInput("OldPass1!")
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performTextInput("StrongNew2!")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("StrongNew2!")
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 3_000) { calls == 1 }
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performClick()
        assertEquals(1, calls)

        release.complete(ApiResult.Success(Unit))
        composeRule.waitUntil(timeoutMillis = 3_000) {
            runCatching {
                composeRule.onNodeWithTag(PasswordTestTags.SUCCESS_BANNER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, calls)
    }
}
