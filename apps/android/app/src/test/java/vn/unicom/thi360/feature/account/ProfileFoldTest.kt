package vn.unicom.thi360.feature.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class ProfileFoldTest {

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
    @Config(qualifiers = "w1024dp-h900dp")
    fun narrowToWideToNarrowPreservesPersonalInfoAndChangeEmailDrafts() {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        var widthDp by mutableStateOf(390)

        composeRule.setContent {
            Box(modifier = Modifier.size(width = widthDp.dp, height = 844.dp)) {
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
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_CARD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performTextInput("Nguyễn Draft Tên")
        composeRule.onNodeWithTag(ProfileTestTags.PHONE_FIELD).performScrollTo().performTextInput("0912345678")
        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_TOGGLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_FIELD).performScrollTo().performTextInput("draft@thi360.vn")

        composeRule.runOnIdle { widthDp = 900 }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_TWO_PANE).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Nguyễn Draft Tên")
        composeRule.onNodeWithTag(ProfileTestTags.PHONE_FIELD).performScrollTo().assertTextContains("0912345678")
        composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_FIELD).performScrollTo().assertTextContains("draft@thi360.vn")

        composeRule.runOnIdle { widthDp = 390 }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.BACK_BUTTON).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Nguyễn Draft Tên")
        composeRule.onNodeWithTag(ProfileTestTags.PHONE_FIELD).performScrollTo().assertTextContains("0912345678")
        composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_FIELD).performScrollTo().assertTextContains("draft@thi360.vn")
    }
}
