package vn.unicom.thi360.feature.payment

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.payment.domain.CustomTabsPaymentBrowser
import vn.unicom.thi360.feature.payment.domain.PaymentBrowser
import vn.unicom.thi360.feature.payment.domain.PaymentUrlResolver
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.ui.CheckoutScreen
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutViewModel

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class PaymentBrowserLauncherTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resolvesAbsoluteVerbatimAndRelativeAgainstApiBaseUrl() {
        val absolute = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=20000000&vnp_ReturnUrl=https%3A%2F%2Fthi360.zone17.click%2Fcheckout%2Fresult%2Fapp"
        assertEquals(absolute, PaymentUrlResolver.resolve(absolute, "https://api.example.com"))

        val relativeWithSlash = "/mock-vnpay/pay?vnp_TxnRef=DH001"
        assertEquals(
            "http://10.0.2.2:8080/mock-vnpay/pay?vnp_TxnRef=DH001",
            PaymentUrlResolver.resolve(relativeWithSlash, "http://10.0.2.2:8080/"),
        )

        val relativeNoSlash = "mock-vnpay/pay?vnp_TxnRef=DH002"
        assertEquals(
            "https://thi360.zone17.click/mock-vnpay/pay?vnp_TxnRef=DH002",
            PaymentUrlResolver.resolve(relativeNoSlash, "https://thi360.zone17.click"),
        )
    }

    @Test
    fun customTabsBrowserLaunchesCustomTabIntentAndHandlesActivityNotFound() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val capturedIntents = mutableListOf<Intent>()
        val recordingContext = object : ContextWrapper(appContext) {
            override fun startActivity(intent: Intent) {
                capturedIntents += intent
            }

            override fun startActivity(intent: Intent, options: Bundle?) {
                capturedIntents += intent
            }
        }

        val browser = CustomTabsPaymentBrowser()
        val url = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=TX1"
        assertTrue(browser.open(recordingContext, url))
        assertEquals(1, capturedIntents.size)
        val launched = capturedIntents.single()
        assertEquals(Intent.ACTION_VIEW, launched.action)
        assertEquals(url, launched.dataString)
        assertTrue(
            "CustomTabsIntent must include EXTRA_SESSION",
            launched.hasExtra(CustomTabsIntent.EXTRA_SESSION),
        )

        val failingContext = object : ContextWrapper(appContext) {
            override fun startActivity(intent: Intent) {
                throw ActivityNotFoundException("no browser")
            }

            override fun startActivity(intent: Intent, options: Bundle?) {
                throw ActivityNotFoundException("no browser")
            }
        }
        assertFalse(browser.open(failingContext, url))
    }

    @Test
    fun browserFailureShowsGenericErrorAndDeletesSavedPendingOrder() {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondApiFixture("POST", "/api/v1/me/orders", 201, "orders-create-201-redirect.json")
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val pendingStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() }
        var savedBeforeBrowserOpen = false
        val failingBrowser = PaymentBrowser { _, _ ->
            savedBeforeBrowserOpen = pendingStore.readValid(5_000L)?.orderCode == PaymentTestFixtures.ORDER_CODE
            false
        }
        val vm = CheckoutViewModel(
            code = PaymentTestFixtures.EXAM_CODE,
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = pendingStore,
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            clockMillis = { 5_000L },
        )
        try {
            vm.loadInitial()
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    CheckoutScreen(
                        viewModel = vm,
                        paymentBrowser = failingBrowser,
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenCatalog = {},
                        onOpenProduct = {},
                        onOpenPaymentResult = {},
                        onOpenOrders = {},
                        onOpenMyExams = {},
                        onOpenPrepare = {},
                        onOpenAttempt = {},
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntilMainIdle { vm.state.value.isPayButtonEnabled }
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_SUBMIT_ERROR).fetchSemanticsNodes().isNotEmpty()
            }

            assertTrue("PendingPaymentOrder must be saved immediately before opening browser", savedBeforeBrowserOpen)
            assertNull("PendingPaymentOrder must be removed when browser open fails", pendingStore.readValid(5_000L))
            composeRule.onNodeWithText("Không thể tạo đơn hàng. Vui lòng thử lại.").performScrollTo().assertIsDisplayed()
        } finally {
            scope.cancel()
            harness.close()
        }
    }
}
