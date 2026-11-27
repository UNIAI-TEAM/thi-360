package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiError
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class AccountEntryTest {

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
    @Config(qualifiers = "w390dp-h640dp")
    fun singlePaneCardAndProfileRowOpenProfileBackRestoresScrollAndOrdersOpensOrdersRoute() {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
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

        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).assertIsDisplayed().assertTextContains("Hồ sơ")
        composeRule.onNodeWithTag(AccountTestTags.ORDERS_ROW).assertIsDisplayed().assertTextContains("Đơn của tôi")

        composeRule.onNodeWithTag(AccountTestTags.PROFILE_CARD).performClick()
        composeRule.onNodeWithTag(ProfileTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTestTags.BACK_BUTTON).performClick()
        composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_SINGLE_PANE).assertIsDisplayed()

        composeRule.onNodeWithTag(AccountTestTags.LIST_COLUMN).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AccountTestTags.LOGOUT_BUTTON).assertIsDisplayed()

        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTestTags.BACK_BUTTON).performClick()
        composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_SINGLE_PANE).assertIsDisplayed()

        composeRule.onNodeWithTag(AccountTestTags.ORDERS_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(PaymentTestTags.ORDERS_SCREEN).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun profileAndOrdersRowsRemainClickableDuringLoadingErrorAndOfflineStates() {
        var mode = 0
        var calls = 0
        val gate = CompletableDeferred<ApiResult<UserProfile>>()
        val scriptedGateway = object : AccountProfileGateway {
            override suspend fun fetchProfile(): ApiResult<UserProfile> {
                calls++
                return when (mode) {
                    0 -> gate.await()
                    1 -> ApiResult.HttpFailure(500, ApiError(code = "INTERNAL_ERROR"))
                    else -> ApiResult.NetworkFailure(IOException("Offline"))
                }
            }
        }
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
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
                profileGateway = scriptedGateway,
                apiBaseUrl = server.url("/"),
            )
        }

        composeRule.onNodeWithTag(AccountTestTags.PROFILE_LOADING).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.ORDERS_ROW).performClick()
        composeRule.onNodeWithTag(PaymentTestTags.ORDERS_SCREEN).assertIsDisplayed()

        mode = 1
        gate.complete(ApiResult.HttpFailure(500, ApiError(code = "INTERNAL_ERROR")))
        composeRule.onNodeWithContentDescription("Quay lại").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_ERROR).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).performClick()
        composeRule.onNodeWithTag(ProfileTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTestTags.BACK_BUTTON).performClick()

        mode = 2
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_RETRY_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_OFFLINE).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).performClick()
        composeRule.onNodeWithTag(ProfileTestTags.SCREEN).assertIsDisplayed()
        assertTrue(calls >= 2)
    }
}
