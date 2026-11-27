package vn.unicom.thi360.shell

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.legal.LegalId
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.AppRoute

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class RouteOpenersTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun mountShell(): Pair<AppNavigator, NavHostController> {
        var navigator: AppNavigator? = null
        var controller: NavHostController? = null
        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            controller = navController
            navigator = appNav
            Thi360App(
                preferences = AppPreferences(locale = AppLocale.VI),
                navController = navController,
                navigator = appNav,
            )
        }
        composeRule.waitForIdle()
        return checkNotNull(navigator) to checkNotNull(controller)
    }

    private fun NavHostController.leafStack(): List<String> =
        currentBackStack.value
            .mapNotNull { entry ->
                val route = entry.destination.route
                if (route == null || route == graph.route || route in Destination.entries.map { it.route }) {
                    null
                } else {
                    route
                }
            }

    @Test
    fun openProductCheckoutPaymentResultOrdersAndOrderPushOnCurrentTab() {
        val (nav, controller) = mountShell()

        composeRule.runOnIdle { nav.openProduct("PROD-1") }
        assertEquals(listOf("home/catalog", "home/product/{code}"), controller.leafStack())

        composeRule.runOnIdle { nav.openCheckout("PROD-1", seenVersionNo = 2, seenPrice = 99_000L) }
        assertEquals(
            listOf("home/catalog", "home/product/{code}", "home/${AppRoute.CHECKOUT_PATTERN}"),
            controller.leafStack(),
        )

        composeRule.runOnIdle { nav.openPaymentResult(AppRoute.PaymentResult.ByOrder("ORD-1")) }
        assertEquals("home/${AppRoute.PAYMENT_RESULT_PATTERN}", controller.currentDestination?.route)

        composeRule.runOnIdle { nav.openOrders() }
        assertEquals("home/orders", controller.currentDestination?.route)

        composeRule.runOnIdle { nav.openOrder("ORD-1") }
        assertEquals("home/order/{orderCode}", controller.currentDestination?.route)
    }

    @Test
    fun openPrepareResultReviewProfileAndLegalPushExpectedRoutes() {
        val (nav, controller) = mountShell()

        composeRule.runOnIdle { nav.switchTab(Destination.MyExams) }
        composeRule.runOnIdle { nav.openPrepare("ent-1") }
        assertEquals(listOf("my_exams/my-exams", "my_exams/prepare/{entitlementId}"), controller.leafStack())

        composeRule.runOnIdle { nav.openResult("att-1") }
        assertEquals(
            listOf("my_exams/my-exams", "my_exams/prepare/{entitlementId}", "my_exams/result/{attemptId}"),
            controller.leafStack(),
        )

        composeRule.runOnIdle { nav.openReview("att-1") }
        assertEquals(
            listOf(
                "my_exams/my-exams",
                "my_exams/prepare/{entitlementId}",
                "my_exams/result/{attemptId}",
                "my_exams/review/{attemptId}",
            ),
            controller.leafStack(),
        )

        composeRule.runOnIdle { nav.switchTab(Destination.Account) }
        composeRule.runOnIdle { nav.openProfile() }
        assertEquals(listOf("account/root", "account/profile"), controller.leafStack())

        composeRule.runOnIdle { nav.openProfileVerifyEmail() }
        assertEquals(listOf("account/root", "account/profile", "account/profile/verify-email"), controller.leafStack())

        composeRule.runOnIdle { nav.openLegal(LegalId.PRIVACY) }
        assertEquals(
            listOf("account/root", "account/profile", "account/profile/verify-email", "account/legal/{id}"),
            controller.leafStack(),
        )
    }

    @Test
    fun openDeleteAccount() {
        val (nav, controller) = mountShell()
        composeRule.runOnIdle { nav.switchTab(Destination.Account) }
        composeRule.runOnIdle { nav.openDeleteAccount() }
        assertEquals(listOf("account/root", "account/account/delete"), controller.leafStack())
        assertEquals("account/account/delete", controller.currentDestination?.route)
    }

    @Test
    fun openAttemptReplaceAttemptAndCloseAttemptKeepOriginTabStackIntact() {
        val (nav, controller) = mountShell()
        composeRule.runOnIdle { nav.switchTab(Destination.MyExams) }
        composeRule.runOnIdle { nav.openAttempt("att-old") }

        assertEquals(listOf("my_exams/my-exams", "attempt/{attemptId}"), controller.leafStack())
        assertEquals("att-old", controller.currentBackStackEntry?.arguments?.getString(AppRoute.ARG_ATTEMPT_ID))

        // replaceAttempt removes old attempt so back goes directly to origin tab, not old attempt.
        composeRule.runOnIdle { nav.replaceAttempt("att-new") }
        assertEquals(listOf("my_exams/my-exams", "attempt/{attemptId}"), controller.leafStack())
        assertEquals("att-new", controller.currentBackStackEntry?.arguments?.getString(AppRoute.ARG_ATTEMPT_ID))

        composeRule.runOnIdle { nav.closeAttempt() }
        assertEquals(listOf("my_exams/my-exams"), controller.leafStack())
    }

    @Test
    fun openAttemptAfterStartPopsPrepareFromOriginTabBeforeOpeningAttempt() {
        val (nav, controller) = mountShell()
        composeRule.runOnIdle { nav.switchTab(Destination.MyExams) }
        composeRule.runOnIdle { nav.openPrepare("ent-start-1") }
        assertEquals(listOf("my_exams/my-exams", "my_exams/prepare/{entitlementId}"), controller.leafStack())

        composeRule.runOnIdle { nav.openAttemptAfterStart("att-started-1") }
        val stack = controller.leafStack()
        assertEquals(listOf("my_exams/my-exams", "attempt/{attemptId}"), stack)
        assertFalse("prepare route must be popped by openAttemptAfterStart", stack.any { it.contains("prepare") })
    }

    @Test
    fun openResultFromAttemptPopsAttemptAndPrepareAndLandsOnResultInOriginTab() {
        val (nav, controller) = mountShell()
        composeRule.runOnIdle { nav.switchTab(Destination.MyExams) }
        composeRule.runOnIdle { nav.openPrepare("ent-2") }
        composeRule.runOnIdle { nav.openAttempt("att-2") }
        assertEquals(
            listOf("my_exams/my-exams", "my_exams/prepare/{entitlementId}", "attempt/{attemptId}"),
            controller.leafStack(),
        )

        composeRule.runOnIdle { nav.openResultFromAttempt("att-2") }
        val stack = controller.leafStack()
        assertEquals(listOf("my_exams/my-exams", "my_exams/result/{attemptId}"), stack)
        assertFalse(stack.any { it.contains("attempt/{attemptId}") })
        assertFalse(stack.any { it.contains("prepare") })

        // Back from Result returns to the screen before the attempt flow (MyExams root).
        composeRule.runOnIdle { nav.back() }
        assertEquals(listOf("my_exams/my-exams"), controller.leafStack())
    }

    @Test
    fun switchTabWithReloadClearsTargetSubStackToRootAndIncrementsReloadCounter() {
        val (nav, controller) = mountShell()
        composeRule.runOnIdle { nav.switchTab(Destination.MyExams) }
        composeRule.runOnIdle { nav.openPrepare("ent-3") }
        composeRule.runOnIdle { nav.switchTab(Destination.Home) }
        composeRule.runOnIdle { nav.openProduct("PROD-9") }

        // Switching to MyExams with reload = true clears prepare/ent-3 back to MyExams root.
        composeRule.runOnIdle { nav.switchTab(Destination.MyExams, reload = true) }
        assertEquals(listOf("my_exams/my-exams"), controller.leafStack())
        assertEquals(1L, nav.reloadCounters.value[Destination.MyExams])

        // Switching to History with reload = true clears stack and passes resetFilters = true.
        composeRule.runOnIdle { nav.switchTab(Destination.History, reload = true) }
        assertEquals(1L, nav.reloadCounters.value[Destination.History])
        assertTrue(
            controller.currentBackStackEntry?.arguments?.getBoolean(AppRoute.ARG_RESET_FILTERS) == true,
        )
    }

    @Test
    fun accountTabSupportsCrossRouteOrderCheckoutProductPrepareAttemptAndResultFlows() {
        val (nav, controller) = mountShell()

        composeRule.runOnIdle { nav.switchTab(Destination.Account) }
        composeRule.runOnIdle { nav.openOrders() }
        composeRule.runOnIdle { nav.openCheckout("MATH01", seenVersionNo = 1, seenPrice = 50_000L) }
        assertEquals(
            listOf("account/root", "account/orders", "account/${AppRoute.CHECKOUT_PATTERN}"),
            controller.leafStack(),
        )

        composeRule.runOnIdle { nav.openProduct("MATH01") }
        assertEquals("account/product/{code}", controller.currentDestination?.route)

        composeRule.runOnIdle { nav.openPrepare("ent-acc-1") }
        assertEquals("account/prepare/{entitlementId}", controller.currentDestination?.route)

        composeRule.runOnIdle { nav.openAttempt("att-acc-1") }
        assertEquals("attempt/{attemptId}", controller.currentDestination?.route)

        composeRule.runOnIdle { nav.openResultFromAttempt("att-acc-1") }
        assertEquals(
            listOf(
                "account/root",
                "account/orders",
                "account/${AppRoute.CHECKOUT_PATTERN}",
                "account/product/{code}",
                "account/result/{attemptId}",
            ),
            controller.leafStack(),
        )

        composeRule.runOnIdle { nav.openReview("att-acc-1") }
        assertEquals("account/review/{attemptId}", controller.currentDestination?.route)
    }

    @Test
    fun accountPinnedOpenersFromHomeSwitchTabAndPreserveHomeBackStack() {
        val (nav, controller) = mountShell()

        composeRule.runOnIdle { nav.openProduct("PROD-KEEP") }
        assertEquals(listOf("home/catalog", "home/product/{code}"), controller.leafStack())

        composeRule.runOnIdle { nav.openProfile() }
        assertEquals(listOf("account/root", "account/profile"), controller.leafStack())

        composeRule.runOnIdle { nav.switchTab(Destination.Home) }
        assertEquals(listOf("home/catalog", "home/product/{code}"), controller.leafStack())

        composeRule.runOnIdle { nav.openProfileVerifyEmail() }
        assertEquals(
            listOf("account/root", "account/profile", "account/profile/verify-email"),
            controller.leafStack(),
        )

        composeRule.runOnIdle { nav.switchTab(Destination.Home) }
        assertEquals(listOf("home/catalog", "home/product/{code}"), controller.leafStack())

        composeRule.runOnIdle { nav.openDeleteAccount() }
        assertEquals("account/account/delete", controller.currentDestination?.route)
        assertEquals("account/root", controller.leafStack().first())

        composeRule.runOnIdle { nav.switchTab(Destination.Home) }
        assertEquals(listOf("home/catalog", "home/product/{code}"), controller.leafStack())

        composeRule.runOnIdle { nav.openLegal(LegalId.TERMS) }
        assertEquals("account/legal/{id}", controller.currentDestination?.route)
        assertEquals("account/root", controller.leafStack().first())

        composeRule.runOnIdle { nav.switchTab(Destination.Home) }
        assertEquals(listOf("home/catalog", "home/product/{code}"), controller.leafStack())
    }
}
