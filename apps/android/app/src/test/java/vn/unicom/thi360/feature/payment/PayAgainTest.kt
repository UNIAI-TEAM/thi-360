package vn.unicom.thi360.feature.payment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
import vn.unicom.thi360.feature.payment.domain.PaymentResultEntry
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.ui.OrdersScreen
import vn.unicom.thi360.feature.payment.ui.PaymentResultScreen
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.payment.viewmodel.OrdersViewModel
import vn.unicom.thi360.feature.payment.viewmodel.PaymentResultViewModel
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class PayAgainTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun singleFlightAcrossList() {
        val gate = CountDownLatch(1)
        val twoOrdersList = PaymentTestFixtures.mutateJson("orders-list-200.json") { obj ->
            val first = obj["items"]!!.jsonArray.first().jsonObject
            val second = JsonObject(
                first + mapOf(
                    "id" to JsonPrimitive("00000000-0000-7000-8000-000000000099"),
                    "code" to JsonPrimitive("DH261126000002"),
                ),
            )
            JsonObject(
                obj + mapOf(
                    "totalItems" to JsonPrimitive(2),
                    "items" to JsonArray(listOf(first, second)),
                ),
            )
        }
        val harness = PaymentTestFixtures.createHarness {
            respondJson("GET", "/api/v1/me/orders", 200, twoOrdersList)
            respondDynamic("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/pay") {
                gate.await(5, TimeUnit.SECONDS)
                ContractFakeServer.apiFixtureResponse(200, "orders-pay-200.json")
            }
            respondApiFixture("POST", "/api/v1/me/orders/DH261126000002/pay", 200, "orders-pay-200.json")
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val browser = RecordingPaymentBrowser()
        val vm = OrdersViewModel(
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            usernameProvider = { "student.demo" },
        )
        try {
            vm.loadInitial()
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    OrdersScreen(
                        viewModel = vm,
                        paymentBrowser = browser,
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenOrder = {},
                        onOpenMyExams = {},
                        onOpenCheckout = {},
                        onOpenCatalog = {},
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.orderContinueButton(PaymentTestFixtures.ORDER_CODE)).fetchSemanticsNodes().isNotEmpty()
            }

            // Double tap order 1 AND try order 2 while order 1 is in flight -> ONLY 1 request total!
            vm.payOrder(PaymentTestFixtures.ORDER_CODE, "EXAM-DEMO-03")
            vm.payOrder(PaymentTestFixtures.ORDER_CODE, "EXAM-DEMO-03")
            vm.payOrder("DH261126000002", "EXAM-DEMO-03")
            gate.countDown()
            composeRule.waitUntilMainIdle { browser.openedUrls.size == 1 }
            assertEquals(1, harness.server.requestCount("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/pay"))
            assertEquals(0, harness.server.requestCount("POST", "/api/v1/me/orders/DH261126000002/pay"))
        } finally {
            gate.countDown()
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun keyPolicy() {
        val keySeq = AtomicInteger(0)
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders", 200, "orders-list-200.json")
            respondSequence(
                "POST",
                "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/pay",
                { PaymentTestFixtures.networkDisconnectResponse() },
                { MockResponse().setResponseCode(408).addHeader("Retry-After", "5").setBody("""{"code":"REQUEST_TIMEOUT","message":"timeout"}""") },
                { MockResponse().setResponseCode(429).setBody("""{"code":"RATE_LIMITED","message":"rate"}""") },
                { MockResponse().setResponseCode(503).setBody("""{"code":"SERVICE_UNAVAILABLE","message":"down"}""") },
                { ContractFakeServer.apiFixtureResponse(409, "orders-pay-409-order-expired.json") },
                { ContractFakeServer.apiFixtureResponse(200, "orders-pay-200.json") },
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = OrdersViewModel(
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            usernameProvider = { "student.demo" },
            keyGenerator = { "pay-key-${keySeq.incrementAndGet()}" },
        )
        try {
            vm.loadInitial()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("GET", "/api/v1/me/orders") == 1 &&
                    vm.state.value.orders is vn.unicom.thi360.core.ui.LoadState.Loaded
            }

            repeat(6) { idx ->
                vm.payOrder(PaymentTestFixtures.ORDER_CODE, "EXAM-DEMO-03")
                val target = idx + 1
                composeRule.waitUntilMainIdle {
                    harness.server.requestCount("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/pay") >= target &&
                        !vm.state.value.anyActionInFlight
                }
            }

            val keys = harness.server.requests("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/pay")
                .map { it.getHeader("Idempotency-Key") }
            assertEquals(6, keys.size)
            // First 5 (network, 408, 429, 503, then 409) share pay-key-1; 409 resets -> 6th uses pay-key-2
            assertEquals(listOf("pay-key-1", "pay-key-1", "pay-key-1", "pay-key-1", "pay-key-1"), keys.take(5))
            assertEquals("pay-key-2", keys[5])
            assertNotEquals(keys[4], keys[5])
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun errorActions() {
        val host = ComposeSlotHost(composeRule)
        var openedCheckout: String? = null
        var openedCatalog = false

        // 1. In Orders list: ORDER_EXPIRED & PAYMENT_ATTEMPTS_EXCEEDED show "Tạo đơn mới"; PRODUCT_DISCONTINUED shows "Về danh mục bài thi"; ORDER_NOT_PENDING shows message & reloads list
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders", 200, "orders-list-200.json")
            respondSequence(
                "POST",
                "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/pay",
                { ContractFakeServer.apiFixtureResponse(409, "orders-pay-409-order-expired.json") },
                { ContractFakeServer.apiFixtureResponse(422, "orders-pay-422-payment-attempts-exceeded.json") },
                { ContractFakeServer.apiFixtureResponse(410, "orders-pay-410-product-discontinued.json") },
                { ContractFakeServer.apiFixtureResponse(409, "orders-pay-409-order-not-pending.json") },
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = OrdersViewModel(
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            usernameProvider = { "student.demo" },
        )
        try {
            vm.loadInitial()
            host.set {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    OrdersScreen(
                        viewModel = vm,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenOrder = {},
                        onOpenMyExams = {},
                        onOpenCheckout = { openedCheckout = it },
                        onOpenCatalog = { openedCatalog = true },
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.orderContinueButton(PaymentTestFixtures.ORDER_CODE)).fetchSemanticsNodes().isNotEmpty()
            }

            // ORDER_EXPIRED -> "Tạo đơn mới"
            composeRule.onNodeWithTag(PaymentTestTags.orderContinueButton(PaymentTestFixtures.ORDER_CODE)).performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDERS_CREATE_NEW_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.ORDERS_CREATE_NEW_BUTTON).performClick()
            assertEquals("EXAM-DEMO-03", openedCheckout)

            // PAYMENT_ATTEMPTS_EXCEEDED -> still has Cancel button + "Tạo đơn mới"
            composeRule.onNodeWithTag(PaymentTestTags.orderContinueButton(PaymentTestFixtures.ORDER_CODE)).performClick()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/pay") == 2 &&
                    !vm.state.value.anyActionInFlight
            }
            composeRule.onNodeWithTag(PaymentTestTags.ORDERS_CREATE_NEW_BUTTON).assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.orderCancelButton(PaymentTestFixtures.ORDER_CODE)).assertIsDisplayed()

            // PRODUCT_DISCONTINUED -> "Về danh mục bài thi"
            composeRule.onNodeWithTag(PaymentTestTags.orderContinueButton(PaymentTestFixtures.ORDER_CODE)).performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDERS_TO_CATALOG_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.ORDERS_TO_CATALOG_BUTTON).performClick()
            assertTrue(openedCatalog)

            // ORDER_NOT_PENDING -> message displayed & list reloaded
            val listCountBefore = harness.server.requestCount("GET", "/api/v1/me/orders")
            composeRule.onNodeWithTag(PaymentTestTags.orderContinueButton(PaymentTestFixtures.ORDER_CODE)).performClick()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("GET", "/api/v1/me/orders") > listCountBefore &&
                    !vm.state.value.anyActionInFlight
            }
            composeRule.onNodeWithText("Đơn không còn ở trạng thái chờ thanh toán.").assertIsDisplayed()
        } finally {
            scope.cancel()
            harness.close()
        }

        // 2. On PaymentResultScreen: PAYMENT_ATTEMPTS_EXCEEDED shows error + "Xem chi tiết đơn", NO "Tạo đơn mới"
        val resHarness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-payment-failed.json")
            respondApiFixture("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/pay", 422, "orders-pay-422-payment-attempts-exceeded.json")
        }
        val resScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val resVm = PaymentResultViewModel(
            initialEntry = PaymentResultEntry.ByOrder(PaymentTestFixtures.ORDER_CODE),
            gateway = PaymentTestFixtures.createGateway(resHarness),
            pendingOrderStore = PendingPaymentOrderStore { resHarness.accountScopedStoreProvider.current() },
            apiBaseUrl = resHarness.server.url("/"),
            scope = resScope,
        )
        try {
            resVm.start()
            host.set {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    PaymentResultScreen(
                        viewModel = resVm,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenMyExams = {},
                        onOpenOrder = {},
                        onOpenOrders = {},
                        onOpenCheckout = {},
                        onOpenCatalog = {},
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_RETRY_PAY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_RETRY_PAY_BUTTON).performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_ACTION_ERROR_BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Đơn đã vượt quá số lần tạo giao dịch thanh toán. Vui lòng tạo đơn mới.").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_VIEW_ORDER_BUTTON).assertIsDisplayed()
            composeRule.onNodeWithText("Tạo đơn mới").assertDoesNotExist()
        } finally {
            resVm.onLeaveScreen()
            resScope.cancel()
            resHarness.close()
        }
    }
}
