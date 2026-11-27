package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.legal.LegalRepository
import vn.unicom.thi360.core.legal.LegalTestTags
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.ContractRepoLegalAssetSource

@RunWith(RobolectricTestRunner::class)
class AccountLegalRowsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun singlePaneOpensTermsAndPrivacyViaAppNavigator() {
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
                legalRepository = LegalRepository(ContractRepoLegalAssetSource()),
                apiBaseUrl = server.url("/"),
            )
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_FULL_NAME).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(AccountTestTags.LEGAL_TERMS_ROW)
            .performScrollTo()
            .assertTextContains("Điều kiện giao dịch chung")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(LegalTestTags.SCREEN).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(LegalTestTags.TITLE)
            .assertTextContains("Điều kiện giao dịch chung", substring = true)
        composeRule.onNodeWithTag(LegalTestTags.BACK_BUTTON).performClick()

        composeRule.onNodeWithTag(AccountTestTags.LEGAL_PRIVACY_ROW)
            .performScrollTo()
            .assertTextContains("Chính sách bảo mật thông tin")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(LegalTestTags.TITLE)
                    .assertTextContains("Chính sách bảo mật thông tin", substring = true)
                true
            }.getOrDefault(false)
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun twoPaneOpensTermsAndPrivacyInDetailPane() {
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
                legalRepository = LegalRepository(ContractRepoLegalAssetSource()),
                apiBaseUrl = server.url("/"),
            )
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_FULL_NAME).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(AccountTestTags.LEGAL_TERMS_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.LEGAL_TERMS_ROW).assertIsSelected()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(LegalTestTags.TITLE)
                    .assertTextContains("Điều kiện giao dịch chung", substring = true)
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(AccountTestTags.LEGAL_PRIVACY_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.LEGAL_PRIVACY_ROW).assertIsSelected()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(LegalTestTags.TITLE)
                    .assertTextContains("Chính sách bảo mật thông tin", substring = true)
                true
            }.getOrDefault(false)
        }
    }
}
