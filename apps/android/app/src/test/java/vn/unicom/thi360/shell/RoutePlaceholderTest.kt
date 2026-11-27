package vn.unicom.thi360.shell

import androidx.compose.runtime.CompositionLocalProvider
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
import vn.unicom.thi360.feature.catalog.CatalogTestFixtures
import vn.unicom.thi360.feature.catalog.LocalCatalogDependencies
import vn.unicom.thi360.feature.catalog.ProductDetailTestTags
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
        val catalogDeps = CatalogTestFixtures.createDeterministicFixtureDependencies()

        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            navigator = appNav
            CompositionLocalProvider(LocalCatalogDependencies provides catalogDeps) {
                Thi360App(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    navController = navController,
                    navigator = appNav,
                )
            }
        }

        val nav = checkNotNull(navigator)

        composeRule.runOnIdle { nav.openProduct("TOEIC-2026") }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProductDetailTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductDetailTestTags.PRODUCT_CODE)
            .assertTextContains("TOEIC-2026", substring = true)

        composeRule.runOnIdle { nav.openCheckout("TOEIC-2026", seenVersionNo = 2, seenPrice = 150_000L) }
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.payment.ui.PaymentTestTags.CHECKOUT_SCREEN).assertIsDisplayed()

        composeRule.runOnIdle { nav.openPaymentResult(AppRoute.PaymentResult.ByOrder("ORD-77")) }
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.payment.ui.PaymentTestTags.RESULT_SCREEN).assertIsDisplayed()

        composeRule.runOnIdle {
            nav.openPaymentResult(
                AppRoute.PaymentResult.ByReturnLink("https://thi360.zone17.click/return?vnp_ResponseCode=00"),
            )
        }
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.payment.ui.PaymentTestTags.RESULT_SCREEN).assertIsDisplayed()

        composeRule.runOnIdle { nav.openOrders() }
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.payment.ui.PaymentTestTags.ORDERS_SCREEN).assertIsDisplayed()

        composeRule.runOnIdle { nav.openOrder("ORD-88") }
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.payment.ui.PaymentTestTags.ORDER_DETAIL_SCREEN).assertIsDisplayed()

        composeRule.runOnIdle { nav.openPrepare("ent-99") }
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.prepare.PrepareTestTags.SCREEN).assertIsDisplayed()

        composeRule.runOnIdle { nav.openAttempt("att-55") }
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_NAME).assertTextContains("attempt")
        composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_PARAMS).assertTextContains("attemptId=att-55", substring = true)
        composeRule.runOnIdle { nav.closeAttempt() }

        composeRule.runOnIdle { nav.openResult("att-55") }
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.result.ResultTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.result.ResultTestTags.screenFor("att-55")).assertIsDisplayed()

        composeRule.runOnIdle { nav.openReview("att-55") }
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.result.review.ReviewTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.result.review.ReviewTestTags.screenFor("att-55")).assertIsDisplayed()

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
