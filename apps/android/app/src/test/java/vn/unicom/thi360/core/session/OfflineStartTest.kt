package vn.unicom.thi360.core.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.ApiError
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags

@RunWith(RobolectricTestRunner::class)
class OfflineStartTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun offlineLaunchNeverExpiresByDeviceClockAndRetryRefreshDecidesWhenOnline() {
        // Even with 1-second expiry fields, app must never expire by device clock while offline.
        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "stored-access",
                refreshToken = "stored-refresh",
                accessExpiresIn = 1,
                refreshExpiresIn = 1,
                mustChangePassword = false,
                username = "student.offline",
            ),
        )
        var nextRefreshResult: ApiResult<Tokens> = ApiResult.NetworkFailure(IOException("Offline"))
        val gateway = object : MobileAuthGateway {
            override suspend fun login(identifier: String, password: String): ApiResult<Tokens> =
                error("unused")

            override suspend fun refresh(refreshToken: String): ApiResult<Tokens> = nextRefreshResult

            override suspend fun logout(refreshToken: String): ApiResult<Unit> = ApiResult.Success(Unit)
        }
        val sessionManager = SessionManager(tokenStore = tokenStore, authGateway = gateway)

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = sessionManager,
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            sessionManager.state.value == SessionState.Active(username = "student.offline", isOffline = true)
        }
        composeRule.waitForIdle()

        assertNotNull("Offline cold start must preserve stored tokens", tokenStore.read())
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home)).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.OFFLINE_BANNER).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.OFFLINE_RETRY_BUTTON)
            .assertIsDisplayed()
            .assertTextContains("Thử lại")

        // Network returns and refresh succeeds -> banner clears and session stays Active.
        nextRefreshResult = ApiResult.Success(
            Tokens(
                accessToken = "fresh-access-online",
                refreshToken = "fresh-refresh-online",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.offline",
            ),
        )
        composeRule.onNodeWithTag(LoginTestTags.OFFLINE_RETRY_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            sessionManager.state.value == SessionState.Active(username = "student.offline", isOffline = false)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LoginTestTags.OFFLINE_BANNER).assertDoesNotExist()
        assertEquals("fresh-access-online", tokenStore.read()?.accessToken)
    }

    @Test
    fun offlineThenOnline401SignsOutToExpired() = runTest {
        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "stored-access",
                refreshToken = "expired-on-server",
                accessExpiresIn = 1,
                refreshExpiresIn = 1,
                mustChangePassword = false,
                username = "student.offline",
            ),
        )
        var online = false
        val gateway = object : MobileAuthGateway {
            override suspend fun login(identifier: String, password: String): ApiResult<Tokens> =
                error("unused")

            override suspend fun refresh(refreshToken: String): ApiResult<Tokens> = if (!online) {
                ApiResult.NetworkFailure(IOException("No route to host"))
            } else {
                ApiResult.HttpFailure(401, ApiError(code = "SESSION_REVOKED"))
            }

            override suspend fun logout(refreshToken: String): ApiResult<Unit> = ApiResult.Success(Unit)
        }
        val sessionManager = SessionManager(tokenStore = tokenStore, authGateway = gateway)

        assertEquals(RefreshOutcome.Offline, sessionManager.validateSessionOnLaunch())
        assertTrue(sessionManager.state.value is SessionState.Active)
        assertNotNull(tokenStore.read())

        online = true
        assertEquals(RefreshOutcome.Expired, sessionManager.retryConnection())
        assertEquals(SessionState.SignedOut(SignOutReason.Expired), sessionManager.state.value)
        assertNull(tokenStore.read())
    }
}
