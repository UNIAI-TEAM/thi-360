package vn.unicom.thi360.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import org.junit.After
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
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LoadStateTestTags
import vn.unicom.thi360.core.ui.LoadStateView
import vn.unicom.thi360.feature.account.AccountProfileGateway
import vn.unicom.thi360.feature.account.AccountTestTags
import vn.unicom.thi360.feature.account.UserProfile
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.RoutePlaceholderTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class LocaleSwitchOnPortalRoutesTest {

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
    @Config(qualifiers = "w412dp-h915dp")
    fun switchingLocaleUpdatesPortalPlaceholderAndLoadStateImmediatelyWhileKeepingSessionActiveAndParamsIntact() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(
                Tokens("acc", "ref", 300, 604800, false, "student.locale", accountId = "acc-locale"),
            ),
            localeProvider = { AppLocale.VI },
        )
        var preferences by mutableStateOf(AppPreferences(locale = AppLocale.VI))
        var navigator: AppNavigator? = null

        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            navigator = appNav
            AppRoot(
                preferences = preferences,
                onSelectLocale = { preferences = preferences.copy(locale = it) },
                onSelectThemeMode = {},
                navController = navController,
                navigator = appNav,
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                profileGateway = object : AccountProfileGateway {
                    override suspend fun fetchProfile(): ApiResult<UserProfile> =
                        ApiResult.Success(UserProfile("student.locale", "Học viên", "hv@thi360.vn", true))
                },
            )
        }

        val nav = checkNotNull(navigator)
        composeRule.runOnIdle { nav.openProduct("EXAM-SERVER-ID-99") }
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_PARAMS)
            .assertTextContains("code=EXAM-SERVER-ID-99", substring = true)

        // Switch to Account tab and change language to EN.
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.LANGUAGE_OPTION_EN).performClick()
        composeRule.waitForIdle()

        // Session stays Active (never returns to Login).
        assertTrue(stack.sessionManager.state.value is SessionState.Active)

        // Return to Home tab: sub-route parameter preserved while UI strings switched to English immediately.
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_PARAMS)
            .assertTextContains("code=EXAM-SERVER-ID-99", substring = true)
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).assertTextContains("Home")
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun loadStateViewUpdatesCopyImmediatelyOnLocaleChange() {
        var locale by mutableStateOf(AppLocale.VI)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = locale)) {
                LoadStateView<String>(
                    state = LoadState.Failed(kind = LoadFailureKind.Offline, canRetry = true),
                    onRetry = {},
                    emptyContent = {},
                    content = {},
                )
            }
        }

        composeRule.onNodeWithTag(LoadStateTestTags.ERROR_TITLE).assertTextContains("Không có kết nối")
        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).assertTextContains("Thử lại")

        composeRule.runOnIdle { locale = AppLocale.EN }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LoadStateTestTags.ERROR_TITLE).assertTextContains("No connection")
        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).assertTextContains("Retry")
    }
}
