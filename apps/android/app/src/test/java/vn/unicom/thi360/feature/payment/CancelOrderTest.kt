package vn.unicom.thi360.feature.payment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.ui.OrderDetailScreen
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.payment.viewmodel.OrderDetailViewModel
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class CancelOrderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun keepOrderClosesDialogWithoutApiAndConfirmCancelsWithSnackbar() {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-pending-payment.json")
            respondApiFixture("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/cancel", 200, "orders-cancel-200.json")
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = OrderDetailViewModel(
            orderCode = PaymentTestFixtures.ORDER_CODE,
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
        )
        try {
            vm.load()
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    OrderDetailScreen(
                        viewModel = vm,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenOrders = {},
                        onOpenOrder = {},
                        onOpenMyExams = {},
                        onOpenCheckout = {},
                        onOpenCatalog = {},
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_CANCEL_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }

            // Open dialog -> "Giữ đơn" closes without calling API
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_CANCEL_BUTTON).performClick()
            composeRule.onNodeWithText("Hủy đơn ${PaymentTestFixtures.ORDER_CODE}?").assertIsDisplayed()
            composeRule.onNodeWithText("Mã giảm giá đã dùng cho đơn này được trả lại.", substring = true).assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.CANCEL_DIALOG_KEEP).performClick()
            composeRule.onNodeWithTag(PaymentTestTags.CANCEL_DIALOG).assertDoesNotExist()
            assertEquals(0, harness.server.requestCount("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/cancel"))

            // Open dialog -> Confirm -> closes dialog, shows Snackbar "Đã hủy đơn {code}.", state updates to "Đã hủy"
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_CANCEL_BUTTON).performClick()
            composeRule.onNodeWithTag(PaymentTestTags.CANCEL_DIALOG_CONFIRM).performClick()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/cancel") == 1 &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CANCEL_DIALOG).fetchSemanticsNodes().isEmpty()
            }
            composeRule.waitUntilMainIdle {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDERS_SNACKBAR_HOST).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithText("Đã hủy đơn ${PaymentTestFixtures.ORDER_CODE}.").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Đã hủy đơn ${PaymentTestFixtures.ORDER_CODE}.").assertIsDisplayed()
            composeRule.onNodeWithText("Đã hủy").assertIsDisplayed()
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun cancel409KeepsDialogOpenShowsErrorAndFollowsIdempotencyKeyPolicy() {
        val keySeq = AtomicInteger(0)
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-pending-payment.json")
            respondSequence(
                "POST",
                "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/cancel",
                { PaymentTestFixtures.networkDisconnectResponse() },
                { MockResponse().setResponseCode(408).addHeader("Retry-After", "5").setBody("""{"code":"REQUEST_TIMEOUT","message":"timeout"}""") },
                { MockResponse().setResponseCode(429).setBody("""{"code":"RATE_LIMITED","message":"rate"}""") },
                { MockResponse().setResponseCode(503).setBody("""{"code":"SERVICE_UNAVAILABLE","message":"down"}""") },
                { ContractFakeServer.apiFixtureResponse(409, "orders-cancel-409-order-not-pending.json") },
                { ContractFakeServer.apiFixtureResponse(200, "orders-cancel-200.json") },
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = OrderDetailViewModel(
            orderCode = PaymentTestFixtures.ORDER_CODE,
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            keyGenerator = { "cancel-key-${keySeq.incrementAndGet()}" },
        )
        try {
            vm.load()
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    OrderDetailScreen(
                        viewModel = vm,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenOrders = {},
                        onOpenOrder = {},
                        onOpenMyExams = {},
                        onOpenCheckout = {},
                        onOpenCatalog = {},
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_CANCEL_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_CANCEL_BUTTON).performClick()

            // First 5 calls (network, 408, 429, 503, 409) all keep dialog open & share cancel-key-1
            repeat(5) { idx ->
                composeRule.onNodeWithTag(PaymentTestTags.CANCEL_DIALOG_CONFIRM).performClick()
                val target = idx + 1
                composeRule.waitUntilMainIdle {
                    harness.server.requestCount("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/cancel") >= target &&
                        vm.state.value.cancelDialog?.submitting == false
                }
                composeRule.onNodeWithTag(PaymentTestTags.CANCEL_DIALOG).assertIsDisplayed()
            }
            composeRule.onNodeWithTag(PaymentTestTags.CANCEL_DIALOG_ERROR)
                .assertIsDisplayed()
                .assertTextContains("Đơn không còn ở trạng thái chờ thanh toán.")

            // 6th call after 409 uses a NEW key (cancel-key-2)
            composeRule.onNodeWithTag(PaymentTestTags.CANCEL_DIALOG_CONFIRM).performClick()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/cancel") == 6 &&
                    vm.state.value.cancelDialog == null
            }

            val keys = harness.server.requests("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/cancel")
                .map { it.getHeader("Idempotency-Key") }
            assertEquals(listOf("cancel-key-1", "cancel-key-1", "cancel-key-1", "cancel-key-1", "cancel-key-1"), keys.take(5))
            assertEquals("cancel-key-2", keys[5])
            assertNotEquals(keys[4], keys[5])
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun inFlightCancelLocksButtonsBlocksDismissAndIgnoresDoubleTap() {
        val gate = CountDownLatch(1)
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-pending-payment.json")
            respondDynamic("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/cancel") {
                gate.await(5, TimeUnit.SECONDS)
                ContractFakeServer.apiFixtureResponse(200, "orders-cancel-200.json")
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = OrderDetailViewModel(
            orderCode = PaymentTestFixtures.ORDER_CODE,
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
        )
        try {
            vm.load()
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    OrderDetailScreen(
                        viewModel = vm,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenOrders = {},
                        onOpenOrder = {},
                        onOpenMyExams = {},
                        onOpenCheckout = {},
                        onOpenCatalog = {},
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_CANCEL_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_CANCEL_BUTTON).performClick()

            // Double tap confirm while in flight -> only 1 request, dialog cannot be dismissed, buttons locked
            vm.confirmCancelOrder()
            vm.confirmCancelOrder()
            vm.dismissCancelDialog() // ignored while submitting
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(PaymentTestTags.CANCEL_DIALOG).assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.CANCEL_DIALOG_CONFIRM).assertIsNotEnabled()
            composeRule.onNodeWithTag(PaymentTestTags.CANCEL_DIALOG_KEEP).assertIsNotEnabled()

            gate.countDown()
            composeRule.waitUntilMainIdle { vm.state.value.cancelDialog == null }
            assertEquals(1, harness.server.requestCount("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/cancel"))
        } finally {
            gate.countDown()
            scope.cancel()
            harness.close()
        }
    }
}
