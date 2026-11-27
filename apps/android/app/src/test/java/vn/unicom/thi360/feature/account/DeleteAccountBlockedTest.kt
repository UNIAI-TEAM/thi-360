package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.junit.After
import org.junit.Assert.assertEquals
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
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.AppRoute
import vn.unicom.thi360.shell.shellDestination
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class DeleteAccountBlockedTest {

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

    private fun mountBlockedViaAppRoot(
        fixture: String,
        transform: (String) -> String = { it },
    ): NavHostController {
        server.respondApiFixture("GET", "/api/v1/me/account/deletion-check", 200, fixture, transform)
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        lateinit var navController: NavHostController
        lateinit var navigator: AppNavigator

        composeRule.setContent {
            val controller = rememberNavController()
            val nav = androidx.compose.runtime.remember(controller) { AppNavigator(controller) }
            navController = controller
            navigator = nav
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                startDestination = Destination.Account.route,
                navController = controller,
                navigator = nav,
                profileGateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/")),
                accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
                apiBaseUrl = server.url("/"),
            )
        }

        composeRule.runOnIdle { navigator.openDeleteAccount() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_CARD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(DeleteAccountTestTags.PASSWORD_FIELD).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteAccountTestTags.ACKNOWLEDGE_CHECKBOX).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteAccountTestTags.SUBMIT_BUTTON).assertDoesNotExist()
        return navController
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun blockedAttemptShowsAttemptMessageAndNavigatesToMyExamsViaAppNavigator() {
        val navController = mountBlockedViaAppRoot("me-account-deletion-check-200-blocked-attempt.json")
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ATTEMPT_MESSAGE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ORDER_MESSAGE).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_PENDING_PAYMENT_MESSAGE).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_ORDERS_BUTTON).assertDoesNotExist()

        composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_EXAMS_BUTTON).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(Destination.MyExams, navController.currentDestination.shellDestination())
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun blockedOrderShowsOrderMessageWithoutOrdersOrMyExamsButton() {
        mountBlockedViaAppRoot("me-account-deletion-check-200-blocked-order.json")
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ORDER_MESSAGE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ATTEMPT_MESSAGE).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_PENDING_PAYMENT_MESSAGE).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_EXAMS_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_ORDERS_BUTTON).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun blockedAttemptAndOrderBeingCheckedOmitsOrdersButton() {
        mountBlockedViaAppRoot("me-account-deletion-check-200-blocked.json") { raw ->
            raw.replace(", \"ORDER_PENDING_PAYMENT\"", "")
                .replace(",\"ORDER_PENDING_PAYMENT\"", "")
        }
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ATTEMPT_MESSAGE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ORDER_MESSAGE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_PENDING_PAYMENT_MESSAGE).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_EXAMS_BUTTON).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_ORDERS_BUTTON).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun blockedPendingPaymentShowsPendingPaymentMessageAndNavigatesToOrdersViaAppNavigator() {
        val navController = mountBlockedViaAppRoot("me-account-deletion-check-200-blocked-pending-payment.json")
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_PENDING_PAYMENT_MESSAGE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ATTEMPT_MESSAGE).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ORDER_MESSAGE).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_EXAMS_BUTTON).assertDoesNotExist()

        composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_ORDERS_BUTTON).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            AppRoute.tabRoute(Destination.Account, AppRoute.ORDERS_PATH),
            navController.currentDestination?.route,
        )
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun blockedAllThreeRendersEveryReasonAndBothActionsWireThroughAppNavigator() {
        val navController = mountBlockedViaAppRoot("me-account-deletion-check-200-blocked.json")
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ATTEMPT_MESSAGE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ORDER_MESSAGE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_PENDING_PAYMENT_MESSAGE).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_ORDERS_BUTTON).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            AppRoute.tabRoute(Destination.Account, AppRoute.ORDERS_PATH),
            navController.currentDestination?.route,
        )

        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_EXAMS_BUTTON).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_EXAMS_BUTTON).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(Destination.MyExams, navController.currentDestination.shellDestination())
    }
}
