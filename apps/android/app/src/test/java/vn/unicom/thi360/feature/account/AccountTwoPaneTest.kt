package vn.unicom.thi360.feature.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
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
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.ui.ListDetailTestTags
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.recovery.PasswordTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class AccountTwoPaneTest {

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

    private fun verifyTwoPaneNavigation() {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
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
                composeRule.onNodeWithTag(ProfileTestTags.INFO_SECTION).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_TWO_PANE).assertIsDisplayed()
        composeRule.onNodeWithTag(ListDetailTestTags.TWO_COLUMN).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).assertIsSelected()
        composeRule.onNodeWithTag(ProfileTestTags.SCREEN).assertIsDisplayed()

        composeRule.onNodeWithTag(AccountTestTags.ORDERS_ROW).performClick()
        composeRule.onNodeWithTag(AccountTestTags.ORDERS_ROW).assertIsSelected()
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).assertIsNotSelected()
        composeRule.onNodeWithTag(PaymentTestTags.ORDERS_SCREEN).assertIsDisplayed()

        composeRule.onNodeWithTag(AccountTestTags.CHANGE_PASSWORD_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.CHANGE_PASSWORD_ROW).assertIsSelected()
        composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()

        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_ROW).assertIsSelected()
        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_OPTION_EN).assertIsDisplayed()

        composeRule.onNodeWithTag(AccountTestTags.THEME_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.THEME_ROW).assertIsSelected()
        composeRule.onNodeWithTag(AccountTestTags.THEME_OPTION_DARK).assertIsDisplayed()

        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).assertIsSelected()
        composeRule.onNodeWithTag(ProfileTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun twoPane840dpDefaultsToProfileAndSwitchesAllPanes() = verifyTwoPaneNavigation()

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun twoPane1280dpDefaultsToProfileAndSwitchesAllPanes() = verifyTwoPaneNavigation()

    @Test
    fun featureAccountHasNoDirectImportFromFeaturePayment() {
        val candidates = listOf(
            File("src/main/java/vn/unicom/thi360/feature/account"),
            File("app/src/main/java/vn/unicom/thi360/feature/account"),
        )
        val dir = candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot locate feature/account directory")
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val text = file.readText(Charsets.UTF_8)
            assertFalse(
                "${file.name} must not import feature.payment directly",
                text.contains("vn.unicom.thi360.feature.payment"),
            )
        }
    }
}
