package vn.unicom.thi360.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.compose.rememberNavController
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
import vn.unicom.thi360.shell.routes.RoutePlaceholderTestTags

@RunWith(RobolectricTestRunner::class)
class RoutePlaceholderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun everyRouteOpenedViaAppNavigatorRendersPlaceholderWithTypedParams() {
        var navigator: AppNavigator? = null

        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            navigator = appNav
            Thi360App(
                preferences = AppPreferences(locale = AppLocale.VI),
                navController = navController,
                navigator = appNav,
            )
        }

        val nav = checkNotNull(navigator)

        composeRule.runOnIdle { nav.openProduct("TOEIC-2026") }
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_NAME).assertIsDisplayed().assertTextContains("product")
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_PARAMS).assertTextContains("code=TOEIC-2026", substring = true)

        composeRule.runOnIdle { nav.openCheckout("TOEIC-2026", seenVersionNo = 2, seenPrice = 150_000L) }
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_NAME).assertTextContains("checkout")
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_PARAMS)
            .assertTextContains("code=TOEIC-2026", substring = true)
            .assertTextContains("seenVersionNo=2", substring = true)
            .assertTextContains("seenPrice=150000", substring = true)

        composeRule.runOnIdle { nav.openPaymentResult(AppRoute.PaymentResult.ByOrder("ORD-77")) }
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_NAME).assertTextContains("payment-result")
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_PARAMS).assertTextContains("orderCode=ORD-77", substring = true)

        composeRule.runOnIdle {
            nav.openPaymentResult(
                AppRoute.PaymentResult.ByReturnLink("https://thi360.zone17.click/return?vnp_ResponseCode=00"),
            )
        }
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_NAME).assertTextContains("payment-result")
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_PARAMS).assertTextContains("source=returnLink", substring = true)

        composeRule.runOnIdle { nav.openOrders() }
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_NAME).assertTextContains("orders")

        composeRule.runOnIdle { nav.openOrder("ORD-88") }
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_NAME).assertTextContains("order")
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_PARAMS).assertTextContains("orderCode=ORD-88", substring = true)

        composeRule.runOnIdle { nav.openPrepare("ent-99") }
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_NAME).assertTextContains("prepare")
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_PARAMS).assertTextContains("entitlementId=ent-99", substring = true)

        composeRule.runOnIdle { nav.openAttempt("att-55") }
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_NAME).assertTextContains("attempt")
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_PARAMS).assertTextContains("attemptId=att-55", substring = true)
        composeRule.runOnIdle { nav.closeAttempt() }

        composeRule.runOnIdle { nav.openResult("att-55") }
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_NAME).assertTextContains("result")
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_PARAMS).assertTextContains("attemptId=att-55", substring = true)

        composeRule.runOnIdle { nav.openReview("att-55") }
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_NAME).assertTextContains("review")
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_PARAMS).assertTextContains("attemptId=att-55", substring = true)

        composeRule.runOnIdle { nav.switchTab(Destination.Account) }
        composeRule.runOnIdle { nav.openProfile() }
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.account.ProfileTestTags.SCREEN).assertIsDisplayed()

        composeRule.runOnIdle { nav.openProfileVerifyEmail() }
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.account.VerifyEmailTestTags.SCREEN).assertIsDisplayed()

        composeRule.runOnIdle { nav.openDeleteAccount() }
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.account.DeleteAccountTestTags.SCREEN).assertIsDisplayed()

        composeRule.runOnIdle { nav.openLegal(LegalId.TERMS) }
        composeRule.onNodeWithTag(vn.unicom.thi360.core.legal.LegalTestTags.SCREEN).assertIsDisplayed()
    }
}
